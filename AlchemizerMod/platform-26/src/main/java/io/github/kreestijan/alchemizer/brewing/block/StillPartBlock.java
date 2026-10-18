package io.github.kreestijan.alchemizer.brewing.block;

import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A still part that is nothing but a block.
 *
 * <p>The eight parts the still grows after the tank — the air pressure valve, the bellows, the
 * funnel, the flue stack, the resonance crystal, the two condensers and the tubing — are one class
 * because they are one behaviour. Every number any of them contributes is a count the validator
 * takes by looking at the world; none of them holds state, ticks, saves anything or answers a
 * right-click. What is left is a block with a name and a material, which is the {@code Properties}
 * the registry hands in. Eight classes here would be the same fifteen lines and the same comment
 * eight times, and the one thing they would genuinely differ in — material and hardness — is
 * already the argument.
 *
 * <p>The single behaviour they do share is the point of this class. A part that is broken has to
 * tell the nearest pressure chamber, and that is not a courtesy: the chamber decides once, when the
 * machine is assembled, how long a brew takes and how many potions it yields, and then holds that
 * answer for the length of the run. Without this call, pulling a valve out of a working still mid
 * brew would leave the brew running on a machine that no longer exists. See {@link
 * PressureChamberBlockEntity#partRemovedNear}.
 */
public class StillPartBlock extends Block {

  /**
   * @param properties the block's properties, already stamped with its registry id
   */
  public StillPartBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected void affectNeighborsAfterRemoval(
      BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    PressureChamberBlockEntity.partRemovedNear(level, pos);
  }
}
