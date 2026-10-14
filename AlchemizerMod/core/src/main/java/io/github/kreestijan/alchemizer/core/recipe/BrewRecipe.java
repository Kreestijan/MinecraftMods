package io.github.kreestijan.alchemizer.core.recipe;

import io.github.kreestijan.alchemizer.core.potion.PotionDefinition;
import java.util.List;
import java.util.Objects;

/**
 * One way to brew: an ordered list of ingredient slots, a time setting, and the potion that
 * results.
 *
 * <p>A recipe lists only the slots it uses, in order. Slot order is significant — moving an
 * ingredient changes which recipe matches, which is what makes the still a puzzle rather than a
 * vending machine.
 *
 * @param id the recipe's namespaced id, for diagnostics and for the recipe book
 * @param slots what each used slot must contain, from the first slot onwards
 * @param time the hourglass setting this recipe requires
 * @param potion the potion produced
 * @param tier the still tier and fuel tier this recipe demands
 */
public record BrewRecipe(
    String id, List<SlotMatcher> slots, HourglassPreset time, PotionDefinition potion, int tier) {

  /** How many ingredient slots the pressure chamber has. */
  public static final int MAX_SLOTS = 5;

  public BrewRecipe {
    Objects.requireNonNull(id, "id");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    Objects.requireNonNull(slots, "slots");
    slots = List.copyOf(slots);
    if (slots.isEmpty()) {
      throw new IllegalArgumentException("a recipe must use at least one slot");
    }
    if (slots.size() > MAX_SLOTS) {
      throw new IllegalArgumentException(
          "a recipe may use at most " + MAX_SLOTS + " slots, got " + slots.size());
    }
    Objects.requireNonNull(time, "time");
    Objects.requireNonNull(potion, "potion");
    if (tier < 1) {
      throw new IllegalArgumentException("tier must be at least 1, got " + tier);
    }
  }

  /**
   * @param slots the ingredients in the still, in order
   * @return whether this recipe is satisfied by exactly those ingredients
   */
  public boolean matches(List<IngredientSignature> slots) {
    if (slots.size() != this.slots.size()) {
      return false;
    }
    for (int i = 0; i < slots.size(); i++) {
      if (!slots.get(i).matches(this.slots.get(i))) {
        return false;
      }
    }
    return true;
  }
}
