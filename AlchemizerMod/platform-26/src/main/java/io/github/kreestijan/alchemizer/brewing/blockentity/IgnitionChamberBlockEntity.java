package io.github.kreestijan.alchemizer.brewing.blockentity;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.block.IgnitionChamberBlock;
import io.github.kreestijan.alchemizer.common.fuel.FuelTags;
import io.github.kreestijan.alchemizer.core.fuel.BurnState;
import io.github.kreestijan.alchemizer.core.fuel.BurnTracker;
import io.github.kreestijan.alchemizer.core.fuel.FuelTier;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The Ignition Chamber's single fuel slot and its fire.
 *
 * <p>The slot and the fire are deliberately two different things. The slot holds what has not been
 * lit yet; the burn state is what is alight. One item leaves the slot when the fire is lit and the
 * next one leaves it when the fire goes out, which is what makes a stack of coal behave the way a
 * player expects a stack of coal to behave.
 *
 * <p><b>Nothing lights until a brew wants it.</b> A loaded fuel sits in the slot, and the chamber
 * asks the pressure chamber above whether a brew is under way before taking one. Idle fuel used to
 * burn itself out doing nothing, which made loading the machine ahead of time a way to lose coal.
 * The gating is on the lighting only: once alight, a fuel burns down to nothing regardless.
 *
 * <p>The chamber holds no {@code ItemStackHandler} and exposes no capability, on purpose. A still
 * is worked by hand — nothing may pipe fuel into it, or pull an ingredient out of it, or it stops
 * being a machine a player operates and becomes one a farm operates.
 *
 * <p>The burn count is not synchronised to the client. The server is the only side that ticks it,
 * and the client learns everything it needs from the block's lit state.
 */
public class IgnitionChamberBlockEntity extends BlockEntity implements Container {

  /** Save keys. These are also the keys a {@code /data} command addresses. */
  private static final String KEY_FUEL = "fuel";

  private static final String KEY_BURN_REMAINING = "burn_remaining";
  private static final String KEY_BURN_TOTAL = "burn_total";
  private static final String KEY_BURN_TIER = "burn_tier";

  /** There is one slot, so its index is a constant rather than something to iterate over. */
  private static final int SLOT_FUEL = 0;

  private static final int SLOT_COUNT = 1;

  private ItemStack fuel = ItemStack.EMPTY;

  private BurnState burn = BurnState.cold();

  /**
   * How long the fuel currently alight was going to burn for, so a gauge can draw a fraction rather
   * than a countdown.
   */
  private int burnTotalTicks;

  /**
   * The last item this chamber refused to light, so the refusal is reported once instead of every
   * tick. Not saved: it exists only to keep one run's log readable.
   */
  private @Nullable Item unlightableReported;

