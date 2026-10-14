package io.github.kreestijan.alchemizer.common.effect;

import io.github.kreestijan.alchemizer.core.effect.EffectId;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * Turns the plain-text effect ids {@code core} carries into the game's own effect holders, and back
 * the other way for the one fact that only the game knows.
 *
 * <p>This is the second half of the seam described in the module's package documentation. {@code
 * core} identifies an effect by the same {@code namespace:path} string Minecraft does, because it
 * cannot name {@code Holder<MobEffect>}; this is the only place that knows both representations.
 *
 * <p><b>An unresolvable id is not an error here.</b> A potion can name an effect from a mod that is
 * not installed, or one that was renamed, and the answer to "what is that" is then simply nothing.
 * Deciding what to do about it belongs to the caller, which can say which potion was affected; a
 * throw here would take out the whole brew for one bad id.
 */
public final class EffectAdapter {

  private EffectAdapter() {}

  /**
   * @param id the effect's namespaced id, as authored in data
   * @return the registered effect, or empty when nothing is registered under that id
   */
  public static Optional<Holder<MobEffect>> resolve(EffectId id) {
    Identifier key = Identifier.tryParse(id.value());
    if (key == null) {
      // The id is shaped the way core requires but not the way Minecraft does — an uppercase letter
      // is the usual reason. Nothing is registered under a name Minecraft cannot spell.
      return Optional.empty();
    }
    return BuiltInRegistries.MOB_EFFECT.get(key).<Holder<MobEffect>>map(effect -> effect);
  }

  /**
   * Whether an effect takes hold all at once instead of running for a duration.
   *
   * <p>Asked of the effect rather than authored alongside its value. There is exactly one true
   * answer per effect, and it already exists in the game; a second copy in data could only ever
   * disagree with it.
   *
   * @param effect the effect to ask about
   * @return whether the effect is instant
   */
  public static boolean isInstant(Holder<MobEffect> effect) {
    return effect.value().isInstantenous();
  }
}
