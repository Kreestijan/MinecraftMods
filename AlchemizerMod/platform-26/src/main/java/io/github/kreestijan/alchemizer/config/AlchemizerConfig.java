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
  private static final ModConfigSpec.IntValue MIN_BREW_TICKS;
  private static final ModConfigSpec.DoubleValue BELLOWS_FACTOR;
  private static final ModConfigSpec.ConfigValue<List<? extends Integer>> HOURGLASS_BASE_SECONDS;

  private static final ModConfigSpec.IntValue FALLBACK_EFFECT_BASE_VALUE;
  private static final ModConfigSpec.DoubleValue POTENCY_STEP;
  private static final ModConfigSpec.IntValue DURATION_BASELINE_TICKS;
  private static final ModConfigSpec.DoubleValue DURATION_EXPONENT;

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
        hourglass.stream().map(Integer::intValue).toList(),
        BELLOWS_FACTOR.getAsDouble(),
        MIN_BREW_TICKS.getAsInt(),
        FALLBACK_EFFECT_BASE_VALUE.getAsInt(),
        new ValueFactors(
            POTENCY_STEP.getAsDouble(),
            DURATION_BASELINE_TICKS.getAsInt(),
            DURATION_EXPONENT.getAsDouble()),
        DEBUG_LOGGING.getAsBoolean());
  }
}
