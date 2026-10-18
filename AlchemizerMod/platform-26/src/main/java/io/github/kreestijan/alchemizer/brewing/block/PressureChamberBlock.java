package io.github.kreestijan.alchemizer.brewing.block;

import com.mojang.serialization.MapCodec;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberData;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberMenu;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Pressure Chamber: the still's reaction vessel, and the block that owns the machine.
 *
 * <p>It is the chamber that holds the ingredients and runs the brew, so it is also the chamber that
 * asks whether the parts around it form a still. The ignition chamber below it is a part of the
 * machine, not the machine itself.
 *
 * <p>No block state properties are declared. Unlike the ignition chamber this block has no lit
 * state: whether it is working is a property of the brew inside it, which a player reads from the
 * screen rather than from a glowing side.
 */
public class PressureChamberBlock extends BaseEntityBlock {

  /** The block's own name, which is also what the brewing screen is titled. */
  private static final String TITLE_KEY = "block.alchemizer.pressure_chamber";

  /**
   * @param properties the block's properties, already stamped with its registry id
   */
  public PressureChamberBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(PressureChamberBlock::new);
  }

  @Override
  public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new PressureChamberBlockEntity(pos, state);
  }

  /**
   * Opens the brewing screen.
   *
   * <p>Everything the screen draws comes from the block entity through the menu's data slots, so
   * opening it is the whole of what the block does — there is no state to hand over and no packet
   * to send. The screen is opened only on the server, and the client is told by the open-menu
   * packet; a client that opened its own screen would be looking at a container the server never
   * made, and a click in it would be discarded.
   */
  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    if (!level.isClientSide()
        && level.getBlockEntity(pos) instanceof PressureChamberBlockEntity chamber) {
      player.openMenu(
          new SimpleMenuProvider(
              (containerId, inventory, opener) ->
                  new PressureChamberMenu(
                      containerId, inventory, chamber, new PressureChamberData(chamber)),
              Component.translatable(TITLE_KEY)));
    }
    return InteractionResult.SUCCESS;
  }

  /**
   * Puts out the firebox when the chamber goes.
   *
   * <p>The fire lives in the chamber's block entity, so a chamber that leaves the world must not
   * leave a glowing firebox behind it. The firebox itself may already be gone — an explosion takes
   * both — which is what the position's guards are for.
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
    PressureChamberBlockEntity.writeFireboxLit(level, pos.below(), false);
  }

  /**
   * Ticks the brew, on the server only.
   *
   * <p>The server check is the same one the ignition chamber makes and for the same reason: the
   * brew timer exists in exactly one place. A client that ticked its own copy would drift, and a
   * player would watch a progress bar that disagrees with the potion they eventually get.
   *
   * <p>Most ticks do nothing. The block entity returns immediately unless a brew is actually
   * running, which is what keeps a room full of idle stills from costing anything.
   */
  @Override
  public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level instanceof ServerLevel
        ? createTickerHelper(
            type, ModBlockEntities.PRESSURE_CHAMBER.get(), PressureChamberBlockEntity::serverTick)
        : null;
  }
}
