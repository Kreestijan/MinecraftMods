package io.github.kreestijan.alchemizer.brewing.blockentity;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.common.item.StackSignatureFactory;
import io.github.kreestijan.alchemizer.common.potion.PotionBuilder;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.fuel.FuelRules;
import io.github.kreestijan.alchemizer.core.fuel.FuelTier;
import io.github.kreestijan.alchemizer.core.machine.StillProblem;
import io.github.kreestijan.alchemizer.core.machine.ValidationResult;
import io.github.kreestijan.alchemizer.core.potion.PotionDefinition;
import io.github.kreestijan.alchemizer.core.recipe.BrewRecipe;
import io.github.kreestijan.alchemizer.core.recipe.BrewResolver;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.recipe.IngredientSignature;
import io.github.kreestijan.alchemizer.core.tank.TankLevel;
import io.github.kreestijan.alchemizer.core.tank.TankMath;
import io.github.kreestijan.alchemizer.core.timing.BrewSession;
import io.github.kreestijan.alchemizer.core.timing.BrewTime;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.defs.ModDefinitions;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import io.github.kreestijan.alchemizer.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The Pressure Chamber's block entity: the still, as far as the rest of the mod is concerned.
 *
 * <p>It holds the ingredients, the bottle, and the brew in progress, and it is where every rule
 * about brewing is applied. The rules themselves are not here — they are in {@code core}, where
 * they can be unit-tested without a world — and this class is the assembly of them: read the slots,
 * ask the recipe book, take the water, watch the fire, hand back what came out.
 *
 * <p><b>Ingredients are spent when the brew starts, not when it finishes.</b> That is what makes a
 * failure cost something. If the still kept the ingredients until the end, a player could watch a
 * failed brew, pull everything back out and lose nothing, and the machine would stop being a
 * gamble. It also removes a whole class of question — what happens if a slot is emptied halfway
 * through — because after the start there is nothing left in the slots to change.
 *
 * <p><b>A brew pauses when the fire goes out, and is lost when the machine does.</b> Those are two
 * different things and they are treated differently. Running out of fuel holds the progress where
 * it is, and lighting the fire again resumes it: the player has paid for the time they have spent,
 * and taking it back would make the machine feel arbitrary rather than demanding. But the brew is
 * spent for good the moment the machine stops being a machine — the chunk unloading, the server
 * restarting, or any part of the still being taken away. It is not saved, and there is nothing to
 * rebuild towards; a chamber that comes back is idle. That is what makes the ingredients cost
 * something.
 *
 * <p>Nothing here exposes a capability, on purpose. A still is worked by hand: no hopper may load
 * its ingredients or empty its output, or it stops being a machine a player operates and becomes
 * one a farm operates. {@link Container} is implemented for the menu and nothing else.
 */
public class PressureChamberBlockEntity extends BlockEntity implements Container {

  /** The first of the five ingredient slots. */
  public static final int SLOT_INGREDIENT_FIRST = 0;

  /** How many ingredient slots there are. One per slot a recipe may use. */
  public static final int SLOT_INGREDIENT_COUNT = BrewRecipe.MAX_SLOTS;

  /**
   * The slot holding the glass bottles going in.
   *
   * <p>A whole stack may sit here. A brew takes the bottles it needs and leaves the rest, which is
   * what makes loading the machine a thing a player does once rather than once per potion. The
   * question the start asks is therefore whether there are <em>at least</em> enough bottles, not
   * whether there are exactly enough.
   */
  public static final int SLOT_BOTTLE = SLOT_INGREDIENT_FIRST + SLOT_INGREDIENT_COUNT;

  /**
   * The first of the two slots reserved for enhancers.
   *
   * <p>Nothing writes them and nothing reads them: enhancers arrive after the baseline, and the
   * indices exist now so that they are fixed before a menu is built against them. A slot invented
   * later, at a different index, would silently shift every index after it.
   */
  public static final int SLOT_ENHANCER_FIRST = SLOT_BOTTLE + 1;

  /** How many reserved enhancer slots there are. */
  public static final int SLOT_ENHANCER_COUNT = 2;

