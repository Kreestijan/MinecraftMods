package io.github.kreestijan.alchemizer.defs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kreestijan.alchemizer.core.effect.EffectId;
import io.github.kreestijan.alchemizer.core.effect.EffectSpec;
import java.util.List;

/**
 * A potion, as authored in {@code data/<namespace>/alchemizer/potion/}.
 *
 * <p>A potion is a list of effects and nothing else. It has no name of its own in the file: the
 * file's name is the potion's id, and that id is what the game looks up as a translation key. It
 * has no tier and no recipe either — what makes a potion reachable is a brew recipe that names it,
 * so the same potion can be brewed more than one way.
 *
 * <p><b>An empty list is legal and means something.</b> A potion with no effects is drinkable and
 * does nothing, which is exactly what the Test Potion is for: it proves the whole loop — matching,
 * water, fuel, time, bottle, output — with no effect machinery in the way. Read as "this file is
 * incomplete" it would look like a mistake, so it is called out here.
 *
 * @param effects the effects applied on drinking; may be empty
 * @param defaultUnlocked whether this potion's recipe stays known once discovery gates the rest
 */
public record PotionDef(List<EffectSpec> effects, boolean defaultUnlocked) {

  /** The amplifier is clamped to this by the effect instance itself, so accepting more is a lie. */
  private static final int MAX_POTENCY = 255;

  /**
   * Effect ids are decoded by construction so that a misspelt or un-namespaced id is a load error
   * naming the file, rather than an exception from {@link EffectId}'s constructor at some later
   * point where the file is no longer in hand.
   */
  private static final Codec<EffectId> EFFECT_ID_CODEC =
      Codec.STRING.flatXmap(
          text -> {
            try {
              return DataResult.success(new EffectId(text));
            } catch (IllegalArgumentException notAnId) {
              return DataResult.error(
                  () -> "expected a single namespace:path effect id, got '" + text + "'");
            }
          },
          id -> DataResult.success(id.value()));

  /**
   * {@code potency} and {@code duration_ticks} are both optional because both have a natural zero:
   * a potion may well want the weakest application of an effect, and an instant effect has no
   * duration to state. A lasting effect that omits {@code duration_ticks} therefore lasts no time
   * at all, which is a mistake the author sees immediately rather than a default guessed here.
   */
  private static final Codec<EffectSpec> EFFECT_CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      EFFECT_ID_CODEC.fieldOf("effect").forGetter(EffectSpec::effect),
                      Codec.intRange(0, MAX_POTENCY)
                          .optionalFieldOf("potency", 0)
                          .forGetter(EffectSpec::potency),
                      Codec.intRange(0, Integer.MAX_VALUE)
                          .optionalFieldOf("duration_ticks", 0)
                          .forGetter(EffectSpec::durationTicks))
                  .apply(instance, EffectSpec::new));

  public static final Codec<PotionDef> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      EFFECT_CODEC
                          .listOf()
                          .optionalFieldOf("effects", List.of())
                          .forGetter(PotionDef::effects),
                      Codec.BOOL
                          .optionalFieldOf("default_unlocked", false)
                          .forGetter(PotionDef::defaultUnlocked))
                  .apply(instance, PotionDef::new));
}
