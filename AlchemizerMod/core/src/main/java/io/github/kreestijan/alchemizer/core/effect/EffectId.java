package io.github.kreestijan.alchemizer.core.effect;

import java.util.Objects;

/**
 * The namespaced identifier of a mob effect, held as plain text.
 *
 * <p>This module has no Minecraft on its classpath, so it cannot name {@code Holder<MobEffect>} or
 * {@code Identifier}. Effects are therefore identified here by the same {@code namespace:path}
 * string that Minecraft itself uses, and the platform adapter translates between the two
 * representations. That is what keeps the mod from being limited to vanilla effects: any id
 * resolves, whether it is {@code minecraft:speed} or one this mod registers later.
 *
 * @param value the full {@code namespace:path} id, for example {@code minecraft:speed}
 */
public record EffectId(String value) {

  public EffectId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Effect id must not be blank");
    }
    int separator = value.indexOf(':');
    if (separator <= 0
        || separator == value.length() - 1
        || value.indexOf(':', separator + 1) >= 0) {
      throw new IllegalArgumentException(
          "Effect id must be a single namespace:path pair, got '" + value + "'");
    }
  }

  /**
   * @return the part before the colon
   */
  public String namespace() {
    return value.substring(0, value.indexOf(':'));
  }

  /**
   * @return the part after the colon
   */
  public String path() {
    return value.substring(value.indexOf(':') + 1);
  }

  @Override
  public String toString() {
    return value;
  }
}
