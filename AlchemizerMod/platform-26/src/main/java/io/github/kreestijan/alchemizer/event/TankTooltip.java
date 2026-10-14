package io.github.kreestijan.alchemizer.event;

import io.github.kreestijan.alchemizer.brewing.blockentity.AlchemicalTankBlockEntity;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.tank.TankLevel;
import io.github.kreestijan.alchemizer.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Says how much water a tank item is carrying, on hover.
 *
 * <p>A broken tank keeps its water on the item as block entity save data. The game has a component
 * for exactly that — {@code DataComponents.BLOCK_ENTITY_DATA} — but its own tooltip speaks only for
 * the uses the component was written for, a spawn egg's entity and a spawner's, so a tank draws
 * nothing and a player holding a full one sees a tank.
 *
 * <p><b>Why the tooltip is added from an event rather than from an item class.</b> The obvious
 * place is an {@code appendHoverText} override on a {@code BlockItem} subclass, and that is what
 * this was first written as. The method is deprecated in this version of Minecraft, and overriding
 * a deprecated method is itself a warning, which this build refuses. A {@code @SuppressWarnings}
 * would have hidden that rather than answered it, and nothing else in this mod suppresses a
 * warning: the one other deprecated call met earlier was replaced with the method the game points
 * at instead. So the tank's item is a plain block item, and the line is added here, through the
 * event the game fires while it is gathering a stack's tooltip — which is not deprecated and is
 * where a mod adds a line to an item it does not otherwise need a class for.
 *
 * <p>Nothing is hidden or held back: a tank's contents are not a secret, and a player who cannot
 * see them has to place the tank to find out what they are carrying.
 */
public final class TankTooltip {

  private TankTooltip() {}

  /**
   * Adds the tank's level to its tooltip, and nothing else.
   *
   * <p>An empty tank says nothing at all rather than saying "0/8000": an empty tank is what the
   * item is by default, so a line reporting it would be a line on every tank a player has ever
   * crafted and one more thing to read past. A tank with water in it is the case worth telling
   * apart.
   *
   * <p>The capacity is the client's config value, which can differ from the server's. The level is
   * clamped through {@link TankLevel} rather than shown raw, exactly as {@link
   * AlchemicalTankBlockEntity#level()} clamps it, so a client whose config is smaller than the
   * server's shows a full tank instead of an impossible "5000/4000". The figure is advisory either
   * way: it is the client's own reading, and the tank's stored level is the server's.
   *
   * <p>This runs for every tooltip in the game, so the first thing it does is reject everything
   * that is not a tank. It also runs with no player at all, while the client is building its search
   * trees at startup — which is why nothing here reads the player or the world.
   *
   * @param event the tooltip being gathered, which this adds one line to
   */
  public static void onItemTooltip(ItemTooltipEvent event) {
    ItemStack stack = event.getItemStack();
    if (!stack.is(ModItems.ALCHEMICAL_TANK.get())) {
      return;
    }
    int stored = AlchemicalTankBlockEntity.storedMbOf(stack);
    if (stored <= 0) {
      return;
    }
    int capacity = ConfigCache.current().tankCapacityMb();
    TankLevel level = new TankLevel(Math.min(stored, capacity), capacity);
    event
        .getToolTip()
        .add(
            Component.translatable(
                "gui.alchemizer.water.mb", level.storedMb(), level.capacityMb()));
  }
}
