package io.github.kreestijan.alchemizer.core.potion;

import io.github.kreestijan.alchemizer.core.effect.EffectSpec;
import io.github.kreestijan.alchemizer.core.value.PotionValue;
import java.util.List;
import java.util.Objects;

/**
 * A potion the still can produce: an id, the effects it applies, and whether it is known from the
 * start.
 *
 * <p>Potions are authored as datapack data, so adding one never means editing code — including
 * potions built from effects this mod registers later.
 *
 * <p>{@code defaultUnlocked} matters once recipe discovery exists. At baseline every recipe is
 * known to every player, so the flag changes nothing yet; it marks the potions that must stay known
 * when discovery starts gating the rest.
 *
 * @param potionId the namespaced id of this potion, for example {@code alchemizer:test_potion}
 * @param effects the effects applied on drinking; may be empty for a potion that does nothing
 * @param defaultUnlocked whether the recipe stays known once discovery gates the others
 */
public record PotionDefinition(String potionId, List<EffectSpec> effects, boolean defaultUnlocked) {

  public PotionDefinition {
    Objects.requireNonNull(potionId, "potionId");
    if (potionId.isBlank()) {
      throw new IllegalArgumentException("potionId must not be blank");
    }
    Objects.requireNonNull(effects, "effects");
    effects = List.copyOf(effects);
  }

  /**
   * @param potionId the namespaced id
   * @param effects the effects applied on drinking
   * @return a definition that is known from the start
   */
  public static PotionDefinition unlocked(String potionId, List<EffectSpec> effects) {
    return new PotionDefinition(potionId, effects, true);
  }

  /**
   * @return the effects of this potion, reduced to what the value formula needs
   */
  public PotionValue toValue() {
    return new PotionValue(effects);
  }
}