  /**
   * Where a finished brew's potion goes.
   *
   * <p>Its own slot rather than back into the bottle slot, which is what it used to be. Sharing
   * would mean a brew could only be started with an empty bottle slot, and a player who loaded a
   * stack of bottles would either lose the stack or lose the potion. Nothing may be <em>placed</em>
   * here by hand: the machine writes it, and a player takes it out.
   *
   * <p>Appended after the reserved enhancer slots rather than inserted before them, so that every
   * index that meant something before this slot existed still means the same thing.
   */
  public static final int SLOT_OUTPUT = SLOT_ENHANCER_FIRST + SLOT_ENHANCER_COUNT;

  /** Every slot this machine has. */
  public static final int SLOT_COUNT = SLOT_OUTPUT + 1;

  /**
   * How many potions one brew makes.
   *
   * <p>Batching arrives with the condenser, which is out of scope at tier 1, so this is one. It is
   * written as a constant rather than spelled into the checks because every one of them — bottles,
   * water, output count — is "per batch" and would have to change together.
   */
  private static final int BOTTLES_PER_BREW = 1;

  /** The saved stand-in for a brew that is going to fail. No potion id can be empty. */
  private static final String MURKY_RESULT = "";

  /** The tier a failed brew demands, since no recipe is there to name one. */
  private static final int MURKY_TIER = 1;

  private final StillCache still = new StillCache();

  private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

  private BrewSession session = BrewSession.idle();

  /** What the running brew will produce, or null when nothing is running. */
  private @Nullable PlannedBrew planned;

  /**
   * The last answer {@link #nextBrew} gave, so that a question asked every tick is answered once.
   *
   * <p>Kept only for {@link #readiness}, which the container synchroniser polls on every tick and
   * which has to answer whether the output slot could take the <em>next</em> brew's potion. Working
   * that out means matching a recipe, and matching a recipe every tick for a screen that is often
   * not even open is not a cost worth paying. Cleared by every write to the slots, and keyed on the
   * definitions snapshot so that a datapack reload invalidates it without anyone having to say so.
   */
  private @Nullable NextBrew nextBrew;

  /** The hourglass setting the player last chose. Session state, so deliberately not saved. */
  private HourglassPreset selectedPreset = HourglassPreset.FIVE;

  /**
   * A brew that has been committed to: what it will make and what tier of fire it needs.
   *
   * @param potionId the potion to produce, or {@link #MURKY_RESULT} for a brew that will fail
   * @param tier the tier the still and its fuel must both reach for the brew to advance
   */
  private record PlannedBrew(String potionId, int tier) {}

  /**
   * What a brew started right now would be, and what it would make.
   *
   * @param definitions the definitions this answer was derived from, compared by identity so that a
   *     reload discards it without anything having to announce the reload
   * @param preset the hourglass setting it was resolved at, which decides the recipe
   * @param planned the brew that would be committed to
   * @param result the stack that brew would put in the output slot
   */
  private record NextBrew(
      ModDefinitions.Snapshot definitions,
      HourglassPreset preset,
      PlannedBrew planned,
      ItemStack result) {}

  /**
   * What asking the machine to start a brew came to.
   *
   * <p>The reason travels back to the caller rather than being logged here, because the only caller
   * that matters is a player looking at a screen, and the screen is the thing that can explain
   * itself.
   */
  public enum StartResult {
    /** The brew is under way. */
    STARTED,
    /** The parts around the chamber are not a still. */
    NOT_FORMED,
    /** No ingredient slots are filled. */
    NOTHING_TO_BREW,
    /** The bottle slot does not hold enough bottles for a brew. */
    NEEDS_BOTTLES,
    /** The tank cluster does not hold enough water. */
    NEEDS_WATER,
    /** A brew is already running. */
    ALREADY_BREWING,
    /**
     * The output slot cannot take what the next brew would make.
     *
     * <p>Two situations, one answer, because they are the same thing seen twice: a brew that has
     * run its time and cannot hand over its potion, and a fresh start that would have nowhere to
     * put one. In both cases the player's move is identical — take the potions out — so the screen
     * can point at the same slot and say the same words.
     *
     * <p>Appended after {@link #ALREADY_BREWING} rather than inserted, so that every ordinal the
     * client already knows about is unchanged.
     */
    OUTPUT_FULL;

