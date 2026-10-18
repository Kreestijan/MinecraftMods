package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.registry.ModItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * Generates the mod's entries in the vanilla fuel table.
 *
 * <p>The design says fuel burns for as long as vanilla says it burns, which leaves exactly two
 * gaps: Alchemical Coal and the Enhanced Blaze Rod do not exist in vanilla, so nothing has a burn
 * time for either. The chamber reads burn times from the loader's fuel table, so the gaps are
 * filled by adding to that table rather than by special-casing the items in the chamber's code.
 *
 * <p><b>The output lands in the {@code neoforge} namespace, not this mod's.</b> That is the
 * provider's doing, not a mistake: the file is written at the data map type's own id, and every
 * datapack's copies of it are merged at load, so writing into another namespace adds to what is
 * there rather than replacing it.
 *
 * <p>The consequence worth stating plainly: these entries make both items burn in an ordinary
 * furnace as well as in the chamber. The two are the same table, and the design does not ask for
 * them to differ.
 */
public final class ModDataMapProvider extends DataMapProvider {

  /**
   * How long Alchemical Coal burns for, in ticks.
   *
   * <p>Twice a piece of coal. The design gives this fuel a tier but not a burn time, so this is a
   * placeholder that reads as a deliberate upgrade rather than an accident — it is one number, in
   * one place, and changing it changes nothing else.
   */
  private static final int ALCHEMICAL_COAL_BURN_TICKS = 3200;

  /**
   * How long the Enhanced Blaze Rod burns for, in ticks.
   *
   * <p>Twice a blaze rod, which is the same move Alchemical Coal makes on coal: the rung above
   * doubles the rung below, so the ladder reads as a ladder in the one number a player can see
   * without a tooltip. Placeholder, like the other.
   */
  private static final int ENHANCED_BLAZE_ROD_BURN_TICKS = 4800;

  /**
   * @param output the generator's output root
   * @param lookupProvider the registry lookup the generator is running against
   */
  public ModDataMapProvider(
      PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    super(output, lookupProvider);
  }

  @Override
  protected void gather(HolderLookup.Provider provider) {
    // replace is false: this adds a value rather than overriding one, so a datapack that disagrees
    // with this number can still say so.
    builder(NeoForgeDataMaps.FURNACE_FUELS)
        .add(ModItems.ALCHEMICAL_COAL.getKey(), new FurnaceFuel(ALCHEMICAL_COAL_BURN_TICKS), false)
        .add(
            ModItems.ENHANCED_BLAZE_ROD.getKey(),
            new FurnaceFuel(ENHANCED_BLAZE_ROD_BURN_TICKS),
            false);
  }
}
