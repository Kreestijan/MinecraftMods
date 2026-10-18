package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.common.tag.BlockTagIds;
import io.github.kreestijan.alchemizer.core.machine.MachineBlocks;
import io.github.kreestijan.alchemizer.core.machine.MachineBlocks.Part;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The block tags that say which blocks are which part of a still.
 *
 * <p>These tags are the still's definition. Nothing in the mod decides in code that an ignition
 * chamber is an ignition chamber; the tag says so and {@code StillValidator} believes it. A
 * datapack that adds a block to one of these makes that block part of a still, and another mod's
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

  /** Blocks that count as air pressure valves. */
  public static final TagKey<Block> AIR_PRESSURE_VALVE = blockTag("air_pressure_valve");

  /** Blocks that count as bellows. */
  public static final TagKey<Block> BELLOWS = blockTag("bellows");

  /** Blocks that count as funnels. */
  public static final TagKey<Block> FUNNEL = blockTag("funnel");

  /** Blocks that count as flue stacks. */
  public static final TagKey<Block> FLUE_STACK = blockTag("flue_stack");

  /** Blocks that count as resonance crystals. */
  public static final TagKey<Block> RESONANCE_CRYSTAL = blockTag("resonance_crystal");

  /** Blocks that count as condensers. */
  public static final TagKey<Block> CONDENSER = blockTag("condenser");

  /** Blocks that count as advanced condensers. */
  public static final TagKey<Block> ADVANCED_CONDENSER = blockTag("advanced_condenser");

  /** Blocks that count as alchemical tubing. */
  public static final TagKey<Block> ALCHEMICAL_TUBING = blockTag("alchemical_tubing");

  /**
   * Ground a customer would rather walk on.
   *
   * <p>{@code DESIGN.md} §5.2's preferred path blocks: a route taken over one of these costs less
   * than the same route taken over ordinary ground, so a shopkeeper who lays a path down is walked
   * along. It is a tag rather than a block check for the same reason every other tag here is, and
   * it is deliberately its own tag rather than a reading of a vanilla one, because "what this shop
   * thinks a path is" is a decision the mod makes and a datapack may disagree with.
   */
  public static final TagKey<Block> SHOP_PATH_PREFERRED = blockTag("shop_path_preferred");

  private ModTags() {}

  /**
   * Resolves the tags into the form the machine rules read.
   *
   * <p>Resolved per call rather than held: a datapack reload can change what is in these tags, and
   * the caller is a machine being revalidated, which happens when something nearby changed rather
   * than every tick.
   *
   * @return which block ids count as which part, as of right now
   */
  public static MachineBlocks machineBlocks() {
    return MachineBlocks.of(
        Map.ofEntries(
            Map.entry(Part.IGNITION, BlockTagIds.idsIn(IGNITION_CHAMBER)),
            Map.entry(Part.PRESSURE, BlockTagIds.idsIn(PRESSURE_CHAMBER)),
            Map.entry(Part.TANK, BlockTagIds.idsIn(ALCHEMICAL_TANK)),
            Map.entry(Part.VALVE, BlockTagIds.idsIn(AIR_PRESSURE_VALVE)),
            Map.entry(Part.BELLOWS, BlockTagIds.idsIn(BELLOWS)),
            Map.entry(Part.FUNNEL, BlockTagIds.idsIn(FUNNEL)),
            Map.entry(Part.FLUE, BlockTagIds.idsIn(FLUE_STACK)),
            Map.entry(Part.CRYSTAL, BlockTagIds.idsIn(RESONANCE_CRYSTAL)),
            Map.entry(Part.CONDENSER, BlockTagIds.idsIn(CONDENSER)),
            Map.entry(Part.ADVANCED_CONDENSER, BlockTagIds.idsIn(ADVANCED_CONDENSER)),
            Map.entry(Part.TUBING, BlockTagIds.idsIn(ALCHEMICAL_TUBING))));
  }

  private static TagKey<Block> blockTag(String path) {
    return TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, path));
  }
}
