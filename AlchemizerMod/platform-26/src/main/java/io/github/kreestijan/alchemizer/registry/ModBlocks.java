package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.block.AlchemicalTankBlock;
import io.github.kreestijan.alchemizer.brewing.block.AlchemicalTubingBlock;
import io.github.kreestijan.alchemizer.brewing.block.BellowsBlock;
import io.github.kreestijan.alchemizer.brewing.block.IgnitionChamberBlock;
import io.github.kreestijan.alchemizer.brewing.block.PartShapes;
import io.github.kreestijan.alchemizer.brewing.block.PressureChamberBlock;
import io.github.kreestijan.alchemizer.brewing.block.StillPartBlock;
import io.github.kreestijan.alchemizer.shop.block.CounterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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
   * The still's firebox: a required part that glows while the chamber above burns.
   *
   * <p>The fuel and the fire live in the pressure chamber's block entity, which writes this block's
   * lit state; the firebox itself holds nothing, ticks nothing and opens no screen. It emits light,
   * and the light level is read from the block state rather than kept as a field: a lit firebox is
   * brighter than a cold one, and the state is already what tells a client which model to draw. A
   * separate "is it bright" flag would be a second copy of the same fact.
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
   * loses it loses the brew and everything in it, where a lost tank or firebox is a part to replace
   * and a paused brew to resume.
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

  // The seven parts below are the tier 2 to tier 4 blocks from DESIGN.md §3.1, plus the tubing from
  // the same table. Six of them are StillPartBlock: blocks that differ only in material and in the
  // shape their model and collision box share. The bellows is the exception — it faces the block it
  // is placed against, which is a second behaviour, so it has its own class. The order they are
  // declared in is the order a player builds them, and the order the creative tab lists them.

  /**
   * Bolted to the side of the ignition chamber: what turns a fire into a draught.
   *
   * <p>The first part that is not strictly necessary to brew. A valve on its own does nothing at
   * all — it is the bellows hung on it that the brew time counts — which is why the two are
   * separate blocks and why the ladder asks for both.
   */
  public static final DeferredBlock<StillPartBlock> AIR_PRESSURE_VALVE =
      BLOCKS.registerBlock(
          "air_pressure_valve",
          properties -> new StillPartBlock(properties, PartShapes.AIR_PRESSURE_VALVE),
          properties ->
              properties
                  .mapColor(MapColor.METAL)
                  .strength(2.0F, 6.0F)
                  .sound(SoundType.METAL)
                  .noOcclusion());

  /**
   * The leather-and-wood lungs that hang on a valve and shorten a brew.
   *
   * <p>Wood rather than metal, and softer than the valve it hangs on: it is the one part of the
   * machine a player is meant to read as replaceable.
   */
  public static final DeferredBlock<BellowsBlock> BELLOWS =
      BLOCKS.registerBlock(
          "bellows",
          BellowsBlock::new,
          properties ->
              properties
                  .mapColor(MapColor.WOOD)
                  .strength(1.5F, 3.0F)
                  .sound(SoundType.WOOD)
                  .noOcclusion());

  /**
   * Sits on top of the pressure chamber and feeds the recipe book.
   *
   * <p>The funnel is the one part whose effect is not on the machine but in the menu: a still with
   * one grows a book button beside the panel. It contributes nothing to a brew and is not asked for
   * by any tier below the third.
   */
  public static final DeferredBlock<StillPartBlock> FUNNEL =
      BLOCKS.registerBlock(
          "funnel",
          properties -> new StillPartBlock(properties, PartShapes.FUNNEL),
          properties ->
              properties
                  .mapColor(MapColor.METAL)
                  .strength(2.0F, 6.0F)
                  .sound(SoundType.METAL)
                  .noOcclusion());

  /**
   * The chimney that stands on a valve and carries the fumes away.
   *
   * <p>Only the bottom block of a stack has to touch the valve; the rest pile upward. The validator
   * counts the blocks in the stack, not the stacks, so a taller chimney is worth no more than a
   * short one — the tier asks for a flue stack, and one block is a stack.
   */
  public static final DeferredBlock<StillPartBlock> FLUE_STACK =
      BLOCKS.registerBlock(
          "flue_stack",
          properties -> new StillPartBlock(properties, PartShapes.FLUE_STACK),
          properties ->
              properties
                  .mapColor(MapColor.METAL)
                  .strength(2.0F, 6.0F)
                  .sound(SoundType.METAL)
                  .noOcclusion());

  /**
   * The tier 4 part: a stone that hums in time with the brew.
   *
   * <p>It is placed against the machine rather than in it, and exactly one is allowed. Two crystals
   * on one still is not a better still, it is a machine with no tier at all — see {@code
   * StillProblem.MULTIPLE_CRYSTAL}.
   */
  public static final DeferredBlock<StillPartBlock> RESONANCE_CRYSTAL =
      BLOCKS.registerBlock(
          "resonance_crystal",
          properties -> new StillPartBlock(properties, PartShapes.RESONANCE_CRYSTAL),
          properties ->
              properties
                  .mapColor(MapColor.METAL)
                  .strength(1.0F, 6.0F)
                  .sound(SoundType.GLASS)
                  .noOcclusion());

  /**
   * Attached past the tank: each one doubles the batch a single brew produces.
   *
   * <p>Basic and advanced are two blocks rather than one block with a state because the difference
   * is not a mode a player switches — it is what the block is made of, which is settled when the
   * block is crafted and never changes afterwards.
   */
  public static final DeferredBlock<StillPartBlock> CONDENSER =
      BLOCKS.registerBlock(
          "condenser",
          properties -> new StillPartBlock(properties, PartShapes.CONDENSER),
          properties ->
              properties
                  .mapColor(MapColor.METAL)
                  .strength(1.5F, 6.0F)
                  .sound(SoundType.GLASS)
                  .noOcclusion());

  /**
   * The same job as the condenser, four times over instead of twice.
   *
   * <p>Quadruple rather than a second doubling: condensers add, they do not compound, so one
   * advanced condenser and three basic ones is ten potions and not twenty-four. The arithmetic
   * lives in {@code PartCounts#maxBatch}.
   */
  public static final DeferredBlock<StillPartBlock> ADVANCED_CONDENSER =
      BLOCKS.registerBlock(
          "advanced_condenser",
          properties -> new StillPartBlock(properties, PartShapes.CONDENSER),
          properties ->
              properties
                  .mapColor(MapColor.METAL)
                  .strength(2.0F, 6.0F)
                  .sound(SoundType.GLASS)
                  .noOcclusion());

  /**
   * The pipe that lets a tank or a condenser stand further off than face-to-face.
   *
   * <p>It is the one part that is not a plain box: a 4-pixel glass tube with an arm toward every
   * side that touches another tube, the chamber, a tank or a condenser. The shape is shared by the
   * model, the selection outline and the collision box, so a run of tubing is a line of thin pipes
   * rather than a wall of blocks. It is also the one part whose appearance depends on its texture
   * being translucent; see {@link AlchemicalTubingBlock}.
   */
  public static final DeferredBlock<AlchemicalTubingBlock> ALCHEMICAL_TUBING =
      BLOCKS.registerBlock(
          "alchemical_tubing",
          AlchemicalTubingBlock::new,
          properties ->
              properties
                  .mapColor(MapColor.METAL)
                  .strength(1.5F, 6.0F)
                  .sound(SoundType.GLASS)
                  .noOcclusion());

  /**
   * The storefront: the block a shop is built around.
   *
   * <p>Blast resistant to obsidian's own figure and immovable by pistons, because the addendum's A7
   * asks for both and a shop's Counter is the one block in this mod a player cannot afford to lose
   * to a stray creeper. Breaking it by hand is refused outright for anyone but the shop's own
   * people — see {@code CounterProtection} — so the strength here is about everything that is not a
   * player.
   */
  public static final DeferredBlock<CounterBlock> COUNTER =
      BLOCKS.registerBlock(
          "counter",
          CounterBlock::new,
          properties ->
              properties
                  .mapColor(MapColor.WOOD)
                  .strength(2.5F, 1200.0F)
                  .sound(SoundType.WOOD)
                  .pushReaction(PushReaction.BLOCK));

  private ModBlocks() {}
}
