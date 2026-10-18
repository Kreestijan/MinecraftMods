package io.github.kreestijan.alchemizer.customer;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.shop.ShopLedger;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import io.github.kreestijan.alchemizer.shop.ShopData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import org.jspecify.annotations.Nullable;

/**
 * Closes the shops whose own people slept through the night.
 *
 * <p>{@code DESIGN.md} §5.2 and addendum A6 give a shop opening hours, and the night skip is where
 * they end: a shop whose owner or co-owner went to bed and passed the night is shut, because the
 * person who could serve anybody is asleep and the sun is up. Its customers go with it — the shop
 * is what a customer is waiting at, so a street outside a shut shop is empty as a consequence of
 * the shop closing rather than by a rule of its own. That is one sentence in {@code
 * CustomerSpawner}, which dismisses the customers of any shop that is not trading, and it is why
 * nothing here goes looking for entities.
 *
 * <p><b>A sleeper closes their own shops and nobody else's.</b> The first version of this took the
 * whole street away at daybreak, which is right in single player and wrong on a server: one player
 * sleeping skips the night for everybody, and a mod that answered it by shutting every shop would
 * be shutting shops whose owners were standing in them. So the sleepers are worked out first, and a
 * shop is closed only when one of <em>its</em> owners or co-owners is among them. A shop belonging
 * to somebody who stayed up keeps its customers and its trade, and goes on being a shop.
 *
 * <p><b>Why this event, and how the sleepers are found.</b> {@code SleepFinishedTimeEvent} is fired
 * when the night is skipped, after the beds have been counted and before the level's clock moves.
 * It carries the level and the clock adjustment and nothing about the people who slept, so the
 * level is asked instead: {@link ServerLevel#players()} and whether each of them is still in bed.
 * That works because of where in vanilla's own tick the event sits — {@code ServerLevel} fires it
 * and only then calls {@code wakeUpAllPlayers} — so at this moment the sleepers are still flagged
 * as sleeping. Reading the flag rather than keeping a list of our own is deliberate: a list would
 * have to be pruned every time somebody left the server half way through a night, and the world
 * already knows.
 *
 * <p>Registered by hand from {@link Alchemizer} rather than with {@code @EventBusSubscriber}, for
 * the reason {@code StartupProbe} gives: that annotation's scanning defines the subscriber in the
 * wrong classloader for a class that touches game types.
 */
public final class NightSkip {

  private NightSkip() {}

  /**
   * @param event the night was skipped, in whichever level the players slept
   */
  public static void onSleepFinished(SleepFinishedTimeEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) {
      return;
    }

    try {
      List<UUID> slept = sleepersIn(level);
      if (slept.isEmpty()) {
        return;
      }
      closeTheShopsOf(level, slept);
    } catch (RuntimeException failure) {
      // An event handler must never let an exception out. A shop left open through a night somebody
      // slept is a cosmetic problem; a crashed server is not.
      Alchemizer.LOGGER.error(
          "Alchemizer: could not close the shops of the players who slept in {}",
          level.dimension().identifier(),
          failure);
    }
  }

  /**
   * @param level the level the night was skipped in
   * @return the players still in their beds in that level, which is who skipped it
   */
  private static List<UUID> sleepersIn(ServerLevel level) {
    List<ServerPlayer> everyone = level.players();
    List<UUID> slept = new ArrayList<>(everyone.size());
    for (int index = 0; index < everyone.size(); index++) {
      ServerPlayer player = everyone.get(index);
      if (player.isSleeping()) {
        slept.add(player.getUUID());
      }
    }
    return slept;
  }

  /**
   * Shuts every shop in the level that one of those players is a stakeholder of.
   *
   * <p>Shut through the ledger rather than by writing the flag, so that this is the same rule
   * everything else asks: {@code setOpen} is what refuses anybody who is not an owner or co-owner,
   * and it answers whether the shop changed, which is also what the saved data is told to keep.
   *
   * @param level the level the night was skipped in
   * @param slept who slept
   */
  private static void closeTheShopsOf(ServerLevel level, List<UUID> slept) {
    ShopData shops = ShopData.of(level);
    ShopLedger ledger = shops.ledger();

    boolean changed = false;
    List<ShopRecord> all = ledger.records();
    for (int index = 0; index < all.size(); index++) {
      ShopRecord shop = all.get(index);
      UUID sleeper = aSleeperAmong(shop, slept);
      if (sleeper != null) {
        changed |= ledger.setOpen(shop.id(), sleeper, false);
      }
    }
    if (changed) {
      shops.changed();
    }
  }

  /**
   * @param shop the shop
   * @param slept who slept
   * @return one of the sleepers who may close that shop, or null when none of them may
   */
  private static @Nullable UUID aSleeperAmong(ShopRecord shop, List<UUID> slept) {
    for (int index = 0; index < slept.size(); index++) {
      UUID player = slept.get(index);
      if (shop.roleOf(player).isStakeholder()) {
        return player;
      }
    }
    return null;
  }
}
