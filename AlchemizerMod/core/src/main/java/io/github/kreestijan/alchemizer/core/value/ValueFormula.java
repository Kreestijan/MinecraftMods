package io.github.kreestijan.alchemizer.core.value;

import io.github.kreestijan.alchemizer.core.effect.EffectDefinition;
import io.github.kreestijan.alchemizer.core.effect.EffectRegistry;
import io.github.kreestijan.alchemizer.core.effect.EffectSpec;
import java.util.Objects;

/**
 * What a potion is worth: {@code DESIGN.md}'s base value, scaled by potency and by duration.
 *
 * <p>A potion with several effects is worth the sum of its parts, and one with no effects is worth
 * nothing. Nothing reads this yet — customers and barter are out of scope for the baseline — so the
 * tests assert the shape of the curve rather than particular totals, and the numbers themselves
 * live in {@link ValueFactors}.
 */
public final class ValueFormula {

  private ValueFormula() {}

  /**
   * @param potion the effects to value
   * @param registry supplies each effect's base value, falling back for unpriced effects
   * @param factors the shape of the curve
   * @return the potion's worth, never negative
   */
  public static double value(PotionValue potion, EffectRegistry registry, ValueFactors factors) {
    Objects.requireNonNull(potion, "potion");
    Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(factors, "factors");

    double total = 0.0;
    for (EffectSpec spec : potion.effects()) {
      EffectDefinition definition = registry.resolve(spec.effect());
      total +=
          definition.baseValue()
              * potencyFactor(spec.potency(), factors)
              * durationFactor(definition.instant(), spec.durationTicks(), factors);
    }

    // Fail safe rather than propagate a NaN or an infinity into a price the player can see.
    if (!Double.isFinite(total) || total < 0.0) {
      return 0.0;
    }
    return total;
  }

  /**
   * @param potency the zero-based amplifier
   * @param factors the shape of the curve
   * @return the multiplier applied to the base value for this potency
   */
  public static double potencyFactor(int potency, ValueFactors factors) {
    return 1.0 + Math.max(0, potency) * factors.potencyStep();
  }

  /**
   * @param instant whether the effect applies immediately, in which case duration is irrelevant
   * @param durationTicks how long the effect lasts
   * @param factors the shape of the curve
   * @return the multiplier applied to the base value for this duration
   */
  public static double durationFactor(boolean instant, int durationTicks, ValueFactors factors) {
    if (instant) {
      return 1.0;
    }
    double ratio = Math.max(1, durationTicks) / (double) factors.durationBaselineTicks();
    return Math.pow(ratio, factors.durationExponent());
  }
}
