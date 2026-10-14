package io.github.kreestijan.alchemizer.core.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.effect.EffectDefinition;
import io.github.kreestijan.alchemizer.core.effect.EffectId;
import io.github.kreestijan.alchemizer.core.effect.EffectRegistry;
import io.github.kreestijan.alchemizer.core.effect.EffectSpec;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The value curve, asserted by shape rather than by total.
 *
 * <p>Nothing reads a potion's worth yet — customers and barter are out of scope for the baseline —
 * so these tests pin the properties that must survive later tuning: more is worth more, several
 * effects add up, and no combination produces a negative or infinite price.
 */
class ValueFormulaTest {

  private static final ValueFactors FACTORS =
      new ValueFactors(
          TuningDefaults.POTENCY_STEP,
          TuningDefaults.DURATION_BASELINE_TICKS,
          TuningDefaults.DURATION_EXPONENT);

  private static final EffectId SPEED = new EffectId("minecraft:speed");
  private static final EffectId HEALING = new EffectId("minecraft:instant_health");
  private static final EffectId UNKNOWN = new EffectId("alchemizer:not_yet_priced");

  private static final EffectRegistry REGISTRY =
      new EffectRegistry(
          Map.of(
              SPEED, new EffectDefinition(SPEED, 12, false),
              HEALING, new EffectDefinition(HEALING, 20, true)),
          1);

  private static double valueOf(EffectSpec... specs) {
    return ValueFormula.value(new PotionValue(List.of(specs)), REGISTRY, FACTORS);
  }

  private static EffectSpec spec(EffectId effect, int potency, int durationTicks) {
    return new EffectSpec(effect, potency, durationTicks);
  }

  @Test
  void aPotionWithNoEffectsIsWorthNothing() {
    assertEquals(0.0, ValueFormula.value(PotionValue.none(), REGISTRY, FACTORS));
  }

  @Test
  void anEmptyEffectListIsLegal() {
    assertEquals(0, PotionValue.none().effects().size());
    assertEquals(0.0, valueOf());
  }

  @Test
  void higherPotencyIsWorthAtLeastAsMuch() {
    double base = valueOf(spec(SPEED, 0, 3600));
    double stronger = valueOf(spec(SPEED, 1, 3600));
    double strongest = valueOf(spec(SPEED, 3, 3600));

    assertTrue(stronger > base, "potency 1 should beat potency 0");
    assertTrue(strongest > stronger, "potency 3 should beat potency 1");
  }

  @Test
  void longerDurationIsWorthAtLeastAsMuch() {
    double shortBrew = valueOf(spec(SPEED, 0, 600));
    double longBrew = valueOf(spec(SPEED, 0, 3600));
    double marathon = valueOf(spec(SPEED, 0, 20 * 60 * 8));

    assertTrue(longBrew > shortBrew);
    assertTrue(marathon > longBrew);
  }

  @Test
  void durationDiminishesSoLongerBrewsCannotRunAway() {
    // Four times the baseline must be worth less than four times the baseline's value: the exponent
    // is below one precisely so that a very long potion is good, not unbounded.
    double baseline = valueOf(spec(SPEED, 0, 3600));
    double quadruple = valueOf(spec(SPEED, 0, 4 * 3600));

    assertTrue(quadruple < 4 * baseline, "duration should have diminishing returns");
    assertTrue(quadruple > baseline);
  }

  @Test
  void instantEffectsIgnoreDuration() {
    double instant = valueOf(spec(HEALING, 0, 1));
    double pretended = valueOf(spec(HEALING, 0, 20 * 60 * 10));

    assertEquals(instant, pretended);
    assertEquals(20.0, instant, 1.0e-9);
  }

  @Test
  void effectsAddUp() {
    double speedAlone = valueOf(spec(SPEED, 0, 3600));
    double healingAlone = valueOf(spec(HEALING, 0, 1));
    double both = valueOf(spec(SPEED, 0, 3600), spec(HEALING, 0, 1));

    assertEquals(speedAlone + healingAlone, both, 1.0e-9);
  }

  @Test
  void anUnpricedEffectIsWorthTheFallbackNotNothing() {
    double speedAlone = valueOf(spec(SPEED, 0, 3600));
    double unknownAlone = valueOf(spec(UNKNOWN, 0, 3600));
    double pricedAndUnknown = valueOf(spec(SPEED, 0, 3600), spec(UNKNOWN, 0, 3600));

    assertTrue(unknownAlone > 0.0, "an unpriced effect still has a base value");
    assertEquals(speedAlone + unknownAlone, pricedAndUnknown, 1.0e-9);
  }

  @Test
  void valueIsNeverNegative() {
    EffectRegistry zeroFallback = EffectRegistry.withFallbackOnly(0);

    double value =
        ValueFormula.value(
            new PotionValue(List.of(spec(UNKNOWN, 0, 0), spec(UNKNOWN, 2, 0))),
            zeroFallback,
            FACTORS);

    assertEquals(0.0, value);
  }

  @Test
  void potencyFactorGrowsLinearlyFromOne() {
    assertEquals(1.0, ValueFormula.potencyFactor(0, FACTORS), 1.0e-9);
    assertEquals(1.25, ValueFormula.potencyFactor(1, FACTORS), 1.0e-9);
    assertEquals(1.0, ValueFormula.potencyFactor(-4, FACTORS), 1.0e-9);
  }

  @Test
  void durationFactorIsOneAtTheBaselineAndForInstants() {
    assertEquals(
        1.0,
        ValueFormula.durationFactor(false, TuningDefaults.DURATION_BASELINE_TICKS, FACTORS),
        1.0e-9);
    assertEquals(1.0, ValueFormula.durationFactor(true, 0, FACTORS), 1.0e-9);
  }

  @Test
  void aZeroTickEffectIsTreatedAsTheShortestPossibleRatherThanFree() {
    // Duration is clamped to at least one tick, so a malformed definition cannot make an effect
    // worth nothing by claiming it lasts no time at all.
    double zeroTicks = ValueFormula.durationFactor(false, 0, FACTORS);
    double oneTick = ValueFormula.durationFactor(false, 1, FACTORS);

    assertEquals(oneTick, zeroTicks, 1.0e-9);
    assertTrue(zeroTicks > 0.0);
  }

  @Test
  void aNegativePotencyCannotDiscountTheBaseValue() {
    assertEquals(
        ValueFormula.potencyFactor(0, FACTORS), ValueFormula.potencyFactor(-3, FACTORS), 1.0e-9);
  }
}
