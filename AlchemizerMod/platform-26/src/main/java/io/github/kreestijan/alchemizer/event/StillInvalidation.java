package io.github.kreestijan.alchemizer.event;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.registry.ModTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Tells a still that something near it changed.
 *
 * <p>A still is not built by an event — it is whatever the blocks around the pressure chamber
 * happen to be, and that can change without the chamber being touched at all. This is the other
 * half of {@code StillCache}: that class remembers the answer, and this is what says when the
 * answer is no longer true.
 *
 * <p><b>Why this event.</b> {@code NeighborNotifyEvent} is fired from the block-change path itself,
 * for every block in the world, with the position that changed. It is the only notification that
 * arrives for a change made by anything at all — a player, a piston, an explosion, a command, or
 * another mod — because every one of those goes through the same {@code setBlock}. A listener on
 * player interaction would miss most of them.
 *
 * <p><b>Why the gate.</b> That event fires constantly, for grass growing as much as for a tank
 * being placed, so the first thing checked is whether the change could possibly matter: the block
 * that changed, or one of the six beside it, has to be a machine part. Everything else is rejected
 * after seven block reads. Only then is the search made, and it is bounded by the same {@code
 * scanRadius} the validator itself uses, which is the furthest a chamber could be and still be
 * affected by this cell.
 *
 * <p><b>Why the six neighbours as well as the cell itself.</b> The event reports the state a block
 * has <em>after</em> the change, so a part that was just removed reads as air. Its neighbours are
 * what still say a machine was here. Without that check, breaking the tank off a working still
 * would be the one change the cache never heard about.
 *
 * <p><b>Nothing is validated here.</b> The chambers found are marked dirty and the next reader pays
 * for one scan. A player who breaks three tanks in a row costs one validation, not three, and a
 * change that turns out not to matter costs nothing at all.
 *
 * <p>Registered by hand from {@link Alchemizer} rather than with {@code @EventBusSubscriber}, which
 * defines the subscriber in the wrong classloader for a class that touches game types; see {@code
 * StartupProbe} for the whole story.
 */
public final class StillInvalidation {

  /**
   * Held as a constant rather than read from {@link Direction#values()} at each call, because that
   * method hands back a fresh copy of the array every time and this runs on every block change.
   */
  private static final Direction[] DIRECTIONS = Direction.values();

  /**
   * Every tag that makes a block a part of a still.
   *
   * <p>This is the gate's whole definition of "could matter", so it has to name every part the
   * validator knows. It used to name only the three structural ones, and that was a bug with a
   * shape worth remembering: the chamber only hears about a change within {@code scanRadius} of it,
   * so a part placed out of reach of a chamber or a tank was never announced to the cache at all.
   * Building a still up out of order therefore worked, and building it on afterwards did not — one
   * condenser touching the chamber was counted, the next three in the row behind it were not, and a
   * resonance crystal leaning against a valve was invisible.
   *
   * <p>The tag keys are what is tested, not the block ids inside them, so a datapack that declares
   * another mod's block a condenser is honoured here exactly as it is by the validator. It is
   * walked by index rather than with a for-each for the same reason {@link #DIRECTIONS} is an
   * array: this runs on every block change in the level, and an iterator per cell is an allocation
   * this does not need to make.
   */
  private static final List<TagKey<Block>> PART_TAGS =
      List.of(
          ModTags.IGNITION_CHAMBER,
          ModTags.PRESSURE_CHAMBER,
          ModTags.ALCHEMICAL_TANK,
          ModTags.AIR_PRESSURE_VALVE,
          ModTags.BELLOWS,
          ModTags.FUNNEL,
          ModTags.FLUE_STACK,
          ModTags.RESONANCE_CRYSTAL,
          ModTags.CONDENSER,
          ModTags.ADVANCED_CONDENSER,
          ModTags.ALCHEMICAL_TUBING);

  private StillInvalidation() {}

  /**
   * @param event a block has changed, and its neighbours are being told about it
   */
  public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
    // A dedicated server is the only place a still is real. On a client the block entities exist
    // but nothing about them is authoritative, and marking caches dirty there would schedule work
    // whose answer would be overwritten by the next sync anyway.
    if (!(event.getLevel() instanceof ServerLevel level)) {
      return;
    }

    BlockPos changed = event.getPos();
    try {
      if (!couldAffectAStill(level, changed)) {
        return;
      }
      markNearbyChambers(level, changed);
    } catch (RuntimeException failure) {
      // A tick or event handler must never let an exception out: one bad block would otherwise take
      // the server down. A stale cache is a far smaller problem than a crash, and the chamber will
      // be revalidated the next time something near it changes.
      Alchemizer.LOGGER.error(
          "Alchemizer: could not invalidate stills near {} in {}",
          changed,
          level.dimension().identifier(),
          failure);
    }
  }

  /**
   * @return whether a still could care about this cell changing
   */
  private static boolean couldAffectAStill(ServerLevel level, BlockPos changed) {
    if (isMachinePart(level, changed)) {
      return true;
    }
    for (Direction direction : DIRECTIONS) {
      if (isMachinePart(level, changed.relative(direction))) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param level the level the cell is in
   * @param pos the cell to ask about
   * @return whether this cell is any part of a still
   */
  private static boolean isMachinePart(ServerLevel level, BlockPos pos) {
    if (!level.isLoaded(pos)) {
      return false;
    }
    BlockState state = level.getBlockState(pos);
    for (int i = 0; i < PART_TAGS.size(); i++) {
      if (state.is(PART_TAGS.get(i))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Marks every pressure chamber close enough to have seen this cell.
   *
   * @param changed the cell that changed
   */
  private static void markNearbyChambers(ServerLevel level, BlockPos changed) {
    int radius = ConfigCache.current().scanRadius();
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          cursor.set(changed.getX() + x, changed.getY() + y, changed.getZ() + z);
          if (level.getBlockEntity(cursor) instanceof PressureChamberBlockEntity chamber) {
            chamber.markStillDirty();
          }
        }
      }
    }
  }
}
