package io.github.kreestijan.alchemizer.event;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Tells a shop that the world around its Counter has changed.
 *
 * <p>A navigation field is a picture of the ground, and the ground can change without the Counter
 * being touched: a player walls their shop in, lays a path, digs a stair down to it, or another mod
 * grows a tree across the route. None of those is an event about the Counter, so the field would
 * quietly go on describing a world that is no longer there. This is the other half of {@code
 * ShopPathField}: that class remembers the route, and this is what says when the remembered route
 * is no longer true.
 *
 * <p><b>Why this event.</b> {@code NeighborNotifyEvent} is fired from the block-change path itself,
 * for every block in the world, with the position that changed. It is the only notification that
 * arrives for a change made by anything at all — a player, a piston, an explosion, a command or
 * another mod — because every one of those goes through the same {@code setBlock}. It is also,
 * which matters more here than for a still, fired <em>on removal as well as on placement</em>: the
 * event reports the state a block has after the change, and this listener asks the block nothing
 * about what it is. A shop walled in by a change the listener never heard would be a shop that
 * never recovered, because nothing would ever come along to dirty it again.
 *
 * <p><b>Why the search is over chunks rather than over cells.</b> A still searches a cube of {@code
 * scanRadius} cells around the change, which is affordable at a radius of three. A field reaches
 * sixteen blocks by default and may be configured to reach further, and a cube of that size is
 * thirty-three cells on a side — thirty-five thousand lookups on every block change in the level,
 * which is not. So the neighbourhood is walked as chunks instead: nine of them at the default
 * radius, each one looked up only if it is already loaded, and each one asked for the block
 * entities it holds rather than for what is at every position. A chunk that is not loaded is
 * skipped rather than loaded, because a Counter in a chunk nobody is watching is not ticking anyway
 * and will build its field from scratch when it comes back.
 *
 * <p><b>Nothing is rebuilt here.</b> The Counters found are marked dirty and the next tick of each
 * one pays for one build, subject to the field's own cooldown. A player who places forty path
 * blocks in a row costs one rebuild per shop, not forty, and a change nowhere near a Counter costs
 * nine chunk lookups.
 *
 * <p>Registered by hand from {@link Alchemizer} rather than with {@code @EventBusSubscriber}, for
 * the reason {@code StartupProbe} gives: that annotation's scanning defines the subscriber in the
 * wrong classloader for a class that touches game types.
 */
public final class PathInvalidation {

  private PathInvalidation() {}

  /**
   * @param event a block has changed, and its neighbours are being told about it
   */
  public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
    // A dedicated server is the only place a field is built. The client has block entities too, but
    // nothing about them is authoritative, and marking caches dirty there would schedule work whose
    // answer the next sync overwrites.
    if (!(event.getLevel() instanceof ServerLevel level)) {
      return;
    }

    BlockPos changed = event.getPos();
    try {
      markNearbyCounters(level, changed);
    } catch (RuntimeException failure) {
      // A tick or event handler must never let an exception out: one strange block would otherwise
      // take the server down. A stale field is a far smaller problem than a crash, and the field is
      // rebuilt the next time anything near the Counter changes.
      Alchemizer.LOGGER.error(
          "Alchemizer: could not invalidate shop paths near {} in {}",
          changed,
          level.dimension().identifier(),
          failure);
    }
  }

  /**
   * Marks every Counter close enough to have had its field changed by this cell.
   *
   * @param level the level the change happened in
   * @param changed the cell that changed
   */
  private static void markNearbyCounters(ServerLevel level, BlockPos changed) {
    int radius = ConfigCache.current().shopFieldRadius();
    int minChunkX = (changed.getX() - radius) >> 4;
    int maxChunkX = (changed.getX() + radius) >> 4;
    int minChunkZ = (changed.getZ() - radius) >> 4;
    int maxChunkZ = (changed.getZ() + radius) >> 4;

    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
          continue;
        }
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
          if (entity instanceof CounterBlockEntity counter
              && isNear(radius, changed, entity.getBlockPos())) {
            counter.pathField().markDirty();
          }
        }
      }
    }
  }

  /**
   * @param radius the reach of a field
   * @param changed the cell that changed
   * @param counter where a Counter stands
   * @return whether a field built around that Counter covers the changed cell
   */
  private static boolean isNear(int radius, BlockPos changed, BlockPos counter) {
    return Math.abs(changed.getX() - counter.getX()) <= radius
        && Math.abs(changed.getZ() - counter.getZ()) <= radius;
  }
}
