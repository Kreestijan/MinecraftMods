package io.github.kreestijan.alchemizer.core.recipe;

import java.util.Objects;
import java.util.Set;

/**
 * One occupied ingredient slot, described in plain text so that this module needs no Minecraft
 * types.
 *
 * <p>The platform adapter builds these from real item stacks. Carrying the item's tag ids rather
 * than a reference to the effect leaves {@link RecipeMatcher} a pure function that a unit test can
 * drive without a running game.
 *
 * <p>{@link #EMPTY} stands for an unoccupied slot. It exists because an empty slot is not the same
 * as a missing one: the still has five slots always, and a gap in the middle of them is a mistake
 * the matcher has to be able to see.
 *
 * @param itemId the item's namespaced id, empty only for {@link #EMPTY}
 * @param tagIds every item tag the stack carries, or an empty set for {@link #EMPTY}
 * @param count how many items are in the slot; zero means the slot is vacant
 */
public record IngredientSignature(String itemId, Set<String> tagIds, int count) {

  /** The vacant slot. */
  public static final IngredientSignature EMPTY = new IngredientSignature("", Set.of(), 0);

  public IngredientSignature {
    Objects.requireNonNull(itemId, "itemId");
    Objects.requireNonNull(tagIds, "tagIds");
    tagIds = Set.copyOf(tagIds);
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative, got " + count);
    }
    if (count == 0 && (!itemId.isEmpty() || !tagIds.isEmpty())) {
      throw new IllegalArgumentException("a vacant slot must carry no item and no tags");
    }
    if (count > 0 && itemId.isEmpty()) {
      throw new IllegalArgumentException("an occupied slot must name its item");
    }
  }

  /**
   * @param itemId the item's namespaced id
   * @param tagIds every item tag the stack carries
   * @return a signature for a single item in a slot
   */
  public static IngredientSignature of(String itemId, Set<String> tagIds) {
    return new IngredientSignature(itemId, tagIds, 1);
  }

  /**
   * @return whether the slot is vacant
   */
  public boolean isEmpty() {
    return count == 0;
  }

  /**
   * @param matcher the pattern to test against
   * @return whether this ingredient satisfies that pattern
   */
  public boolean matches(SlotMatcher matcher) {
    return !isEmpty() && matcher.accepts(this);
  }
}
