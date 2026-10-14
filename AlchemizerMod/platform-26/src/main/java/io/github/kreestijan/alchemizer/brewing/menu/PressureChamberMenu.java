package io.github.kreestijan.alchemizer.brewing.menu;

import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity.StartResult;
import io.github.kreestijan.alchemizer.brewing.water.WaterContainers;
import io.github.kreestijan.alchemizer.core.machine.StillProblem;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The brewing screen's slot layout, its buttons, and its view of the brew.
 *
 * <p>Every slot defers its "may this go in?" question to the chamber's own answer, for the reason
 * given in {@link IgnitionChamberMenu}: a vanilla slot does not consult the container, so a slot
 * that did not ask would quietly accept anything.
 *
 * <p>The enhancer slots are here rather than added later, and are inactive. They are the two slots
 * the design reserves for the enhancement chain, which is out of scope at tier 1; giving them their
 * indices now means a screen that grows them later does not renumber every slot the client already
 * knows about.
 *
 * <p><b>The client sends a button id and nothing else.</b> A start intent is re-derived on the
 * server from the machine's actual state, so a modified client can ask to brew and be refused, but
 * cannot tell the server that it has the ingredients it does not have.
 */
public class PressureChamberMenu extends AbstractContainerMenu {

  /** Button id for the {@code n}th hourglass, in {@link HourglassPreset} order. */
  public static final int BUTTON_PRESET_COUNT = HourglassPreset.values().length;

  /** Button id for the start button, one past the last hourglass. */
  public static final int BUTTON_START = BUTTON_PRESET_COUNT;

  /**
   * Button id for pouring a carried water container into the still's tanks, one past the start.
   *
   * <p>A button rather than a slot, because the gauge is not a slot and a click on it has no item
   * to exchange. The client sends the id and nothing else; the server reads its own cursor, so a
   * modified client can ask to fill and be refused, but cannot tell the server it is holding water
   * it is not holding.
   */
  public static final int BUTTON_FILL_TANK = BUTTON_START + 1;

  private static final int INGREDIENT_SLOT_START = PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST;

  /**
   * One past the machine's own slots, which is where the player inventory begins.
   *
   * <p>Read from the chamber rather than written as a number, because this is the boundary that
   * decides which direction a shift-click goes: everything below it is the machine and everything
   * at or above it is the player. A slot added to the chamber without this following it would send
   * shift-clicks the wrong way, quietly.
   */
  private static final int MACHINE_SLOT_END = PressureChamberBlockEntity.SLOT_COUNT;

  private static final int INV_SLOT_START = MACHINE_SLOT_END;

  private static final int INV_SLOT_END = INV_SLOT_START + 27;

  private static final int HOTBAR_SLOT_START = INV_SLOT_END;

  private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

  /**
   * Where the five ingredient wells start, and how far apart they are.
   *
   * <p>Forty-four in and eighteen apart puts the row at 44 to 132, which is centred in the 176-wide
   * panel to the pixel. The three slot rows are one spacing apart and share that centre, which is
   * what the screen draws its gauge alongside.
   */
  private static final int INGREDIENT_X = 44;

  private static final int SLOT_SPACING = 18;

  /** The middle of the panel, and so the left edge of the two slots that sit alone on their row. */
  private static final int SLOT_CENTRE_X = 80;

  /** The three slot rows, top to bottom, one spacing apart. */
  private static final int BOTTLE_Y = 41;

  private static final int INGREDIENT_Y = 59;

  /**
   * Where the output slot sits, on its own row below the ingredients.
   *
   * <p>Empty in the placeholder artwork, so the well a slot is drawn on is not there yet and the
   * slot will not be visible until it is hovered. That is the panel texture's business, not this
   * layout's.
   */
  private static final int OUTPUT_Y = 77;

  /**
   * Where the enhancer slots would sit if they were ever shown.
   *
   * <p>Off the panel rather than at the origin: an inactive slot is neither drawn nor hoverable, so
   * this coordinate is never used, and putting it far away means a bug that made one of them active
   * would show as an empty screen rather than as a stray slot on top of the artwork.
   */
  private static final int HIDDEN_X = -1000;

  private static final int HIDDEN_Y = -1000;

  /**
   * The player inventory's origin.
   *
   * <p>Well below vanilla's 84. The panel here is taller than the standard one, because the machine
   * needs three slot rows where a furnace needs one, and the inventory sits under the machine's
   * half rather than beside it. The screen sets its own height to match; the two numbers are a
   * pair.
   */
  private static final int INVENTORY_X = 8;

  private static final int INVENTORY_Y = 126;

  private final Container chamber;

