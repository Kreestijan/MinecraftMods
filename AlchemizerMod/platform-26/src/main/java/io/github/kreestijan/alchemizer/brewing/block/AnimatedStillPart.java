package io.github.kreestijan.alchemizer.brewing.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A still part whose model shows the brew working.
 *
 * <p>The chamber is the only thing that knows whether a brew is progressing, so it tells the parts
 * around it when to start and stop. A part owns the rest: its own frames, its own timer and its own
 * block state. That keeps the chamber from having to know how any particular part moves, and it
 * means a second animated part is a new implementation of this interface rather than another branch
 * in the chamber.
 *
 * <p>Callers pass the state they read, because the part's animation properties are part of it.
 */
public interface AnimatedStillPart {

  /**
   * Starts or stops the part's animation.
   *
   * @param level the level the part is in
   * @param pos the part's position
   * @param state the part's current state
   * @param animating whether the still is brewing
   */
  void setAnimating(Level level, BlockPos pos, BlockState state, boolean animating);
}
