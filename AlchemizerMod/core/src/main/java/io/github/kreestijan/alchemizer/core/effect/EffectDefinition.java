package io.github.kreestijan.alchemizer.core.effect;

import java.util.Objects;

/**
 * What the value formula needs to know about one effect.
 *
 * <p>{@code baseValue} is the effect's worth before potency and duration are applied. It is
 * authored as datapack data rather than compiled in, so an effect added by another mod — or by this
 * mod later — can be given a value without touching code.
 *
 * <p>{@code instant} marks effects that take full hold the moment the potion is drunk (healing,
 * harming, saturation). Their value does not scale with duration, because the duration is
 * meaningless to them.
 *
 * @param id the effect this describes
 * @param baseValue the unmodified worth of one application, never negative
 * @param instant whether the effect applies immediately rather than over time
 */
public record EffectDefinition(EffectId id, int baseValue, boolean instant) {

  public EffectDefinition {
    Objects.requireNonNull(id, "id");
    if (baseValue < 0) {
      throw new IllegalArgumentException("baseValue must not be negative, got " + baseValue);
    }
  }
}
