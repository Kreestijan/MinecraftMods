package io.github.kreestijan.alchemizer.defs;

import io.github.kreestijan.alchemizer.core.effect.EffectRegistry;
import io.github.kreestijan.alchemizer.core.potion.PotionDefinition;
import io.github.kreestijan.alchemizer.core.recipe.RecipeBook;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The datapack definitions, in the form the rules read them, as of the last reload.
 *
 * <p>The registries under this are Minecraft's, loaded from JSON and frozen for the life of a
 * server; the rules are {@code core}'s, which cannot name a registry. This is where the second is
 * built from the first, once per reload, so that a brew in progress costs a map lookup rather than
 * a registry walk.
 *
 * <p>Two things make this worth a class rather than a call. First, a block entity can be ticked
 * before any datapack has been read — including on a client, where these registries are never
 * loaded at all — and something has to answer. Second, rebuilding the book on every brew would be
 * work done thousands of times to produce the same answer.
 *
 * <p>The field is volatile because it is written at the end of a reload and read from the server
 * thread. The snapshot it points at is immutable, so publishing it is the only concern.
 */
public final class ModDefinitions {

  private static volatile Snapshot current = Snapshot.empty(TuningDefaults.defaultTuning());

  private ModDefinitions() {}

  /**
   * @return the definitions in force right now, never null
   */
  public static Snapshot current() {
    return current;
  }

  /**
   * Replaces the definitions.
   *
   * <p>Called from the reload listener's apply step, which runs on the server thread, and nowhere
   * else.
   *
   * @param snapshot the definitions just loaded
   */
  static void publish(Snapshot snapshot) {
    current = snapshot;
  }

  /**
   * Everything a brew needs to decide what it is making.
   *
   * @param effects what each effect is worth
   * @param potions every authored potion, by its id
   * @param recipes every authored recipe
   */
  public record Snapshot(
      EffectRegistry effects, Map<String, PotionDefinition> potions, RecipeBook recipes) {

    public Snapshot {
      Objects.requireNonNull(effects, "effects");
      potions = Map.copyOf(potions);
      Objects.requireNonNull(recipes, "recipes");
    }

    /**
     * @param tuning supplies the fallback value an unpriced effect is given
     * @return definitions in which nothing is authored and no brew can succeed
     */
    public static Snapshot empty(Tuning tuning) {
      return new Snapshot(
          EffectRegistry.withFallbackOnly(tuning.fallbackEffectBaseValue()),
          Map.of(),
          RecipeBook.empty());
    }

    /**
     * @param potionId the potion's namespaced id
     * @return the potion, or empty when no datapack defines one under that id
     */
    public Optional<PotionDefinition> potion(String potionId) {
      return Optional.ofNullable(potions.get(potionId));
    }
  }
}
