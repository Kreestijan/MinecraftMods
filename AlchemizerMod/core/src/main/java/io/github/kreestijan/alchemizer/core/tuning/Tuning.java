package io.github.kreestijan.alchemizer.core.tuning;

import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.value.ValueFactors;
import java.util.List;
import java.util.Objects;

/**
 * Every number the rules depend on, in one immutable bundle.
 *
 * <p>The mod's config file is the user-facing surface, but config is read in one place — the
 * platform module — and passed in as this. That keeps the rules testable without a config file, and
 * keeps the "where does this number come from" question answerable by looking at a single
 * declaration.
 *
 * @param machineTierCap the highest still tier the mod will recognise
 * @param fuelTierGatingEnabled whether a recipe's tier must be met by both the fuel and the machine
 * @param potionStackSize how many potions sharing every trait may occupy one stack; this overrides
 *     vanilla's own limit of one rather than sitting beside it, so it is a whole-game rule and not
 *     only a rule about this mod's machines
 * @param tankCapacityMb how much water one Alchemical Tank holds
 * @param doseMb how much water one potion consumes
 * @param scanRadius how far from the pressure chamber the still is searched for parts, in blocks
 * @param hourglassBaseSeconds the unmodified brew time at each hourglass setting, by preset ordinal
 * @param bellowsFactor the multiplier applied per bellows or valve, below one to shorten the brew
 * @param minBrewTicks the floor under a brew time, so that no combination reaches zero
 * @param fallbackEffectBaseValue the worth given to an effect with no authored entry
 * @param valueFactors the shape of the value curve
 * @param debugLogging whether the mod logs still validation in detail
 */
public record Tuning(
    int machineTierCap,
    boolean fuelTierGatingEnabled,
    int potionStackSize,
    int tankCapacityMb,
    int doseMb,
    int scanRadius,
    List<Integer> hourglassBaseSeconds,
    double bellowsFactor,
    int minBrewTicks,
    int fallbackEffectBaseValue,
    ValueFactors valueFactors,
    boolean debugLogging) {

  public Tuning {
    if (machineTierCap < 1) {
      throw new IllegalArgumentException(
          "machineTierCap must be at least 1, got " + machineTierCap);
    }
    if (potionStackSize < 1) {
      throw new IllegalArgumentException(
          "potionStackSize must be at least 1, got " + potionStackSize);
    }
    if (tankCapacityMb < 0) {
      throw new IllegalArgumentException(
          "tankCapacityMb must not be negative, got " + tankCapacityMb);
    }
    if (doseMb < 1) {
      throw new IllegalArgumentException("doseMb must be at least 1, got " + doseMb);
    }
    if (scanRadius < 1) {
      throw new IllegalArgumentException("scanRadius must be at least 1, got " + scanRadius);
    }
    Objects.requireNonNull(hourglassBaseSeconds, "hourglassBaseSeconds");
    hourglassBaseSeconds = List.copyOf(hourglassBaseSeconds);
    if (hourglassBaseSeconds.size() != HourglassPreset.values().length) {
      throw new IllegalArgumentException(
          "hourglassBaseSeconds must hold one entry per preset ("
              + HourglassPreset.values().length
              + "), got "
              + hourglassBaseSeconds.size());
    }
    for (int i = 0; i < hourglassBaseSeconds.size(); i++) {
      Integer seconds = hourglassBaseSeconds.get(i);
      if (seconds == null || seconds <= 0) {
        throw new IllegalArgumentException(
            "hourglassBaseSeconds[" + i + "] must be positive, got " + seconds);
      }
    }
    if (!Double.isFinite(bellowsFactor) || bellowsFactor <= 0 || bellowsFactor > 1) {
      throw new IllegalArgumentException(
          "bellowsFactor must be finite and within (0, 1], got " + bellowsFactor);
    }
    if (minBrewTicks < 1) {
      throw new IllegalArgumentException("minBrewTicks must be at least 1, got " + minBrewTicks);
    }
    if (fallbackEffectBaseValue < 0) {
      throw new IllegalArgumentException(
          "fallbackEffectBaseValue must not be negative, got " + fallbackEffectBaseValue);
    }
    Objects.requireNonNull(valueFactors, "valueFactors");
  }

  /**
   * @param preset the hourglass setting
   * @return the unmodified brew time at that setting, in seconds
   */
  public int baseSecondsFor(HourglassPreset preset) {
    Objects.requireNonNull(preset, "preset");
    return hourglassBaseSeconds.get(preset.ordinal());
  }

  /**
   * @return this tuning with logging switched on
   */
  public Tuning withDebugLogging() {
    return new Tuning(
        machineTierCap,
        fuelTierGatingEnabled,
        potionStackSize,
        tankCapacityMb,
        doseMb,
        scanRadius,
        hourglassBaseSeconds,
        bellowsFactor,
        minBrewTicks,
        fallbackEffectBaseValue,
        valueFactors,
        true);
  }
}
