package io.github.kreestijan.alchemizer.core.effect;

import java.util.Map;
import java.util.Objects;

/**
 * The set of authored effect definitions, plus the answer for everything else.
 *
 * <p>An effect with no authored entry is not an error. Brewing must still work when a player builds
 * a potion from an effect nobody has priced yet, so {@link #resolve(EffectId)} falls back to a
 * configured default rather than failing. Unknown effects are treated as non-instant, since instant
 * effects are the exception and the platform adapter can supply the true answer when it can resolve
 * the effect at all.
 *
 * @param byId the authored definitions, keyed by effect id
 * @param fallbackBaseValue the worth given to an effect with no authored entry
 */
public record EffectRegistry(Map<EffectId, EffectDefinition> byId, int fallbackBaseValue) {

  public EffectRegistry {
    Objects.requireNonNull(byId, "byId");
    byId = Map.copyOf(byId);
    if (fallbackBaseValue < 0) {
      throw new IllegalArgumentException(
          "fallbackBaseValue must not be negative, got " + fallbackBaseValue);
    }
  }

  /**
   * An empty registry in which every effect falls back to {@code fallbackBaseValue}.
   *
   * @param fallbackBaseValue the worth given to any effect
   * @return a registry with no authored entries
   */
  public static EffectRegistry withFallbackOnly(int fallbackBaseValue) {
    return new EffectRegistry(Map.of(), fallbackBaseValue);
  }

  /**
   * @param id the effect to look up
   * @return the authored definition, or a fallback definition for an unpriced effect
   */
  public EffectDefinition resolve(EffectId id) {
    Objects.requireNonNull(id, "id");
    EffectDefinition authored = byId.get(id);
    if (authored != null) {
      return authored;
    }
    return new EffectDefinition(id, fallbackBaseValue, false);
  }

  /**
   * @param id the effect to look up
   * @return whether an entry was authored for this effect
   */
  public boolean isPriced(EffectId id) {
    return byId.containsKey(id);
  }
}
