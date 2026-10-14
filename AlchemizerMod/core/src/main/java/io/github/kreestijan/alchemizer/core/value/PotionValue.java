package io.github.kreestijan.alchemizer.core.value;

import io.github.kreestijan.alchemizer.core.effect.EffectSpec;
import java.util.List;
import java.util.Objects;

/**
 * A potion reduced to the effects it carries, which is all the value formula needs.
 *
 * <p>Kept separate from the potion definition so that changing what a potion is called, or which
 * recipe produces it, cannot change what it is worth.
 *
 * @param effects the effects the potion applies; may be empty, which is worth nothing
 */
public record PotionValue(List<EffectSpec> effects) {

  public PotionValue {
    Objects.requireNonNull(effects, "effects");
    effects = List.copyOf(effects);
  }

  public static PotionValue of(EffectSpec... effects) {
    return new PotionValue(List.of(effects));
  }

  /**
   * @return a potion carrying no effects
   */
  public static PotionValue none() {
    return new PotionValue(List.of());
  }
}
