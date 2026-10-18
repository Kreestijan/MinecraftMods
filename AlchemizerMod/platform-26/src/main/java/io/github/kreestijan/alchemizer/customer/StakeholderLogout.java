package io.github.kreestijan.alchemizer.customer;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Clears a shop's street when the last person who could serve anybody leaves it.
 *
 * <p>Addendum A6, as an assumption rather than a rule: "when the last owner/co-owner leaves, or the
 * shop is closed, existing customers despawn cleanly like at night skip". A customer is a visitor
 * with a shop's opening hours, and a shop whose owner has signed off is shut whether or not the
 * record says so — there is nobody to accept a sale, nobody for the customer to talk to, and a
 * street of people waiting outside an empty Counter is the sight this mod is written to avoid. So
 * they are gone rather than lingering, which is the same departure {@code NightSkip} gives them.
 *
 * <p><b>Why this event.</b> {@code PlayerLoggedOutEvent} is fired as a player disconnects. There is
 * no hook that fires when a <em>shop</em> loses its last stakeholder, because a shop is a record in
 * the level's saved data and not an object that anybody watches; the leaving player is the only
 * thing that changes, and this is the moment it changes.
 *
 * <p><b>The leaver is excluded by name, not by trusting the player list.</b> Whether the event
 * fires before or after that player is taken off the server's list is not something this class
 * should depend on — the two orderings would give different answers about a player who is the only
 * stakeholder of their own shop, and one of those answers is wrong. Excluding them outright makes
 * the question "who else is here" rather than "has the list been updated yet".
 *
 * <p><b>Shops are found through their customers rather than the other way round.</b> A shop is a
 * record with no position and no registry of its own, so there is no list of shops to walk; but a
 * customer knows which Counter it came for, and only shops that have customers are affected by this
 * rule at all. The Counter is looked up from the customer's remembered position, which is a chunk
 * lookup on a loaded chunk at the moment somebody logs out.
 *
 * <p>Registered by hand from {@link Alchemizer} rather than with {@code @EventBusSubscriber}, for
 * the reason {@code StartupProbe} gives: that annotation's scanning defines the subscriber in the
 * wrong classloader for a class that touches game types.
 */
public final class StakeholderLogout {

  private StakeholderLogout() {}

  /**
   * @param event a player has disconnected
   */
  public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer leaving)
        || !(leaving.level() instanceof ServerLevel level)) {
      return;
    }

    try {
      MinecraftServer server = level.getServer();
      List<ServerPlayer> stillHere = everyoneElse(server, leaving);
      for (ServerLevel elsewhere : server.getAllLevels()) {
        dismissUnattended(elsewhere, stillHere);
      }
    } catch (RuntimeException failure) {
      // An event handler must never let an exception out. A customer left standing at a shop whose
      // owner has gone is a cosmetic problem; a crashed server is not.
      Alchemizer.LOGGER.error("Alchemizer: could not clear unattended shops", failure);
    }
  }

  /**
   * @param server the server
   * @param leaving who is on their way out
   * @return the players who are still on it, not counting that one
   */
  private static List<ServerPlayer> everyoneElse(MinecraftServer server, ServerPlayer leaving) {
    List<ServerPlayer> everyone = server.getPlayerList().getPlayers();
    List<ServerPlayer> stillHere = new ArrayList<>(everyone.size());
    for (int index = 0; index < everyone.size(); index++) {
      ServerPlayer player = everyone.get(index);
      if (!player.getUUID().equals(leaving.getUUID())) {
        stillHere.add(player);
      }
    }
    return stillHere;
  }

  /**
   * Sends away the customers of every shop in a level that nobody could serve.
   *
   * @param level the level to look through
   * @param online the players who are still here, the one who has just left already taken out
   */
  private static void dismissUnattended(ServerLevel level, List<ServerPlayer> online) {
    // Collected before any of them is touched: leaving the world removes an entity from the
    // collection being walked, which is the same one-pass-to-find, one-pass-to-act shape NightSkip
    // uses.
    List<Customer> present = new ArrayList<>();
    for (Entity entity : level.getAllEntities()) {
      if (entity instanceof Customer customer) {
        present.add(customer);
      }
    }

    for (int index = 0; index < present.size(); index++) {
      Customer customer = present.get(index);
      Optional<BlockPos> whereTheShopIs = customer.counter();
      if (whereTheShopIs.isEmpty()) {
        // A customer that never learned which shop it came for has nothing to wait for and no route
        // to walk, which it works out for itself within its grace period. Nothing to do here.
        continue;
      }
      if (!(level.getBlockEntity(whereTheShopIs.get()) instanceof CounterBlockEntity counter)) {
        // Its Counter is not standing or not loaded, so there is no shop to ask about — and the
        // customer's own route will have gone with it.
        continue;
      }
      ShopRecord shop = counter.record().orElse(null);
      if (shop == null || isAttendedBy(online, shop)) {
        continue;
      }
      customer.despawn(level);
    }
  }

  /**
   * @param online the players who are still here
   * @param shop the shop
   * @return whether any of them owns or co-owns it, which is A6's condition for a shop that is open
   *     in fact rather than only in its record
   */
  private static boolean isAttendedBy(List<ServerPlayer> online, ShopRecord shop) {
    for (int index = 0; index < online.size(); index++) {
      if (shop.isStakeholder(online.get(index).getUUID())) {
        return true;
      }
    }
    return false;
  }
}
