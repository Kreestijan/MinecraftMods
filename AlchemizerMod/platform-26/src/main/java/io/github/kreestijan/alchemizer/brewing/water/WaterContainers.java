package io.github.kreestijan.alchemizer.brewing.water;

import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.tank.TankMath;
import java.util.function.IntPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.Tags;

/**
 * Reads how much water a player is carrying, and what is left once they have poured it out.
 *
 * <p>This exists as one place rather than as logic inside the tank block, because the same question
 * is asked from three directions: a right-click on a tank in the world, a click on the pressure
 * chamber's gauge, and a click on the tank's own gauge. All three want the same answer, and copies
 * of the answer would eventually disagree about a water bottle.
 *
 * <p><b>What counts as a water container.</b> The item tag {@code c:buckets/water}, which is the
 * conventional one and which NeoForge fills with {@code minecraft:water_bucket} — so any mod that
 * adds its own water bucket to that tag works with the tank without this mod knowing it exists. A
 * vanilla water bottle counts too, and is worth one dose. Nothing else does: a filled bucket of
 * another fluid is not water, and neither is a potion that happens to be made with water.
 *
 * <p><b>Why a bottle is worth {@code doseMb}.</b> "A bottle of water makes a bottle of potion" is
 * the mod's own unit, and one dose is that unit. The alternative — guessing at a fluid amount for a
 * bottle — has nothing to go on: this version of Minecraft gives a bucket a volume of one thousand
 * millibuckets and a bottle none at all, so any other number would be invented here rather than
 * read from the game.
 *
 * <p>Nothing here touches a fluid capability, deliberately. The tag is a list of items, it is
 * extensible by datapack, and it answers the question the machine actually asks — how much water is
 * in your hand — without a transaction context to get wrong.
 */
public final class WaterContainers {

  private WaterContainers() {}

  /**
   * @param stack the stack in a player's hand or on a menu cursor
   * @return the millibuckets of water it carries, or zero when it carries none
   */
  public static int waterMb(ItemStack stack) {
    if (stack.isEmpty()) {
      return 0;
    }
    if (stack.typeHolder().is(Tags.Items.BUCKETS_WATER)) {
      return TankMath.BUCKET_MB;
    }
    return isWaterBottle(stack) ? ConfigCache.current().doseMb() : 0;
  }

  /**
   * @param stack a stack {@link #waterMb} reported water for
   * @return what the stack leaves behind once its water has been poured out
   */
  public static ItemStack emptied(ItemStack stack) {
    return isWaterBottle(stack) ? new ItemStack(Items.GLASS_BOTTLE) : new ItemStack(Items.BUCKET);
  }

  /**
   * Pours what is on a menu's cursor into something that can hold water, spending the container.
   *
   * <p>The two gauge clicks both mean this and nothing else, so it is written once: read the
   * cursor, ask the destination to take all of it or refuse, and put the emptied container back on
   * the cursor. The cursor is set here rather than left to the next broadcast because the client
   * draws what it was last told, and a player would otherwise watch the bucket sit there until the
   * next synchronisation.
   *
   * <p>All or nothing is the destination's rule, not this one: a destination with no room refuses
   * the whole amount, which is what keeps a full tank from costing a player a bucket. Which stack
   * counts as a water container, and what it leaves behind, are {@link #waterMb} and {@link
   * #emptied}.
   *
   * @param menu the menu whose cursor is read and written
   * @param player the player whose cursor it is
   * @param sink takes millibuckets and answers whether they fitted, all or nothing
   * @return whether the water was poured
   */
  public static boolean pourCursorInto(
      AbstractContainerMenu menu, Player player, IntPredicate sink) {
    ItemStack carried = menu.getCarried();
    int waterMb = waterMb(carried);
    if (waterMb <= 0 || !sink.test(waterMb)) {
      return false;
    }
    menu.setCarried(ItemUtils.createFilledResult(carried, player, emptied(carried)));
    return true;
  }

  /**
   * A drinkable bottle of plain water, as opposed to a potion that has an effect or no potion at
   * all. {@link PotionContents#is} is what settles it: it is true only for the water potion with no
   * custom effects layered on top.
   *
   * @param stack the stack to judge
   * @return whether it is a vanilla water bottle
   */
  private static boolean isWaterBottle(ItemStack stack) {
    if (stack.getItem() != Items.POTION) {
      return false;
    }
    PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
    return contents != null && contents.is(Potions.WATER);
  }
}
