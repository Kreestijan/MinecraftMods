package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.registry.ModBlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/**
 * Loot tables for the mod's blocks.
 *
 * <p>{@link #getKnownBlocks()} is overridden, and that override is load-bearing rather than
 * tidiness. The base class defaults to every block in the game and then insists that each one has a
 * table in this provider, so leaving it alone makes the generator fail on the first vanilla block
 * it reaches.
 *
 * <p>The tank's table is deliberately empty. Its drop is a tank still carrying the water it held,
 * and a table cannot express that: the level is the block entity's plain save data, while the one
 * vanilla function that copies a block entity onto its item — {@code minecraft:copy_components} —
 * copies registered data components, and there is no component here for it to name. The drop comes
 * from {@code AlchemicalTankBlock#getDrops} instead, and the table exists only because every known
 * block must have one.
 */
public final class ModBlockLoot extends BlockLootSubProvider {

  /**
   * @param registries the registry lookup the generator is running against
   */
  public ModBlockLoot(HolderLookup.Provider registries) {
    // No block in this mod survives an explosion by default, so the resistant set is empty. The
    // feature flags are the vanilla set: nothing here is gated behind an experiment.
    super(Set.of(), FeatureFlags.VANILLA_SET, registries);
  }

  @Override
  protected void generate() {
    dropSelf(ModBlocks.IGNITION_CHAMBER.get());
    dropSelf(ModBlocks.PRESSURE_CHAMBER.get());
    // Empty on purpose, and the block is the reason: see the class comment.
    add(ModBlocks.ALCHEMICAL_TANK.get(), noDrop());
  }

  @Override
  protected Iterable<Block> getKnownBlocks() {
    return List.of(
        ModBlocks.IGNITION_CHAMBER.get(),
        ModBlocks.PRESSURE_CHAMBER.get(),
        ModBlocks.ALCHEMICAL_TANK.get());
  }
}
