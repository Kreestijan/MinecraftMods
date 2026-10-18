package io.github.kreestijan.alchemizer.brewing.block;

import com.mojang.serialization.MapCodec;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The pipe that lets a tank or a condenser stand further off than face-to-face.
 *
 * <p>Unlike the seven plain parts beside it, tubing has a shape: a 4-pixel core with an arm run to
 * every side that touches something it can carry a connection to. Vanilla's {@link PipeBlock} is
 * exactly that shape and is reused rather than re-derived, so the selection box, the collision box
 * and the model all agree on where the pipe is. The block state still has one boolean per side;
 * {@link PipeBlock#PROPERTY_BY_DIRECTION} already names them.
 *
 * <p><b>What counts as a connection.</b> Tube to tube, and tube to the pressure chamber, a tank or
 * either condenser. Those are the same endpoints the validator walks between, so an arm is never
 * drawn into a block that cannot carry the connection. The test is made against tags, not block
 * ids, for the same reason the validator uses them: a datapack that declares another mod's block a
 * tank should see the pipe grow an arm toward it.
 *
 * <p><b>Translucency is not decided here.</b> In this version of Minecraft a block has no render
 * layer to register; the model baker derives each quad's layer from the alpha of the texture it
 * samples. The pipe is translucent because its texture is, and {@code ModModelProvider} also marks
 * the material {@code force_translucent} so that replacing the art cannot silently turn it opaque.
 */
public class AlchemicalTubingBlock extends PipeBlock {

  /**
   * Half-width is half of this, in pixels: a 4-pixel pipe, the width the rest of the still's art is
   * drawn around. The shape is a 4x4x4 core with 4x4 arms, which is also the model's geometry.
   */
  private static final float PIPE_WIDTH = 4.0F;

  public static final MapCodec<AlchemicalTubingBlock> CODEC =
      simpleCodec(AlchemicalTubingBlock::new);

  /**
   * @param properties the block's properties, already stamped with its registry id
   */
  public AlchemicalTubingBlock(BlockBehaviour.Properties properties) {
    super(PIPE_WIDTH, properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(NORTH, false)
            .setValue(EAST, false)
            .setValue(SOUTH, false)
            .setValue(WEST, false)
            .setValue(UP, false)
            .setValue(DOWN, false));
  }

  @Override
  protected MapCodec<? extends PipeBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
  }

  @Override
  protected BlockState updateShape(
      BlockState state,
      LevelReader level,
      ScheduledTickAccess ticks,
      BlockPos pos,
      Direction directionToNeighbour,
      BlockPos neighbourPos,
      BlockState neighbourState,
      RandomSource random) {
    // Only the side that changed is recomputed. The other five cannot have learned anything from
    // this neighbour, and each one has its own update coming if it did.
    return state.setValue(
        PROPERTY_BY_DIRECTION.get(directionToNeighbour), connectsTo(neighbourState));
  }

  @Override
  protected void affectNeighborsAfterRemoval(
      BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    // The same contract every other still part has: losing one pauses a running brew and makes the
    // chamber look at its surroundings again.
    PressureChamberBlockEntity.partRemovedNear(level, pos);
  }

  /**
   * A glass pipe should not hide the block behind it, so the camera and line-of-sight rays are
   * allowed through. This is the shape vanilla gives iron bars and glass; the selection outline
   * still comes from {@link PipeBlock#getShape}, so the player still points at the pipe.
   */
  @Override
  protected VoxelShape getVisualShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return Shapes.empty();
  }

  @Override
  protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
    return 1.0F;
  }

  @Override
  protected boolean propagatesSkylightDown(BlockState state) {
    return true;
  }

  /**
   * Hides the faces two adjacent pipes turn toward each other. The arms meet exactly at the block
   * boundary, so without this a run of translucent tubing would blend two coplanar end caps into a
   * brighter seam at every joint.
   */
  @Override
  protected boolean skipRendering(
      BlockState state, BlockState neighbourState, Direction direction) {
    return neighbourState.is(ModTags.ALCHEMICAL_TUBING)
        || super.skipRendering(state, neighbourState, direction);
  }

  /**
   * @return the state with every arm set from the block currently on that side
   */
  private static BlockState withConnections(
      BlockGetter level, BlockPos pos, BlockState defaultState) {
    BlockState state = defaultState;
    for (Direction direction : Direction.values()) {
      state =
          state.setValue(
              PROPERTY_BY_DIRECTION.get(direction),
              connectsTo(level.getBlockState(pos.relative(direction))));
    }
    return state;
  }

  /**
   * @return whether a pipe arm should run into this neighbour
   */
  private static boolean connectsTo(BlockState neighbour) {
    return neighbour.is(ModTags.ALCHEMICAL_TUBING)
        || neighbour.is(ModTags.PRESSURE_CHAMBER)
        || neighbour.is(ModTags.ALCHEMICAL_TANK)
        || neighbour.is(ModTags.CONDENSER)
        || neighbour.is(ModTags.ADVANCED_CONDENSER);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
  }
}
