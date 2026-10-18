package io.github.kreestijan.alchemizer.brewing.menu;

import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity.StartResult;
import io.github.kreestijan.alchemizer.brewing.water.WaterContainers;
import io.github.kreestijan.alchemizer.core.machine.StillProblem;
import io.github.kreestijan.alchemizer.core.recipe.BrewRecipe;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.defs.ModDefinitions;
import io.github.kreestijan.alchemizer.defs.RecipeBookView;
import io.github.kreestijan.alchemizer.registry.ModAttachments;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import java.util.List;
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
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * The brewing screen's slot layout, its buttons, and its view of the brew.
 *
 * <p>Every slot defers its "may this go in?" question to {@link
 * PressureChamberBlockEntity#canPlaceItem}: a vanilla slot does not consult the container, so a
 * slot that did not ask would quietly accept anything.
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

  /**
   * Button ids for the batch control: one notch down, one notch up, and the switch.
   *
   * <p>Two ids rather than one carrying a direction, because the client is not allowed to say
   * anything but which button was pressed. The wheel is what moves the dial, and the server has no
   * wheel — it is told "up" or "down" and moves its own number by one, so a client that asks to go
   * up ten times gets ten notches and never a number of its own choosing.
   */
  public static final int BUTTON_BATCH_DOWN = BUTTON_FILL_TANK + 1;

  public static final int BUTTON_BATCH_UP = BUTTON_BATCH_DOWN + 1;

  public static final int BUTTON_BATCH_TOGGLE = BUTTON_BATCH_UP + 1;

  /**
   * The first of the recipe book's button ids: two per recipe, in the book's own order.
   *
   * <p>The index of a recipe is carried as {@code (buttonId - BUTTON_BOOK_BASE) / 2} and whether
   * the press was the star as the low bit, so the whole of a book intent is one integer — and one
   * the server re-derives a recipe from rather than trusting. A recipe id on the wire would be the
   * client naming what is brewed; an index into the list the server itself sent cannot be.
   */
  public static final int BUTTON_BOOK_BASE = BUTTON_BATCH_TOGGLE + 1;

  /**
   * The tier at which a funnel can sit on the chamber, and so the tier at which the book exists.
   *
   * <p>The same number the validator's ladder uses for its funnel rung. It is written here rather
   * than asked of the validator because this is not the rule — the rule is that a funnel is what
   * makes a tier-3 still — but the point at which a button appears, and a screen that offered the
   * book on a still without a funnel would be offering it on a machine the design does not give
   * one.
   */
  public static final int FUNNEL_TIER = 3;

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
   * Where the five ingredient wells start, and how the row is shaped.
   *
   * <p>A shallow zigzag rather than a straight row: {@code INGREDIENT_PITCH} across, and {@code
   * INGREDIENT_STAGGER} down for every other well. The pitch is deliberately wider than a slot —
   * three pixels of panel show between neighbours — so the stagger reads as a pattern rather than
   * as a texture that was drawn a little off.
   */
  private static final int INGREDIENT_X = 55;

  private static final int INGREDIENT_Y = 49;

  private static final int INGREDIENT_PITCH = 21;

  private static final int INGREDIENT_STAGGER = 8;

  /** The bottle slot, at the head of the right-hand column above the tank gauge. */
  private static final int BOTTLE_X = 186;

  private static final int BOTTLE_Y = 41;

  /**
   * Where the output slot sits, on its own row below the ingredients.
   *
   * <p>Centred on the panel, which is where a brew's one result belongs. The bottle above it is not
   * centred, and that is the point of the two: the bottle is an input and belongs with the tank it
   * feeds, and the result is not an input at all.
   *
   * <p>Its well is drawn in the placeholder artwork. That is the panel texture's business, not this
   * layout's, but the two are a pair: moving this without moving the well in the texture leaves the
   * slot drawn on bare panel.
   */
  private static final int OUTPUT_X = 97;

  private static final int OUTPUT_Y = 100;

  /**
   * The two enhancer slots, down the left-hand side.
   *
   * <p>Shown rather than reserved-and-hidden, which is what they used to be. Nothing supplies an
   * enhancer yet, so the chamber still refuses to accept anything into them — see {@code
   * PressureChamberBlockEntity#canPlaceItem} — and what a player meets is a pair of slots that will
   * not take anything, rather than a pair that silently swallowed it. The layout and the texture
   * are built for them now so that the day an enhancer exists there is nothing left to move.
   */
  private static final int ENHANCER_X = 7;

  private static final int ENHANCER_Y = 41;

  private static final int ENHANCER_PITCH = 20;

  /**
   * The fuel slot, at the foot of the left-hand column below the enhancers.
   *
   * <p>Public because the screen draws the flame inside this well, which is one pixel in from the
   * slot on every side. Its recess is drawn in the panel artwork at one pixel up-left of the slot,
   * as every well's is.
   */
  public static final int FUEL_X = 7;

  public static final int FUEL_Y = 109;

  /**
   * The player inventory's origin.
   *
   * <p>Well below vanilla's 84. The panel here is taller than the standard one, because the machine
   * needs three slot rows where a furnace needs one, and the inventory sits under the machine's
   * half rather than beside it. The screen sets its own height to match; the two numbers are a
   * pair.
   *
   * <p><b>Not vanilla's eight.</b> The inventory is nine wells, so its well block is 162 wide, and
   * the panel here is 210: at eight it would sit against the left edge with a wide band of bare
   * panel beside it. Vanilla centres the block by putting its first well at {@code (panelWidth -
   * 162) / 2} — seven in a 176-wide panel, which is why the slots start at eight — so here the
   * wells start at twenty-four and the slots at twenty-five.
   *
   * <p>The vertical is {@code panelHeight - 82}, the gap vanilla leaves between its own label line
   * and its first row: {@code 240 - 82 = 158}, with the label on 146.
   *
   * <p>Public because the screen lines the inventory's label up with the well this puts the first
   * slot in, which is one pixel left of it — see {@code PanelLabels#inventoryLabelX}.
   */
  public static final int INVENTORY_X = 25;

  private static final int INVENTORY_Y = 158;

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
              chamber,
              INGREDIENT_SLOT_START + i,
              INGREDIENT_X + i * INGREDIENT_PITCH,
              INGREDIENT_Y + (i % 2) * INGREDIENT_STAGGER));
    }
    addSlot(new MachineSlot(chamber, PressureChamberBlockEntity.SLOT_BOTTLE, BOTTLE_X, BOTTLE_Y));
    for (int i = 0; i < PressureChamberBlockEntity.SLOT_ENHANCER_COUNT; i++) {
      addSlot(
          new MachineSlot(
              chamber,
              PressureChamberBlockEntity.SLOT_ENHANCER_FIRST + i,
              ENHANCER_X,
              ENHANCER_Y + i * ENHANCER_PITCH));
    }
    addSlot(new MachineSlot(chamber, PressureChamberBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
    addSlot(new MachineSlot(chamber, PressureChamberBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y));

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
      boolean placed;
      if (chamber.canPlaceItem(PressureChamberBlockEntity.SLOT_FUEL, inSlot)) {
        // A fuel aims at the fuel slot, and a fuel slot that is full is a refusal, not a reason to
        // fall through: a coal must never land in an ingredient well.
        placed =
            moveItemStackTo(
                inSlot,
                PressureChamberBlockEntity.SLOT_FUEL,
                PressureChamberBlockEntity.SLOT_FUEL + 1,
                false);
      } else if (isBottle(inSlot)) {
        placed =
            moveItemStackTo(
                inSlot,
                PressureChamberBlockEntity.SLOT_BOTTLE,
                PressureChamberBlockEntity.SLOT_BOTTLE + 1,
                false);
      } else {
        placed =
            moveItemStackTo(
                inSlot, INGREDIENT_SLOT_START, PressureChamberBlockEntity.SLOT_BOTTLE, false);
      }
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

    if (buttonId == BUTTON_BATCH_TOGGLE) {
      return entity.toggleBatch();
    }

    if (buttonId == BUTTON_BATCH_UP || buttonId == BUTTON_BATCH_DOWN) {
      // Stepped from the dial rather than from what is on show, so that a still whose ceiling has
      // dropped to one still has its dial wound down a notch at a time and does not need the
      // condensers put back to be moved at all.
      int step = buttonId == BUTTON_BATCH_UP ? 1 : -1;
      return entity.setBatchMultiplier(entity.batchMultiplier() + step);
    }

    if (buttonId >= BUTTON_BOOK_BASE) {
      // Asked last, and by range rather than by equality, because the book's ids are one per recipe
      // and there is no fixed number of them. The hourglass branch below is the fallback and takes
      // whatever is left, which is why nothing may be appended after the book's range.
      return bookIntent(player, entity, buttonId - BUTTON_BOOK_BASE);
    }

    Optional<HourglassPreset> preset = HourglassPreset.ofOrdinal(buttonId);
    preset.ifPresent(entity::selectPreset);
    return preset.isPresent();
  }

  /**
   * Runs one press on the recipe book: either a star toggled or a recipe loaded.
   *
   * <p>Everything the press could be wrong about is re-checked here against the server's own state.
   * The still has to have the funnel that gives it a book at all; the recipe is taken from the
   * server's own list at the index the client named rather than from anything the client sent; the
   * still has to be big enough for it; and the inventory the load draws on is read here, not
   * described by the caller. A modified client can therefore ask for a recipe it cannot see, or for
   * a load it cannot supply, and be told no — but it cannot tell this machine what it is holding.
   *
   * @param player the player who pressed the button
   * @param entity the chamber the press is about
   * @param intent the press, as {@code index * 2 + (star ? 1 : 0)}
   * @return whether the press was understood
   */
  private static boolean bookIntent(Player player, PressureChamberBlockEntity entity, int intent) {
    int tier = entity.still().tier().tier();
    if (tier < FUNNEL_TIER) {
      return false;
    }

    List<BrewRecipe> all = ModDefinitions.current().recipes().all();
    int index = intent / 2;
    if (index >= all.size()) {
      return false;
    }
    BrewRecipe recipe = all.get(index);

    if ((intent & 1) == 1) {
      return toggleFavourite(player, recipe.id());
    }
    if (tier < recipe.tier()) {
      // Listed but not loadable. The book shows a player what the next rung of the still is for, so
      // a recipe above this machine's tier is still a row — it simply cannot be loaded into this
      // one
      // yet.
      return false;
    }
    return entity.loadRecipe(recipe, player);
  }

  /**
   * Stars or unstars a recipe for one player.
   *
   * <p>An attachment rather than a field on the menu, because a menu lives for as long as a screen
   * is open and a favourite has to outlive the walk back to the chest. The bookmark is read through
   * {@code getExistingDataOrNull} first: a player who has starred nothing has no attachment at all,
   * and asking for one by {@code getData} would put an empty book on the wire a moment before the
   * starred one.
   *
   * @param player the player whose book is being changed
   * @param recipeId the recipe to star or unstar
   * @return whether the press was understood, which is always — a star is not a refusal
   */
  private static boolean toggleFavourite(Player player, String recipeId) {
    AttachmentType<RecipeBookView> type = ModAttachments.RECIPE_BOOK.get();
    RecipeBookView book = player.getExistingDataOrNull(type);
    if (book == null) {
      book = RecipeBookView.of(ModDefinitions.current().recipes());
    }
    player.setData(type, book.toggled(recipeId));
    return true;
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
   * @return whether the chamber's fire is burning
   */
  public boolean isLit() {
    return data.get(PressureChamberData.LIT) != 0;
  }

  /**
   * @return how much of the current fuel is left, from zero to one; zero when the chamber is cold
   */
  public float burnProgress() {
    int total = data.get(PressureChamberData.BURN_TOTAL);
    if (total <= 0) {
      return 0.0F;
    }
    return Math.min(
        1.0F, Math.max(0, data.get(PressureChamberData.BURN_REMAINING)) / (float) total);
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
   * @return how many potions the next brew would make, which is one until the server has said
   *     otherwise — the same reading the machine will price a start at
   */
  public int batchMultiplier() {
    return data.get(PressureChamberData.BATCH_MULTIPLIER);
  }

  /**
   * @return the largest batch the still's condensers allow; one means no condensers, and is what
   *     keeps the control off the screen entirely
   */
  public int batchMaximum() {
    return data.get(PressureChamberData.BATCH_MAXIMUM);
  }

  /**
   * @return whether batch brewing is switched on, which is not the same as the next brew making
   *     more than one
   */
  public boolean batchEnabled() {
    return data.get(PressureChamberData.BATCH_ENABLED) != 0;
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
   * @return whether a stack is a glass bottle, which is what the bottle slot takes
   */
  private static boolean isBottle(ItemStack stack) {
    return stack.getItem() == Items.GLASS_BOTTLE;
  }
}
