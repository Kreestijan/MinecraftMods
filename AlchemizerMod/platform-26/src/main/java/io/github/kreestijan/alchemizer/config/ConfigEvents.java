package io.github.kreestijan.alchemizer.config;

import io.github.kreestijan.alchemizer.Alchemizer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Keeps {@link ConfigCache} in step with the config file.
 *
 * <p>The config events live in the loader's {@code event.config} package, not in {@code config}
 * alongside {@link ModConfig} itself. That split is easy to get wrong and produces an import that
 * looks right and does not resolve.
 *
 * <p>Both events are handled. Loading fires once when the file is first read; reloading fires every
 * time after, including on {@code /reload}. Holding all the numbers in one object means a reload
 * swaps the object and nothing else has to notice, which is the point of the cache existing.
 *
 * <p>The mod id is checked even though these events reach this mod's own bus. It costs one string
 * comparison and it means a second config file added later cannot silently trigger a re-read.
 */
@EventBusSubscriber(modid = Alchemizer.MOD_ID)
public final class ConfigEvents {

  private ConfigEvents() {}

  @SubscribeEvent
  public static void onLoading(ModConfigEvent.Loading event) {
    refreshIfOurs(event.getConfig());
  }

  @SubscribeEvent
  public static void onReloading(ModConfigEvent.Reloading event) {
    refreshIfOurs(event.getConfig());
  }

  private static void refreshIfOurs(ModConfig config) {
    if (Alchemizer.MOD_ID.equals(config.getModId())) {
      ConfigCache.refresh();
    }
  }
}
