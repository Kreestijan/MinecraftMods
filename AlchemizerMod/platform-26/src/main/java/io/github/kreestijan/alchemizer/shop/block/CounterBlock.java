package io.github.kreestijan.alchemizer.shop.block;

import com.mojang.serialization.MapCodec;
import io.github.kreestijan.alchemizer.core.shop.PlacementOutcome;
import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.core.shop.ShopLimits;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import io.github.kreestijan.alchemizer.registry.ModDataComponents;
import io.github.kreestijan.alchemizer.shop.ShopData;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import io.github.kreestijan.alchemizer.shop.menu.CounterMenu;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The storefront: the block a shop is built around.
 *
 * <p><b>A player's shop, which is why its only block state is the way it faces.</b> No open/closed
 * variant and no lit state: everything else that changes about a Counter changes in its record, not
 * in its block, because a state property means a model permutation and a palette entry and puts a
 * copy of the shop's condition somewhere a client can see and a server has to keep true. The facing
 * is the exception, and it is one for a reason the rest do not have — it is not a picture of the
 * shop's condition but a fact about the block, and the shop's queue is drawn from it: customers
 * line up in the direction a Counter faces, so the direction has to exist somewhere both the block
 * and the block entity can read, and a block state is where the game keeps that.
 *
 * <p><b>Placement does the interesting work, and vanilla does the hard half of it.</b> By the time
 * {@link #setPlacedBy} runs, {@code BlockItem.place} has already put the item's components onto the
 * block entity — so a Counter being moved is already holding its old shop id before this class is
 * asked anything. What is left is the design's rule about what that id means now: nothing on the
 * item means a new shop, an id whose Counter is gone means the shop coming back, and an id whose
 * Counter is <em>still standing</em> means a copy, which gets a shop of its own.
 *
 * <p>Registered at obsidian's blast resistance and immovable by pistons, per the addendum's A7. The
 * resistance is set through {@code strength}'s second argument rather than by overriding {@code
 * getExplosionResistance}, so it is one number in one place beside the hardness it belongs with.
 */
public class CounterBlock extends BaseEntityBlock {

  /**
   * Which way the Counter faces, and therefore which way its queue runs.
   *
   * <p>Vanilla's own property rather than a new one, so the name, the serialised form, the four
   * values and the default are the ones every other facing block in the game already uses — and so
   * that a resource pack or a datapack's model swap has the property it expects to find.
   */
  public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

  /** The block's own name, which is also what the Counter's screen is titled. */
  private static final String TITLE_KEY = "block.alchemizer.counter";

  /**
   * @param properties the block's properties
   */
  public CounterBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(CounterBlock::new);
  }

  /**
   * A Counter is only ever a facing and nothing else, so {@code facing} is the whole definition.
   *
   * @param builder the state definition being built
   */
  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  /**
   * Sets the Counter down facing the player who placed it.
   *
   * <p>Vanilla's own convention for a block with a front, and the furnace is the one to read for
   * it: a player looking north is standing on the north cell's southern neighbour, so the face that
   * turns to them is the opposite of the way they look. That is what makes the front of a shop the
   * side its owner was standing on when they built it, which is the side they will put the door on.
   *
   * @param context where and by whom the Counter is being placed
   * @return the state it is placed in
   */
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new CounterBlockEntity(pos, state);
  }

  /**
   * Gives the Counter a server tick, which is where its customers are worked out.
   *
   * <p>On the server only, and that is not a formality: the field is built by reading the world and
   * the arrivals are entities, neither of which a client has any business doing. A client's Counter
   * is a block entity with an id in it and nothing more.
   *
   * <p>The helper this goes through is the vanilla one, which compares the block entity type it was
   * asked about with the one it was given and hands back the ticker only for its own block. That is
   * what keeps this method from being called for a block entity of some other type sitting in the
   * same position after a change.
   *
   * @param level the level the Counter is in
   * @param state the Counter's block state
   * @param type the block entity type being ticked
   * @return the ticker, or null on a client
   */
  @Override
  public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level instanceof ServerLevel
        ? createTickerHelper(type, ModBlockEntities.COUNTER.get(), CounterBlockEntity::serverTick)
        : null;
  }

  /**
   * Opens the shop's screen.
   *
   * <p>Opened with the Counter's own position, which is the one thing this mod's screens have to be
   * told beyond the menu itself: what the screen draws of a shop is names, names travel in their
   * own payload, and that payload says which Counter it is about rather than who may read it.
   * Vanilla's open-screen packet has nowhere to carry a position, so this goes through NeoForge's
   * richer one — see {@code ModMenus} — and the client's menu comes out of it already knowing what
   * it is looking at.
   *
   * <p>Opened for anyone, owner, co-owner and stranger alike. What a stranger may do once it is
   * open is nothing — the ledger refuses every press — but being able to see that a shop is shut is
   * a reading, not an action, and there is no reason to make a player walk up to a closed shop and
   * guess.
   *
   * @param state the Counter's block state
   * @param level the level the click happened in
   * @param pos the Counter's position
   * @param player the player who clicked
   * @param hitResult where on the block the click landed
   * @return whether the click was claimed
   */
  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CounterBlockEntity counter) {
      player.openMenu(
          new SimpleMenuProvider(
              (containerId, inventory, opener) ->
                  new CounterMenu(containerId, inventory, counter, opener),
              Component.translatable(TITLE_KEY)),
          pos);
    }
    return InteractionResult.SUCCESS;
  }

  /**
   * Claims the shop this Counter belongs to.
   *
   * <p>Only a player can own a shop, so a Counter placed by anything else — a dispenser, a piston,
   * a command — is left unclaimed. That is a real state rather than an error: {@link
   * io.github.kreestijan.alchemizer.event.CounterProtection} treats a Counter with no shop as
   * unowned and therefore breakable by anyone, so an unclaimed Counter is never a block nobody can
   * remove.
   */
  @Override
  public void setPlacedBy(
      Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);

    if (!(level instanceof ServerLevel server)
        || !(placer instanceof Player player)
        || !(level.getBlockEntity(pos) instanceof CounterBlockEntity counter)) {
      return;
    }

    ShopId offered = stack.get(ModDataComponents.SHOP_ID.get());
    ShopData shops = ShopData.of(server);
    PlacementOutcome outcome =
        shops
            .ledger()
            .claimOnPlace(offered, player.getUUID(), ShopLimits.defaults(), CounterBlock::mintId);

    counter.setShopId(outcome.id());
    shops.changed();
  }

  /**
   * Sets the shop down again without forgetting it.
   *
   * <p>The record is kept, not deleted — that is the whole of "breaking and re-placing restores
   * everything". What changes is that the shop is no longer standing, which is what tells a later
   * placement of the same id that it is a move rather than a copy.
   *
   * <p>The people waiting here go with it. A customer holds the shop's field and its queue directly
   * rather than looking them up, so a broken Counter leaves them holding the last thing it built —
   * a line that would still be there and still be walkable. They leave now, the way they would if
   * the shop had been closed, rather than standing in the road until their patience runs out.
   */
  @Override
  protected void affectNeighborsAfterRemoval(
      BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    if (level.getBlockEntity(pos) instanceof CounterBlockEntity counter) {
      counter
          .shopId()
          .ifPresent(
              id -> {
                ShopData shops = ShopData.of(level);
                shops.ledger().release(id);
                shops.changed();
              });
      counter.dismissCustomers(level);
    }
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
  }

  /**
   * @return a fresh shop id, from the thread's own randomness
   */
  private static ShopId mintId() {
    return ShopId.mint(ThreadLocalRandom.current()::nextLong);
  }
}
