package io.github.kreestijan.alchemizer.core.value;

/**
 * The shape of the value curve, kept apart from the effect data it is applied to.
 *
 * <p>{@code DESIGN.md} fixes the form of the formula — base value, scaled by potency, scaled by
 * duration — but not the numbers. They live here, in one place, so they can be tuned once value
 * becomes player-visible through customers and barter.
 *
 * <p>The duration curve is a power rather than a straight line: {@code durationExponent} below 1
 * means doubling a potion's duration less than doubles its worth. That keeps a stack of duration
 * enhancers from running away once they exist.
 *
 * @param potencyStep how much each potency level adds, as a fraction of the base value
 * @param durationBaselineTicks the duration treated as a factor of one
 * @param durationExponent the power the duration ratio is raised to
 */
public record ValueFactors(double potencyStep, int durationBaselineTicks, double durationExponent) {

  public ValueFactors {
    if (!Double.isFinite(potencyStep) || potencyStep < 0) {
      throw new IllegalArgumentException("potencyStep must be finite and non-negative");
    }
    if (durationBaselineTicks <= 0) {
      throw new IllegalArgumentException(
          "durationBaselineTicks must be positive, got " + durationBaselineTicks);
    }
    if (!Double.isFinite(durationExponent) || durationExponent <= 0) {
      throw new IllegalArgumentException("durationExponent must be finite and positive");
    }
  }
}
