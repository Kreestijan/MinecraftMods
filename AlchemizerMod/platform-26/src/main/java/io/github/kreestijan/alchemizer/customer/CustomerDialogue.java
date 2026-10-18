package io.github.kreestijan.alchemizer.customer;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.customer.menu.CustomerDialogueMenu;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Opens the Serve/Refuse dialogue when a customer is right-clicked.
 *
 * <p>This is the customer's one interaction, and it is deliberately narrow. Right-clicking any
 * customer — wherever it is standing in the line, whether it has arrived or is still walking — is
 * the shopkeeper's way of dealing with it, and the dialogue is the whole of what that means today:
 * see {@code CustomerDialogueMenu} for why the two options do the same thing.
 *
 * <p><b>A click that is not the shopkeeper's is left to vanilla.</b> Leads, name tags, empty hands
 * and every other interaction in the game go on working, because this handler only claims the click
 * when it has actually opened something: a player who holds nothing in this shop, or a customer
 * whose shop cannot be worked out, falls through to whatever the click meant before this mod
 * existed. Cancelling in those cases would make a customer the one mob in the game that cannot be
 * put on a lead by a passing player.
 *
 * <p><b>What the client may say about this is nothing.</b> The event reaches both sides, and only
 * the server side is acted on — the client is not told whether the player is a stakeholder, and it
 * has no business opening a window the server would refuse. So the client's local interaction is
 * left alone and the server closes it out by opening the menu, which is the same shape a Counter's
 * right-click has: the click is an intention, and the server decides what it meant.
 *
 * <p>Registered by hand from {@link Alchemizer} rather than with {@code @EventBusSubscriber}, for
 * the reason {@code StartupProbe} gives: that annotation's scanning defines the subscriber in the
 * wrong classloader for a class that touches game types.
 */
public final class CustomerDialogue {

  /**
   * What the dialogue's window is titled.
   *
   * <p>The block's own naming convention: a menu is titled with what it is about, and this one is
   * about a customer rather than about a shop, which is why it is not the Counter's title.
   */
  private static final String TITLE_KEY = "gui.alchemizer.customer.dialogue";

  private CustomerDialogue() {}

  /**
   * @param event a player right-clicked an entity
   */
  public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
    Level level = event.getEntity().level();
    if (level.isClientSide()
        || !(event.getTarget() instanceof Customer customer)
        || !(event.getEntity() instanceof ServerPlayer player)) {
      return;
    }

    try {
      if (!openFor(player, customer)) {
        return;
      }
      // Claimed only now, once the window is open: vanilla's own handling of this click is what a
      // customer is for when there is no dialogue to show.
      event.setCancellationResult(InteractionResult.SUCCESS);
      event.setCanceled(true);
    } catch (RuntimeException failure) {
      // An event handler must never let an exception out. A customer that cannot be talked to is a
      // cosmetic problem; a crashed server is not.
      Alchemizer.LOGGER.error("Alchemizer: could not open the dialogue for a customer", failure);
    }
  }

  /**
   * Opens the dialogue for a player who may act at the customer's shop.
   *
   * <p>The customer names the Counter it came for, and the Counter names the shop; the ledger is
   * then asked what the player is to that shop, which is the same rule that decides who may open
   * and close one. A customer that never learned which shop it came for, a Counter that is no
   * longer standing, and a player who is a stranger to the shop all answer the same way: no
   * dialogue, and the click goes to vanilla.
   *
   * @param player who right-clicked
   * @param customer who was clicked
   * @return whether a dialogue was opened
   */
  private static boolean openFor(ServerPlayer player, Customer customer) {
    Optional<BlockPos> whereTheShopIs = customer.counter();
    if (whereTheShopIs.isEmpty()) {
      return false;
    }
    Level level = player.level();
    if (!(level.getBlockEntity(whereTheShopIs.get()) instanceof CounterBlockEntity counter)) {
      return false;
    }
    Optional<ShopId> shop = counter.shopId();
    if (shop.isEmpty() || !counter.roleOf(player.getUUID()).isStakeholder()) {
      return false;
    }

    player.openMenu(
        new SimpleMenuProvider(
            (containerId, inventory, opener) ->
                new CustomerDialogueMenu(containerId, inventory, customer),
            Component.translatable(TITLE_KEY)));
    return true;
  }
}