  private final ContainerData data;

  /**
   * The client's constructor, which is the one a {@code MenuType} can call.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   */
  public PressureChamberMenu(int containerId, Inventory inventory) {
    this(
        containerId,
        inventory,
        new SimpleContainer(PressureChamberBlockEntity.SLOT_COUNT),
        new SimpleContainerData(PressureChamberData.COUNT));
  }

  /**
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   * @param chamber the chamber being opened
   * @param data the brew figures, read live from the chamber
   */
  public PressureChamberMenu(
      int containerId, Inventory inventory, Container chamber, ContainerData data) {
    super(ModMenus.PRESSURE_CHAMBER.get(), containerId);
    checkContainerSize(chamber, PressureChamberBlockEntity.SLOT_COUNT);
    checkContainerDataCount(data, PressureChamberData.COUNT);
    this.chamber = chamber;
    this.data = data;

    for (int i = 0; i < PressureChamberBlockEntity.SLOT_INGREDIENT_COUNT; i++) {
      addSlot(
          new MachineSlot(
              chamber, INGREDIENT_SLOT_START + i, INGREDIENT_X + i * SLOT_SPACING, INGREDIENT_Y));
    }
    addSlot(
        new MachineSlot(chamber, PressureChamberBlockEntity.SLOT_BOTTLE, SLOT_CENTRE_X, BOTTLE_Y));
    for (int i = 0; i < PressureChamberBlockEntity.SLOT_ENHANCER_COUNT; i++) {
      addSlot(
          new HiddenSlot(
              chamber, PressureChamberBlockEntity.SLOT_ENHANCER_FIRST + i, HIDDEN_X, HIDDEN_Y));
    }
    addSlot(
        new MachineSlot(chamber, PressureChamberBlockEntity.SLOT_OUTPUT, SLOT_CENTRE_X, OUTPUT_Y));

    addDataSlots(data);
    addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
  }

  @Override
  public boolean stillValid(Player player) {
    return chamber.stillValid(player);
  }

  /**
   * Shifts stacks between the machine and the player's inventory.
   *
   * <p>Which machine slot a shift-clicked stack aims at is decided by what the stack is, not by the
   * slot order. An ingredient slot accepts anything, so a bottle offered to the ingredient range
   * first would be taken by the first ingredient slot and never reach the bottle slot; a player
   * shift-clicking a stack of bottles means the bottle slot, and that is what this does.
   *
   * <p>Out of the machine is decided by index alone: everything below {@link #MACHINE_SLOT_END} is
   * the machine, output slot included, and everything at or above it is the player. Nothing aims
   * <em>at</em> the output slot in either direction, which is what the chamber's own refusal does
   * for {@link #moveItemStackTo}.
   *
   * <p>Both directions run through {@link #moveItemStackTo}, which asks the slot, so the bottle
   * rule and the enhancer refusal hold here without being restated. The enhancer slots are never a
   * target in either direction.
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

    if (slotIndex < MACHINE_SLOT_END) {
      if (!moveItemStackTo(inSlot, INV_SLOT_START, HOTBAR_SLOT_END, true)) {
        return ItemStack.EMPTY;
      }
    } else {
      boolean placed =
          isBottle(inSlot)
              ? moveItemStackTo(
                  inSlot,
                  PressureChamberBlockEntity.SLOT_BOTTLE,
                  PressureChamberBlockEntity.SLOT_BOTTLE + 1,
                  false)
              : moveItemStackTo(
                  inSlot, INGREDIENT_SLOT_START, PressureChamberBlockEntity.SLOT_BOTTLE, false);
      if (!placed) {
        return ItemStack.EMPTY;
      }
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
   * <p>Every branch re-reads the chamber rather than trusting the request. The hourglass branch
   * only records a choice; the start branch asks the chamber to start at the choice the server
   * itself remembers, not at one the client named; the fill branch reads the server's own cursor,
   * which the slot packets have already populated. A client that lies therefore changes nothing.
   *
   * @param player the player who pressed the button
   * @param buttonId which button, from the constants at the top of this class
   * @return whether the press was understood
   */
  @Override
  public boolean clickMenuButton(Player player, int buttonId) {
    if (!(chamber instanceof PressureChamberBlockEntity entity)) {
      // Unreachable: the client's menu holds a throwaway container, and a button press is delivered
      // to the server's menu, which holds the chamber. Answering "not understood" is the honest
      // response to a request that arrived somewhere it cannot be acted on.
      return false;
    }

    if (buttonId == BUTTON_FILL_TANK) {
      return fillFromCursor(player, entity);
    }

    if (buttonId == BUTTON_START) {
      return entity.startBrew(entity.selectedPreset()) == StartResult.STARTED;
    }

    Optional<HourglassPreset> preset = HourglassPreset.ofOrdinal(buttonId);
    preset.ifPresent(entity::selectPreset);
    return preset.isPresent();
  }

