package io.github.kreestijan.alchemizer.core.tuning;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.value.ValueFactors;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The shipping numbers, checked against the ranges the config file will advertise.
 *
 * <p>A default outside its own declared range is a bug that only appears on a player's machine, and
 * it costs nothing to rule out here.
 */
class TuningDefaultsTest {

  @Test
  void everyDefaultLiesInsideItsDeclaredRange() {
    assertInRange(
        "machineTierCap",
        TuningDefaults.MACHINE_TIER_CAP,
        TuningDefaults.MACHINE_TIER_CAP_MIN,
        TuningDefaults.MACHINE_TIER_CAP_MAX);
    assertInRange(
        "potionStackSize",
        TuningDefaults.POTION_STACK_SIZE,
        TuningDefaults.POTION_STACK_SIZE_MIN,
        TuningDefaults.POTION_STACK_SIZE_MAX);
    assertInRange(
        "tankCapacityMb",
        TuningDefaults.TANK_CAPACITY_MB,
        TuningDefaults.TANK_CAPACITY_MB_MIN,
        TuningDefaults.TANK_CAPACITY_MB_MAX);
    assertInRange(
        "doseMb", TuningDefaults.DOSE_MB, TuningDefaults.DOSE_MB_MIN, TuningDefaults.DOSE_MB_MAX);
    assertInRange(
        "scanRadius",
        TuningDefaults.SCAN_RADIUS,
        TuningDefaults.SCAN_RADIUS_MIN,
        TuningDefaults.SCAN_RADIUS_MAX);
    assertInRange(
        "minBrewTicks",
        TuningDefaults.MIN_BREW_TICKS,
        TuningDefaults.MIN_BREW_TICKS_MIN,
        TuningDefaults.MIN_BREW_TICKS_MAX);
    assertInRange(
        "fallbackEffectBaseValue",
        TuningDefaults.FALLBACK_EFFECT_BASE_VALUE,
        TuningDefaults.FALLBACK_EFFECT_BASE_VALUE_MIN,
        TuningDefaults.FALLBACK_EFFECT_BASE_VALUE_MAX);
    assertInRange(
        "durationBaselineTicks",
        TuningDefaults.DURATION_BASELINE_TICKS,
        TuningDefaults.DURATION_BASELINE_TICKS_MIN,
        TuningDefaults.DURATION_BASELINE_TICKS_MAX);
  }

  @Test
  void everyFloatingDefaultLiesInsideItsDeclaredRange() {
    assertInRange(
        "bellowsFactor",
        TuningDefaults.BELLOWS_FACTOR,
        TuningDefaults.BELLOWS_FACTOR_MIN,
        TuningDefaults.BELLOWS_FACTOR_MAX);
    assertInRange(
        "potencyStep",
        TuningDefaults.POTENCY_STEP,
        TuningDefaults.POTENCY_STEP_MIN,
        TuningDefaults.POTENCY_STEP_MAX);
    assertInRange(
        "durationExponent",
        TuningDefaults.DURATION_EXPONENT,
        TuningDefaults.DURATION_EXPONENT_MIN,
        TuningDefaults.DURATION_EXPONENT_MAX);
  }

  @Test
  void theMinimumBrewTimeIsTheOneSecondTheDesignAsksFor() {
    assertEquals(20, TuningDefaults.MIN_BREW_TICKS);
  }

  @Test
  void theShippingDefaultsBuildAValidTuning() {
    Tuning tuning = TuningDefaults.defaultTuning();

    assertEquals(TuningDefaults.MACHINE_TIER_CAP, tuning.machineTierCap());
    assertEquals(TuningDefaults.FUEL_TIER_GATING_ENABLED, tuning.fuelTierGatingEnabled());
    assertEquals(TuningDefaults.POTION_STACK_SIZE, tuning.potionStackSize());
    assertEquals(TuningDefaults.TANK_CAPACITY_MB, tuning.tankCapacityMb());
    assertEquals(TuningDefaults.DOSE_MB, tuning.doseMb());
    assertEquals(TuningDefaults.SCAN_RADIUS, tuning.scanRadius());
    assertEquals(TuningDefaults.MIN_BREW_TICKS, tuning.minBrewTicks());
    assertEquals(
        new ValueFactors(
            TuningDefaults.POTENCY_STEP,
            TuningDefaults.DURATION_BASELINE_TICKS,
            TuningDefaults.DURATION_EXPONENT),
        tuning.valueFactors());
    assertEquals(TuningDefaults.DEBUG_LOGGING, tuning.debugLogging());
  }

