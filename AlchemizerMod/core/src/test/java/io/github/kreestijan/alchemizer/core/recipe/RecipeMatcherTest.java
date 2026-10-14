package io.github.kreestijan.alchemizer.core.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.potion.BrewOutcome;
import io.github.kreestijan.alchemizer.core.potion.PotionDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Matching the five slots against the book.
 *
 * <p>The recipe under test is the Test Potion the mod ships with: a spider eye, then a dandelion,
 * at the five second setting.
 */
class RecipeMatcherTest {

  private static final PotionDefinition TEST_POTION =
      PotionDefinition.unlocked("alchemizer:test_potion", List.of());

  private static final BrewRecipe TEST_RECIPE =
      new BrewRecipe(
          "alchemizer:test_potion",
          List.of(
              new SlotMatcher.ByItem("minecraft:spider_eye"),
              new SlotMatcher.ByItem("minecraft:dandelion")),
          HourglassPreset.FIVE,
          TEST_POTION,
          1);

  private static final RecipeBook BOOK = RecipeBook.of(List.of(TEST_RECIPE));

  private static IngredientSignature item(String id, String... tags) {
    return IngredientSignature.of(id, Set.of(tags));
  }

  /** Pads the given slots with vacant ones up to the full five the pressure chamber has. */
  private static List<IngredientSignature> slots(IngredientSignature... given) {
    List<IngredientSignature> all = new ArrayList<>(List.of(given));
    if (all.size() > BrewRecipe.MAX_SLOTS) {
      throw new IllegalArgumentException("too many slots for this helper");
    }
    while (all.size() < BrewRecipe.MAX_SLOTS) {
      all.add(IngredientSignature.EMPTY);
    }
    return List.copyOf(all);
  }

  @Test
  void theTestPotionMatches() {
    List<IngredientSignature> inventory =
        slots(item("minecraft:spider_eye"), item("minecraft:dandelion"));

    BrewOutcome outcome = BrewResolver.resolve(inventory, HourglassPreset.FIVE, BOOK);

    BrewOutcome.Success success = assertInstanceOf(BrewOutcome.Success.class, outcome);
    assertEquals("alchemizer:test_potion", success.potion().potionId());
    assertTrue(success.potion().effects().isEmpty(), "the Test Potion drinks to nothing");
  }

  @Test
  void orderMatters() {
    List<IngredientSignature> reversed =
        slots(item("minecraft:dandelion"), item("minecraft:spider_eye"));

    assertInstanceOf(
        BrewOutcome.MurkyMess.class, BrewResolver.resolve(reversed, HourglassPreset.FIVE, BOOK));
  }

  @Test
  void theWrongHourglassSettingIsADifferentRecipe() {
    List<IngredientSignature> inventory =
        slots(item("minecraft:spider_eye"), item("minecraft:dandelion"));

    assertInstanceOf(
        BrewOutcome.MurkyMess.class,
        BrewResolver.resolve(inventory, HourglassPreset.FIFTEEN, BOOK));
  }

  @Test
  void aGapBeforeAnIngredientMatchesNothing() {
    List<IngredientSignature> holed =
        slots(item("minecraft:spider_eye"), IngredientSignature.EMPTY, item("minecraft:dandelion"));

    assertTrue(RecipeMatcher.hasHole(holed));
    assertInstanceOf(
        BrewOutcome.MurkyMess.class, BrewResolver.resolve(holed, HourglassPreset.FIVE, BOOK));
  }

  @Test
  void trailingEmptySlotsAreNotAGap() {
    List<IngredientSignature> inventory =
        slots(item("minecraft:spider_eye"), item("minecraft:dandelion"));

    assertEquals(
        List.of(item("minecraft:spider_eye"), item("minecraft:dandelion")),
        RecipeMatcher.usedSlots(inventory));
  }

  @Test
  void anEmptyStillMatchesNothingRatherThanEveryRecipe() {
    assertInstanceOf(
        BrewOutcome.MurkyMess.class, BrewResolver.resolve(slots(), HourglassPreset.FIVE, BOOK));
  }

  @Test
  void oneIngredientTooManyMatchesNothing() {
    List<IngredientSignature> tooMany =
        slots(item("minecraft:spider_eye"), item("minecraft:dandelion"), item("minecraft:sugar"));

    assertInstanceOf(
        BrewOutcome.MurkyMess.class, BrewResolver.resolve(tooMany, HourglassPreset.FIVE, BOOK));
  }

  @Test
  void theMatcherInsistsOnExactlyFiveSlots() {
    List<IngredientSignature> three =
        List.of(
            item("minecraft:spider_eye"), item("minecraft:dandelion"), IngredientSignature.EMPTY);

    assertThrows(
        IllegalArgumentException.class,
        () -> RecipeMatcher.match(three, HourglassPreset.FIVE, BOOK));
  }

  @Test
  void anIngredientFromTheWrongNamespaceDoesNotMatch() {
    List<IngredientSignature> inventory =
        slots(item("someothermod:spider_eye"), item("minecraft:dandelion"));

    assertInstanceOf(
        BrewOutcome.MurkyMess.class, BrewResolver.resolve(inventory, HourglassPreset.FIVE, BOOK));
  }

  @Test
  void aTagSlotAcceptsAnyItemCarryingThatTag() {
    BrewRecipe tagRecipe =
        new BrewRecipe(
            "alchemizer:tagged",
            List.of(new SlotMatcher.ByTag("minecraft:planks")),
            HourglassPreset.FIVE,
            PotionDefinition.unlocked("alchemizer:tagged", List.of()),
            1);
    RecipeBook book = RecipeBook.of(List.of(tagRecipe));

    BrewOutcome outcome =
        BrewResolver.resolve(
            slots(item("minecraft:birch_planks", "minecraft:planks")), HourglassPreset.FIVE, book);

    assertInstanceOf(BrewOutcome.Success.class, outcome);
  }

  @Test
  void aRepeatedIngredientNeedsBothCopies() {
    BrewRecipe repeated =
        new BrewRecipe(
            "alchemizer:repeated",
            List.of(
                new SlotMatcher.ByItem("minecraft:sugar"),
                new SlotMatcher.ByItem("minecraft:redstone"),
                new SlotMatcher.ByItem("minecraft:sugar")),
            HourglassPreset.FIVE,
            PotionDefinition.unlocked("alchemizer:repeated", List.of()),
            1);
    RecipeBook book = RecipeBook.of(List.of(repeated));

    assertInstanceOf(
        BrewOutcome.Success.class,
        BrewResolver.resolve(
            slots(item("minecraft:sugar"), item("minecraft:redstone"), item("minecraft:sugar")),
            HourglassPreset.FIVE,
            book));

    assertInstanceOf(
        BrewOutcome.MurkyMess.class,
        BrewResolver.resolve(
            slots(item("minecraft:sugar"), item("minecraft:sugar"), item("minecraft:redstone")),
            HourglassPreset.FIVE,
            book));
  }
}
