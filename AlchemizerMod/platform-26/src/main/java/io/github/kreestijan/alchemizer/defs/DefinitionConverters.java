package io.github.kreestijan.alchemizer.defs;

import com.mojang.logging.LogUtils;
import io.github.kreestijan.alchemizer.common.effect.EffectAdapter;
import io.github.kreestijan.alchemizer.core.effect.EffectDefinition;
import io.github.kreestijan.alchemizer.core.effect.EffectId;
import io.github.kreestijan.alchemizer.core.effect.EffectRegistry;
import io.github.kreestijan.alchemizer.core.potion.PotionDefinition;
import io.github.kreestijan.alchemizer.core.recipe.BrewRecipe;
import io.github.kreestijan.alchemizer.core.recipe.RecipeBook;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import org.slf4j.Logger;

/**
 * Turns the loaded datapack registries into the records {@code core} reasons about.
 *
 * <p>Everything here is one-way and allocation-heavy, which is fine: it runs once per reload, on a
 * background thread, and produces values that are then immutable and shared.
 *
 * <p><b>Bad data is reported, not fatal.</b> Three different authoring mistakes are possible, and
 * each is handled at the level where the damage is smallest. A recipe that names a potion nobody
 * defined is dropped, because a recipe that can never match is worse than no recipe — it hides the
 * mistake. An effect value for an effect that is not registered is kept, because a datapack may
 * legitimately price an effect another datapack provides. Two recipes claiming the same ingredients
 * at the same time setting is the one that cannot be narrowed: either could be meant, so the book
 * is emptied rather than one of them being picked by iteration order, and the log says so.
 */
final class DefinitionConverters {

  private static final Logger LOGGER = LogUtils.getLogger();

  private DefinitionConverters() {}

  /**
   * @param registries the loaded registries, which must include this mod's three
   * @param tuning supplies the fallback value an unpriced effect is given
   * @return the definitions in the form the rules read
   */
  static ModDefinitions.Snapshot convert(HolderLookup.Provider registries, Tuning tuning) {
    Map<String, PotionDefinition> potions = potions(registries);
    return new ModDefinitions.Snapshot(
        effects(registries, tuning), potions, recipes(registries, potions));
  }

  /**
   * @param registries the loaded registries
   * @param tuning supplies the fallback value an unpriced effect is given
   * @return every authored effect value
   */
  private static EffectRegistry effects(HolderLookup.Provider registries, Tuning tuning) {
    Map<EffectId, EffectDefinition> byId = new HashMap<>();
    for (Holder.Reference<EffectValueDef> entry :
        registries.lookupOrThrow(ModRegistries.EFFECT_VALUE).listElements().toList()) {
      EffectId id = new EffectId(entry.key().identifier().toString());
      EffectValueDef definition = entry.value();

      // Asked of the game rather than authored, so that the flag cannot disagree with the effect it
      // describes. An effect that does not resolve is priced anyway, but cannot be known to be
      // instant — and an unresolvable effect is the one case where a potion naming it would have no
      // effects at all, so the value never reaches the formula either way.
      boolean instant = EffectAdapter.resolve(id).map(EffectAdapter::isInstant).orElse(false);

      byId.put(id, new EffectDefinition(id, definition.baseValue(), instant));
    }
    return new EffectRegistry(byId, tuning.fallbackEffectBaseValue());
  }

  /**
   * @param registries the loaded registries
   * @return every authored potion, keyed by the id it is named after
   */
  private static Map<String, PotionDefinition> potions(HolderLookup.Provider registries) {
    Map<String, PotionDefinition> byId = new HashMap<>();
    for (Holder.Reference<PotionDef> entry :
        registries.lookupOrThrow(ModRegistries.POTION).listElements().toList()) {
      String id = entry.key().identifier().toString();
      PotionDef definition = entry.value();
      byId.put(id, new PotionDefinition(id, definition.effects(), definition.defaultUnlocked()));
    }
    return byId;
  }

  /**
   * @param registries the loaded registries
   * @param potions every potion, so that a recipe's target can be resolved now rather than at brew
   *     time
   * @return a book of every recipe whose potion exists
   */
  private static RecipeBook recipes(
      HolderLookup.Provider registries, Map<String, PotionDefinition> potions) {
    List<BrewRecipe> built = new ArrayList<>();
    for (Holder.Reference<BrewRecipeDef> entry :
        registries.lookupOrThrow(ModRegistries.BREW_RECIPE).listElements().toList()) {
      String id = entry.key().identifier().toString();
      BrewRecipeDef definition = entry.value();
      String potionId = definition.potion().toString();

      PotionDefinition potion = potions.get(potionId);
      if (potion == null) {
        LOGGER.warn(
            "Brew recipe {} produces potion {}, which no datapack defines; the recipe is ignored",
            id,
            potionId);
        continue;
      }

      built.add(
          new BrewRecipe(id, definition.slots(), definition.time(), potion, definition.tier()));
    }

    try {
      return RecipeBook.of(built);
    } catch (IllegalArgumentException clash) {
      // Two recipes for the same ingredients at the same time setting: only one could ever match,
      // and which one is not something this code may decide. Emptying the book is the loud answer —
      // every brew becomes Murky Mess — and the message names both recipes.
      LOGGER.error(
          "The brew recipes are inconsistent, so no brew can succeed: {}", clash.getMessage());
      return RecipeBook.empty();
    }
  }
}
