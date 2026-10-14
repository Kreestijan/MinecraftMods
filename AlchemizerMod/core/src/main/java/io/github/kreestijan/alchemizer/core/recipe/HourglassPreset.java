package io.github.kreestijan.alchemizer.core.recipe;

import java.util.Optional;

/**
 * The four hourglass settings on the still.
 *
 * <p>The preset is part of what identifies a recipe, not just a speed knob: the same ingredients
 * set to a different time are a different potion, or none at all.
 */
public enum HourglassPreset {
  FIVE(5),
  FIFTEEN(15),
  THIRTY(30),
  FORTY_FIVE(45);

  private final int baseSeconds;

  HourglassPreset(int baseSeconds) {
    this.baseSeconds = baseSeconds;
  }

  /**
   * @return the unmodified brew time at this setting, before any reduction
   */
  public int baseSeconds() {
    return baseSeconds;
  }

  /**
   * @param ordinal the enum ordinal as it arrives over the network
   * @return the preset, or empty if the ordinal is out of range
   */
  public static Optional<HourglassPreset> ofOrdinal(int ordinal) {
    HourglassPreset[] values = values();
    if (ordinal < 0 || ordinal >= values.length) {
      return Optional.empty();
    }
    return Optional.of(values[ordinal]);
  }
}
