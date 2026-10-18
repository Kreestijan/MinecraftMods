package io.github.kreestijan.alchemizer.shop.pathing;

import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a worn block into a path, the way a shovel does.
 *
 * <p>{@code DESIGN.md} §5.2: repeated walking turns grass and dirt into path. Whether a cell has
 * been walked enough is {@code PathWear}'s question, in {@code core}; this is what happens once the
 * answer is yes, and it is in the platform module because the answer is a block id.
 *
 * <p><b>Vanilla's own table, copied deliberately.</b> A shovel flattens exactly six blocks into a
 * dirt path, and this flattens the same six into the same block — a footpath worn by customers
 * should be the block a player would have made with their own shovel, because it is looked at,
 * walked on and built around in exactly the same way. Copying the table rather than reading it is
 * the only option: {@code ShovelItem}'s own map is protected and this has no business reaching into
 * it, and a mod that redefined what a path is would make the two disagree.
 *
 * <p><b>The cell above has to be air, and that is vanilla's rule too.</b> A block that changes
 * shape under something standing on it destroys it: a torch, a flower, a fence gate or a rail would
 * all be gone, and the player who put it there would have lost it to a feature they did not ask
 * for. So a cell with anything above it is left alone — worn, counted, and not converted.
 */
public final class PathFlattening {

  /**
   * What a shovel turns into a dirt path, and what a customer's feet turn into one too.
   *
   * <p>Held as {@code BlockState}s rather than as a set of blocks, so that this is the same shape
   * vanilla's own table is: the day the mod wants one of them to become something else, the change
   * is a value here and not a special case somewhere below.
   */
  private static final Map<Block, BlockState> FLATTENED =
      Map.of(
          Blocks.GRASS_BLOCK, Blocks.DIRT_PATH.defaultBlockState(),
          Blocks.DIRT, Blocks.DIRT_PATH.defaultBlockState(),
          Blocks.PODZOL, Blocks.DIRT_PATH.defaultBlockState(),
          Blocks.COARSE_DIRT, Blocks.DIRT_PATH.defaultBlockState(),
          Blocks.MYCELIUM, Blocks.DIRT_PATH.defaultBlockState(),
          Blocks.ROOTED_DIRT, Blocks.DIRT_PATH.defaultBlockState());

  /**
   * What a block change is announced as.
   *
   * <p>Vanilla's shovel uses these three: neighbours are updated because the block's shape changed
   * under whatever is beside it, clients are told, and the change is applied at once rather than
   * deferred to the end of the tick. The neighbour update is what tells the shop's own navigation
   * field that the ground has changed — a dirt path is cheaper to walk than the grass it replaced —
   * so this is also what keeps the field honest about a street it just wore.
   */
  private static final int FLAGS =
      Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE;

  private PathFlattening() {}

  /**
   * Flattens one block if it can be flattened.
   *
   * @param level the level the block is in
   * @param ground where the block is
   * @return whether the block became a path; false when it is not one a path comes from, when
   *     something is standing on it, or when the chunk is not loaded
   */
  public static boolean flatten(ServerLevel level, BlockPos ground) {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(ground, "ground");

    // Both cells are read only if the chunk is loaded — the rule AGENTS.md §3 asks for, and the
    // reason a customer standing at the edge of a loaded area cannot make the server load anything.
    if (!level.isLoaded(ground)) {
      return false;
    }
    BlockState worn = level.getBlockState(ground);
    BlockState path = FLATTENED.get(worn.getBlock());
    if (path == null) {
      return false;
    }

    BlockPos above = ground.above();
    if (!level.isLoaded(above) || !level.getBlockState(above).isAir()) {
      return false;
    }
    return level.setBlock(ground, path, FLAGS);
  }
}
