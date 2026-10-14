package io.github.kreestijan.alchemizer.defs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.recipe.SlotMatcher;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * One way to brew, as authored in {@code data/<namespace>/alchemizer/brew_recipe/}.
 *
 * <p>A recipe lists only the slots it uses, in order — the matcher pads the rest with nothing, so
 * "the empty slots are at the end" is true of every recipe by construction and only ever has to be
 * checked on the input side.
 *
 * <p>The potion is named by id rather than spelled out, so one potion brewed two ways is one potion
 * file and two recipe files. The converter resolves the id against the potion registry when the
 * datapack loads; a recipe naming a potion that does not exist is skipped with a warning rather
 * than being allowed to sit in the book and never match.
 *
 * @param slots what each used slot must contain, from the first slot onwards
 * @param time the hourglass setting this recipe requires
 * @param potion the id of the potion this recipe produces
 * @param tier the still tier and fuel tier this recipe demands
 */
public record BrewRecipeDef(
    List<SlotMatcher> slots, HourglassPreset time, Identifier potion, int tier) {

  /**
   * The authored form of one slot: exactly one of the two spellings, never both and never neither.
   *
   * <p>Kept as a separate shape because that is what makes the "exactly one" rule expressible as a
   * decode error. A record holding two optionals that both happen to be present is a record that
   * has to be checked by every reader; a decode that refuses it is checked once, at load, where the
   * file name is still known.
   */
  private record RawSlot(Optional<String> item, Optional<String> tag) {

    private static final MapCodec<RawSlot> MAP_CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance
                    .group(
                        Codec.STRING.optionalFieldOf("item").forGetter(RawSlot::item),
                        Codec.STRING.optionalFieldOf("tag").forGetter(RawSlot::tag))
                    .apply(instance, RawSlot::new));

    private static RawSlot of(SlotMatcher matcher) {
      return switch (matcher) {
        case SlotMatcher.ByItem byItem ->
            new RawSlot(Optional.of(byItem.itemId()), Optional.empty());
        case SlotMatcher.ByTag byTag -> new RawSlot(Optional.empty(), Optional.of(byTag.tagId()));
      };
    }

    private DataResult<SlotMatcher> toMatcher() {
      if (item.isPresent() == tag.isPresent()) {
        return DataResult.error(
            () -> "a brew slot must name exactly one of \"item\" or \"tag\", got " + this);
      }
      try {
        return DataResult.success(
            item.<SlotMatcher>map(SlotMatcher.ByItem::new)
                .orElseGet(() -> new SlotMatcher.ByTag(tag.orElseThrow())));
      } catch (IllegalArgumentException blank) {
        return DataResult.error(blank::getMessage);
      }
    }
  }

  private static final Codec<SlotMatcher> SLOT_CODEC =
      RawSlot.MAP_CODEC
          .codec()
          .flatXmap(RawSlot::toMatcher, matcher -> DataResult.success(RawSlot.of(matcher)));

  /**
   * The hourglass setting, written the way it is named on the machine — {@code "5s"}, {@code
   * "15s"}, and so on.
   *
   * <p>The label comes from the preset's own name rather than from the configured brew time, so
   * that retuning how long five seconds takes does not silently rename the setting every recipe
   * refers to.
   */
  private static final Codec<HourglassPreset> TIME_CODEC =
      Codec.STRING.flatXmap(
          BrewRecipeDef::presetFromLabel, preset -> DataResult.success(label(preset)));

  public static final Codec<BrewRecipeDef> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      SLOT_CODEC.listOf().fieldOf("slots").forGetter(BrewRecipeDef::slots),
                      TIME_CODEC.fieldOf("time").forGetter(BrewRecipeDef::time),
                      Identifier.CODEC.fieldOf("potion").forGetter(BrewRecipeDef::potion),
                      Codec.intRange(1, TuningDefaults.MACHINE_TIER_CAP)
                          .fieldOf("tier")
                          .forGetter(BrewRecipeDef::tier))
                  .apply(instance, BrewRecipeDef::new));

  private static String label(HourglassPreset preset) {
    return preset.baseSeconds() + "s";
  }

  private static DataResult<HourglassPreset> presetFromLabel(String text) {
    for (HourglassPreset preset : HourglassPreset.values()) {
      if (label(preset).equals(text)) {
        return DataResult.success(preset);
      }
    }
    return DataResult.error(
        () ->
            "unknown hourglass setting '"
                + text
                + "'; expected one of "
                + Arrays.stream(HourglassPreset.values()).map(BrewRecipeDef::label).toList());
  }
}
