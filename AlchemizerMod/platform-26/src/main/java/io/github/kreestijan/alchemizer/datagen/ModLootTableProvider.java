package io.github.kreestijan.alchemizer.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * The loot table generator, wired to {@link ModBlockLoot}.
 *
 * <p>The required-table set is empty, not the set of this mod's blocks. It is a list of tables that
 * must exist for the game to boot, and the game boots fine without a mod's tables; filling it in
 * would turn a missing table into a startup crash instead of a block that drops nothing.
 *
 * <p>The context set is the block one, matching the provider. A provider registered against the
 * wrong context set is accepted silently and then never runs, which is a confusing way to lose an
 * afternoon.
 */
public final class ModLootTableProvider extends LootTableProvider {

  /**
   * @param output the generator's output root
   * @param registries the registry lookup the generator is running against
   */
  public ModLootTableProvider(
      PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
    super(
        output,
        Set.of(),
        List.of(new SubProviderEntry(ModBlockLoot::new, LootContextParamSets.BLOCK)),
        registries);
  }
}
