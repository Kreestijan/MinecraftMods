package io.github.kreestijan.alchemizer.brewing.blockentity;

import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bellows' block entity, which exists so the client has something to hang a renderer on.
 *
 * <p>It holds no inventory and saves nothing. The one field is presentation state: when the client
 * first saw this bellows animating, so the renderer can start its breath at the open pose instead
 * of joining mid-cycle. It lives in the block entity rather than the render state because the
 * render state is rebuilt from the block entity every frame, and a start time that was rebuilt each
 * frame would always be "now".
 *
 * <p>The server creates the same block entity but never reads the field; there is no ticker and no
 * sync. The animation itself is entirely a client-side render.
 */
public class BellowsBlockEntity extends BlockEntity {

  /** The game time the client started this breath, or -1 when it is not animating. */
  private long animationStart = -1;

  /**
   * @param pos the block's position
   * @param state the block's state
   */
  public BellowsBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.BELLOWS.get(), pos, state);
  }

  /**
   * @return the game time the breath started, or -1 when there is none
   */
  public long animationStart() {
    return animationStart;
  }

  /**
   * @param gameTime the game time the breath started
   */
  public void setAnimationStart(long gameTime) {
    this.animationStart = gameTime;
  }

  /** Forgets the start so the next activation begins at the open pose. */
  public void clearAnimationStart() {
    this.animationStart = -1;
  }
}
