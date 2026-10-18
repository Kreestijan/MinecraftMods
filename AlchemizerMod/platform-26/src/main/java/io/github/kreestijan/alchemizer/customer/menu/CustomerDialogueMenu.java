package io.github.kreestijan.alchemizer.customer.menu;

import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.customer.Customer;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Serve/Refuse dialogue: two buttons, no slots, and one customer.
 *
 * <p><b>Nothing about asks, potions or payment exists yet</b>, so this is the whole of what the
 * dialogue does: a customer may be served or refused, and both of them dismiss it. The difference
 * between the two today is which button was pressed and nothing else, so the menu is built so that
 * the difference can be given a meaning later without unpicking anything: {@link #clickMenuButton}
 * answers each button by name, and the one place a departure happens is a single call that a serve
 * will one day replace with a sale.
 *
 * <p><b>The client cannot say who the customer is.</b> There is no serverbound payload and no
 * customer id on the wire: the screen is opened on a particular customer by the server, and the
 * only thing that travels back is a button id, which is the same intent-not-content rule the
 * brewing and Counter menus follow. The shop is re-derived here from the customer's own memory of
 * which Counter it came for, and the player's right to be served is asked of the ledger rather than
 * remembered from when the window opened — a co-owner removed between opening the dialogue and
 * pressing Serve is refused by the ledger, not by a flag this class kept.
 *
 * <p>No slots at all, deliberately: a dialogue is not an inventory, and the player has nothing to
 * move into it. That is also why the screen draws no wells — see {@code CustomerDialogueScreen}.
 */
public class CustomerDialogueMenu extends AbstractContainerMenu {

  /** The button that serves the customer: the option a later stage will give a meaning to. */
  public static final int BUTTON_SERVE = 0;

  /**
   * The button that turns the customer away. It dismisses the customer, exactly as serving does.
   */
  public static final int BUTTON_REFUSE = 1;

  /**
   * How far from the customer a dialogue keeps working, as a distance squared.
   *
   * <p>Eight blocks, which is the reach vanilla gives a container: a player who walks away from the
   * customer finds the dialogue gone rather than pressing buttons at somebody across the street.
   */
  private static final double REACH_SQR = 64.0;

  /** The customer this dialogue is about, on the server only. The client's menu holds none. */
  private final @Nullable Customer customer;

  /**
   * The client's constructor, for a window opened with nothing attached to it.
   *
   * <p>Reachable through {@code MenuType.create(int, Inventory)}, which is what the loader falls
   * back to when a menu type is asked for a menu with no data. A dialogue is about one customer,
   * and that is the server's knowledge rather than the client's, so the client's menu is a pair of
   * buttons whose presses the server will resolve against a customer it never told the client
   * about.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   */
  public CustomerDialogueMenu(int containerId, Inventory inventory) {
    this(containerId, inventory, (RegistryFriendlyByteBuf) null);
  }

  /**
   * The client's constructor for a window that carried extra data.
   *
   * <p>This is the three-argument shape {@code IContainerFactory} asks for — the type is registered
   * through {@code IMenuTypeExtension}, as the Counter's is, because that registration is also what
   * lets the server open a window for something other than a block. Nothing is read from the
   * buffer, because this dialogue sends no extra data, which is also what makes the null it is
   * handed on the vanilla path harmless.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   * @param data the window's extra data, which this dialogue has none of
   */
  public CustomerDialogueMenu(
      int containerId, Inventory inventory, @Nullable RegistryFriendlyByteBuf data) {
    this(containerId, inventory, (Customer) null);
  }

  /**
   * The server's constructor, and the one every other constructor above arrives at.
   *
   * @param containerId the window id the server chose
   * @param inventory the player's inventory
   * @param customer the customer this dialogue is about, or null on the client
   */
  public CustomerDialogueMenu(int containerId, Inventory inventory, @Nullable Customer customer) {
    super(ModMenus.CUSTOMER_DIALOGUE.get(), containerId);
    this.customer = customer;
  }

  /**
   * @param player who is looking at the dialogue
   * @return whether it may stay open, which on the client is always yes: only the server knows
   *     where the customer is, and a window the server has closed is closed there and not here
   */
  @Override
  public boolean stillValid(Player player) {
    Customer who = customer;
    if (who == null) {
      return true;
    }
    return !who.isRemoved() && player.distanceToSqr(who) < REACH_SQR;
  }

  /**
   * There is nowhere for a stack to go, so a shift-click hands it straight back.
   *
   * @param player the player doing the move
   * @param slotIndex the slot the stack came from
   * @return empty, always: this menu has no slots for a stack to move between
   */
  @Override
  public ItemStack quickMoveStack(Player player, int slotIndex) {
    return ItemStack.EMPTY;
  }

  /**
   * Serves or refuses the customer, which today is the same clean departure either way.
   *
   * <p><b>Everything is re-derived.</b> The customer comes from this menu's own server-side field —
   * the client never names one — and the shop comes from the Counter the customer remembers walking
   * to, which is asked again here because a customer half way through being dismissed, or one whose
   * Counter has been broken, has no shop left to answer for it. The player's right to act is asked
   * of the same {@code ShopRole} rule every other shop action asks: a press from anybody who is not
   * an owner or co-owner of that shop is refused and changes nothing.
   *
   * <p><b>The two buttons are answered by name rather than as one thing.</b> They do the same thing
   * today and they will not for long, so the branch that will change is the one that says "serve",
   * and an id that is neither is refused rather than read as whichever is first.
   *
   * @param player the player who pressed the button
   * @param buttonId which button, from the constants at the top of this class
   * @return whether the press was understood and the customer acted on
   */
  @Override
  public boolean clickMenuButton(Player player, int buttonId) {
    Customer who = customer;
    if (who == null || who.isRemoved()) {
      return false;
    }
    if (!(who.level() instanceof ServerLevel level)) {
      return false;
    }

    Optional<BlockPos> whereTheShopIs = who.counter();
    if (whereTheShopIs.isEmpty()) {
      // A customer that never learned which shop it came for. There is nobody to say who may serve
      // it, so nobody may — which is the honest answer rather than a default.
      return false;
    }
    if (!(level.getBlockEntity(whereTheShopIs.get()) instanceof CounterBlockEntity counter)) {
      return false;
    }
    Optional<ShopId> shop = counter.shopId();
    if (shop.isEmpty() || !counter.roleOf(player.getUUID()).isStakeholder()) {
      return false;
    }

    switch (buttonId) {
      case BUTTON_SERVE -> serve(level, who);
      case BUTTON_REFUSE -> turnAway(level, who);
      default -> {
        return false;
      }
    }
    return true;
  }

  /**
   * Serves a customer: the sale a shop exists for, which today is only the clean departure.
   *
   * <p>PLACEHOLDER: this is where serving will mean something — an ask, a payment, and a customer
   * that leaves satisfied — and the reason the two buttons are separate branches rather than one
   * dismissal is so that it can mean something without anything around it moving.
   *
   * @param level the level the customer is in
   * @param customer who is being served
   */
  private static void serve(ServerLevel level, Customer customer) {
    customer.despawn(level);
  }

  /**
   * Turns a customer away, which is the same departure: a customer with nothing bought and nothing
   * to complain about leaves the way every other departure does.
   *
   * @param level the level the customer is in
   * @param customer who is being turned away
   */
  private static void turnAway(ServerLevel level, Customer customer) {
    customer.despawn(level);
  }
}
