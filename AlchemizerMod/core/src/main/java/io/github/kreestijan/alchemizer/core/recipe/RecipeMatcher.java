package io.github.kreestijan.alchemizer.core.recipe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns the contents of the five ingredient slots into a recipe, or into nothing.
 *
 * <p>The one rule that is about shape rather than ingredients: <strong>empty slots may only come
 * last</strong>. A gap with an ingredient after it is not a shorter recipe, it is a mistake, and it
 * matches nothing. This is checked here rather than in the menu because the same layout arrives
 * from both the menu and a reloaded block entity.
 */
public final class RecipeMatcher {

  private RecipeMatcher() {}

  /**
   * @param slots exactly {@link BrewRecipe#MAX_SLOTS} slots, from first to last
   * @param preset the hourglass setting
   * @param book the recipes to match against
   * @return the recipe these ingredients brew, if there is one
   * @throws IllegalArgumentException if {@code slots} is not the full set of five
   */
  public static Optional<BrewRecipe> match(
      List<IngredientSignature> slots, HourglassPreset preset, RecipeBook book) {
    Objects.requireNonNull(slots, "slots");
    Objects.requireNonNull(preset, "preset");
    Objects.requireNonNull(book, "book");

    if (slots.size() != BrewRecipe.MAX_SLOTS) {
      throw new IllegalArgumentException(
          "expected exactly " + BrewRecipe.MAX_SLOTS + " slots, got " + slots.size());
    }
    if (hasHole(slots)) {
      return Optional.empty();
    }

    List<IngredientSignature> used = usedSlots(slots);
    if (used.isEmpty()) {
      return Optional.empty();
    }
    return book.find(used, preset);
  }

  /**
   * @param slots the ingredient slots
   * @return whether an occupied slot follows a vacant one
   */
  public static boolean hasHole(List<IngredientSignature> slots) {
    Objects.requireNonNull(slots, "slots");
    for (int i = firstEmptyIndex(slots) + 1; i < slots.size(); i++) {
      if (!slots.get(i).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param slots the ingredient slots
   * @return the occupied slots up to the first gap
   */
  public static List<IngredientSignature> usedSlots(List<IngredientSignature> slots) {
    Objects.requireNonNull(slots, "slots");
    return List.copyOf(slots.subList(0, firstEmptyIndex(slots)));
  }

  private static int firstEmptyIndex(List<IngredientSignature> slots) {
    for (int i = 0; i < slots.size(); i++) {
      if (slots.get(i).isEmpty()) {
        return i;
      }
    }
    return slots.size();
  }
}
