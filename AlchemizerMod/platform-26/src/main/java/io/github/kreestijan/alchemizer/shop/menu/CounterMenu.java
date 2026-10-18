package io.github.kreestijan.alchemizer.shop.menu;

import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.core.shop.ShopLimits;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import io.github.kreestijan.alchemizer.core.shop.ShopRole;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import io.github.kreestijan.alchemizer.shop.CounterView;
import io.github.kreestijan.alchemizer.shop.ShopData;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import io.github.kreestijan.alchemizer.shop.network.CounterNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Counter's menu: no slot of its own, a handful of numbers, and three intents.
 *
 * <p><b>No machine slots, and that is not an omission.</b> Every other menu in this mod is a view
 * of a container a machine owns. A Counter owns nothing — its state is a shop record in the level's
 * saved data, and the only thing a player does with it is govern who may trade at it. So all this
 * keeps of the player is the standard inventory, and everything else arrives as numbers.
 *
 * <p><b>A shop outlives a menu, and a menu is not the only one of it.</b> Two players can have the
 * same Counter open at once, so nothing about the shop is cached here: every press re-reads the
 * record, re-derives the index, and asks the ledger, which is where the design's rules live. What
 * this class contributes is the translation — a button id into a position in a list the server
 * built — and nothing else.
 *
 * <p><b>The lists are rebuilt, never remembered.</b> A candidate index only means anything against
 * the order the client was shown, so the view and the add intent are both built from {@link
 * #candidates}, which orders by name for exactly that reason: a menu opened, a player joining and a
 * button pressed a second later must land on the same person. An index past the end of the list as
 * it stands now is refused and changes nothing, which is what a client looking at a shop that has
 * since changed gets.
 */
public class CounterMenu extends AbstractContainerMenu {

  /**
   * Button id for the shop's Open/Closed control, which any stakeholder may press (addendum A6).
   */
  public static final int BUTTON_TOGGLE_OPEN = 0;

  /**
   * The first of the add buttons: one per candidate, in the order {@link #candidates} sorts them.
   *
   * <p>An index into the list the server itself built, never a name and never an id — a client that
   * could name who it wanted added could add anyone on the server to a shop it merely happened to
   * be standing near. The owner alone may press one; the ledger is what refuses everyone else.
   */
  public static final int BUTTON_ADD_CANDIDATE_BASE = BUTTON_TOGGLE_OPEN + 1;

  /**
   * The first of the remove buttons: one per co-owner, in the order the record holds them.
   *
   * <p>A separate range rather than a flag on the add id, because the two index lists that are not
   * the same list — the same number means a different person on each — and an id that did not say
   * which range it was in would be one a client could use to reach the wrong one.
   */
  public static final int BUTTON_REMOVE_CO_OWNER_BASE =
      BUTTON_ADD_CANDIDATE_BASE + CounterView.MAX_NAMES;

  /**
   * One past the last button id, so that the range tests have an end that is not a magic number.
   */
  private static final int BUTTON_LIMIT = BUTTON_REMOVE_CO_OWNER_BASE + CounterView.MAX_NAMES;

  /**
   * How tall the screen's panel is, which is the line the inventory is measured from.
   *
   * <p>Taller than vanilla's own height, because the shop's lists need rows and rows need room
   * above the player's inventory: at the standard height there would be space for the owner's line,
   * the toggle and nothing else. It is the brewing screens' own ceiling — see {@code
   * PressureChamberScreen} for why 240 is the tallest a panel may be and still fit the smallest
   * window vanilla's auto GUI scale guarantees.
   *
   * <p><b>It is kept here rather than on the screen, and that is the fix.</b> The screen used to
   * keep a copy of it while the menu kept a bare {@code INVENTORY_Y}: the pair was asserted in a
   * comment and nowhere else, and the two drifted apart — an inventory at 150 under a panel whose
   * label line falls at 147, which drew the label through the first row of wells. The screen now
   * hands this to its parent and to {@code PanelLabels} rather than naming a height of its own, so
   * the panel, the label's line and the inventory's line are three readings of one number.
   */
  public static final int PANEL_HEIGHT = 240;

  /**
   * The player inventory's origin, in panel coordinates.
   *
   * <p>The width is vanilla's own eight, because this panel is vanilla's own width. The height is
   * not: the shop's lists need room above the inventory, so the panel is taller than the standard
   * one and the inventory sits under them.
   *
   * <p><b>{@code PANEL_HEIGHT - 82}, and written as that subtraction rather than as the 158 it
   * comes to.</b> Eighty-two is the gap vanilla leaves between a panel's bottom edge and its first
   * inventory row, and {@code PanelLabels} puts the label's line eleven above that — so the two
   * agree by construction, which is what a bare number here stopped doing. The three brewing menus
   * place their inventory on the same rule.
   *
   * <p>Public because the screen lines the inventory's label up with the well this puts the first
   * slot in, which is one pixel left of it — see {@code PanelLabels#inventoryLabelX}.
   */
  public static final int INVENTORY_X = 8;

  private static final int INVENTORY_Y = PANEL_HEIGHT - 82;

  private static final int INV_SLOT_COUNT = 27;

  private static final int HOTBAR_SLOT_COUNT = 9;

  /**
   * The order the candidate list is drawn in and indexed by.
   *
   * <p>By name, then by id. The name is what makes the list stable between the send and the press,
   * which is the whole point of ordering it at all; the id is there only because two players can
   * share a name — this mod's own game tests give every mock player the same one — and a sort that
   * left ties in whatever order the player list happened to be in would make the index depend on
   * something no client can see.
   */
  private static final Comparator<ServerPlayer> CANDIDATE_ORDER =
      Comparator.comparing((ServerPlayer player) -> player.getGameProfile().name())
          .thenComparing(player -> player.getUUID().toString());

  /**
   * Which Counter this menu is about, on both sides.
   *
   * <p>The client is told, by the server, with the window — vanilla's open-screen packet carries no
   * position, so this arrives as extra data on NeoForge's richer one. It is the key the screen
   * looks its names up under, and a client that believed the wrong position would find no view and
   * draw none of it.
   */
  private final BlockPos pos;

  /** The Counter, on the server only. The client's menu is built against the numbers alone. */
  private final @Nullable CounterBlockEntity counter;

  private final ContainerData data;

  /**
   * The client's constructor, for a window opened with no extra data.
   *
   * <p>Reachable through {@code MenuType.create(int, Inventory)}, which is what the loader falls
   * back to when a menu type is asked for a menu with nothing attached. This mod always opens a
   * Counter with its position, so the position here is a placeholder that matches no view and the
   * screen draws its empty state rather than somebody else's shop.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   */
  public CounterMenu(int containerId, Inventory inventory) {
    this(containerId, inventory, BlockPos.ZERO, null, new SimpleContainerData(CounterData.COUNT));
  }

  /**
   * The client's constructor for a window that carried the Counter's position.
   *
   * <p>This is the three-argument shape {@code IContainerFactory} asks for. The buffer is null when
   * the two-argument path above was taken — that factory's default delegates to this one with no
   * data at all — which is why it is checked rather than read.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   * @param data the window's extra data, or null when there was none
   */
  public CounterMenu(int containerId, Inventory inventory, @Nullable RegistryFriendlyByteBuf data) {
    this(
        containerId,
        inventory,
        data == null ? BlockPos.ZERO : data.readBlockPos(),
        null,
        new SimpleContainerData(CounterData.COUNT));
  }

  /**
   * The server's constructor.
   *
   * <p>The viewer is passed through to the numbers rather than kept: the one thing this menu
   * reports about them is their role, and a role is a number a data slot can carry.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   * @param counter the Counter being opened
   * @param viewer who is opening it
   */
  public CounterMenu(
      int containerId, Inventory inventory, CounterBlockEntity counter, Player viewer) {
    this(
        containerId,
        inventory,
        counter.getBlockPos(),
        counter,
        new CounterData(counter, viewer.getUUID()));
  }

  /**
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   * @param pos which Counter this menu is about
   * @param counter the Counter, or null on the client
   * @param data the numbers the screen draws
   */
  private CounterMenu(
      int containerId,
      Inventory inventory,
      BlockPos pos,
      @Nullable CounterBlockEntity counter,
      ContainerData data) {
    super(ModMenus.COUNTER.get(), containerId);
    checkContainerDataCount(data, CounterData.COUNT);
    this.pos = pos;
    this.counter = counter;
    this.data = data;

    addDataSlots(data);
    addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
  }

  /**
   * @return which Counter this menu is about, which is the key the screen looks its view up under
   */
  public BlockPos pos() {
    return pos;
  }

  /**
   * @param player who is asking
   * @return whether the Counter is still there to be looked at; true on the client, which has no
   *     block entity and is closed by the window closing instead
   */
  @Override
  public boolean stillValid(Player player) {
    return counter == null || Container.stillValidBlockEntity(counter, player);
  }

  /**
   * Shuffles stacks between the main inventory and the hotbar.
   *
   * <p>The Counter owns no slots, so this is the ordinary player-only move: everything in the main
   * inventory goes to the hotbar, and everything in the hotbar goes to the main inventory. Without
   * it a shift-click would do nothing at all, because the parent's answer is to hand the stack
   * straight back — which is right for a machine screen with nowhere to route to and wrong for one
   * whose whole inventory is the player's.
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

    int inventoryEnd = INV_SLOT_COUNT;
    int hotbarEnd = INV_SLOT_COUNT + HOTBAR_SLOT_COUNT;
    boolean placed =
        slotIndex < inventoryEnd
            ? moveItemStackTo(inSlot, inventoryEnd, hotbarEnd, false)
            : moveItemStackTo(inSlot, 0, inventoryEnd, false);
    if (!placed) {
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
   * <p>Every branch re-reads the shop and re-checks the actor against it. The toggle and the two
   * list presses are handed to {@code ShopLedger}, which is where the design's rules about who may
   * do what are written — so an owner adding a co-owner, a stranger trying to open the shop, and a
   * co-owner trying to fire another co-owner are all refused by the same code the rest of the mod
   * asks, rather than by a second opinion kept here.
   *
   * <p><b>What this adds on top is the index.</b> A client sends a position in a list, and the list
   * is the server's own — rebuilt here for the press so that it is the list as it stands now, not
   * as it stood when the menu opened. A position past the end of it is refused outright, which is
   * what a stale screen gets: the shop has changed under it, and guessing at a neighbour would act
   * on somebody the player never chose.
   *
   * @param player the player who pressed the button
   * @param buttonId which button, from the constants at the top of this class
   * @return whether the press was understood and the shop changed
   */
  @Override
  public boolean clickMenuButton(Player player, int buttonId) {
    if (counter == null || !(counter.getLevel() instanceof ServerLevel server)) {
      // Unreachable: the client's menu holds no Counter, and a press is delivered to the server's
      // menu, which does. Answering "not understood" is the honest reply to a request that arrived
      // somewhere it cannot be acted on.
      return false;
    }
    Optional<ShopId> shopId = counter.shopId();
    ShopRecord shop = counter.record().orElse(null);
    if (shopId.isEmpty() || shop == null) {
      return false;
    }

    ShopData shops = ShopData.of(server);
    UUID actor = player.getUUID();
    boolean changed;

    if (buttonId == BUTTON_TOGGLE_OPEN) {
      changed = shops.ledger().setOpen(shopId.get(), actor, !shop.open());
    } else if (buttonId >= BUTTON_ADD_CANDIDATE_BASE && buttonId < BUTTON_REMOVE_CO_OWNER_BASE) {
      List<ServerPlayer> candidates = candidates(server, shop);
      int index = buttonId - BUTTON_ADD_CANDIDATE_BASE;
      if (index >= candidates.size()) {
        return false;
      }
      changed =
          shops
              .ledger()
              .addCoOwner(
                  shopId.get(), actor, candidates.get(index).getUUID(), ShopLimits.defaults());
    } else if (buttonId >= BUTTON_REMOVE_CO_OWNER_BASE && buttonId < BUTTON_LIMIT) {
      List<UUID> coOwners = shop.coOwners();
      int index = buttonId - BUTTON_REMOVE_CO_OWNER_BASE;
      if (index >= coOwners.size()) {
        return false;
      }
      changed = shops.ledger().removeCoOwner(shopId.get(), actor, coOwners.get(index));
    } else {
      return false;
    }

    if (!changed) {
      return false;
    }

    // Written back before the view is built, so that what the player is sent is the shop as they
    // have just left it rather than as it was a moment ago.
    shops.changed();
    if (player instanceof ServerPlayer serverPlayer) {
      sendView(serverPlayer);
    }
    return true;
  }

  /**
   * Tells one player what the shop at this Counter now is.
   *
   * <p>Sent to the player whose menu this is and to nobody else: two people can have the same
   * Counter open, and each has their own role in it, so a broadcast would be one player's answer
   * reaching another's screen.
   *
   * @param player who should be told
   */
  public void sendView(ServerPlayer player) {
    CounterNetwork.sendTo(player, pos, view());
  }

  /**
   * Builds the shop as the screen draws it.
   *
   * <p>Facts about the shop and nothing about a viewer, for the reason {@code CounterView} gives:
   * one value is sent to one player here, but the shape it has is the shape that stays correct if
   * ever it is not.
   *
   * @return the shop's names and whether it is trading, or an empty view for a Counter with no shop
   */
  public CounterView view() {
    if (counter == null || !(counter.getLevel() instanceof ServerLevel server)) {
      return CounterView.empty();
    }
    Optional<ShopId> shopId = counter.shopId();
    ShopRecord shop = counter.record().orElse(null);
    if (shopId.isEmpty() || shop == null) {
      return CounterView.empty();
    }

    return new CounterView(
        shopId.get().value(),
        nameOf(server, shop.owner()),
        coOwnerNames(server, shop),
        candidateNames(server, shop),
        shop.open());
  }

  /**
   * The players online now who hold nothing in this shop yet.
   *
   * <p>The list an add intent indexes and the list the view carries, built the same way both times
   * so that the two cannot disagree about what position three is.
   *
   * <p>Ordered by name, and truncated to the payload's own ceiling: a busy server has more players
   * online than a view can hold, and a list that grew past the codec's cap would fail to send at
   * all rather than merely being long.
   *
   * @param level the level the Counter stands in
   * @param shop the shop standing there
   * @return the candidates, in the order an add button's index refers to them
   */
  public static List<ServerPlayer> candidates(ServerLevel level, ShopRecord shop) {
    List<ServerPlayer> candidates = new ArrayList<>();
    for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
      if (isCandidate(shop, player.getUUID())) {
        candidates.add(player);
      }
    }
    candidates.sort(CANDIDATE_ORDER);
    return List.copyOf(candidates.subList(0, Math.min(candidates.size(), CounterView.MAX_NAMES)));
  }

  /**
   * The one place the rule about who may be added is written.
   *
   * <p>Asked by the list above and counted by {@code CounterData}, which needs the number every
   * tick and cannot afford to build the list every tick. Two copies of a rule are two chances for
   * them to drift, so this is shared even though each caller uses it in a different shape.
   *
   * @param shop the shop
   * @param player who is being considered
   * @return whether they are a stranger to it, which is what makes them addable
   */
  public static boolean isCandidate(ShopRecord shop, UUID player) {
    return shop.roleOf(player) == ShopRole.STRANGER;
  }

  /**
   * Handles a Counter being opened, which is where the screen is first told who is in the shop.
   *
   * <p>A game bus listener rather than a line inside the block's own right-click, because there is
   * more than one way a window opens and this is the one every way arrives at. It is filtered by
   * the menu it is handed rather than by who opened it: the event fires for every container in the
   * game, and only the Counter's own has anything to send.
   *
   * <p>Fires on the server only — see {@code ServerPlayer#openMenu} — so the entity below is always
   * a server player and the view always has a shop behind it to describe.
   *
   * @param event the window that has just opened
   */
  public static void onContainerOpen(PlayerContainerEvent.Open event) {
    if (event.getContainer() instanceof CounterMenu menu
        && event.getEntity() instanceof ServerPlayer player) {
      menu.sendView(player);
    }
  }

  /**
   * @return whether a shop stands at this Counter, which is the first thing the screen waits for
   */
  public boolean hasShop() {
    return data.get(CounterData.HAS_SHOP) != 0;
  }

  /**
   * @return whether the shop is trading; meaningful only once {@link #hasShop()}
   */
  public boolean open() {
    return data.get(CounterData.OPEN) != 0;
  }

  /**
   * @return how many players the owner has added, which the screen only prints as a count
   */
  public int coOwnerCount() {
    return data.get(CounterData.CO_OWNER_COUNT);
  }

  /**
   * @return how many players are online and addable right now, which is the size of the list the
   *     add buttons index — not necessarily of the list the screen was last sent
   */
  public int candidateCount() {
    return data.get(CounterData.CANDIDATE_COUNT);
  }

  /**
   * What the player looking at this screen is to the shop.
   *
   * <p>The role arrives as an ordinal, and an ordinal that is not one of the three is treated as no
   * answer at all rather than as the first of them: a client reads its own numbers as zeroes until
   * the server's first sync lands, and zero is the owner.
   *
   * @return the viewer's role, or empty before the server has said
   */
  public Optional<ShopRole> role() {
    ShopRole[] roles = ShopRole.values();
    int ordinal = data.get(CounterData.ROLE);
    if (ordinal < 0 || ordinal >= roles.length) {
      return Optional.empty();
    }
    return Optional.of(roles[ordinal]);
  }

  /**
   * @param level the level the Counter stands in
   * @param shop the shop
   * @return the co-owners' names, in the order the record holds them, capped at what a view can
   *     carry
   */
  private static List<String> coOwnerNames(ServerLevel level, ShopRecord shop) {
    List<UUID> coOwners = shop.coOwners();
    int count = Math.min(coOwners.size(), CounterView.MAX_NAMES);
    List<String> names = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      names.add(nameOf(level, coOwners.get(index)));
    }
    return names;
  }

  /**
   * @param level the level the Counter stands in
   * @param shop the shop
   * @return the candidates' names, in the same order {@link #candidates} put them
   */
  private static List<String> candidateNames(ServerLevel level, ShopRecord shop) {
    List<ServerPlayer> candidates = candidates(level, shop);
    List<String> names = new ArrayList<>(candidates.size());
    for (ServerPlayer player : candidates) {
      names.add(shorten(player.getGameProfile().name()));
    }
    return names;
  }

  /**
   * What a shop should call one of its people.
   *
   * <p>A player who is online is asked; a player who is not is looked up in the server's own name
   * cache, which is the list of everyone who has ever joined. That cache is a map lookup and not a
   * network call, so asking it while a menu is being opened costs nothing.
   *
   * <p>A name that neither knows — a shop whose owner has never been seen by this server, which a
   * copied world can produce — comes back empty. That is drawn as an unnamed row rather than
   * guessed at: an empty name is honest about not knowing, and a made-up one would be a lie about
   * who owns the shop.
   *
   * @param level the level the Counter stands in
   * @param player whose name is wanted
   * @return their name, or the empty string when this server has never heard of them
   */
  private static String nameOf(ServerLevel level, UUID player) {
    ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
    if (online != null) {
      return shorten(online.getGameProfile().name());
    }
    return level
        .getServer()
        .services()
        .nameToIdCache()
        .get(player)
        .map(NameAndId::name)
        .map(CounterMenu::shorten)
        .orElse("");
  }

  /**
   * Cuts a name down to what a view can carry.
   *
   * <p>Minecraft's own player names are sixteen characters and so need no cutting; a name from the
   * server's cache or from a hand-edited save need not be, and the codec would refuse to write a
   * longer one — a failure at the far end of a send, with the shop's own screen as the casualty.
   *
   * @param name the name to keep inside the wire's limit
   * @return the name, at most {@code CounterView.MAX_NAME_LENGTH} characters of it
   */
  private static String shorten(String name) {
    return name.length() <= CounterView.MAX_NAME_LENGTH
        ? name
        : name.substring(0, CounterView.MAX_NAME_LENGTH);
  }
}
