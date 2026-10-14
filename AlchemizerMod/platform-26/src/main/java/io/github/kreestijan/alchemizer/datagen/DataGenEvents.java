package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Subscribes the mod's data providers to the generator.
 *
 * <p>The client gather event is the only one this mod listens to, and that is not an oversight: the
 * loader runs client and server generation in a single client pass so that a mod does not have to
 * maintain two generated trees for no benefit. There is no separate server event to miss.
 *
 * <p>The {@code Dist.CLIENT} filter is required rather than decorative. The gather event is a mod
 * bus event, so it would otherwise be delivered in the dedicated server too, where the classes it
 * references — the model provider above all — do not exist. A wrong dist here is a silent no-op in
 * one environment and a crash in the other, which is why it is spelled out.
 */
@EventBusSubscriber(modid = Alchemizer.MOD_ID, value = Dist.CLIENT)
public final class DataGenEvents {

  private DataGenEvents() {}

  @SubscribeEvent
  public static void onGatherData(GatherDataEvent.Client event) {
    // The datapack registries first, so that the lookup every later provider is handed already
    // contains this mod's own entries and a tag or a recipe can refer to them by key.
    event.createDatapackRegistryObjects(ModDatapackEntries.BUILDER);

    event.createProvider(ModLangProvider::new);
    event.createProvider(ModModelProvider::new);
    event.createProvider(ModBlockTagsProvider::new);
    event.createProvider(ModItemTagsProvider::new);
    event.createProvider(ModDataMapProvider::new);
    event.createProvider(ModRecipeProvider.Runner::new);
    event.createProvider(ModLootTableProvider::new);
  }
}
