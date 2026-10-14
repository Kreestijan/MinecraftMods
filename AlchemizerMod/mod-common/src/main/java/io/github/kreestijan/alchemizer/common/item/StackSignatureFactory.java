package io.github.kreestijan.alchemizer.common.item;

import io.github.kreestijan.alchemizer.core.recipe.IngredientSignature;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Translates real item stacks into the plain-text signatures {@code core} matches recipes with.
 *
 * <p>This is one half of the seam described in the module's package documentation: it is the only
 * place that knows both what an {@link ItemStack} is and what an {@link IngredientSignature} is.
 * Everything downstream of it is pure Java.
 *
 * <p>The translation is deliberately eager. Materialising the tag ids costs an allocation, but
 * matching runs on a button press and on menu open rather than every tick, so the cost never lands
 * on the hot path, and in exchange the matcher stays a function a unit test can drive without a
 * running game.
 *
 * <p>Item tags are read through {@link ItemStack#typeHolder()}. The obvious-looking {@code
 * ItemStack#is(TagKey)} does not exist in this version of Minecraft, and the tag set cannot be
 * recovered from the item once the stack is gone, which is why it is copied out here rather than
 * looked up later.
 */
public final class StackSignatureFactory {

  private StackSignatureFactory() {}

  /**
   * @param stack the stack occupying a still slot; may be empty
   * @return the slot's signature, or {@link IngredientSignature#EMPTY} if the slot is vacant
   * @throws IllegalStateException if the stack's item is somehow not in the item registry
   */
  public static IngredientSignature of(ItemStack stack) {
    if (stack.isEmpty()) {
      return IngredientSignature.EMPTY;
    }
    Item item = stack.getItem();
    Identifier id = BuiltInRegistries.ITEM.getKey(item);
    if (id == null) {
      // Not reachable through normal play: every obtainable item is registered. It is checked
      // rather than ignored because a null id would travel onwards as a mysterious mismatch
      // instead of as a clear failure.
      throw new IllegalStateException("item is not in the item registry: " + item);
    }
    return new IngredientSignature(id.toString(), tagIdsOf(stack), stack.getCount());
  }

  /**
   * @param stacks the still's five ingredient slots, in slot order
   * @return one signature per slot, in the same order
   */
  public static List<IngredientSignature> ofFive(List<ItemStack> stacks) {
    return stacks.stream().map(StackSignatureFactory::of).toList();
  }

  /**
   * @param stack a non-empty stack
   * @return every item tag the stack's item carries, as namespaced ids
   */
  private static java.util.Set<String> tagIdsOf(ItemStack stack) {
    return stack
        .typeHolder()
        .tags()
        .map(TagKey::location)
        .map(Identifier::toString)
        .collect(Collectors.toUnmodifiableSet());
  }
}
