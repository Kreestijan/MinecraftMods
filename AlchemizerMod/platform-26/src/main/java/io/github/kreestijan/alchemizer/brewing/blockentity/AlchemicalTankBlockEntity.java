package io.github.kreestijan.alchemizer.brewing.blockentity;

import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.tank.TankLevel;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Alchemical Tank's water level.
 *
 * <p>The level is stored as a plain {@code int} of millibuckets, not as a fluid stack. This version
 * of Minecraft has two fluid systems running side by side, and a still is forbidden from exposing a
 * fluid handler anyway; a tank is a reservoir for the brewing loop, not a pipe fitting. Keeping the
 * number plain also means the tank's arithmetic is the same code the {@code core} module
 * unit-tests.
 *
 * <p>Every write goes through {@link TankLevel}, so there is exactly one place that decides what
 * "empty", "full" and "clamped" mean.
 */
public class AlchemicalTankBlockEntity extends BlockEntity {

  /** The save key for the water level. */
  private static final String KEY_STORED_MB = "stored_mb";

  private int storedMb;

  /**
   * @param pos the tank's position in the world
   * @param state the tank's block state
   */
  public AlchemicalTankBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.ALCHEMICAL_TANK.get(), pos, state);
  }

  /**
   * @return the tank's contents and how much it can hold
   */
  public TankLevel level() {
    int capacity = capacityMb();
    // Clamped rather than trusted. The capacity is a config value, so a tank filled while it was
    // larger and then a config change that shrinks it would leave more water stored than can be
    // held, and TankLevel refuses to represent that. Reporting the tank as full is the honest
    // answer: the excess is unreachable either way, and the alternative is an exception on the
    // tick that reads it.
    return new TankLevel(Math.min(storedMb, capacity), capacity);
  }

  /**
   * Reads the capacity from the config cache rather than from a field, because the capacity is a
   * player-editable rule: a reload has to change it without the chunk being reloaded, and the cache
   * is what makes that read cheap enough to do here.
   *
   * @return how much water this tank can hold, in millibuckets
   */
  public int capacityMb() {
    return ConfigCache.current().tankCapacityMb();
  }

  /**
   * @param storedMb the new water level; clamped to what this tank can actually hold
   */
  public void setStoredMb(int storedMb) {
    this.storedMb = new TankLevel(storedMb, capacityMb()).storedMb();
    setChanged();
  }

  /**
   * Adds water, or adds nothing at all.
   *
   * <p>All or nothing because the caller is holding something that carries water, and pouring part
   * of a bucket and then refusing the rest would take water the player never got to keep. A caller
   * that wants to spread an amount over several tanks divides it up itself.
   *
   * @param amountMb how much to add
   * @return whether it fitted, and was added
   */
  public boolean tryAddWater(int amountMb) {
    TankLevel current = level();
    if (amountMb <= 0 || current.room() < amountMb) {
      return false;
    }
    setStoredMb(current.fill(amountMb).storedMb());
    return true;
  }

  /**
   * Whether a player may still have this tank's screen open.
   *
   * <p>The question comes from the menu, but the answer is the block's, so it is asked of the block
   * entity rather than assembled by each menu from its position: {@link
   * Container#stillValidBlockEntity} is the same reach-and-still-there test every vanilla container
   * menu makes, and the tank is not a {@code Container} only because it holds water rather than
   * stacks.
   *
   * @param player the player whose screen this is
   * @return whether the tank is still there and still within reach
   */
  public boolean stillValid(Player player) {
    return Container.stillValidBlockEntity(this, player);
  }

  @Override
  protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    output.putInt(KEY_STORED_MB, storedMb);
  }

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    // A level missing from the save reads as an empty tank rather than as a failure to load the
    // chunk. That covers a save written before this key existed and any future version that drops
    // it, at the cost of one integer that only ever holds water.
    storedMb = input.getIntOr(KEY_STORED_MB, 0);
  }

  /**
   * Reads the water a tank item is carrying.
   *
   * <p>A tank's item carries its block entity's own save data, so the level travels in exactly the
   * format {@link #saveAdditional} writes — which is why this reader lives here rather than beside
   * the item that asks for it: one class knows the persistence format, and the item asks it instead
   * of keeping a second copy of the key.
   *
   * <p>The raw stored figure comes back rather than a clamped one, for the same reason {@link
   * #loadAdditional} keeps it raw: the capacity is a config value, and a level that no longer fits
   * is a question for whoever displays it. {@link #level()} is where the clamping happens.
   *
   * @param stack the stack to read; anything carrying no tank save data reads as empty
   * @return the water the stack carries, in millibuckets
   */
  public static int storedMbOf(ItemStack stack) {
    TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
    return data == null ? 0 : Math.max(0, data.copyTagWithoutId().getIntOr(KEY_STORED_MB, 0));
  }
}