    /**
     * @param ordinal the ordinal as it was carried across the menu's data slots
     * @return the result, or empty if the ordinal is out of range
     */
    public static Optional<StartResult> ofOrdinal(int ordinal) {
      StartResult[] values = values();
      if (ordinal < 0 || ordinal >= values.length) {
        return Optional.empty();
      }
      return Optional.of(values[ordinal]);
    }
  }

  /**
   * @param pos the chamber's position in the world
   * @param state the chamber's block state
   */
  public PressureChamberBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.PRESSURE_CHAMBER.get(), pos, state);
  }

  /**
   * Says that a block near this chamber changed, so the remembered shape is no longer trustworthy.
   *
   * <p>It deliberately does not rescan. The chamber is marked and the next reader pays for one
   * scan; a player who breaks three tanks in a row causes one validation, not three.
   */
  public void markStillDirty() {
    still.markDirty();
  }

  /**
   * @return whether a brew is under way, finished or not
   */
  public boolean hasBrew() {
    return planned != null;
  }

  /**
   * Tells every chamber near a part that has just left the world to reconsider itself.
   *
   * <p>This is how a still learns it has been broken. It is called from the part's block, on the
   * removal path itself, rather than from a rule polled every tick — and that distinction is the
   * whole design. A validator cannot tell a cell that is empty from a cell whose chunk is not
   * loaded, because it reads the world through a view that answers "nothing here" to both. A
   * per-tick "the still is unformed, so lose the brew" would therefore destroy brews that were
   * never broken, the moment a player walked far enough away. A removal hook asks the one question
   * that has a real answer: did a part actually go?
   *
   * <p>The scan radius is the validator's own, which is the furthest a chamber could be and still
   * have had this cell as a part.
   *
   * <p>Called during a block change, where an exception escaping would take the server with it, so
   * everything is caught. A failed revalidation leaves a stale cache, which the next block change
   * repairs; a crash leaves nothing.
   *
   * @param level the server level the part was removed from
   * @param removed where the part was
   */
  public static void partRemovedNear(ServerLevel level, BlockPos removed) {
    try {
      int radius = ConfigCache.current().scanRadius();
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      for (int x = -radius; x <= radius; x++) {
        for (int y = -radius; y <= radius; y++) {
          for (int z = -radius; z <= radius; z++) {
            cursor.set(removed.getX() + x, removed.getY() + y, removed.getZ() + z);
            if (level.getBlockEntity(cursor) instanceof PressureChamberBlockEntity chamber) {
              chamber.onPartRemoved();
            }
          }
        }
      }
    } catch (RuntimeException failure) {
      Alchemizer.LOGGER.error(
          "Alchemizer: could not check stills near a removed part at {} in {}",
          removed,
          level.dimension().identifier(),
          failure);
    }
  }

  /**
   * Re-checks this chamber now that a machine part near it has been taken away, and gives up the
   * brew if the still is no longer whole.
   *
   * <p>The still is re-scanned rather than assumed broken. A tank standing next to a working still
   * but not connected to it is a perfectly ordinary thing to have, and breaking that one must leave
   * the brew alone; only a fresh look can tell the two cases apart.
   */
  private void onPartRemoved() {
    markStillDirty();
    if (planned != null && !still().isFormed()) {
      planned = null;
      session = BrewSession.idle();
      setChanged();
    }
  }

  /**
   * @return whether the parts around this chamber currently form a still, and why not when they do
   *     not
   */
  public ValidationResult still() {
    Level level = getLevel();
    if (level == null) {
      // A block entity that is not in a world has no surroundings to judge. This is not reachable
      // for a placed block; it is here because the alternative would be a null check at every call
      // site, and answering "no still" is both safe and honest.
      return ValidationResult.invalid(StillProblem.NOT_IN_A_WORLD);
    }
    return still.validation(level, worldPosition);
  }

  /**
   * @return where the tanks feeding this still are, or nothing when it is not formed
   */
  public Set<BlockPos> connectedTanks() {
    Level level = getLevel();
    if (level == null) {
      return Set.of();
    }
    return still.tankPositions(level, worldPosition);
  }

  /**
   * The chamber's tick, reached from {@link io.github.kreestijan.alchemizer.brewing.block
   * .PressureChamberBlock#getTicker}.
   *
   * @param level the level, always a server level because the block hands out no client ticker
   * @param pos the chamber's position
   * @param state the chamber's state, which may be one tick stale
   * @param chamber the block entity being ticked
   */
  public static void serverTick(
      Level level, BlockPos pos, BlockState state, PressureChamberBlockEntity chamber) {
    chamber.tick(level);
  }

  private void tick(Level level) {
    PlannedBrew current = planned;
    if (current == null) {
      return;
    }

    if (session.isRunning() && canProgress(level, current)) {
      session = session.advance(true);
      setChanged();
    }

    if (session.isComplete()) {
      finish(current);
    }
  }

  /**
   * @param level the server level
   * @param current the brew in progress
   * @return whether the brew may advance this tick
   */
  private boolean canProgress(Level level, PlannedBrew current) {
    ValidationResult formed = still();
    if (!formed.isFormed()) {
      // A still that is not whole is not a working machine, so the brew stops. It is held rather
      // than thrown away here, because this is the fallback path: a part that is really removed
      // discards the brew at the moment it goes, in onPartRemoved. What is left for this check is a
      // machine the validator cannot see all of — a part whose chunk has unloaded — and holding is
      // the honest answer to that, since nothing was broken.
      return false;
    }
    Tuning tuning = ConfigCache.current();
    return FuelRules.mayBrew(burningFuel(level), formed.tier().tier(), current.tier(), tuning);
  }

  /**
   * Reads the fire out of the ignition chamber this still is built on.
   *
   * <p>The position is not searched for: a formed tier-1 still has exactly one ignition chamber and
   * the validator requires it to be the cell below the pressure chamber, so asking anywhere else
   * would be asking a question already answered.
   *
   * @param level the server level
   * @return the fuel alight below, or empty when there is no fire
   */
  private Optional<FuelTier> burningFuel(Level level) {
    BlockPos firebox = worldPosition.below();
    if (!level.isLoaded(firebox)) {
      return Optional.empty();
    }
    return level.getBlockEntity(firebox) instanceof IgnitionChamberBlockEntity chamber
        ? chamber.burningFuel()
        : Optional.empty();
  }

  /**
   * Hands the finished brew to the player.
   *
   * <p>Called every tick once the brew is complete, and does nothing at all until the output slot
   * can take the result. A brew that finishes with nowhere to put its potion therefore waits at
   * full, exactly the way a furnace does, rather than dropping its output on the floor.
   *
   * @param current the brew that has run its time
   */
  private void finish(PlannedBrew current) {
    ItemStack result = resultOf(current);
    if (!canAccept(SLOT_OUTPUT, result)) {
      return;
    }

    mergeInto(SLOT_OUTPUT, result);
    planned = null;
    session = BrewSession.idle();
    setChanged();
  }

  /**
   * Puts a result into a slot, stacking it onto what is already there.
   *
   * <p>Written against {@link #items} rather than {@link #setItem}, which would clamp through
   * {@link #getMaxStackSize} and, more to the point, would run the invalidation this method's
   * callers do themselves. The caller has already asked {@link #canAccept}, so the addition cannot
   * overflow.
   *
   * @param slot the slot to write
   * @param result the stack to put there
   */
  private void mergeInto(int slot, ItemStack result) {
    ItemStack existing = items.get(slot);
    if (existing.isEmpty()) {
      items.set(slot, result);
    } else {
      existing.grow(result.getCount());
    }
    invalidateNextBrew();
  }

  /**
   * @param current the brew that has run its time
   * @return what it produced
   */
  private ItemStack resultOf(PlannedBrew current) {
    if (current.potionId().equals(MURKY_RESULT)) {
      return new ItemStack(ModItems.MURKY_MESS.get(), BOTTLES_PER_BREW);
    }

    Optional<PotionDefinition> definition = ModDefinitions.current().potion(current.potionId());
    if (definition.isEmpty()) {
      // Reachable when a datapack removes a potion while a still is mid-brew, or on a reload that
      // drops the file. The brew still finishes; it just finishes as the failure it has become.
      Alchemizer.LOGGER.warn(
          "A brew at {} was making {}, which is no longer defined; it finished as murky mess",
          worldPosition,
          current.potionId());
      return new ItemStack(ModItems.MURKY_MESS.get(), BOTTLES_PER_BREW);
    }

    return PotionBuilder.build(Items.POTION, definition.get());
  }

  /**
   * Starts a brew, spending the ingredients, the bottles and the water.
   *
   * <p>Everything is decided here and nothing is decided later: the recipe is matched once, and
   * what it produced is remembered as an id rather than re-derived at the end. A reload between the
   * start and the finish can therefore change what a potion <em>is</em>, but not which potion this
   * brew is making.
   *
   * @param preset the hourglass setting the player chose
   * @return what happened, for the caller to explain
   */
  public StartResult startBrew(HourglassPreset preset) {
    Objects.requireNonNull(preset, "preset");

    ValidationResult formed = still();
    // Asked at the preset this call will commit to, so the two cannot disagree about the recipe.
    StartResult ready = readiness(formed, preset);
    if (ready != StartResult.STARTED) {
      return ready;
    }

    Tuning tuning = ConfigCache.current();
    int waterNeeded = TankMath.cost(BOTTLES_PER_BREW, tuning.doseMb());

    // Read before the slots are spent, because spending them is what changes the answer. Resolving
    // through the same memo the readiness check used is what guarantees the brew that starts is the
    // one that was approved, rather than a second match that could in principle disagree.
    PlannedBrew next = nextBrew(preset).planned();

    spendIngredients();
    items.get(SLOT_BOTTLE).shrink(BOTTLES_PER_BREW);
    withdrawWater(waterNeeded);
    invalidateNextBrew();

    this.planned = next;
    session = BrewSession.start(BrewTime.ticks(preset, formed.tier().pressureSources(), tuning));
    setChanged();
    return StartResult.STARTED;
  }

  /**
   * What a brew started at this moment would come to, without starting one.
   *
   * <p>This is the answer a screen draws: it is what decides which part of the machine is
   * highlighted as the thing standing in the way, and whether a brew would be accepted at all.
   *
   * <p>{@link StartResult#STARTED} here means "every condition is met", not "a brew is running".
   * That is what lets one enum answer both questions — a started brew and a startable one are the
   * same state from the machine's point of view, and the alternative would be a second enum whose
   * values are this one's minus the ones that do not apply.
   *
   * <p>Cheap enough to poll every tick, with one exception that is deliberately narrow. Every
   * question it asks can be answered from the slots alone — matching a recipe can never refuse a
   * start, because a layout no recipe describes brews Murky Mess. The one question that does need
   * the recipe is whether the output slot could take what the next brew would make, and that is
   * only asked when the output slot is non-empty, which is exactly when it matters. The answer is
   * then remembered by {@link #nextBrew}, so a full output slot costs one match rather than one per
   * tick.
   *
   * @return why a brew would be refused, or {@link StartResult#STARTED} if it would not be
   */
  public StartResult readiness() {
    return readiness(still(), selectedPreset);
  }

  /**
   * @param formed the already-resolved shape of the machine, so a caller that has one does not pay
   *     for a second look
   * @param preset the hourglass setting to judge the start at, so that a start is never approved
   *     against one recipe and then committed to another
   * @return why a brew would be refused, or {@link StartResult#STARTED} if it would not be
   */
  private StartResult readiness(ValidationResult formed, HourglassPreset preset) {
    if (planned != null) {
      // A brew whose time is up but which is still holding `planned` is one `finish` refused, and
      // the only thing that refuses it is an output slot that cannot take the result. So the two
      // states are distinguishable without asking the recipe again. The screen may say OUTPUT_FULL
      // one tick after it became true, because `finish` runs after this check.
      return session.isComplete() ? StartResult.OUTPUT_FULL : StartResult.ALREADY_BREWING;
    }
    if (!formed.isFormed()) {
      return StartResult.NOT_FORMED;
    }
    if (ingredientStacks().stream().allMatch(ItemStack::isEmpty)) {
      return StartResult.NOTHING_TO_BREW;
    }

    ItemStack bottles = items.get(SLOT_BOTTLE);
    // At least enough, not exactly enough: the slot holds a stack and a brew takes its share.
    if (bottles.getItem() != Items.GLASS_BOTTLE || bottles.getCount() < BOTTLES_PER_BREW) {
      return StartResult.NEEDS_BOTTLES;
    }

    // Asked before the water so that every refusal the screen draws as a highlighted slot comes
    // before the one it draws on the gauge. Asked at the preset the caller named rather than at the
    // remembered one, so that a start cannot be approved against one recipe and committed to
    // another.
    if (!items.get(SLOT_OUTPUT).isEmpty() && !canAccept(SLOT_OUTPUT, nextBrew(preset).result())) {
      return StartResult.OUTPUT_FULL;
    }

    Tuning tuning = ConfigCache.current();
    if (!clusterLevel(tuning).canWithdraw(TankMath.cost(BOTTLES_PER_BREW, tuning.doseMb()))) {
      return StartResult.NEEDS_WATER;
    }
    return StartResult.STARTED;
  }

  /**
   * What a brew started with this hourglass setting would be, and what it would make.
   *
   * <p>The answer is remembered because {@link #readiness} asks for it on a tick, and matching a
   * recipe is real work: it builds five ingredient signatures and walks the recipe book. Kept keyed
   * on the definitions snapshot by identity, so a datapack reload drops it with nothing having to
   * announce the reload, and on the preset, because the preset picks the recipe. Cleared by {@link
   * #invalidateNextBrew} from every write that could change the answer.
   *
   * <p>This resolves a recipe but never refuses one: a layout nothing matches is still a brew, and
   * its result is the Murky Mess {@link #resultOf} would hand over.
   *
   * @param preset the hourglass setting to resolve at
   * @return the brew that would be committed to, and the stack it would put in the output slot
   */
  private NextBrew nextBrew(HourglassPreset preset) {
    ModDefinitions.Snapshot definitions = ModDefinitions.current();
    NextBrew cached = nextBrew;
    if (cached != null && cached.definitions() == definitions && cached.preset() == preset) {
      return cached;
    }

    List<IngredientSignature> signatures = StackSignatureFactory.ofFive(ingredientStacks());
    Optional<BrewRecipe> recipe =
        BrewResolver.resolveRecipe(signatures, preset, definitions.recipes());
    PlannedBrew plannedBrew =
        recipe
            .map(matched -> new PlannedBrew(matched.potion().potionId(), matched.tier()))
            .orElseGet(() -> new PlannedBrew(MURKY_RESULT, MURKY_TIER));
    NextBrew resolved = new NextBrew(definitions, preset, plannedBrew, resultOf(plannedBrew));
    nextBrew = resolved;
    return resolved;
  }

  /** Forgets the remembered answer, because something that could change it changed. */
  private void invalidateNextBrew() {
    nextBrew = null;
  }

  /**
   * @return whether the fire below should be burning: a brew is under way, unfinished, and the
   *     still is whole
   */
  public boolean wantsFire() {
    return planned != null && session.isRunning() && still().isFormed();
  }

  /**
   * Adds water to the cluster, a tank at a time, or nothing at all.
   *
   * <p>All or nothing on purpose: the caller is holding a bucket, and a bucket that is half-poured
   * and then refused would leave the player having paid for water they never got. The room is
   * summed before a drop is placed, so a cluster with room for exactly the amount accepts it and a
   * cluster one millibucket short refuses it whole.
   *
   * @param amountMb how much to add in total
   * @return whether it all fitted, and was all added
   */
  public boolean tryAddWater(int amountMb) {
    if (amountMb <= 0) {
      return false;
    }
    Level level = getLevel();
    if (level == null) {
      return false;
    }

    List<AlchemicalTankBlockEntity> tanks = new ArrayList<>();
    long room = 0;
    for (BlockPos pos : sortedTankPositions()) {
      if (level.getBlockEntity(pos) instanceof AlchemicalTankBlockEntity tank) {
        tanks.add(tank);
        room += tank.level().room();
      }
    }
    if (room < amountMb) {
      return false;
    }

    int remaining = amountMb;
    for (AlchemicalTankBlockEntity tank : tanks) {
      if (remaining <= 0) {
        break;
      }
      int offered = Math.min(tank.level().room(), remaining);
      if (offered > 0 && tank.tryAddWater(offered)) {
        remaining -= offered;
      }
    }
    return remaining == 0;
  }

  /**
   * The water held by every tank feeding this still, added up.
   *
   * <p>This is what the screen's gauge shows. It is the cluster's total rather than this block's,
   * because a still with two tanks holds twice as much and a player who filled both should see
   * that.
   *
   * @return the cluster's water, or an empty tank's worth when this chamber is not in a world
   */
  public TankLevel clusterWater() {
    return clusterLevel(ConfigCache.current());
  }

  /**
   * @return whether the firebox below is burning, which is not the same as whether a brew may
   *     advance — a wood fire is lit and powers nothing
   */
  public boolean isLit() {
    Level level = getLevel();
    return level != null && burningFuel(level).isPresent();
  }

  /**
   * @return the hourglass setting the player last chose, which the next brew will use
   */
  public HourglassPreset selectedPreset() {
    return selectedPreset;
  }

  /**
   * Records the player's hourglass choice.
   *
   * <p>Pending rather than saved: it is which button is lit on a screen the player is looking at,
   * and a still that remembered a time from three sessions ago would apply it to ingredients the
   * player had long forgotten choosing them for.
   *
   * @param preset the chosen setting
   */
  public void selectPreset(HourglassPreset preset) {
    this.selectedPreset = Objects.requireNonNull(preset, "preset");
    setChanged();
  }

  /**
   * @return the brew in progress, at the point it has reached
   */
  public BrewSession brewSession() {
    return session;
  }

  /**
   * @return the potion the running brew will produce, or empty when nothing is running
   */
  public Optional<String> brewingPotion() {
    PlannedBrew current = planned;
    return current == null || current.potionId().equals(MURKY_RESULT)
        ? Optional.empty()
        : Optional.of(current.potionId());
  }

  /** Takes one item from each filled ingredient slot. */
  private void spendIngredients() {
    for (int slot = SLOT_INGREDIENT_FIRST; slot < SLOT_BOTTLE; slot++) {
      items.get(slot).shrink(1);
    }
  }

  /**
   * @param tuning the tuning in force
   * @return the water held by every tank feeding this still, added up
   */
  private TankLevel clusterLevel(Tuning tuning) {
    Level level = getLevel();
    if (level == null) {
      return TankLevel.empty(tuning.tankCapacityMb());
    }

    int stored = 0;
    int capacity = 0;
    for (BlockPos pos : connectedTanks()) {
      if (level.getBlockEntity(pos) instanceof AlchemicalTankBlockEntity tank) {
        TankLevel level1 = tank.level();
        stored += level1.storedMb();
        capacity += level1.capacityMb();
      }
    }
    return capacity > 0
        ? new TankLevel(stored, capacity)
        : TankLevel.empty(tuning.tankCapacityMb());
  }

  /**
   * Takes water out of the cluster, a tank at a time.
   *
   * <p>The tanks are visited in a fixed order rather than in whatever order the set happens to hand
   * them over. The total taken is the same either way, but the level left in any one tank is not,
   * and a player watching two tanks drain unevenly from one brew to the next would be right to call
   * it a bug.
   *
   * @param amountMb how much to take in total
   */
  private void withdrawWater(int amountMb) {
    Level level = getLevel();
    if (level == null || amountMb <= 0) {
      return;
    }

    int remaining = amountMb;
    for (BlockPos pos : sortedTankPositions()) {
      if (remaining <= 0) {
        break;
      }
      if (!(level.getBlockEntity(pos) instanceof AlchemicalTankBlockEntity tank)) {
        continue;
      }
      int available = tank.level().storedMb();
      int taken = Math.min(available, remaining);
      if (taken > 0) {
        tank.setStoredMb(available - taken);
        remaining -= taken;
      }
    }
  }

  /**
   * The positions of the tanks feeding this still, in the order water is taken from and given to
   * them.
   *
   * <p>Lowest first, then west to east, then north to south. Any fixed order would do; what matters
   * is that the drain and the fill use the same one, which is why they share this rather than each
   * sorting for themselves.
   *
   * <p>The coordinates are read through lambdas with their parameter type written out rather than
   * as {@code BlockPos::getY}. {@link BlockPos} has both an instance {@code getY()} and a static
   * {@code getY(long)}, which packs a position into a node, so the bare method reference is
   * ambiguous and the compiler resolves it to the static one.
   *
   * @return the tank positions in that order
   */
  private List<BlockPos> sortedTankPositions() {
    List<BlockPos> positions = new ArrayList<>(connectedTanks());
    Comparator<BlockPos> tankOrder =
        Comparator.comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getX())
            .thenComparingInt(pos -> pos.getZ());
    positions.sort(tankOrder);
    return positions;
  }

  /**
   * @return the five ingredient slots, in slot order
   */
  private List<ItemStack> ingredientStacks() {
    return List.copyOf(items.subList(SLOT_INGREDIENT_FIRST, SLOT_BOTTLE));
  }

  /**
   * Writes the chamber's slots, and deliberately nothing else.
   *
   * <p>A brew in progress is not saved. It is spent the moment it starts — the ingredients, the
   * bottles and the water are already gone — and a chamber that comes back from disk is a chamber
   * with nothing brewing, which is the same state it is in after a reload for any other reason. The
   * slots still persist, because those are the player's, not the brew's.
   */
  @Override
  protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    ContainerHelper.saveAllItems(output, items);
  }

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    ContainerHelper.loadAllItems(input, items);
    invalidateNextBrew();
  }

  // Container, for the menu. Not a capability: see the class comment.

  @Override
  public int getContainerSize() {
    return SLOT_COUNT;
  }

  @Override
  public boolean isEmpty() {
    for (ItemStack stack : items) {
      if (!stack.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public ItemStack getItem(int slot) {
    checkSlot(slot);
    return items.get(slot);
  }

  @Override
  public ItemStack removeItem(int slot, int count) {
    checkSlot(slot);
    ItemStack stack = items.get(slot);
    if (stack.isEmpty() || count <= 0) {
      return ItemStack.EMPTY;
    }

    // Handing back the stack itself when it is all being taken, and a split-off copy when it is
    // not, is the same shape every vanilla container uses; the difference matters because split
    // mutates the slot's stack and the whole-stack case must not.
    ItemStack removed;
    if (stack.getCount() <= count) {
      removed = stack;
      items.set(slot, ItemStack.EMPTY);
    } else {
      removed = stack.split(count);
    }
    invalidateNextBrew();
    setChanged();
    return removed;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    checkSlot(slot);
    ItemStack removed = items.get(slot);
    items.set(slot, ItemStack.EMPTY);
    invalidateNextBrew();
    return removed;
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    checkSlot(slot);
    Objects.requireNonNull(stack, "stack");
    int max = getMaxStackSize(stack);
    // Copied only when clamping, so the usual path keeps the caller's stack and the rare one does
    // not leave the caller holding a stack whose count was quietly changed underneath it.
    items.set(slot, stack.getCount() > max ? stack.copyWithCount(max) : stack);
    invalidateNextBrew();
    setChanged();
  }

  @Override
  public int getMaxStackSize() {
    return 64;
  }

  @Override
  public void clearContent() {
    items.clear();
    invalidateNextBrew();
    setChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return Container.stillValidBlockEntity(this, player);
  }

  /**
   * @param slot the slot being offered an item
   * @param stack the item being placed
   * @return whether that item may go there
   */
  @Override
  public boolean canPlaceItem(int slot, ItemStack stack) {
    if (slot == SLOT_OUTPUT) {
      // Said out loud rather than left to the catch-all below. The catch-all happens to cover this
      // index today, but only because the output slot was appended after the reserved ones; a
      // refusal a player depends on should not rest on where a constant happens to sit.
      return false;
    }
    if (slot >= SLOT_ENHANCER_FIRST) {
      // Reserved for enhancers, which nothing can supply yet. Refusing here rather than hiding the
      // slots is what makes the refusal real: a menu cannot be the only thing standing between a
      // player and a slot index.
      return false;
    }
    if (slot == SLOT_BOTTLE) {
      return stack.isEmpty() || stack.getItem() == Items.GLASS_BOTTLE;
    }
    return true;
  }

  /**
   * @param slot the slot to accept a result into
   * @param result the stack that would go there
   * @return whether the slot has room for it
   */
  private boolean canAccept(int slot, ItemStack result) {
    ItemStack existing = items.get(slot);
    if (existing.isEmpty()) {
      return true;
    }
    if (!ItemStack.isSameItemSameComponents(existing, result)) {
      return false;
    }
    return existing.getCount() + result.getCount() <= existing.getMaxStackSize();
  }

  private static void checkSlot(int slot) {
    if (slot < 0 || slot >= SLOT_COUNT) {
      throw new IndexOutOfBoundsException(
          "Pressure chamber has " + SLOT_COUNT + " slots, got index " + slot);
    }
  }
}
