package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.common.tag.BlockTagIds;
import io.github.kreestijan.alchemizer.core.machine.MachineBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The block tags that say which blocks are which part of a still.
 *
 * <p>These three tags are the still's definition. Nothing in the mod decides in code that an
 * ignition chamber is an ignition chamber; the tag says so and {@code StillValidator} believes it.
 * A datapack that adds a block to one of these makes that block part of a still, and another mod's
 * chamber can be made interchangeable with this one without anything being recompiled.
 *
 * <p>The keys are declared here rather than spelled out at the two places that use them — this
 * mod's data generator and the resolver below — so that the tag being written and the tag being
 * read cannot become two different tags. That failure is silent: the tags would load, match
 * nothing, and every still in the game would stop forming.
 *
 * <p>Fuel tiers are deliberately not here. They live in the seam, next to the {@code FuelTier} enum
 * that names their paths, because the ignition chamber reads them through {@code FuelTags} and
 * nothing in this module needs to know they exist.
 */
public final class ModTags {

  /** Blocks that count as the still's firebox. */
  public static final TagKey<Block> IGNITION_CHAMBER = blockTag("ignition_chamber");

  /** Blocks that count as the still's reaction vessel, sitting on the firebox. */
  public static final TagKey<Block> PRESSURE_CHAMBER = blockTag("pressure_chamber");

  /** Blocks that count as the still's water reservoir. */
  public static final TagKey<Block> ALCHEMICAL_TANK = blockTag("alchemical_tank");

  private ModTags() {}

  /**
   * Resolves the three tags into the form the machine rules read.
   *
   * <p>Resolved per call rather than held: a datapack reload can change what is in these tags, and
   * the caller is a machine being revalidated, which happens when something nearby changed rather
   * than every tick.
   *
   * @return which block ids count as which part, as of right now
   */
  public static MachineBlocks machineBlocks() {
    return new MachineBlocks(
        BlockTagIds.idsIn(IGNITION_CHAMBER),
        BlockTagIds.idsIn(PRESSURE_CHAMBER),
        BlockTagIds.idsIn(ALCHEMICAL_TANK));
  }

  private static TagKey<Block> blockTag(String path) {
    return TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, path));
  }
}