  /**
   * Pours the cursor's water into the still's tanks.
   *
   * <p>The chamber spreads it over the tanks it is drawing on and refuses the whole amount if they
   * cannot all take their share; what pouring costs and leaves behind is {@link
   * WaterContainers#pourCursorInto}, which the tank's own screen asks for as well.
   *
   * @param player the player whose cursor is being read
   * @param entity the chamber being filled
   * @return whether the fill happened
   */
  private boolean fillFromCursor(Player player, PressureChamberBlockEntity entity) {
    return WaterContainers.pourCursorInto(this, player, entity::tryAddWater);
  }

  /**
   * @return whether the firebox below is burning
   */
  public boolean isLit() {
    return data.get(PressureChamberData.LIT) != 0;
  }

  /**
   * @return whether the blocks around the chamber currently form a still
   */
  public boolean isFormed() {
    return data.get(PressureChamberData.FORMED) != 0;
  }

  /**
   * @return how far the running brew has got, from zero to one; zero when nothing is running
   */
  public float brewProgress() {
    int total = data.get(PressureChamberData.BREW_TOTAL);
    if (total <= 0) {
      return 0.0F;
    }
    int elapsed = Math.max(0, data.get(PressureChamberData.BREW_ELAPSED));
    return Math.min(1.0F, elapsed / (float) total);
  }

  /**
   * @return millibuckets of water in the tanks feeding this still
   */
  public int waterStoredMb() {
    return data.get(PressureChamberData.WATER_STORED);
  }

  /**
   * @return millibuckets those tanks hold between them
   */
  public int waterCapacityMb() {
    return data.get(PressureChamberData.WATER_CAPACITY);
  }

  /**
   * @return the hourglass setting the server will brew at, or empty if the client has not been told
   */
  public Optional<HourglassPreset> selectedPreset() {
    return HourglassPreset.ofOrdinal(data.get(PressureChamberData.PRESET));
  }

  /**
   * @return why a brew would be refused right now, or empty if the client has not been told
   */
  public Optional<StartResult> readiness() {
    return StartResult.ofOrdinal(data.get(PressureChamberData.READINESS));
  }

  /**
   * @return the tier of the still around the chamber; zero when the parts are not a still, which is
   *     also what an unsynced client reads, and the status line only consults it once readiness has
   *     arrived
   */
  public int stillTier() {
    return data.get(PressureChamberData.TIER);
  }

  /**
   * @return the first thing wrong with the machine, or empty when nothing is or when the client has
   *     not been told; the two cases are told apart by the readiness the status line is drawn with
   */
  public Optional<StillProblem> stillProblem() {
    return StillProblem.ofOrdinal(data.get(PressureChamberData.PROBLEM));
  }

  /**
   * A slot whose rule is the chamber's.
   *
   * <p>Emptying is always allowed: a player has to be able to take back an item they put in the
   * wrong place, and a slot that refused would trap it.
   */
  private static class MachineSlot extends Slot {

    /**
     * @param chamber the chamber whose slot this is
     * @param slot the slot's index within the chamber
     * @param x where it sits in the panel
     * @param y where it sits in the panel
     */
    private MachineSlot(Container chamber, int slot, int x, int y) {
      super(chamber, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      return stack.isEmpty() || container.canPlaceItem(getSlotIndex(), stack);
    }
  }

  /**
   * A slot that is present but not shown, for the enhancer positions the design reserves.
   *
   * <p>Inactive is the whole mechanism: an inactive slot is skipped when slots are drawn and when
   * the hovered slot is worked out, which is what "reserved but not rendered" means here. It would
   * also refuse anything placed in it, since it inherits the chamber's own answer for an enhancer
   * slot, which is no.
   */
  private static final class HiddenSlot extends MachineSlot {

    /**
     * @param chamber the chamber whose slot this is
     * @param slot the slot's index within the chamber
     * @param x where it would sit, if it were ever shown
     * @param y where it would sit, if it were ever shown
     */
    private HiddenSlot(Container chamber, int slot, int x, int y) {
      super(chamber, slot, x, y);
    }

    @Override
    public boolean isActive() {
      return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      return stack.isEmpty();
    }
  }

  /**
   * @return whether a stack is a glass bottle, which is what the bottle slot takes
   */
  private static boolean isBottle(ItemStack stack) {
    return stack.getItem() == Items.GLASS_BOTTLE;
  }
}
