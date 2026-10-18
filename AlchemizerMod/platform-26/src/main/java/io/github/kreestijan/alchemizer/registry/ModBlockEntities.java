package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.blockentity.AlchemicalTankBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.IgnitionChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's block entity types.
 *
 * <p>Registered through the vanilla registry key rather than a {@code createBlockEntities} helper,
 * because this version of Minecraft has no such helper: the convenience factories cover items,
 * blocks, entities and data components, and block entity types are not among them.
 *
 * <p>The constructor takes the block entity supplier and the blocks that may carry it. The {@code
 * BlockEntityType.Builder} that older code reaches for no longer exists, and the valid-blocks set
 * is a required argument, not an optional one — a block entity type with no valid block can never
 * be created in a world.
 *
 * <p>The supplier is a lambda so that the block lookup happens when the registry event fires rather
 * than when this class is first touched, which is what keeps the block and the block entity from
 * depending on each other's initialisation order.
 */
public final class ModBlockEntities {

  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Alchemizer.MOD_ID);

  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<IgnitionChamberBlockEntity>>
      IGNITION_CHAMBER =
          BLOCK_ENTITIES.register(
              "ignition_chamber",
              () ->
                  new BlockEntityType<>(
                      IgnitionChamberBlockEntity::new, ModBlocks.IGNITION_CHAMBER.get()));

  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<PressureChamberBlockEntity>>
      PRESSURE_CHAMBER =
          BLOCK_ENTITIES.register(
              "pressure_chamber",
              () ->
                  new BlockEntityType<>(
                      PressureChamberBlockEntity::new, ModBlocks.PRESSURE_CHAMBER.get()));

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlchemicalTankBlockEntity>>
      ALCHEMICAL_TANK =
          BLOCK_ENTITIES.register(
              "alchemical_tank",
              () ->
                  new BlockEntityType<>(
                      AlchemicalTankBlockEntity::new, ModBlocks.ALCHEMICAL_TANK.get()));

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CounterBlockEntity>>
      COUNTER =
          BLOCK_ENTITIES.register(
              "counter",
              () -> new BlockEntityType<>(CounterBlockEntity::new, ModBlocks.COUNTER.get()));

  private ModBlockEntities() {}
}
