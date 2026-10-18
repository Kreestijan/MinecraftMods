package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModDataComponents;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

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

    // Every still part is an ordinary block with nothing to remember, so it drops itself. This is
    // what makes pulling a still apart recoverable rather than destructive.
    dropSelf(ModBlocks.AIR_PRESSURE_VALVE.get());
    dropSelf(ModBlocks.BELLOWS.get());
    dropSelf(ModBlocks.FUNNEL.get());
    dropSelf(ModBlocks.FLUE_STACK.get());
    dropSelf(ModBlocks.RESONANCE_CRYSTAL.get());
    dropSelf(ModBlocks.CONDENSER.get());
    dropSelf(ModBlocks.ADVANCED_CONDENSER.get());
    dropSelf(ModBlocks.ALCHEMICAL_TUBING.get());

    // The Counter carries its shop id on the item, so its drop is the block plus that one
    // component.
    // Vanilla's copy_components reads a block entity's implicit components — which is where the
    // Counter exposes the id — so this needs no drop code of its own. That is the difference a
    // component makes: the tank's water is plain save data, which the loot system cannot see, so
    // its
    // drop has to be built by hand; a shop id is a registered component, so it is copied for free.
    add(
        ModBlocks.COUNTER.get(),
        createSingleItemTable(ModBlocks.COUNTER.get())
            .apply(
                CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                    .include(ModDataComponents.SHOP_ID.get())));
  }

  @Override
  protected Iterable<Block> getKnownBlocks() {
    return List.of(
        ModBlocks.IGNITION_CHAMBER.get(),
        ModBlocks.PRESSURE_CHAMBER.get(),
        ModBlocks.ALCHEMICAL_TANK.get(),
        ModBlocks.AIR_PRESSURE_VALVE.get(),
        ModBlocks.BELLOWS.get(),
        ModBlocks.FUNNEL.get(),
        ModBlocks.FLUE_STACK.get(),
        ModBlocks.RESONANCE_CRYSTAL.get(),
        ModBlocks.CONDENSER.get(),
        ModBlocks.ADVANCED_CONDENSER.get(),
        ModBlocks.ALCHEMICAL_TUBING.get(),
        ModBlocks.COUNTER.get());
  }
}
