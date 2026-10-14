package io.github.kreestijan.alchemizer.core.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every brew recipe the still knows, indexed so that matching does not scan the whole set.
 *
 * <p>Recipes are grouped by how many slots they use and which hourglass setting they need, because
 * both are known before any ingredient is examined.
 *
 * <p>Recipes are also ordered within a group by how specific they are, so that a recipe naming an
 * exact item is preferred over one accepting a tag. Without that, a stack of oak planks would match
 * both a {@code minecraft:planks} recipe and an {@code minecraft:oak_planks} recipe, and which
 * potion came out would depend on map iteration order.
 */
public final class RecipeBook {

  private final Map<Group, List<BrewRecipe>> byGroup;
  private final List<BrewRecipe> all;

  private RecipeBook(Map<Group, List<BrewRecipe>> byGroup, List<BrewRecipe> all) {
    this.byGroup = byGroup;
    this.all = all;
  }

  /**
   * @param recipes the recipes to index
   * @return an immutable book
   * @throws IllegalArgumentException if two recipes use the same ingredients at the same time
   *     setting, since only one of them could ever match
   */
  public static RecipeBook of(Collection<BrewRecipe> recipes) {
    Objects.requireNonNull(recipes, "recipes");

    Map<Shape, BrewRecipe> byShape = new HashMap<>();
    for (BrewRecipe recipe : recipes) {
      Objects.requireNonNull(recipe, "recipe");
      Shape shape = new Shape(recipe.slots(), recipe.time());
      BrewRecipe clash = byShape.putIfAbsent(shape, recipe);
      if (clash != null) {
        throw new IllegalArgumentException(
            "recipes '"
                + clash.id()
                + "' and '"
                + recipe.id()
                + "' use the same ingredients at the same time setting; one would never match");
      }
    }

    Map<Group, List<BrewRecipe>> grouping = new HashMap<>();
    for (BrewRecipe recipe : byShape.values()) {
      grouping
          .computeIfAbsent(
              new Group(recipe.slots().size(), recipe.time()), key -> new ArrayList<>())
          .add(recipe);
    }

    Map<Group, List<BrewRecipe>> immutable = new HashMap<>();
    for (Map.Entry<Group, List<BrewRecipe>> entry : grouping.entrySet()) {
      List<BrewRecipe> group = entry.getValue();
      group.sort(
          Comparator.comparingInt(RecipeBook::specificity)
              .reversed()
              .thenComparing(BrewRecipe::id));
      immutable.put(entry.getKey(), List.copyOf(group));
    }

    List<BrewRecipe> allRecipes = new ArrayList<>(byShape.values());
    allRecipes.sort(Comparator.comparing(BrewRecipe::id));
    return new RecipeBook(Map.copyOf(immutable), List.copyOf(allRecipes));
  }

  /**
   * @return a book with no recipes, in which every brew fails
   */
  public static RecipeBook empty() {
    return new RecipeBook(Map.of(), List.of());
  }

  /**
   * @param usedSlots the occupied slots, without trailing gaps
   * @param preset the hourglass setting
   * @return the first recipe that accepts those ingredients, if any
   */
  public Optional<BrewRecipe> find(List<IngredientSignature> usedSlots, HourglassPreset preset) {
    Objects.requireNonNull(usedSlots, "usedSlots");
    Objects.requireNonNull(preset, "preset");

    List<BrewRecipe> candidates = byGroup.get(new Group(usedSlots.size(), preset));
    if (candidates == null) {
      return Optional.empty();
    }
    for (BrewRecipe recipe : candidates) {
      if (recipe.matches(usedSlots)) {
        return Optional.of(recipe);
      }
    }
    return Optional.empty();
  }

  /**
   * @return every recipe, ordered by id
   */
  public List<BrewRecipe> all() {
    return all;
  }

  /**
   * @return how many recipes this book holds
   */
  public int size() {
    return all.size();
  }

  /**
   * @return whether the book holds no recipes
   */
  public boolean isEmpty() {
    return all.isEmpty();
  }

  private static int specificity(BrewRecipe recipe) {
    int score = 0;
    for (SlotMatcher matcher : recipe.slots()) {
      if (matcher instanceof SlotMatcher.ByItem) {
        score++;
      }
    }
    return score;
  }

  private record Shape(List<SlotMatcher> slots, HourglassPreset preset) {}

  private record Group(int slotCount, HourglassPreset preset) {}
}
