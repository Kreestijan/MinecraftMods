package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.block.AlchemicalTankBlock;
import io.github.kreestijan.alchemizer.brewing.block.IgnitionChamberBlock;
import io.github.kreestijan.alchemizer.brewing.block.PressureChamberBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's blocks.
 *
 * <p>Properties are declared through {@code registerBlock}, which hands the factory a fresh {@link
 * net.minecraft.world.level.block.state.BlockBehaviour.Properties} already stamped with the block's
 * own id. Stamping is not optional in this version of Minecraft: a block without an id throws when
 * it is constructed, and the failure is a long way from the declaration that caused it.
 *
 * <p>The map colour is set on every block on purpose. It defaults to {@code MapColor.NONE}, which
 * renders as an uncoloured square on a map rather than as an error.
 */
public final class ModBlocks {

  public static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks(Alchemizer.MOD_ID);

  /**
   * Matches a burning furnace, which is the brightness players already read as "this is working".
   */
  private static final int LIT_LIGHT_LEVEL = 13;

  /**
   * The still's firebox. Holds a fuel slot and a burn state, so it is a block entity too.
   *
   * <p>It emits light, and the light level is read from the block state rather than kept as a
   * field: a lit chamber is brighter than a cold one, and the state is already what tells a client
   * which model to draw. A separate "is it bright" flag would be a second copy of the same fact.
   */
  public static final DeferredBlock<IgnitionChamberBlock> IGNITION_CHAMBER =
      BLOCKS.registerBlock(
          "ignition_chamber",
          IgnitionChamberBlock::new,
          properties ->
              properties
                  .mapColor(MapColor.METAL)
                  .strength(3.5F, 6.0F)
                  .sound(SoundType.METAL)
                  .lightLevel(
                      state -> state.getValue(IgnitionChamberBlock.LIT) ? LIT_LIGHT_LEVEL : 0));

  /**
   * The reaction vessel. Holds the ingredients and the still's own state, so it is a block entity.
   *
   * <p>It is the hardest of the three to break on purpose: the chamber is the machine. A player who
   * loses it loses the still, where a lost tank or firebox is a part to replace.
   */
  public static final DeferredBlock<PressureChamberBlock> PRESSURE_CHAMBER =
      BLOCKS.registerBlock(
          "pressure_chamber",
          PressureChamberBlock::new,
          properties ->
              properties.mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL));

  /**
   * The water reservoir. Holds a fluid level, so it is a block entity rather than a plain block.
   */
  public static final DeferredBlock<AlchemicalTankBlock> ALCHEMICAL_TANK =
      BLOCKS.registerBlock(
          "alchemical_tank",
          AlchemicalTankBlock::new,
          properties ->
              properties.mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.GLASS));

  private ModBlocks() {}
}
