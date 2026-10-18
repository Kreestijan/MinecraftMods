package io.github.kreestijan.alchemizer.shop.pathing;

import io.github.kreestijan.alchemizer.core.flowfield.NavView;
import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import io.github.kreestijan.alchemizer.registry.ModTags;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A customer's walkable world, read out of a real level.
 *
 * <p>This is the adapter between the game's idea of a place and {@link NavView}'s: it turns an
 * offset from the Counter into a world position, asks the world what is there, and answers with a
 * cost in {@code core}'s own units. Nothing above this line knows a block from a hole.
 *
 * <p><b>What "standable" means.</b> Three things have to hold: the cell at the customer's feet and
 * the cell its head is in have to be free of collision, and the block below has to be ground. The
 * first two are the same question asked twice, because a customer is two blocks tall and a one-cell
 * gap it cannot fit through is not a route. They are asked here rather than left to the entity's
 * own collision handling, because a route that turns out to be impassable is a customer that walks
 * into a wall forever, and the field is where that is preventable.
 *
 * <p><b>Why not vanilla's own answers.</b> Two of them are close at hand and neither fits. {@code
 * BlockState.isPathfindable} is the obvious one and it is wrong for this feature: it returns false
 * for a dirt path, so a shopkeeper's path — the exact block this whole exercise exists to prefer —
 * would be the one thing customers refused to walk on. {@code isFaceSturdy} is the other and it has
 * the same shape of problem: the overload without a {@code SupportType} asks whether the block
 * fills its cell to exactly the ceiling, and a dirt path is fifteen sixteenths of a block, so it
 * fails the test that was supposed to admit it. So the ground test is stated as what it actually
 * means — the block below has a collision shape that reaches most of the way up — and it is written
 * down here rather than buried in a predicate.
 *
 * <p><b>Unreadable cells answer empty rather than guessing.</b> {@link ServerLevel#isLoaded} is
 * checked before anything is read, so a cell in a chunk that is not loaded is reported as unknown
 * rather than as air. Reading it as air would extend a field across a block that is merely not
 * being watched, and a customer spawned on the far side would be stranded there. Reading never
 * forces a chunk to load: a Counter at a chunk border simply does not reach across it until
 * somebody walks over.
 *
 * <p><b>Not thread-safe, and not meant to be shared.</b> One instance belongs to one build: the
 * cursor below is reused for every cell so that a field of eighteen thousand cells allocates a
 * handful of objects rather than a few thousand, which is only safe because a build runs on the
 * server thread from start to finish.
 */
public final class LevelNavView implements NavView {

  /**
   * PLACEHOLDER: how high a block's collision shape has to reach to count as ground.
   *
   * <p>Half a block, which is a slab: anything a customer could step up onto in one go is floor,
   * and anything shallower is not. This is the number that keeps carpets, snow layers and lily pads
   * out of the field without naming any of them, and it is what lets a dirt path in.
   */
  private static final double MIN_GROUND_HEIGHT = 0.5;

  /**
   * What walking onto a preferred path block costs, and what walking onto anything else costs.
   *
   * <p>Given to this view rather than looked up in the config by it, because a view is built on
   * every rebuild of a field and belongs to one build: the numbers arrive from the Counter that
   * asked for the field, which read them once when it was created. Their ratio is the strength of
   * the preference §5.2 asks for — at one to three, a path detour of up to three times the
   * straight-line distance is still the cheaper way round.
   */
  private final int preferredCost;

  private final int plainCost;

  private final ServerLevel level;
  private final BlockPos origin;
  private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

  /**
   * @param level the level to read from
   * @param origin the world position that offset {@code (0, 0, 0)} refers to, which is the Counter
   * @param preferredCost what a preferred path block costs to walk onto
   * @param plainCost what every other walkable block costs to walk onto
   */
  public LevelNavView(ServerLevel level, BlockPos origin, int preferredCost, int plainCost) {
    this.level = Objects.requireNonNull(level, "level");
    // Snapshotted rather than held, for the reason LevelStructureView gives: a view whose idea of
    // the origin moved under it would build a field around a Counter that is no longer there.
    this.origin = Objects.requireNonNull(origin, "origin").immutable();
    this.preferredCost = preferredCost;
    this.plainCost = plainCost;
  }

  @Override
  public OptionalInt entryCostAt(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    int feetX = origin.getX() + pos.x();
    int feetY = origin.getY() + pos.y();
    int feetZ = origin.getZ() + pos.z();

    if (!passable(feetX, feetY, feetZ) || !passable(feetX, feetY + 1, feetZ)) {
      return OptionalInt.empty();
    }

    // The cursor is left at the ground block on purpose: isFaceSturdy is asked about the state at
    // the position it is handed, so the two have to be the same cell.
    cursor.set(feetX, feetY - 1, feetZ);
    if (!level.isLoaded(cursor)) {
      return OptionalInt.empty();
    }
    BlockState ground = level.getBlockState(cursor);
    VoxelShape shape = ground.getCollisionShape(level, cursor);
    if (shape.isEmpty() || shape.max(Direction.Axis.Y) < MIN_GROUND_HEIGHT) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(ground.is(ModTags.SHOP_PATH_PREFERRED) ? preferredCost : plainCost);
  }

  /**
   * @param x the east-west coordinate of the cell to test
   * @param y the up-down coordinate of the cell to test
   * @param z the north-south coordinate of the cell to test
   * @return whether a customer's body fits in that cell
   */
  private boolean passable(int x, int y, int z) {
    cursor.set(x, y, z);
    if (!level.isLoaded(cursor)) {
      return false;
    }
    BlockState state = level.getBlockState(cursor);
    // A fluid is excluded as well as a solid, which the collision test alone would not do: water
    // has no collision shape, so without this a customer would wade along a shoreline that looks
    // like dry ground. The design has customers walk to a shop, not swim to it.
    return state.getCollisionShape(level, cursor).isEmpty() && state.getFluidState().isEmpty();
  }
}
