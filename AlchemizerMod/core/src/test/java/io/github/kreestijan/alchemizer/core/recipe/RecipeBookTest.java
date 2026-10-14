package io.github.kreestijan.alchemizer.core.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.potion.PotionDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The index: what it refuses to hold, and which of two matching recipes wins.
 *
 * <p>The tie-break matters because tags overlap with items. If a book held both a {@code
 * minecraft:planks} recipe and an {@code minecraft:oak_planks} recipe and neither outranked the
 * other, the potion a player got would depend on map iteration order.
 */
class RecipeBookTest {

  private static BrewRecipe recipe(String id, PotionDefinition potion, SlotMatcher... slots) {
    return new BrewRecipe(id, List.of(slots), HourglassPreset.FIVE, potion, 1);
  }

  private static PotionDefinition potion(String id) {
    return PotionDefinition.unlocked(id, List.of());
  }

  private static IngredientSignature oakPlanks() {
    return IngredientSignature.of("minecraft:oak_planks", Set.of("minecraft:planks"));
  }

  @Test
  void anEmptyBookHoldsNothingAndMatchesNothing() {
    RecipeBook book = RecipeBook.empty();

    assertTrue(book.isEmpty());
    assertEquals(0, book.size());
    assertEquals(Optional.empty(), book.find(List.of(oakPlanks()), HourglassPreset.FIVE));
  }

  @Test
  void twoRecipesWithTheSameShapeAreRefused() {
    BrewRecipe first =
        recipe(
            "alchemizer:first",
            potion("alchemizer:a"),
            new SlotMatcher.ByItem("minecraft:sugar"),
            new SlotMatcher.ByItem("minecraft:redstone"));
    BrewRecipe second =
        recipe(
            "alchemizer:second",
            potion("alchemizer:b"),
            new SlotMatcher.ByItem("minecraft:sugar"),
            new SlotMatcher.ByItem("minecraft:redstone"));

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> RecipeBook.of(List.of(first, second)));
    assertTrue(thrown.getMessage().contains("alchemizer:first"));
    assertTrue(thrown.getMessage().contains("alchemizer:second"));
  }

  @Test
  void theSameIngredientsAtAnotherTimeAreNotADuplicate() {
    BrewRecipe fiveSecond =
        recipe(
            "alchemizer:five", potion("alchemizer:a"), new SlotMatcher.ByItem("minecraft:sugar"));
    BrewRecipe fifteenSecond =
        new BrewRecipe(
            "alchemizer:fifteen",
            List.of(new SlotMatcher.ByItem("minecraft:sugar")),
            HourglassPreset.FIFTEEN,
            potion("alchemizer:b"),
            1);

    RecipeBook book = RecipeBook.of(List.of(fiveSecond, fifteenSecond));

    assertEquals(2, book.size());
  }

  @Test
  void anExactItemBeatsATag() {
    BrewRecipe tagRecipe =
        recipe(
            "alchemizer:by_tag",
            potion("alchemizer:tagged"),
            new SlotMatcher.ByTag("minecraft:planks"));
    BrewRecipe itemRecipe =
        recipe(
            "alchemizer:by_item",
            potion("alchemizer:exact"),
            new SlotMatcher.ByItem("minecraft:oak_planks"));

    // Deliberately inserted in the order that would lose if insertion order decided anything.
    RecipeBook book = RecipeBook.of(List.of(tagRecipe, itemRecipe));

    BrewRecipe found = book.find(List.of(oakPlanks()), HourglassPreset.FIVE).orElseThrow();

    assertEquals("alchemizer:by_item", found.id());
    assertEquals("alchemizer:exact", found.potion().potionId());
  }

  @Test
  void aRecipeIsOnlyFoundUnderItsOwnSlotCount() {
    BrewRecipe twoSlot =
        recipe(
            "alchemizer:two",
            potion("alchemizer:a"),
            new SlotMatcher.ByItem("minecraft:sugar"),
            new SlotMatcher.ByItem("minecraft:redstone"));
    RecipeBook book = RecipeBook.of(List.of(twoSlot));

    assertTrue(book.find(List.of(oakPlanks()), HourglassPreset.FIVE).isEmpty());
    assertTrue(book.find(List.of(oakPlanks()), HourglassPreset.FIFTEEN).isEmpty());
    assertTrue(
        book.find(
                List.of(
                    IngredientSignature.of("minecraft:sugar", Set.of()),
                    IngredientSignature.of("minecraft:redstone", Set.of())),
                HourglassPreset.FIVE)
            .isPresent());
  }

  @Test
  void allRecipesAreListedInAStableOrder() {
    RecipeBook book =
        RecipeBook.of(
            List.of(
                recipe(
                    "alchemizer:zebra",
                    potion("alchemizer:z"),
                    new SlotMatcher.ByItem("minecraft:sugar")),
                recipe(
                    "alchemizer:alpha",
                    potion("alchemizer:a"),
                    new SlotMatcher.ByItem("minecraft:redstone"))));

    assertEquals(
        List.of("alchemizer:alpha", "alchemizer:zebra"),
        book.all().stream().map(BrewRecipe::id).toList());
  }
}
