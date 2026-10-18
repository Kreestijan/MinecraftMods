package io.github.kreestijan.alchemizer.brewing.menu;

import io.github.kreestijan.alchemizer.brewing.blockentity.AlchemicalTankBlockEntity;
import io.github.kreestijan.alchemizer.brewing.water.WaterContainers;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The tank screen's layout, and its view of the water.
 *
 * <p><b>A tank has no slots.</b> Where the other two machines trade items with the player, a tank
 * holds a level, so this menu adds nothing but the player's own inventory — the gauge on the screen
 * is the whole of the machine's half, and it is where water goes in and out.
 *
 * <p>Water goes in by clicking the gauge with a container on the cursor, which is a button and not
 * a slot: there is no item to exchange with a tank, so the click has no slot action to hook. The
 * server re-reads its own cursor when the button arrives, so a modified client can ask to pour and
 * be refused, but cannot tell the server it is holding water it is not holding. Water comes out by
 * breaking the tank, which keeps what it holds.
 *
 * <p>The client's menu is built without a tank. It has none to point at — the figures it draws
 * arrive over the container synchroniser like every other screen's here — so the fields it holds
 * are a plain store of the right length rather than a view of anything.
 */
public class AlchemicalTankMenu extends AbstractContainerMenu {

  /**
   * Button id for pouring a carried water container into the tank.
   *
   * <p>The only button this screen has, and it is numbered from zero because there is no other
   * control to number around.
   */
  public static final int BUTTON_FILL_TANK = 0;

  private static final int INV_SLOT_START = 0;

  private static final int INV_SLOT_END = INV_SLOT_START + 27;

  private static final int HOTBAR_SLOT_START = INV_SLOT_END;

  private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

  /**
   * The player inventory's origin.
   *
   * <p>Public because the screen lines the inventory's label up with the well this puts the first
   * slot in, which is one pixel left of it — see {@code PanelLabels#inventoryLabelX}.
   */
  public static final int INVENTORY_X = 8;

  private static final int INVENTORY_Y = 84;

  /** The tank this menu is a view of, or null on the client, which has none. */
  private final @Nullable AlchemicalTankBlockEntity tank;

  private final ContainerData data;

  /**
   * The client's constructor, which is the one a {@code MenuType} can call.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   */
  public AlchemicalTankMenu(int containerId, Inventory inventory) {
    this(containerId, inventory, null);
  }

  /**
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   * @param tank the tank being opened, or null on the client
   */
  public AlchemicalTankMenu(
      int containerId, Inventory inventory, @Nullable AlchemicalTankBlockEntity tank) {
    super(ModMenus.ALCHEMICAL_TANK.get(), containerId);
    this.tank = tank;
    this.data =
        tank == null
            ? new SimpleContainerData(AlchemicalTankData.COUNT)
            : new AlchemicalTankData(tank);

    addDataSlots(data);
    addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
  }

  /**
   * Whether the screen should still be open.
   *
   * <p>The client's copy answers yes to everything, because it has no tank to ask and the server
   * closes the window itself when this says no there — a client that closed its own screen would
   * only be guessing at a block it cannot see the state of.
   *
   * @param player the player whose screen this is
   * @return whether the tank is still there and still within reach
   */
  @Override
  public boolean stillValid(Player player) {
    return tank == null || tank.stillValid(player);
  }

  /**
   * Shifts stacks across the player's own inventory.
   *
   * <p>There is nothing else for a shift-click to aim at, the tank having no slots, so this is the
   * one move every container screen makes with the half of it that has room: between the main
   * inventory and the hotbar.
   *
   * @param player the player doing the move
   * @param slotIndex the slot the stack came from
   * @return the stack that could not be moved, or empty when it all moved
   */
  @Override
  public ItemStack quickMoveStack(Player player, int slotIndex) {
    Slot slot = slots.get(slotIndex);
    if (slot == null || !slot.hasItem()) {
      return ItemStack.EMPTY;
    }

    ItemStack inSlot = slot.getItem();
    ItemStack moved = inSlot.copy();

    boolean fromMain = slotIndex < HOTBAR_SLOT_START;
    int targetStart = fromMain ? HOTBAR_SLOT_START : INV_SLOT_START;
    int targetEnd = fromMain ? HOTBAR_SLOT_END : INV_SLOT_END;
    if (!moveItemStackTo(inSlot, targetStart, targetEnd, false)) {
      return ItemStack.EMPTY;
    }

    if (inSlot.isEmpty()) {
      slot.setByPlayer(ItemStack.EMPTY);
    } else {
      slot.setChanged();
    }
    if (inSlot.getCount() == moved.getCount()) {
      return ItemStack.EMPTY;
    }

    slot.onTake(player, inSlot);
    return moved;
  }

  /**
   * Runs a button press, which is the whole of what the client may ask for.
   *
   * <p>The press names a button and nothing else — no amount, no kind of container — and the water
   * is read from the server's own cursor, which the slot packets have already populated. A client
   * that lies therefore changes nothing.
   *
   * @param player the player who pressed the button
   * @param buttonId which button, from the constants at the top of this class
   * @return whether the press was understood
   */
  @Override
  public boolean clickMenuButton(Player player, int buttonId) {
    if (buttonId != BUTTON_FILL_TANK || tank == null) {
      // Unreachable in the second half: a button press is delivered to the server's menu, which is
      // the one built with a tank. Answering "not understood" is the honest response to a request
      // that arrived somewhere it cannot be acted on.
      return false;
    }
    return WaterContainers.pourCursorInto(this, player, tank::tryAddWater);
  }

  /**
   * @return millibuckets of water in the tank
   */
  public int waterStoredMb() {
    return data.get(AlchemicalTankData.STORED_MB);
  }

  /**
   * @return millibuckets that tank holds
   */
  public int waterCapacityMb() {
    return data.get(AlchemicalTankData.CAPACITY_MB);
  }
}
