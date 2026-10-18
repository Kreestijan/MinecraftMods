package io.github.kreestijan.alchemizer.brewing.block;

import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The Ignition Chamber: the still's firebox.
 *
 * <p>A required multiblock part and nothing more. The fuel, the fire and the fuel slot all live in
 * the pressure chamber above now; this block has no block entity, no ticker and no screen, and a
 * right-click on it does nothing.
 *
 * <p>The one behaviour it keeps is the glow. The pressure chamber's block entity writes this
 * block's {@code lit} state whenever its own fire lights or goes out, and the lit state is the
 * whole of what this block tells the world — a client draws the embers without needing to know how
 * much fuel is left.
 *
 * <p>The {@code lit} property is not a picture of the burn timer; it is the burn timer, narrowed to
 * one bit. The chamber above ticks the real count and flips this when it crosses zero, so the two
 * cannot disagree.
 *
 * <p>Fuels are not listed here. Which item is worth which tier is decided by item tags, and how
 * long each one burns is decided by the vanilla fuel table, both of which a datapack can change.
 * This class only knows how to be on fire.
 */
public class IgnitionChamberBlock extends Block {

  /**
   * Whether the firebox is glowing.
   *
   * <p>Vanilla's own property rather than a new one, so the name, the serialised form and the
   * default are the ones every other lit block in the game already uses.
   */
  public static final BooleanProperty LIT = BlockStateProperties.LIT;

  /**
   * @param properties the block's properties, already stamped with its registry id
   */
  public IgnitionChamberBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(LIT, false));
  }

  /**
   * The firebox is glowing or it is not, and nothing else about it varies, so {@code lit} is the
   * only property in the definition.
   */
  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(LIT);
  }

  /**
   * Tells the still above that one of its parts has gone.
   *
   * <p>Breaking the firebox takes the machine apart, so a brew above it pauses until the firebox is
   * back — the brew itself is kept, because only breaking the chamber ends it. This is the one hook
   * that fires for every way a block can leave the world — a player mining it, an explosion, a
   * piston — and it fires after the world already shows the block gone, so the still that is
   * revalidated is the still as it now is.
   *
   * <p>The state passed in is this block's own, on its way out, which is why the work is done by
   * position rather than through it.
   *
   * @param state the block state being removed
   * @param level the server level
   * @param pos where the block was
   * @param movedByPiston whether a piston moved it rather than it being destroyed
   */
  @Override
  protected void affectNeighborsAfterRemoval(
      BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    PressureChamberBlockEntity.partRemovedNear(level, pos);
  }
}
