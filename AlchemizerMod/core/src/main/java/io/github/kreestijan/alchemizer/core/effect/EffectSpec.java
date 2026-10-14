package io.github.kreestijan.alchemizer.core.effect;

import java.util.Objects;

/**
 * One effect as it appears on a particular potion: which effect, how strong, and for how long.
 *
 * <p>A potion holds a list of these, which may be empty. An empty list is a potion that is
 * drinkable and does nothing — the form the Test Potion takes, and a legitimate result rather than
 * a failure.
 *
 * @param effect the effect applied
 * @param potency the amplifier, zero-based, matching Minecraft's own convention
 * @param durationTicks how long the effect lasts, in game ticks; ignored for instant effects
 */
public record EffectSpec(EffectId effect, int potency, int durationTicks) {

  public EffectSpec {
    Objects.requireNonNull(effect, "effect");
    if (potency < 0) {
      throw new IllegalArgumentException("potency must not be negative, got " + potency);
    }
    if (durationTicks < 0) {
      throw new IllegalArgumentException(
          "durationTicks must not be negative, got " + durationTicks);
    }
  }
}
