package io.github.kreestijan.alchemizer.core.tuning;

import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.tank.TankMath;
import io.github.kreestijan.alchemizer.core.value.ValueFactors;
import java.util.ArrayList;
import java.util.List;

/**
 * The shipping values, and the bounds the config file is allowed to move them within.
 *
 * <p>The bounds live here rather than in the config definition so that a test can assert the
 * defaults sit inside them. A default outside its own declared range is the kind of bug that only
 * shows up on a player's machine, and it is cheap to rule out.
 */
public final class TuningDefaults {

  public static final int MACHINE_TIER_CAP = 4;
  public static final int MACHINE_TIER_CAP_MIN = 1;
  public static final int MACHINE_TIER_CAP_MAX = 4;

  public static final boolean FUEL_TIER_GATING_ENABLED = true;

  /**
   * How many potions share one stack.
   *
   * <p>Vanilla says one. This is the mod overriding that, and the ceiling is sixty-four rather than
   * anything larger because sixty-four is the number a player already reads as "a full stack".
   */
  public static final int POTION_STACK_SIZE = 64;

  public static final int POTION_STACK_SIZE_MIN = 1;
  public static final int POTION_STACK_SIZE_MAX = 64;

  public static final int TANK_CAPACITY_MB = TankMath.BASIC_TANK_CAPACITY_MB;
  public static final int TANK_CAPACITY_MB_MIN = TankMath.BUCKET_MB;
  public static final int TANK_CAPACITY_MB_MAX = 64 * TankMath.BUCKET_MB;

  public static final int DOSE_MB = TankMath.DOSE_MB;
  public static final int DOSE_MB_MIN = 1;
  public static final int DOSE_MB_MAX = TankMath.BUCKET_MB;

  public static final int SCAN_RADIUS = 4;
  public static final int SCAN_RADIUS_MIN = 1;
  public static final int SCAN_RADIUS_MAX = 8;

  public static final double BELLOWS_FACTOR = 0.9;
  public static final double BELLOWS_FACTOR_MIN = 0.5;
  public static final double BELLOWS_FACTOR_MAX = 1.0;

  public static final int MIN_BREW_TICKS = 20;
  public static final int MIN_BREW_TICKS_MIN = 1;
  public static final int MIN_BREW_TICKS_MAX = 20 * 60;

  public static final int FALLBACK_EFFECT_BASE_VALUE = 1;
  public static final int FALLBACK_EFFECT_BASE_VALUE_MIN = 0;
  public static final int FALLBACK_EFFECT_BASE_VALUE_MAX = 100;

  public static final double POTENCY_STEP = 0.25;
  public static final double POTENCY_STEP_MIN = 0.0;
  public static final double POTENCY_STEP_MAX = 2.0;

  public static final int DURATION_BASELINE_TICKS = 3600;
  public static final int DURATION_BASELINE_TICKS_MIN = 20;
  public static final int DURATION_BASELINE_TICKS_MAX = 20 * 60 * 10;

  public static final double DURATION_EXPONENT = 0.5;
  public static final double DURATION_EXPONENT_MIN = 0.1;
  public static final double DURATION_EXPONENT_MAX = 2.0;

  public static final boolean DEBUG_LOGGING = false;

  private TuningDefaults() {}

  /**
   * @return the hourglass base times, in the order of {@link HourglassPreset}
   */
  public static List<Integer> hourglassBaseSeconds() {
    List<Integer> seconds = new ArrayList<>(HourglassPreset.values().length);
    for (HourglassPreset preset : HourglassPreset.values()) {
      seconds.add(preset.baseSeconds());
    }
    return List.copyOf(seconds);
  }

  /**
   * @return the tuning the mod ships with
   */
  public static Tuning defaultTuning() {
    return new Tuning(
        MACHINE_TIER_CAP,
        FUEL_TIER_GATING_ENABLED,
        POTION_STACK_SIZE,
        TANK_CAPACITY_MB,
        DOSE_MB,
        SCAN_RADIUS,
        hourglassBaseSeconds(),
        BELLOWS_FACTOR,
        MIN_BREW_TICKS,
        FALLBACK_EFFECT_BASE_VALUE,
        new ValueFactors(POTENCY_STEP, DURATION_BASELINE_TICKS, DURATION_EXPONENT),
        DEBUG_LOGGING);
  }

  /**
   * Builds the default tuning and fails loudly if it is not internally consistent.
   *
   * <p>Called once at mod construction. It costs nothing and it turns a broken default into a
   * startup error rather than a strange number in a player's game.
   *
   * @throws IllegalStateException if the shipping defaults are inconsistent
   */
  public static void sanityCheck() {
    Tuning tuning = defaultTuning();
    if (tuning.hourglassBaseSeconds().size() != HourglassPreset.values().length) {
      throw new IllegalStateException("hourglass defaults do not cover every preset");
    }
    if (tuning.doseMb() > tuning.tankCapacityMb()) {
      throw new IllegalStateException("a single dose does not fit in a single tank");
    }
  }
}
