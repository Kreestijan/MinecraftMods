package io.github.kreestijan.alchemizer.brewing.menu;

import io.github.kreestijan.alchemizer.core.timing.BrewTime;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The fuel screen's slot layout, and its view of the burn.
 *
 * <p>The fuel slot is not a plain {@link Slot}. A vanilla slot accepts whatever is dropped into it
 * unless the slot itself says otherwise — {@code Container.canPlaceItem} is consulted by hopper
 * insertion and by nothing else in this version — so the check that only ladder fuel may go in is
 * made by the slot subclass below. Without it, the design's rule would be enforced on every path
 * except the one a player actually uses.
 *
 * <p>The client's menu is built against a throwaway container. Slot contents and the burn figures
 * are both written over it by the synchroniser, so the client never needs to know which chamber it
 * is looking at.
 */
public class IgnitionChamberMenu extends AbstractContainerMenu {

  /** The chamber has one slot, so the player inventory starts at index one. */
  private static final int MACHINE_SLOT_COUNT = 1;

  private static final int INV_SLOT_START = MACHINE_SLOT_COUNT;

  private static final int INV_SLOT_END = INV_SLOT_START + 27;

  private static final int HOTBAR_SLOT_START = INV_SLOT_END;

  private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

  /**
   * Where the slot sits in the panel; the panel art draws its recess one pixel up and left.
   *
   * <p>Public because the screen sets the countdown down beside this slot, and "beside" is a
   * statement about where the slot is rather than a second number that happens to land in the right
   * place.
   */
  public static final int SLOT_X = 80;

  private static final int SLOT_Y = 20;

  /**
   * The player inventory's origin, in the same coordinates as the slot above.
   *
   * <p>Public because the screen lines the inventory's label up with the well this puts the first
   * slot in, which is one pixel left of it — see {@code PanelLabels#inventoryLabelX}.
   */
  public static final int INVENTORY_X = 8;

  private static final int INVENTORY_Y = 84;

  private final Container chamber;

  private final ContainerData data;

  /**
   * The client's constructor, which is the one a {@code MenuType} can call.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   */
  public IgnitionChamberMenu(int containerId, Inventory inventory) {
    this(
        containerId,
        inventory,
        new SimpleContainer(MACHINE_SLOT_COUNT),
        new SimpleContainerData(IgnitionChamberData.COUNT));
  }

  /**
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   * @param chamber the chamber being opened
   * @param data the burn figures, read live from the chamber
   */
  public IgnitionChamberMenu(
      int containerId, Inventory inventory, Container chamber, ContainerData data) {
    super(ModMenus.IGNITION_CHAMBER.get(), containerId);
    checkContainerSize(chamber, MACHINE_SLOT_COUNT);
    checkContainerDataCount(data, IgnitionChamberData.COUNT);
    this.chamber = chamber;
    this.data = data;

    addSlot(new FuelSlot(chamber, 0, SLOT_X, SLOT_Y));
    addDataSlots(data);
    addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
  }

  @Override
  public boolean stillValid(Player player) {
    return chamber.stillValid(player);
  }

  /**
   * Shifts stacks between the fuel slot and the player's inventory.
   *
   * <p>Shift-clicking out of the fuel slot always goes to the player; shift-clicking in goes to the
   * fuel slot only if the slot would take it, and to the player otherwise. Both directions run
   * through {@link #moveItemStackTo}, which asks the slot, so the fuel rule is enforced here
   * without being restated.
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

    if (slotIndex < MACHINE_SLOT_COUNT) {
      if (!moveItemStackTo(inSlot, INV_SLOT_START, HOTBAR_SLOT_END, true)) {
        return ItemStack.EMPTY;
      }
    } else if (!moveItemStackTo(inSlot, 0, MACHINE_SLOT_COUNT, false)) {
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
   * @return whether anything is alight, which the screen draws a flame for
   */
  public boolean isLit() {
    return data.get(IgnitionChamberData.LIT) != 0;
  }

  /**
   * @return how much of the current fuel is left, from zero to one; zero when the chamber is cold
   */
  public float burnProgress() {
    int total = data.get(IgnitionChamberData.BURN_TOTAL);
    if (total <= 0) {
      return 0.0F;
    }
    return Math.min(1.0F, burnRemainingTicks() / (float) total);
  }

  /**
   * How long the fuel alight has left, in seconds.
   *
   * <p>Rounded up, so that the countdown reaches zero when the fire goes out rather than up to a
   * second before it. A readout reading nothing left while the chamber was still visibly lit would
   * be telling the player something untrue about the machine in front of them.
   *
   * <p>This is the number the screen's bar is a picture of, in the unit a player counts in, and
   * both are read from the same two data slots so the two cannot disagree.
   *
   * @return seconds left on the fuel burning, or zero when the chamber is cold
   */
  public int burnSecondsRemaining() {
    int ticks = burnRemainingTicks();
    return (ticks + BrewTime.TICKS_PER_SECOND - 1) / BrewTime.TICKS_PER_SECOND;
  }

  /**
   * @return the burn left, clamped at zero, which is what both readings above are built from
   */
  private int burnRemainingTicks() {
    return Math.max(0, data.get(IgnitionChamberData.BURN_REMAINING));
  }

  /**
   * The slot that holds fuel, and only fuel.
   *
   * <p>The rule it defers to is the chamber's own, so the two cannot disagree about what may be lit
   * and what may be placed. An empty stack is always allowed, because that is how a menu empties a
   * slot rather than how a player fills one.
   */
  private static final class FuelSlot extends Slot {

    /**
     * @param chamber the chamber whose slot this is
     * @param slot the slot's index within the chamber
     * @param x where it sits in the panel
     * @param y where it sits in the panel
     */
    private FuelSlot(Container chamber, int slot, int x, int y) {
      super(chamber, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      return stack.isEmpty() || container.canPlaceItem(getSlotIndex(), stack);
    }
  }
}
