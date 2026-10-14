package io.github.kreestijan.alchemizer.core.timing;

import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import java.util.Objects;

/**
 * How long a brew takes.
 *
 * <p>{@code DESIGN.md} gives the curve: the hourglass setting is the base, and each bellows or air
 * pressure valve cuts the time to {@code bellowsFactor} of what it was, so the tenth bellows saves
 * far less than the first. Bellows arrive with tier 2, so a tier 1 still always passes zero and
 * gets the unmodified time.
 *
 * <p>The text also promises the time never reaches zero and asks for no hardcoded minimum. The
 * formula cannot keep that promise on its own — repeated multiplication underflows, and rounding
 * eventually produces zero ticks. So the floor is real and configurable: a clamp of one second by
 * default, rather than a claim that the arithmetic does something it does not.
 */
public final class BrewTime {

  /** Game ticks in one second. Minecraft's tick rate is fixed at twenty. */
  public static final int TICKS_PER_SECOND = 20;

  private BrewTime() {}

  /**
   * @param preset the hourglass setting
   * @param bellowsValves how many bellows and valves feed the still
   * @param tuning supplies the base times, the per-bellows factor and the floor
   * @return the brew time in ticks
   */
  public static int ticks(HourglassPreset preset, int bellowsValves, Tuning tuning) {
    Objects.requireNonNull(preset, "preset");
    Objects.requireNonNull(tuning, "tuning");
    return ticks(
        tuning.baseSecondsFor(preset),
        bellowsValves,
        tuning.bellowsFactor(),
        tuning.minBrewTicks());
  }

  /**
   * @param baseSeconds the unmodified brew time
   * @param bellowsValves how many bellows and valves feed the still
   * @param bellowsFactor the multiplier applied per bellows, within {@code (0, 1]}
   * @param minTicks the floor under the result
   * @return the brew time in ticks, never below {@code minTicks}
   */
  public static int ticks(int baseSeconds, int bellowsValves, double bellowsFactor, int minTicks) {
    if (baseSeconds <= 0) {
      throw new IllegalArgumentException("baseSeconds must be positive, got " + baseSeconds);
    }
    if (minTicks <= 0) {
      throw new IllegalArgumentException("minTicks must be positive, got " + minTicks);
    }
    if (!Double.isFinite(bellowsFactor) || bellowsFactor <= 0 || bellowsFactor > 1) {
      throw new IllegalArgumentException(
          "bellowsFactor must be finite and within (0, 1], got " + bellowsFactor);
    }

    double scaled =
        baseSeconds
            * (double) TICKS_PER_SECOND
            * Math.pow(bellowsFactor, Math.max(0, bellowsValves));
    if (!Double.isFinite(scaled)) {
      return minTicks;
    }

    long rounded = Math.round(scaled);
    if (rounded <= minTicks) {
      return minTicks;
    }
    return (int) Math.min(rounded, Integer.MAX_VALUE);
  }
}
