package io.github.kreestijan.alchemizer.core.recipe;

import io.github.kreestijan.alchemizer.core.potion.BrewOutcome;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The one place that decides whether a finished brew succeeds.
 *
 * <p>Success is a matched recipe. Everything else is Murky Mess: the ingredients, the water and the
 * time are spent and the player gets back something undrinkable. That is deliberate rather than
 * punitive — experimenting has to cost something, or the still's whole premise falls apart.
 *
 * <p>Murky Mess is a fallback here rather than a catch-all recipe in the datapack, so that a
 * datapack cannot accidentally make experimentation free by reordering recipes.
 */
public final class BrewResolver {

  private BrewResolver() {}

  private static final String NO_MATCH = "no recipe matched";

  /**
   * The recipe these ingredients brew, if there is one.
   *
   * <p>Separate from {@link #resolve} because a brew in progress needs more than the potion: it
   * also needs the tier the recipe demands, and that has to be taken from the same match. A second
   * lookup for the tier could disagree with the first, and the still would then brew one recipe
   * while demanding the fuel of another.
   *
   * @param slots exactly {@link BrewRecipe#MAX_SLOTS} slots, from first to last
   * @param preset the hourglass setting
   * @param book the recipes to match against
   * @return the recipe, or empty when nothing matches
   */
  public static Optional<BrewRecipe> resolveRecipe(
      List<IngredientSignature> slots, HourglassPreset preset, RecipeBook book) {
    Objects.requireNonNull(slots, "slots");
    Objects.requireNonNull(preset, "preset");
    Objects.requireNonNull(book, "book");

    return RecipeMatcher.match(slots, preset, book);
  }

  /**
   * @param slots exactly {@link BrewRecipe#MAX_SLOTS} slots, from first to last
   * @param preset the hourglass setting
   * @param book the recipes to match against
   * @return the potion these ingredients brew, or Murky Mess
   */
  public static BrewOutcome resolve(
      List<IngredientSignature> slots, HourglassPreset preset, RecipeBook book) {
    return resolveRecipe(slots, preset, book)
        .<BrewOutcome>map(recipe -> new BrewOutcome.Success(recipe.potion()))
        .orElseGet(() -> new BrewOutcome.MurkyMess(NO_MATCH));
  }
}
