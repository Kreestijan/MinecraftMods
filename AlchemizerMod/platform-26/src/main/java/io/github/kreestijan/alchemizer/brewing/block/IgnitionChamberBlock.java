package io.github.kreestijan.alchemizer.brewing.block;

import com.mojang.serialization.MapCodec;
import io.github.kreestijan.alchemizer.brewing.blockentity.IgnitionChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.menu.IgnitionChamberData;
import io.github.kreestijan.alchemizer.brewing.menu.IgnitionChamberMenu;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Ignition Chamber: the still's firebox.
 *
 * <p>It holds a single fuel slot and burns what is put in it. It is the only part of the still that
 * is lit, and the lit block state is the whole of what it tells the world — a client draws the
 * embers without needing to know how much fuel is left.
 *
 * <p>The {@code lit} property is not a picture of the burn timer; it is the burn timer, narrowed to
 * one bit. The chamber's block entity ticks the real count and flips this when it crosses zero, so
 * the two cannot disagree.
 *
 * <p>Fuels are not listed here. Which item is worth which tier is decided by item tags, and how
 * long each one burns is decided by the vanilla fuel table, both of which a datapack can change.
 * This class only knows how to be on fire.
 */
public class IgnitionChamberBlock extends BaseEntityBlock {

  /**
   * Whether the chamber is currently burning.
   *
   * <p>Vanilla's own property rather than a new one, so the name, the serialised form and the
   * default are the ones every other lit block in the game already uses.
   */
  public static final BooleanProperty LIT = BlockStateProperties.LIT;

  /** The block's own name, which is also what the fuel screen is titled. */
  private static final String TITLE_KEY = "block.alchemizer.ignition_chamber";

  /**
   * @param properties the block's properties, already stamped with its registry id
   */
  public IgnitionChamberBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(LIT, false));
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(IgnitionChamberBlock::new);
  }

  /**
   * The chamber is lit or it is not, and nothing else about it varies, so {@code lit} is the only
   * property in the definition.
   */
  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(LIT);
  }

  @Override
  public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new IgnitionChamberBlockEntity(pos, state);
  }

  /**
   * Opens the fuel screen.
   *
   * <p>Fuel goes in through the screen rather than by right-clicking an item onto the block, so
   * this does the same thing whatever the player is holding — which is also why it overrides {@code
   * useWithoutItem} and not {@code useItemOn}: there is no item interaction to have.
   *
   * <p>The screen is opened only on the server, and the client is told by the open-menu packet. A
   * client that opened its own screen would be looking at a container the server never made, and a
   * click in it would be discarded.
   */
  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    if (!level.isClientSide()
        && level.getBlockEntity(pos) instanceof IgnitionChamberBlockEntity chamber) {
      player.openMenu(
          new SimpleMenuProvider(
              (containerId, inventory, opener) ->
                  new IgnitionChamberMenu(
                      containerId, inventory, chamber, new IgnitionChamberData(chamber)),
              Component.translatable(TITLE_KEY)));
    }
    return InteractionResult.SUCCESS;
  }

  /**
   * Tells the still above that one of its parts has gone.
   *
   * <p>Breaking the firebox takes the machine apart as surely as breaking the chamber does, and the
   * brew goes with it. This is the one hook that fires for every way a block can leave the world —
   * a player mining it, an explosion, a piston — and it fires after the world already shows the
   * block gone, so the still that is revalidated is the still as it now is.
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

  /**
   * Ticks the chamber, on the server only.
   *
   * <p>The server check is what does the work here: a client is handed no ticker at all, so the
   * burn count exists in exactly one place and the client learns about it through the block state.
   * Burning the fuel on both sides would leave two counters to disagree, and a client that is
   * briefly ahead would show a chamber alight that the server considers cold.
   */
  @Override
  public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level instanceof ServerLevel
        ? createTickerHelper(
            type, ModBlockEntities.IGNITION_CHAMBER.get(), IgnitionChamberBlockEntity::serverTick)
        : null;
  }
}
