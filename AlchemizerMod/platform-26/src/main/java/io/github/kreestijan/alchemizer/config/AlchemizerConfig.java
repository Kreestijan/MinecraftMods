package io.github.kreestijan.alchemizer.config;

import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import io.github.kreestijan.alchemizer.core.value.ValueFactors;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's config file, and the only place a number a player can change is declared.
 *
 * <p>The shape of this file is deliberate: every entry is a straight read of a constant in {@link
 * TuningDefaults}, and the range each entry advertises is the range the same constant declares. The
 * defaults and their bounds therefore cannot disagree about what is legal, and {@code
 * TuningDefaultsTest} asserts the shipping value sits inside its own bounds.
 *
 * <p>Nothing reads these values directly. {@link #read()} assembles them into one {@link Tuning},
 * which is what the rest of the mod passes around; that keeps "where did this number come from"
 * answerable from a single declaration, and keeps the rules testable without a config file.
 *
 * <p>This config is {@code COMMON}, which in practice means it belongs to the server. Every value
 * here changes a rule rather than a picture, so a client that disagreed with the server about them
 * would be a client that lies.
 */
public final class AlchemizerConfig {

  public static final ModConfigSpec SPEC;

  private static final ModConfigSpec.IntValue MACHINE_TIER_CAP;
  private static final ModConfigSpec.BooleanValue FUEL_TIER_GATING_ENABLED;
  private static final ModConfigSpec.IntValue POTION_STACK_SIZE;
  private static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

  private static final ModConfigSpec.IntValue TANK_CAPACITY_MB;
  private static final ModConfigSpec.IntValue DOSE_MB;
  private static final ModConfigSpec.IntValue SCAN_RADIUS;
  private static final ModConfigSpec.IntValue MAX_TUBING_LENGTH;
  private static final ModConfigSpec.IntValue MIN_BREW_TICKS;
  private static final ModConfigSpec.DoubleValue BELLOWS_FACTOR;
  private static final ModConfigSpec.ConfigValue<List<? extends Integer>> HOURGLASS_BASE_SECONDS;

  private static final ModConfigSpec.IntValue FALLBACK_EFFECT_BASE_VALUE;
  private static final ModConfigSpec.DoubleValue POTENCY_STEP;
  private static final ModConfigSpec.IntValue DURATION_BASELINE_TICKS;
  private static final ModConfigSpec.DoubleValue DURATION_EXPONENT;

  private static final ModConfigSpec.IntValue CUSTOMERS_PER_SHOP;
  private static final ModConfigSpec.IntValue CUSTOMER_ARRIVAL_TICKS;
  private static final ModConfigSpec.IntValue CUSTOMER_GRACE_TICKS;
  private static final ModConfigSpec.IntValue SHOP_FIELD_RADIUS;
  private static final ModConfigSpec.IntValue SHOP_FIELD_Y_BELOW;
  private static final ModConfigSpec.IntValue SHOP_FIELD_Y_ABOVE;
  private static final ModConfigSpec.IntValue PATH_WEAR_THRESHOLD;
  private static final ModConfigSpec.BooleanValue PATH_WEAR_ENABLED;
  private static final ModConfigSpec.IntValue PREFERRED_PATH_COST;
  private static final ModConfigSpec.IntValue PLAIN_PATH_COST;
  private static final ModConfigSpec.IntValue CLIMB_SURCHARGE;

  static {
    ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    builder.push("general");
    MACHINE_TIER_CAP =
        builder
            .comment(
                "The highest still tier the mod recognises.",
                "Tiers above the ones that exist are accepted, so a world can be prepared for a",
                "later version without editing this file.")
            .defineInRange(
                "machineTierCap",
                TuningDefaults.MACHINE_TIER_CAP,
                TuningDefaults.MACHINE_TIER_CAP_MIN,
                TuningDefaults.MACHINE_TIER_CAP_MAX);
    FUEL_TIER_GATING_ENABLED =
        builder
            .comment(
                "Whether a recipe's tier must be met by the fuel as well as by the machine.",
                "Turning this off makes every recipe brewable on any lit fuel, which is useful",
                "for testing and turns wood into a working fuel.")
            .define("fuelTierGatingEnabled", TuningDefaults.FUEL_TIER_GATING_ENABLED);
    POTION_STACK_SIZE =
        builder
            .comment(
                "How many potions with identical traits share one stack, in place of vanilla's one.",
                "Applies to every potion, vanilla-brewed ones included, because the limit lives on",
                "the potion item rather than on anything this mod owns.",
                "Read once as the game binds item components, so changing it takes effect on the",
                "next load rather than live. Lowering it does not damage stacks that are already",
                "larger; they simply cannot be topped up until they are split.")
            .defineInRange(
                "potionStackSize",
                TuningDefaults.POTION_STACK_SIZE,
                TuningDefaults.POTION_STACK_SIZE_MIN,
                TuningDefaults.POTION_STACK_SIZE_MAX);
    DEBUG_LOGGING =
        builder
            .comment("Logs still validation in detail on every revalidation. Noisy; for diagnosis.")
            .define("debugLogging", TuningDefaults.DEBUG_LOGGING);
    builder.pop();

    builder.push("still");
    TANK_CAPACITY_MB =
        builder
            .comment("How much water one Alchemical Tank holds, in millibuckets.")
            .defineInRange(
                "tankCapacityMb",
                TuningDefaults.TANK_CAPACITY_MB,
                TuningDefaults.TANK_CAPACITY_MB_MIN,
                TuningDefaults.TANK_CAPACITY_MB_MAX);
    DOSE_MB =
        builder
            .comment("How much water one potion consumes, in millibuckets.")
            .defineInRange(
                "doseMb",
                TuningDefaults.DOSE_MB,
                TuningDefaults.DOSE_MB_MIN,
                TuningDefaults.DOSE_MB_MAX);
    SCAN_RADIUS =
        builder
            .comment(
                "How far from the pressure chamber the still is searched for parts, in blocks.",
                "Raising this past four costs search time on every revalidation.")
            .defineInRange(
                "scanRadius",
                TuningDefaults.SCAN_RADIUS,
                TuningDefaults.SCAN_RADIUS_MIN,
                TuningDefaults.SCAN_RADIUS_MAX);
    MAX_TUBING_LENGTH =
        builder
            .comment(
                "How long a run of alchemical tubing may be, counted in tubing blocks.",
                "A tank or condenser further than this from the pressure chamber stops counting,",
                "and a machine whose only tank is that far away says so rather than reporting no",
                "tank at all. Zero refuses tubing outright, so every tank has to touch the chamber.")
            .defineInRange(
                "maxTubingLength",
                TuningDefaults.MAX_TUBING_LENGTH,
                TuningDefaults.MAX_TUBING_LENGTH_MIN,
                TuningDefaults.MAX_TUBING_LENGTH_MAX);
    MIN_BREW_TICKS =
        builder
            .comment(
                "The floor under any brew time, in ticks.",
                "Twenty ticks is one second, which is the shortest brew the design allows.")
            .defineInRange(
                "minBrewTicks",
                TuningDefaults.MIN_BREW_TICKS,
                TuningDefaults.MIN_BREW_TICKS_MIN,
                TuningDefaults.MIN_BREW_TICKS_MAX);
    BELLOWS_FACTOR =
        builder
            .comment(
                "The brew time multiplier applied per bellows or valve.",
                "Below one shortens the brew. The time never reaches zero regardless of this",
                "value, because the floor above is applied afterwards.")
            .defineInRange(
                "bellowsFactor",
                TuningDefaults.BELLOWS_FACTOR,
                TuningDefaults.BELLOWS_FACTOR_MIN,
                TuningDefaults.BELLOWS_FACTOR_MAX);
    HOURGLASS_BASE_SECONDS =
        builder
            .comment(
                "The unmodified brew time at each hourglass setting, in seconds.",
                "One entry per setting, in the order five, fifteen, thirty, forty-five.",
                "The list must keep that length: a short list is rejected rather than padded,",
                "because a missing setting would silently become another setting's time.")
            .defineList(
                "hourglassBaseSeconds",
                TuningDefaults.hourglassBaseSeconds(),
                // What a newly added row starts at, when the config UI offers to add one. The
                // list's
                // length is fixed by the tuning record, so this is only ever a starting point.
                () -> HourglassPreset.FIVE.baseSeconds(),
                value -> value instanceof Integer seconds && seconds > 0);
    builder.pop();

    builder.push("value");
    FALLBACK_EFFECT_BASE_VALUE =
        builder
            .comment(
                "The worth given to an effect that has no authored entry.",
                "An effect with no entry still brews; this is what it is worth when it does.",
                "Zero is legal and means unpriced effects are worth nothing.")
            .defineInRange(
                "fallbackEffectBaseValue",
                TuningDefaults.FALLBACK_EFFECT_BASE_VALUE,
                TuningDefaults.FALLBACK_EFFECT_BASE_VALUE_MIN,
                TuningDefaults.FALLBACK_EFFECT_BASE_VALUE_MAX);
    POTENCY_STEP =
        builder
            .comment("How much each level of potency multiplies an effect's worth by, above one.")
            .defineInRange(
                "potencyStep",
                TuningDefaults.POTENCY_STEP,
                TuningDefaults.POTENCY_STEP_MIN,
                TuningDefaults.POTENCY_STEP_MAX);
    DURATION_BASELINE_TICKS =
        builder
            .comment(
                "The duration at which a timed effect is worth exactly its base value, in ticks.")
            .defineInRange(
                "durationBaselineTicks",
                TuningDefaults.DURATION_BASELINE_TICKS,
                TuningDefaults.DURATION_BASELINE_TICKS_MIN,
                TuningDefaults.DURATION_BASELINE_TICKS_MAX);
    DURATION_EXPONENT =
        builder
            .comment(
                "The exponent applied to a timed effect's duration ratio.",
                "Below one gives diminishing returns, which is what stops a long brew from being",
                "worth an unbounded amount.")
            .defineInRange(
                "durationExponent",
                TuningDefaults.DURATION_EXPONENT,
                TuningDefaults.DURATION_EXPONENT_MIN,
                TuningDefaults.DURATION_EXPONENT_MAX);
    builder.pop();

    builder.push("shop");
    CUSTOMERS_PER_SHOP =
        builder
            .comment(
                "How many customers one shop may have waiting at once.",
                "This is both the shop's cap and the length of its queue: the line is drawn from",
                "the shop's own walkable ground, so a shop with less room for a queue may hold",
                "fewer than this however high it is set. Between one and sixteen.")
            .defineInRange(
                "customersPerShop",
                TuningDefaults.CUSTOMERS_PER_SHOP,
                TuningDefaults.CUSTOMERS_PER_SHOP_MIN,
                TuningDefaults.CUSTOMERS_PER_SHOP_MAX);
    CUSTOMER_ARRIVAL_TICKS =
        builder
            .comment(
                "How long a shop waits between one customer and the next, in ticks.",
                "Twenty ticks is one second, and the default of two hundred is ten. Each shop",
                "keeps its own clock, so this is the interval per shop and not for the world.",
                "Read when a Counter starts ticking: changing it affects Counters loaded after",
                "the change, not the ones already in a loaded chunk.")
            .defineInRange(
                "customerArrivalTicks",
                TuningDefaults.CUSTOMER_ARRIVAL_TICKS,
                TuningDefaults.CUSTOMER_ARRIVAL_TICKS_MIN,
                TuningDefaults.CUSTOMER_ARRIVAL_TICKS_MAX);
    CUSTOMER_GRACE_TICKS =
        builder
            .comment(
                "How long a customer with no usable route waits before leaving, in ticks.",
                "A route goes missing while the shop's navigation field is being rebuilt, which",
                "takes a few ticks, so this has to outlast that; it is also how long a customer",
                "stranded by a broken Counter stands about. Zero leaves at once, and the default",
                "of two hundred is ten seconds.")
            .defineInRange(
                "customerGraceTicks",
                TuningDefaults.CUSTOMER_GRACE_TICKS,
                TuningDefaults.CUSTOMER_GRACE_TICKS_MIN,
                TuningDefaults.CUSTOMER_GRACE_TICKS_MAX);
    SHOP_FIELD_RADIUS =
        builder
            .comment(
                "How far a shop's navigation field reaches horizontally from the Counter, in",
                "blocks. Every customer walks on this field, and a shop whose ground is outside",
                "it has no route and no customers. Raising it costs field build time and memory",
                "with the square of the radius, and it is also how far a block change is looked",
                "for from a Counter. Between one and sixty-four.")
            .defineInRange(
                "shopFieldRadius",
                TuningDefaults.SHOP_FIELD_RADIUS,
                TuningDefaults.SHOP_FIELD_RADIUS_MIN,
                TuningDefaults.SHOP_FIELD_RADIUS_MAX);
    SHOP_FIELD_Y_BELOW =
        builder
            .comment(
                "How far below the Counter that field may reach, in blocks.",
                "Separate from the reach upwards because a route down a mineshaft is not the",
                "same kind of thing as a route up a hill. Zero keeps the field level.",
                "Read together with the radius: the box the field covers has to stay inside the",
                "size the field builder allows, so a very large radius and a very tall reach",
                "cannot both be set at their maximum.")
            .defineInRange(
                "shopFieldYBelow",
                TuningDefaults.SHOP_FIELD_Y_BELOW,
                TuningDefaults.SHOP_FIELD_VERTICAL_MIN,
                TuningDefaults.SHOP_FIELD_VERTICAL_MAX);
    SHOP_FIELD_Y_ABOVE =
        builder
            .comment(
                "How far above the Counter that field may reach, in blocks.",
                "Zero keeps the field level, and the same limit on the box's total size applies")
            .defineInRange(
                "shopFieldYAbove",
                TuningDefaults.SHOP_FIELD_Y_ABOVE,
                TuningDefaults.SHOP_FIELD_VERTICAL_MIN,
                TuningDefaults.SHOP_FIELD_VERTICAL_MAX);
    PATH_WEAR_THRESHOLD =
        builder
            .comment(
                "How many times customers walk over one cell before its ground becomes a path.",
                "Counted per cell and per shop, in memory only: a Counter that unloads or is",
                "broken forgets every pass. Twenty is a shop that has been busy for a while.",
                "Lowering it makes paths appear after a handful of customers, and one pass of one",
                "customer is the floor.")
            .defineInRange(
                "pathWearThreshold",
                TuningDefaults.PATH_WEAR_THRESHOLD,
                TuningDefaults.PATH_WEAR_THRESHOLD_MIN,
                TuningDefaults.PATH_WEAR_THRESHOLD_MAX);
    PATH_WEAR_ENABLED =
        builder
            .comment(
                "Whether walking wears the ground into a path at all.",
                "With this off no ground is ever converted and no passes are counted, so turning",
                "it back on starts every shop's paths from nothing rather than from a tally",
                "collected while it was off.")
            .define("pathWearEnabled", TuningDefaults.PATH_WEAR_ENABLED);
    PREFERRED_PATH_COST =
        builder
            .comment(
                "What a step onto a preferred path block costs the navigation field.",
                "Preferred blocks are the ones in the alchemizer:shop_path_preferred block tag.",
                "What matters is the ratio to the cost of ordinary ground below: at one to three,",
                "a detour along a path of up to three times the straight-line distance is still",
                "the cheaper way round. At least one, because a zero-cost step is one the field",
                "refuses.")
            .defineInRange(
                "preferredPathCost",
                TuningDefaults.PREFERRED_PATH_COST,
                TuningDefaults.PREFERRED_PATH_COST_MIN,
                TuningDefaults.PREFERRED_PATH_COST_MAX);
    PLAIN_PATH_COST =
        builder
            .comment(
                "What a step onto ordinary ground costs the navigation field.",
                "Every walkable cell has to have a price, and this is what nothing special about",
                "it is worth. It should be at least the preferred path cost above, or the",
                "preference the design asks for becomes a penalty. At least one, because a",
                "zero-cost step is one the field refuses.")
            .defineInRange(
                "plainPathCost",
                TuningDefaults.PLAIN_PATH_COST,
                TuningDefaults.PLAIN_PATH_COST_MIN,
                TuningDefaults.PLAIN_PATH_COST_MAX);
    CLIMB_SURCHARGE =
        builder
            .comment(
                "What a step that changes height costs on top of the ground it lands on.",
                "Without it a staircase and a hillside are the same distance and customers",
                "zig-zag up a cliff face. Deliberately small: climbing one block is still worth",
                "taking to get round a wall twice as long. Zero makes a climb free.")
            .defineInRange(
                "climbSurcharge",
                TuningDefaults.CLIMB_SURCHARGE,
                TuningDefaults.CLIMB_SURCHARGE_MIN,
                TuningDefaults.CLIMB_SURCHARGE_MAX);
    builder.pop();

    SPEC = builder.build();
  }

  private AlchemizerConfig() {}

  /**
   * Reads the file into the bundle the rules take.
   *
   * <p>Only call this once the config has loaded. Asking a value for its contents before then
   * throws, which is why {@link ConfigCache} holds the shipping defaults until the load event
   * fires.
   *
   * @return the current tuning
   */
  static Tuning read() {
    List<? extends Integer> hourglass = HOURGLASS_BASE_SECONDS.get();
    return new Tuning(
        MACHINE_TIER_CAP.getAsInt(),
        FUEL_TIER_GATING_ENABLED.getAsBoolean(),
        POTION_STACK_SIZE.getAsInt(),
        TANK_CAPACITY_MB.getAsInt(),
        DOSE_MB.getAsInt(),
        SCAN_RADIUS.getAsInt(),
        MAX_TUBING_LENGTH.getAsInt(),
        hourglass.stream().map(Integer::intValue).toList(),
        BELLOWS_FACTOR.getAsDouble(),
        MIN_BREW_TICKS.getAsInt(),
        FALLBACK_EFFECT_BASE_VALUE.getAsInt(),
        new ValueFactors(
            POTENCY_STEP.getAsDouble(),
            DURATION_BASELINE_TICKS.getAsInt(),
            DURATION_EXPONENT.getAsDouble()),
        DEBUG_LOGGING.getAsBoolean(),
        CUSTOMERS_PER_SHOP.getAsInt(),
        CUSTOMER_ARRIVAL_TICKS.getAsInt(),
        CUSTOMER_GRACE_TICKS.getAsInt(),
        SHOP_FIELD_RADIUS.getAsInt(),
        SHOP_FIELD_Y_BELOW.getAsInt(),
        SHOP_FIELD_Y_ABOVE.getAsInt(),
        PATH_WEAR_THRESHOLD.getAsInt(),
        PATH_WEAR_ENABLED.getAsBoolean(),
        PREFERRED_PATH_COST.getAsInt(),
        PLAIN_PATH_COST.getAsInt(),
        CLIMB_SURCHARGE.getAsInt());
  }
}
