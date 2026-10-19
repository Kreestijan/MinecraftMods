package io.github.kreestijan.alchemizer.client.bellows;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One frame's worth of bellows presentation data.
 *
 * <p>The render state is rebuilt every frame; the breath's start time deliberately is not here. It
 * lives on the block entity so it survives the rebuild.
 */
public class BellowsRenderState extends BlockEntityRenderState {

  /** The bellows' state this frame. */
  public BlockState state = Blocks.AIR.defaultBlockState();

  /** Whether the moving parts should be submitted at all. */
  public boolean animating;

  /** How far each cap has moved toward the middle plate this frame, in pixels. */
  public float lift;

  /** The cap model, used for both caps with different translations. */
  public MovingBlockRenderState cap;

  /** The lower pleat bands' model. */
  public MovingBlockRenderState lowerPleats;

  /** The upper pleat bands' model. */
  public MovingBlockRenderState upperPleats;
}
