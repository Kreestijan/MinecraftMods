package io.github.kreestijan.alchemizer.core.tuning;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.flowfield.FieldBounds;
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
        "maxTubingLength",
        TuningDefaults.MAX_TUBING_LENGTH,
        TuningDefaults.MAX_TUBING_LENGTH_MIN,
        TuningDefaults.MAX_TUBING_LENGTH_MAX);
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
    assertInRange(
        "customersPerShop",
        TuningDefaults.CUSTOMERS_PER_SHOP,
        TuningDefaults.CUSTOMERS_PER_SHOP_MIN,
        TuningDefaults.CUSTOMERS_PER_SHOP_MAX);
    assertInRange(
        "customerArrivalTicks",
        TuningDefaults.CUSTOMER_ARRIVAL_TICKS,
        TuningDefaults.CUSTOMER_ARRIVAL_TICKS_MIN,
        TuningDefaults.CUSTOMER_ARRIVAL_TICKS_MAX);
    assertInRange(
        "customerGraceTicks",
        TuningDefaults.CUSTOMER_GRACE_TICKS,
        TuningDefaults.CUSTOMER_GRACE_TICKS_MIN,
        TuningDefaults.CUSTOMER_GRACE_TICKS_MAX);
    assertInRange(
        "shopFieldRadius",
        TuningDefaults.SHOP_FIELD_RADIUS,
        TuningDefaults.SHOP_FIELD_RADIUS_MIN,
        TuningDefaults.SHOP_FIELD_RADIUS_MAX);
    assertInRange(
        "shopFieldYBelow",
        TuningDefaults.SHOP_FIELD_Y_BELOW,
        TuningDefaults.SHOP_FIELD_VERTICAL_MIN,
        TuningDefaults.SHOP_FIELD_VERTICAL_MAX);
    assertInRange(
        "shopFieldYAbove",
        TuningDefaults.SHOP_FIELD_Y_ABOVE,
        TuningDefaults.SHOP_FIELD_VERTICAL_MIN,
        TuningDefaults.SHOP_FIELD_VERTICAL_MAX);
    assertInRange(
        "pathWearThreshold",
        TuningDefaults.PATH_WEAR_THRESHOLD,
        TuningDefaults.PATH_WEAR_THRESHOLD_MIN,
        TuningDefaults.PATH_WEAR_THRESHOLD_MAX);
    assertInRange(
        "preferredPathCost",
        TuningDefaults.PREFERRED_PATH_COST,
        TuningDefaults.PREFERRED_PATH_COST_MIN,
        TuningDefaults.PREFERRED_PATH_COST_MAX);
    assertInRange(
        "plainPathCost",
        TuningDefaults.PLAIN_PATH_COST,
        TuningDefaults.PLAIN_PATH_COST_MIN,
        TuningDefaults.PLAIN_PATH_COST_MAX);
    assertInRange(
        "climbSurcharge",
        TuningDefaults.CLIMB_SURCHARGE,
        TuningDefaults.CLIMB_SURCHARGE_MIN,
        TuningDefaults.CLIMB_SURCHARGE_MAX);
  }

  /**
   * The shipping numbers are the ones the feature was built with, not merely legal ones.
   *
   * <p>Moving a constant into the config file is a change that no test would notice if it went
   * wrong in the obvious way — a default that drifted by one would still be inside its range — so
   * the four numbers the design's placeholders named are pinned here as well. See {@code
   * CHANGELOG.md}: four places in a shop's line, ten seconds between arrivals, a ten-second grace
   * and twenty passes before a cell becomes a path.
   */
  @Test
  void theMovedPlaceholdersKeepTheNumbersTheyWereShippedWith() {
    assertEquals(4, TuningDefaults.CUSTOMERS_PER_SHOP);
    assertEquals(200, TuningDefaults.CUSTOMER_ARRIVAL_TICKS);
    assertEquals(200, TuningDefaults.CUSTOMER_GRACE_TICKS);
    assertEquals(20, TuningDefaults.PATH_WEAR_THRESHOLD);
    assertEquals(16, TuningDefaults.SHOP_FIELD_RADIUS);
    assertEquals(8, TuningDefaults.SHOP_FIELD_Y_BELOW);
    assertEquals(8, TuningDefaults.SHOP_FIELD_Y_ABOVE);
    assertEquals(1, TuningDefaults.PREFERRED_PATH_COST);
    assertEquals(3, TuningDefaults.PLAIN_PATH_COST);
    assertEquals(2, TuningDefaults.CLIMB_SURCHARGE);
    assertTrue(TuningDefaults.PATH_WEAR_ENABLED);
  }

  /**
   * The largest field the config's ranges allow is one the field type will build.
   *
   * <p>This is the drift the ranges exist to prevent: a radius and a vertical reach that are each
   * legal but do not fit together would be a shop whose Counter threw on every rebuild instead of
   * admitting a customer, and the config file would have advertised both as fine.
   */
  @Test
  void theLargestFieldTheConfigAllowsIsOneTheFieldTypeBuilds() {
    assertDoesNotThrow(
        () ->
            new FieldBounds(
                TuningDefaults.SHOP_FIELD_RADIUS_MAX,
                TuningDefaults.SHOP_FIELD_VERTICAL_MAX,
                TuningDefaults.SHOP_FIELD_VERTICAL_MAX));
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
    assertEquals(TuningDefaults.MAX_TUBING_LENGTH, tuning.maxTubingLength());
    assertEquals(TuningDefaults.MIN_BREW_TICKS, tuning.minBrewTicks());
    assertEquals(
        new ValueFactors(
            TuningDefaults.POTENCY_STEP,
            TuningDefaults.DURATION_BASELINE_TICKS,
            TuningDefaults.DURATION_EXPONENT),
        tuning.valueFactors());
    assertEquals(TuningDefaults.DEBUG_LOGGING, tuning.debugLogging());
    assertEquals(TuningDefaults.CUSTOMERS_PER_SHOP, tuning.customersPerShop());
    assertEquals(TuningDefaults.CUSTOMER_ARRIVAL_TICKS, tuning.customerArrivalTicks());
    assertEquals(TuningDefaults.CUSTOMER_GRACE_TICKS, tuning.customerGraceTicks());
    assertEquals(TuningDefaults.SHOP_FIELD_RADIUS, tuning.shopFieldRadius());
    assertEquals(TuningDefaults.SHOP_FIELD_Y_BELOW, tuning.shopFieldYBelow());
    assertEquals(TuningDefaults.SHOP_FIELD_Y_ABOVE, tuning.shopFieldYAbove());
    assertEquals(TuningDefaults.PATH_WEAR_THRESHOLD, tuning.pathWearThreshold());
    assertEquals(TuningDefaults.PATH_WEAR_ENABLED, tuning.pathWearEnabled());
    assertEquals(TuningDefaults.PREFERRED_PATH_COST, tuning.preferredPathCost());
    assertEquals(TuningDefaults.PLAIN_PATH_COST, tuning.plainPathCost());
    assertEquals(TuningDefaults.CLIMB_SURCHARGE, tuning.climbSurcharge());
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
                TuningDefaults.MAX_TUBING_LENGTH,
                TuningDefaults.hourglassBaseSeconds(),
                TuningDefaults.BELLOWS_FACTOR,
                TuningDefaults.MIN_BREW_TICKS,
                TuningDefaults.FALLBACK_EFFECT_BASE_VALUE,
                new ValueFactors(
                    TuningDefaults.POTENCY_STEP,
                    TuningDefaults.DURATION_BASELINE_TICKS,
                    TuningDefaults.DURATION_EXPONENT),
                false,
                TuningDefaults.CUSTOMERS_PER_SHOP,
                TuningDefaults.CUSTOMER_ARRIVAL_TICKS,
                TuningDefaults.CUSTOMER_GRACE_TICKS,
                TuningDefaults.SHOP_FIELD_RADIUS,
                TuningDefaults.SHOP_FIELD_Y_BELOW,
                TuningDefaults.SHOP_FIELD_Y_ABOVE,
                TuningDefaults.PATH_WEAR_THRESHOLD,
                TuningDefaults.PATH_WEAR_ENABLED,
                TuningDefaults.PREFERRED_PATH_COST,
                TuningDefaults.PLAIN_PATH_COST,
                TuningDefaults.CLIMB_SURCHARGE));
  }

  /**
   * The shop's numbers are checked the same way as the still's, and for the same reason.
   *
   * <p>A shop size of zero is not a small shop and an arrival interval of zero is not a frequent
   * one: the first is an array of no seats and the second a shop that would try to fill its line
   * every tick it ticks. Between them they are the kind of number a config file hands the record
   * without meaning anything by it.
   */
  @Test
  void anImpossibleShopSettingIsRefusedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> tuningWithShop(0, TuningDefaults.CUSTOMER_ARRIVAL_TICKS));
    assertThrows(
        IllegalArgumentException.class, () -> tuningWithShop(TuningDefaults.CUSTOMERS_PER_SHOP, 0));
  }

  /**
   * @param customersPerShop how many customers one shop may hold
   * @param arrivalTicks how long that shop waits between them
   * @return the shipping tuning with those two numbers, which the record validates like any other
   */
  private static Tuning tuningWithShop(int customersPerShop, int arrivalTicks) {
    Tuning defaults = TuningDefaults.defaultTuning();
    return new Tuning(
        defaults.machineTierCap(),
        defaults.fuelTierGatingEnabled(),
        defaults.potionStackSize(),
        defaults.tankCapacityMb(),
        defaults.doseMb(),
        defaults.scanRadius(),
        defaults.maxTubingLength(),
        defaults.hourglassBaseSeconds(),
        defaults.bellowsFactor(),
        defaults.minBrewTicks(),
        defaults.fallbackEffectBaseValue(),
        defaults.valueFactors(),
        defaults.debugLogging(),
        customersPerShop,
        arrivalTicks,
        defaults.customerGraceTicks(),
        defaults.shopFieldRadius(),
        defaults.shopFieldYBelow(),
        defaults.shopFieldYAbove(),
        defaults.pathWearThreshold(),
        defaults.pathWearEnabled(),
        defaults.preferredPathCost(),
        defaults.plainPathCost(),
        defaults.climbSurcharge());
  }

  @Test
  void aNegativeTubingLimitIsRefusedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TuningDefaults.defaultTuning().withMaxTubingLength(-1));
  }

  @Test
  void aTubingLimitOfZeroIsLegalAndRefusesTubingAltogether() {
    Tuning tuning = TuningDefaults.defaultTuning().withMaxTubingLength(0);

    assertEquals(0, tuning.maxTubingLength());
  }

  @Test
  void aTuningCanBeAskedForTheSameNumbersAtADifferentTierCap() {
    Tuning capped = TuningDefaults.defaultTuning().withMachineTierCap(2);

    assertEquals(2, capped.machineTierCap());
    assertEquals(TuningDefaults.MAX_TUBING_LENGTH, capped.maxTubingLength());
    assertEquals(TuningDefaults.SCAN_RADIUS, capped.scanRadius());
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
                TuningDefaults.MAX_TUBING_LENGTH,
                hourglass,
                bellowsFactor,
                TuningDefaults.MIN_BREW_TICKS,
                TuningDefaults.FALLBACK_EFFECT_BASE_VALUE,
                factors,
                false,
                TuningDefaults.CUSTOMERS_PER_SHOP,
                TuningDefaults.CUSTOMER_ARRIVAL_TICKS,
                TuningDefaults.CUSTOMER_GRACE_TICKS,
                TuningDefaults.SHOP_FIELD_RADIUS,
                TuningDefaults.SHOP_FIELD_Y_BELOW,
                TuningDefaults.SHOP_FIELD_Y_ABOVE,
                TuningDefaults.PATH_WEAR_THRESHOLD,
                TuningDefaults.PATH_WEAR_ENABLED,
                TuningDefaults.PREFERRED_PATH_COST,
                TuningDefaults.PLAIN_PATH_COST,
                TuningDefaults.CLIMB_SURCHARGE));
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
