package io.github.kreestijan.alchemizer.core.recipe;

import java.util.Objects;

/**
 * What a recipe expects to find in one ingredient slot.
 *
 * <p>Matching by tag is preferred over matching by item: a tag lets a datapack — or another mod —
 * add an ingredient to a recipe without anyone editing code, which is the rule the whole recipe
 * system follows.
 */
public sealed interface SlotMatcher {

  /**
   * @param signature the ingredient currently in the slot
   * @return whether this slot accepts that ingredient
   */
  boolean accepts(IngredientSignature signature);

  /**
   * Accepts any item carrying a given tag.
   *
   * @param tagId the tag's namespaced id, for example {@code minecraft:planks}
   */
  record ByTag(String tagId) implements SlotMatcher {
    public ByTag {
      Objects.requireNonNull(tagId, "tagId");
      if (tagId.isBlank()) {
        throw new IllegalArgumentException("tagId must not be blank");
      }
    }

    @Override
    public boolean accepts(IngredientSignature signature) {
      return signature.tagIds().contains(tagId);
    }
  }

  /**
   * Accepts exactly one item.
   *
   * @param itemId the item's namespaced id, for example {@code minecraft:spider_eye}
   */
  record ByItem(String itemId) implements SlotMatcher {
    public ByItem {
      Objects.requireNonNull(itemId, "itemId");
      if (itemId.isBlank()) {
        throw new IllegalArgumentException("itemId must not be blank");
      }
    }

    @Override
    public boolean accepts(IngredientSignature signature) {
      return signature.itemId().equals(itemId);
    }
  }
}
