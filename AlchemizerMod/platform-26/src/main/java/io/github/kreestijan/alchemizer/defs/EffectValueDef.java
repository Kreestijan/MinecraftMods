package io.github.kreestijan.alchemizer.defs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one mob effect is worth, as authored in {@code data/<namespace>/alchemizer/effect_value/}.
 *
 * <p>The file is named after the effect it prices and holds one number, which is the whole
 * extension point: an effect added by another mod can be given a value by dropping a JSON file next
 * to this mod's own, with no code change on either side. An effect with no file at all is not an
 * error — {@code EffectRegistry} falls back to a configured default — so a potion built from an
 * unpriced effect still brews.
 *
 * <p><b>There is no "instant" flag here.</b> Whether an effect applies all at once is a fact about
 * the effect, not an opinion a datapack gets to hold, and the game already knows it. The converter
 * asks the effect itself and records the answer alongside this value.
 *
 * @param baseValue the effect's worth before potency and duration are applied
 */
public record EffectValueDef(int baseValue) {

  /**
   * Read with a range rather than as a bare int, so that a negative value is reported as a problem
   * with the file that contains it instead of as an exception from a record's constructor.
   */
  public static final Codec<EffectValueDef> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.intRange(0, Integer.MAX_VALUE)
                          .fieldOf("base_value")
                          .forGetter(EffectValueDef::baseValue))
                  .apply(instance, EffectValueDef::new));
}
