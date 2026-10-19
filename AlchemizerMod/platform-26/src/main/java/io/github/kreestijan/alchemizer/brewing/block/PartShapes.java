package io.github.kreestijan.alchemizer.brewing.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The collision and selection boxes of the seven still parts.
 *
 * <p>Each shape is the model's own footprint, not the block: the valve is a flanged hub, the flue a
 * chimney, the condenser a canister. Keeping the boxes here rather than in {@code ModBlocks} lets
 * the registry stay a registry, and lets the classes that own a shape read it without a second copy
 * of the same numbers. The boxes are in pixels and are the same numbers the models use in {@code
 * ModModelProvider}.
 *
 * <p>The bellows shape is authored pointing north, the same orientation as its model. {@link
 * BellowsBlock} rotates it with {@link Shapes#rotateHorizontal} for the other three facings, which
 * is also the rotation the block-state generator applies to the model.
 */
public final class PartShapes {

  /** A 4-pixel core with a 10x10x2 plate on each face: the valve hub. */
  public static final VoxelShape AIR_PRESSURE_VALVE =
      Shapes.or(
          Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0),
          Block.box(3.0, 3.0, 0.0, 13.0, 13.0, 2.0),
          Block.box(3.0, 3.0, 14.0, 13.0, 13.0, 16.0),
          Block.box(0.0, 3.0, 3.0, 2.0, 13.0, 13.0),
          Block.box(14.0, 3.0, 3.0, 16.0, 13.0, 13.0),
          Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0),
          Block.box(3.0, 14.0, 3.0, 13.0, 16.0, 13.0));

  /** The cap / pleat / plate / pleat / cap sandwich, pointing north. */
  public static final VoxelShape BELLOWS_NORTH =
      Shapes.or(
          Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
          Block.box(2.0, 2.0, 2.0, 14.0, 4.0, 14.0),
          Block.box(1.0, 4.0, 1.0, 15.0, 6.0, 15.0),
          Block.box(0.0, 6.0, 0.0, 16.0, 10.0, 16.0),
          Block.box(1.0, 10.0, 1.0, 15.0, 12.0, 15.0),
          Block.box(2.0, 12.0, 2.0, 14.0, 14.0, 14.0),
          Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0));

  /** A hopper-like bowl, narrowing to the 4-pixel throat that meets the chamber below. */
  public static final VoxelShape FUNNEL =
      Shapes.or(
          Block.box(0.0, 10.0, 0.0, 16.0, 16.0, 16.0),
          Block.box(4.0, 4.0, 4.0, 12.0, 10.0, 12.0),
          Block.box(6.0, 0.0, 6.0, 10.0, 4.0, 10.0));

  /** The 6-pixel chimney plus a 10-pixel collar at each end, so a stack reads as one column. */
  public static final VoxelShape FLUE_STACK =
      Shapes.or(
          Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0),
          Block.box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0),
          Block.box(3.0, 14.0, 3.0, 13.0, 16.0, 13.0));

  /** The crystal cluster: one tall shard and three shorter ones around it. */
  public static final VoxelShape RESONANCE_CRYSTAL =
      Shapes.or(
          Block.box(6.0, 0.0, 6.0, 10.0, 10.0, 10.0),
          Block.box(7.0, 10.0, 7.0, 9.0, 16.0, 9.0),
          Block.box(3.0, 0.0, 7.0, 6.0, 5.0, 10.0),
          Block.box(4.0, 5.0, 8.0, 6.0, 8.0, 10.0),
          Block.box(10.0, 0.0, 4.0, 13.0, 4.0, 7.0),
          Block.box(11.0, 4.0, 5.0, 13.0, 7.0, 7.0),
          Block.box(9.0, 0.0, 10.0, 12.0, 3.0, 13.0));

  /** The condenser canister; the advanced condenser shares the same footprint. */
  public static final VoxelShape CONDENSER = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

  private PartShapes() {}
}
