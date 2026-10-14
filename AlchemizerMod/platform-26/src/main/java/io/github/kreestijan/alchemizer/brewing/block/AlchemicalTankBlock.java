package io.github.kreestijan.alchemizer.brewing.block;

import com.mojang.serialization.MapCodec;
import io.github.kreestijan.alchemizer.brewing.blockentity.AlchemicalTankBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.menu.AlchemicalTankMenu;
import io.github.kreestijan.alchemizer.brewing.water.WaterContainers;
import io.github.kreestijan.alchemizer.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Alchemical Tank: the still's water reservoir.
 *
 * <p>A block entity block rather than a plain block because the tank holds a fluid level that has
 * to survive a save and a reload.
 *
 * <p>No block state properties are declared. The tank has no lit state and no facing, so a state
 * definition would exist only to add a state to the palette and a property to every save.
 *
 * <p><b>Pouring water in is why this block has a handler at all.</b> Without one, a right-click
 * with a water bucket fell through to the bucket's own use, which places a water source in the
 * world next to the tank — the tank looked like it was refusing water while it was really spilling
 * it. The handler below claims water containers and lets everything else through untouched.
 */
public class AlchemicalTankBlock extends BaseEntityBlock {

  /** The block's own name, which is also what the tank's screen is titled. */
  private static final String TITLE_KEY = "block.alchemizer.alchemical_tank";

  /**
   * @param properties the block's properties, already stamped with its registry id
   */
  public AlchemicalTankBlock(Properties properties) {
    super(properties);
  }

  /**
   * The codec every block needs since block states became serialisable data.
   *
   * <p>{@code BaseEntityBlock} leaves this abstract while plain {@code Block} implements it, which
   * means a block entity block that forgets to override it does not compile. That is a deliberate
   * improvement over the alternative of it compiling and then failing at load.
   */
  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(AlchemicalTankBlock::new);
  }

  @Override
  public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new AlchemicalTankBlockEntity(pos, state);
  }

  /**
   * Tells any still this tank was feeding that it has lost a part.
   *
   * <p>A tank is part of the machine, so taking it away mid-brew loses the brew. The chamber
   * revalidates before believing it, which is what keeps a tank that merely stood nearby — never
   * connected, never part of the still — from costing a player their ingredients.
   *
   * <p>It fires for every way a block can leave the world, a player's pickaxe, an explosion and a
   * piston alike, because all of them go through the same block change.
   *
   * @param state the block state being removed
   * @param level the server level
   * @param pos where the tank was
   * @param movedByPiston whether a piston moved it rather than it being destroyed
   */
  @Override
  protected void affectNeighborsAfterRemoval(
      BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    PressureChamberBlockEntity.partRemovedNear(level, pos);
  }

  /**
   * Opens the tank's screen.
   *
   * <p>Reached with anything that carries no water, an empty hand included: {@link #useItemOn}
   * answers {@code TRY_WITH_EMPTY_HAND} for those, which is the game's own way of saying "ask the
   * block itself". A water container never arrives here, because that click is claimed above.
   *
   * <p>Everything the screen draws comes from the block entity through the menu's data slots, so
   * opening it is the whole of what this does, exactly as on the pressure chamber.
   *
   * @param state the tank's block state
   * @param level the level the click happened in
   * @param pos the tank's position
   * @param player the player who clicked
   * @param hitResult where on the block the click landed
   * @return whether the click was claimed
   */
  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    if (!level.isClientSide()
        && level.getBlockEntity(pos) instanceof AlchemicalTankBlockEntity tank) {
      player.openMenu(
          new SimpleMenuProvider(
              (containerId, inventory, opener) ->
                  new AlchemicalTankMenu(containerId, inventory, tank),
              Component.translatable(TITLE_KEY)));
    }
    return InteractionResult.SUCCESS;
  }

  /**
   * Drops a tank that still holds what it was holding.
   *
   * <p>The drop is the block's own business rather than the loot table's, because a loot table
   * cannot carry this. Vanilla's {@code minecraft:copy_components} — how a shulker box keeps its
   * contents across a break — reads a block entity's <em>data components</em>, and a water level is
   * plain save data, so a table that tried to copy it would copy nothing. The table is therefore
   * empty on purpose (see {@code ModBlockLoot}) and this is the one source of the drop.
   *
   * <p>Overriding the drop rather than hooking the block entity's removal is what keeps this tied
   * to the paths that drop things at all. Every {@code dropResources} path arrives here — a
   * pickaxe, an explosion, a piston — and a player breaking the tank in creative is not one of
   * them, so creative does not spray tank items the way a removal hook would.
   *
   * <p>The level travels as the block entity's own save data, on the item, which is the format
   * {@code BlockItem} already knows how to put back: placing the tank restores it with no placement
   * code in this mod at all.
   *
   * @param state the tank's block state
   * @param params the loot context, which is where the block entity is read from
   * @return the single tank stack this block drops
   */
  @Override
  protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
    ItemStack dropped = new ItemStack(ModItems.ALCHEMICAL_TANK.get());
    if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
        instanceof AlchemicalTankBlockEntity tank) {
      // Problems discarded rather than collected: the data being written is one integer this class
      // put there itself, so a complaint from the serialiser would be a bug in the save format
      // rather than something a player could act on.
      TagValueOutput output =
          TagValueOutput.createWithContext(
              ProblemReporter.DISCARDING, params.getLevel().registryAccess());
      tank.saveCustomOnly(output);
      BlockItem.setBlockEntityData(dropped, tank.getType(), output);
    }
    // A path that drops the block with no block entity behind it still drops an empty tank. Losing
    // the water is the lesser failure next to losing the block.
    return List.of(dropped);
  }

  /**
   * Pours a water container into the tank.
   *
   * <p>Only a stack that actually carries water is claimed; everything else, including an empty
   * hand and an empty bucket, falls through to whatever vanilla would have done with it.
   *
   * <p>The held item is swapped by hand rather than by returning a transformed stack, because a
   * block's use result has its held-item conversion ignored: this version's server game mode hands
   * the result straight back to the packet handler, which reads the swing source and nothing else.
   * Vanilla's beehive does exactly this for its bottle, and {@link ItemUtils#createFilledResult} is
   * reused so that the creative case, the stack-of-buckets case and the drop-when-the-inventory-is-
   * full case all behave the way they do everywhere else in the game.
   *
   * <p>A tank with no room refuses the click rather than taking part of the water, so a full tank
   * never costs the player a bucket.
   *
   * @param stack the stack the player is holding
   * @param state the tank's block state
   * @param level the level the click happened in
   * @param pos the tank's position
   * @param player the player who clicked
   * @param hand the hand holding the stack
   * @param hitResult where on the block the click landed
   * @return whether the click was claimed
   */
  @Override
  protected InteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hitResult) {
    int waterMb = WaterContainers.waterMb(stack);
    if (waterMb <= 0) {
      return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
    if (level.isClientSide()) {
      // Claimed on both sides: the client has to believe this click was consumed, or it runs its
      // own prediction of the bucket's world-placement and the water appears for a frame.
      return InteractionResult.SUCCESS;
    }
    if (!(level.getBlockEntity(pos) instanceof AlchemicalTankBlockEntity tank)
        || !tank.tryAddWater(waterMb)) {
      return InteractionResult.FAIL;
    }

    player.setItemInHand(
        hand, ItemUtils.createFilledResult(stack, player, WaterContainers.emptied(stack)));
    return InteractionResult.SUCCESS;
  }
}
