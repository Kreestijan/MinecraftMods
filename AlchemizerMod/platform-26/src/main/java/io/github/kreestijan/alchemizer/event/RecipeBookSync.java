package io.github.kreestijan.alchemizer.event;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.defs.ModDefinitions;
import io.github.kreestijan.alchemizer.defs.RecipeBookView;
import io.github.kreestijan.alchemizer.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Hands each player the recipe book, once per join and once per reload.
 *
 * <p>A client has no recipe data of its own, so the book has to be pushed. The push has one moment
 * it can happen and no others: {@code OnDatapackSyncEvent} fires after the reload listeners have
 * applied, on both the join path and the reload path, which means the definitions are guaranteed
 * fresh and the player guaranteed present. Sending it from the reload listener instead would send
 * it before the new registries were frozen, and sending it per tick would send something that
 * changes twice a session at the cost of a packet every fifty milliseconds.
 *
 * <p><b>The entries are built once per event, not once per player.</b> They are the same for
 * everyone — discovery is the feature that will make them differ, and it is not implemented — so
 * the book is built from the datapack a single time and each player's own favourites are laid over
 * that one template.
 *
 * <p><b>Favourites are read without asking for them.</b> {@code getData} creates the default and
 * syncs it, which would put an empty book on the wire a moment before the real one. {@code
 * getExistingDataOrNull} answers "nothing yet" for a player who has never starred anything, which
 * is exactly the case that should be given the plain book.
 *
 * <p>A failure here costs a stale list on one screen until the next reload, which is why nothing is
 * thrown onwards: an event handler that threw would take the reload — or the join — down with it.
 */
public final class RecipeBookSync {

  private RecipeBookSync() {}

  /**
   * @param event the datapacks have been read, and this is the moment to tell the players about
   *     them
   */
  public static void onDatapackSync(OnDatapackSyncEvent event) {
    try {
      RecipeBookView book = RecipeBookView.of(ModDefinitions.current().recipes());
      event.getRelevantPlayers().forEach(player -> push(player, book));
    } catch (RuntimeException failure) {
      // Caught around the build as well as the send, because a stream that throws partway through
      // would otherwise leave the players it had not reached holding the previous book.
      Alchemizer.LOGGER.error("Alchemizer: could not send the recipe book to players", failure);
    }
  }

  /**
   * @param player the player to give the book to
   * @param book the entries, without anyone's favourites
   */
  private static void push(ServerPlayer player, RecipeBookView book) {
    try {
      RecipeBookView existing = player.getExistingDataOrNull(ModAttachments.RECIPE_BOOK.get());
      RecipeBookView view = existing == null ? book : book.withFavourites(existing.favourites());
      // Which syncs: AttachmentHolder.setData tells the entity, and an entity that is a player
      // sends
      // the update to that player and to whoever else is watching them, filtered by the type's own
      // predicate to the player alone.
      player.setData(ModAttachments.RECIPE_BOOK.get(), view);
    } catch (RuntimeException failure) {
      Alchemizer.LOGGER.error(
          "Alchemizer: could not give {} the recipe book", player.getScoreboardName(), failure);
    }
  }
}