  /**
   * @param pos the chamber's position in the world
   * @param state the chamber's block state
   */
  public IgnitionChamberBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.IGNITION_CHAMBER.get(), pos, state);
  }

  /**
   * What is alight right now.
   *
   * <p>This is how the pressure chamber above asks whether it is allowed to work. The fire belongs
   * to this chamber and the brew belongs to that one, so the question crosses a block boundary, and
   * the answer has to be the same {@link FuelTier} the burn was lit from rather than a second
   * opinion about the item.
   *
   * @return the fuel burning, or empty when the chamber is cold
   */
  public Optional<FuelTier> burningFuel() {
    return burn.fuel();
  }

  /**
   * @return whether anything is alight, which is what the screen's flame either shows or does not
   */
  public boolean isLit() {
    return burn.isLit();
  }

  /**
   * @return ticks of burn left on the fuel alight, or zero when the chamber is cold
   */
  public int burnRemainingTicks() {
    return burn.remainingTicks();
  }

  /**
   * @return how long the fuel alight was going to burn for, or zero when the chamber is cold
   */
  public int burnTotalTicks() {
    return burnTotalTicks;
  }

  /**
   * The chamber's tick, reached from {@link IgnitionChamberBlock#getTicker}.
   *
   * @param level the level, always a server level because the block hands out no client ticker
   * @param pos the chamber's position
   * @param state the chamber's state, which may be one tick stale
   * @param chamber the block entity being ticked
   */
  public static void serverTick(
      Level level, BlockPos pos, BlockState state, IgnitionChamberBlockEntity chamber) {
    chamber.tick(level, pos, state);
  }

  private void tick(Level level, BlockPos pos, BlockState state) {
    boolean wasLit = burn.isLit();

    if (wasLit) {
      // Once alight, a fuel burns to exhaustion whether or not anything is brewing. Only the
      // lighting is gated on a brew wanting the fire; gating the burn-down as well would mean a
      // coal that stops burning whenever the player closes the screen, which is not what a fuel is.
      burn = BurnTracker.tick(burn);
      if (!burn.isLit()) {
        // The fire is out; the gauge has nothing left to be a fraction of.
        burnTotalTicks = 0;
      }
    } else if (wantsFireAbove(level)) {
      lightNextFuel(level);
    }

    boolean isLit = burn.isLit();
    if (isLit != state.getValue(IgnitionChamberBlock.LIT)) {
      // UPDATE_ALL rather than a lighter flag: the lit state is what a client renders, so it has to
      // reach the client, and the neighbours care because the light level changes with it.
      level.setBlock(pos, state.setValue(IgnitionChamberBlock.LIT, isLit), Block.UPDATE_ALL);
    }
    if (wasLit || isLit) {
      // Only a tick that burned something, or lit something, has changed the saved state. Marking
      // the chunk dirty every tick for an empty cold chamber would keep it being written forever.
      setChanged();
    }
  }

  /**
   * Asks the pressure chamber above whether it wants a fire.
   *
   * <p>The cell above is not searched for, because a formed still requires the chamber to sit
   * directly on this block — the same geometry {@code PressureChamberBlockEntity.burningFuel} reads
   * from the other side.
   *
   * <p>Both directions of this conversation are plain field reads. Neither chamber ticks the other,
   * and neither call can reenter, so the worst a tick order can do is light the fire one tick
   * either side of the brew needing it.
   *
   * @param level the server level
   * @return whether the chamber above is running a brew
   */
  private boolean wantsFireAbove(Level level) {
    BlockPos above = worldPosition.above();
    if (!level.isLoaded(above)) {
      // An unloaded neighbour is not a reason to spend fuel. The player is somewhere else, and the
      // chamber above cannot be brewing if its chunk is not loaded.
      return false;
    }
    return level.getBlockEntity(above) instanceof PressureChamberBlockEntity chamber
        && chamber.wantsFire();
  }

  /**
   * Takes one item from the slot and sets it alight, if it can be.
   *
   * <p>Two separate questions, and both have to be answered yes. The tag decides what tier the fuel
   * is worth, and the vanilla fuel table decides how long it burns; an item with a tier and no burn
   * time would light a fire that goes out on the same tick, and an item with a burn time and no
   * tier would burn without ever being able to power a brew. Rather than guess, the chamber leaves
   * the item alone and says so once.
   *
   * @param level the server level, which is where the fuel table lives
   */
  private void lightNextFuel(Level level) {
    if (fuel.isEmpty()) {
      return;
    }
    Optional<FuelTier> tier = FuelTags.tierOf(fuel);
    // The recipe type is null because a fuel table is not per-recipe-type in this version: the same
    // number answers for smelting, blasting and this chamber alike.
    int burnTicks = fuel.getBurnTime(null, level.fuelValues());

    if (tier.isEmpty() || burnTicks <= 0) {
      reportUnlightable(tier, burnTicks);
      return;
    }

    burn = BurnState.burning(burnTicks, tier.get());
    burnTotalTicks = burnTicks;
    unlightableReported = null;
    removeItem(SLOT_FUEL, 1);
  }

  /**
   * @param tier the tier the item resolved to, if any
   * @param burnTicks what the fuel table says, which may be zero
   */
  private void reportUnlightable(Optional<FuelTier> tier, int burnTicks) {
    Item item = fuel.getItem();
    if (item == unlightableReported) {
      return;
    }
    unlightableReported = item;
    Alchemizer.LOGGER.warn(
        "Ignition chamber at {} will not light {}: fuel tier {} and burn time {} ticks."
            + " A fuel needs both an entry in the alchemizer fuel tier tags and a burn time in"
            + " the neoforge:furnace_fuels data map.",
        worldPosition,
        BuiltInRegistries.ITEM.getKey(item),
        tier.map(FuelTier::tier).map(String::valueOf).orElse("none"),
        burnTicks);
  }

  @Override
  protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    // The optional codec writes nothing at all for an empty slot, so an empty chamber saves one
    // fewer key rather than an item stack that says "air".
    output.store(KEY_FUEL, ItemStack.OPTIONAL_CODEC, fuel);
    output.putInt(KEY_BURN_REMAINING, burn.remainingTicks());
    output.putInt(KEY_BURN_TOTAL, burnTotalTicks);
    output.putInt(KEY_BURN_TIER, burn.fuelTier());
  }

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    fuel = input.read(KEY_FUEL, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    burnTotalTicks = Math.max(0, input.getIntOr(KEY_BURN_TOTAL, 0));
    burn =
        readBurn(
            input.getIntOr(KEY_BURN_REMAINING, 0),
            input.getIntOr(KEY_BURN_TIER, BurnState.NO_FUEL_TIER));
  }

  /**
   * Rebuilds the burn state from a save, putting the fire out rather than failing if the two
   * numbers disagree.
   *
   * <p>A chamber that loses its remaining burn time loses nothing a player paid for twice — the
   * fuel item was already consumed either way — whereas throwing here would fail the chunk load and
   * take the rest of the chunk's block entities with it.
   *
   * @param remainingTicks the saved remaining burn time
   * @param fuelTier the saved tier of the fuel alight
   * @return the burn state, or a cold chamber if the save was inconsistent
   */
  private static BurnState readBurn(int remainingTicks, int fuelTier) {
    try {
      return new BurnState(remainingTicks, fuelTier);
    } catch (IllegalArgumentException inconsistent) {
      Alchemizer.LOGGER.warn(
          "Ignition chamber burn state was inconsistent ({} ticks at tier {}); putting the fire out",
          remainingTicks,
          fuelTier,
          inconsistent);
      return BurnState.cold();
    }
  }

  // Container, for the menu. Not a capability: see the class comment.

  @Override
  public int getContainerSize() {
    return SLOT_COUNT;
  }

  @Override
  public int getMaxStackSize() {
    return 64;
  }

  @Override
  public boolean isEmpty() {
    return fuel.isEmpty();
  }

  @Override
  public ItemStack getItem(int slot) {
    checkSlot(slot);
    return fuel;
  }

  @Override
  public ItemStack removeItem(int slot, int count) {
    checkSlot(slot);
    if (fuel.isEmpty() || count <= 0) {
      return ItemStack.EMPTY;
    }
    // Handing back the stack itself when it is all being taken, and a split-off copy when it is
    // not,
    // is the same shape every vanilla container uses; the difference matters because split mutates
    // the slot's stack and the whole-stack case must not.
    ItemStack removed;
    if (fuel.getCount() <= count) {
      removed = fuel;
      fuel = ItemStack.EMPTY;
    } else {
      removed = fuel.split(count);
    }
    setChanged();
    return removed;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    checkSlot(slot);
    ItemStack removed = fuel;
    fuel = ItemStack.EMPTY;
    return removed;
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    checkSlot(slot);
    Objects.requireNonNull(stack, "stack");
    int max = getMaxStackSize(stack);
    // Copied only when clamping, so the usual path keeps the caller's stack and the rare one does
    // not leave the caller holding a stack whose count was quietly changed underneath it.
    fuel = stack.getCount() > max ? stack.copyWithCount(max) : stack;
    setChanged();
  }

  @Override
  public void clearContent() {
    fuel = ItemStack.EMPTY;
    setChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return Container.stillValidBlockEntity(this, player);
  }

  /**
   * The slot takes fuel and nothing else.
   *
   * <p>"Fuel" means an item some fuel tier tag claims, not merely an item with a burn time. The
   * tags are what the design says define the ladder, so an item off the ladder has no tier to brew
   * at and would only sit in the slot looking usable.
   */
  @Override
  public boolean canPlaceItem(int slot, ItemStack stack) {
    return slot == SLOT_FUEL && (stack.isEmpty() || FuelTags.tierOf(stack).isPresent());
  }

  private static void checkSlot(int slot) {
    if (slot != SLOT_FUEL) {
      throw new IndexOutOfBoundsException("Ignition chamber has one slot, got index " + slot);
    }
  }
}