  @Test
  void theShippingDefaultsPassTheirOwnSanityCheck() {
    assertDoesNotThrow(TuningDefaults::sanityCheck);
  }

  @Test
  void theHourglassDefaultsCoverEveryPresetAndAreOrdered() {
    List<Integer> seconds = TuningDefaults.hourglassBaseSeconds();

    assertEquals(HourglassPreset.values().length, seconds.size());
    for (int i = 0; i < seconds.size(); i++) {
      assertEquals(HourglassPreset.values()[i].baseSeconds(), seconds.get(i));
    }
    for (int i = 1; i < seconds.size(); i++) {
      assertTrue(seconds.get(i) > seconds.get(i - 1), "the presets must ascend");
    }
  }

  @Test
  void oneDoseFitsInOneTank() {
    assertTrue(TuningDefaults.DOSE_MB < TuningDefaults.TANK_CAPACITY_MB);
    assertEquals(160, TuningDefaults.TANK_CAPACITY_MB / TuningDefaults.DOSE_MB);
  }

  @Test
  void aTuningCanBeAskedForTheSameNumbersWithLoggingOn() {
    Tuning quiet = TuningDefaults.defaultTuning();
    Tuning loud = quiet.withDebugLogging();

    assertTrue(loud.debugLogging());
    assertEquals(quiet.hourglassBaseSeconds(), loud.hourglassBaseSeconds());
    assertEquals(quiet.bellowsFactor(), loud.bellowsFactor(), 0.0);
  }

  @Test
  void anInconsistentTuningIsRefusedAtConstruction() {
    List<Integer> hourglass = TuningDefaults.hourglassBaseSeconds();
    ValueFactors factors =
        new ValueFactors(
            TuningDefaults.POTENCY_STEP,
            TuningDefaults.DURATION_BASELINE_TICKS,
            TuningDefaults.DURATION_EXPONENT);

    assertThrowsForTuning(0, hourglass, TuningDefaults.BELLOWS_FACTOR, factors);
    assertThrowsForTuning(-1, hourglass, TuningDefaults.BELLOWS_FACTOR, factors);
    assertThrowsForTuning(
        TuningDefaults.MACHINE_TIER_CAP, List.of(5), TuningDefaults.BELLOWS_FACTOR, factors);
    assertThrowsForTuning(TuningDefaults.MACHINE_TIER_CAP, hourglass, 0.0, factors);
    assertThrowsForTuning(TuningDefaults.MACHINE_TIER_CAP, hourglass, 1.2, factors);
  }

  @Test
  void aPotionStackSizeOfZeroOrLessIsRefusedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Tuning(
                TuningDefaults.MACHINE_TIER_CAP,
                true,
                // A potion stack that cannot hold the potion is not a stack size.
                0,
                TuningDefaults.TANK_CAPACITY_MB,
                TuningDefaults.DOSE_MB,
                TuningDefaults.SCAN_RADIUS,
                TuningDefaults.hourglassBaseSeconds(),
                TuningDefaults.BELLOWS_FACTOR,
                TuningDefaults.MIN_BREW_TICKS,
                TuningDefaults.FALLBACK_EFFECT_BASE_VALUE,
                new ValueFactors(
                    TuningDefaults.POTENCY_STEP,
                    TuningDefaults.DURATION_BASELINE_TICKS,
                    TuningDefaults.DURATION_EXPONENT),
                false));
  }

  private static void assertThrowsForTuning(
      int machineTierCap, List<Integer> hourglass, double bellowsFactor, ValueFactors factors) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Tuning(
                machineTierCap,
                true,
                TuningDefaults.POTION_STACK_SIZE,
                TuningDefaults.TANK_CAPACITY_MB,
                TuningDefaults.DOSE_MB,
                TuningDefaults.SCAN_RADIUS,
                hourglass,
                bellowsFactor,
                TuningDefaults.MIN_BREW_TICKS,
                TuningDefaults.FALLBACK_EFFECT_BASE_VALUE,
                factors,
                false));
  }

  private static void assertInRange(String name, int value, int min, int max) {
    assertTrue(value >= min, name + " default " + value + " is below its minimum " + min);
    assertTrue(value <= max, name + " default " + value + " is above its maximum " + max);
  }

  private static void assertInRange(String name, double value, double min, double max) {
    assertTrue(value >= min, name + " default " + value + " is below its minimum " + min);
    assertTrue(value <= max, name + " default " + value + " is above its maximum " + max);
  }
}
