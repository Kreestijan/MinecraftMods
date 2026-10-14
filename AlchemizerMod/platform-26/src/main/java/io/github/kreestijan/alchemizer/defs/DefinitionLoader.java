package io.github.kreestijan.alchemizer.defs;

import com.mojang.logging.LogUtils;
import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.slf4j.Logger;

/**
 * Reads the datapack definitions at the end of every reload and publishes them.
 *
 * <p>It has to happen here rather than at startup because the content is data: a datapack can add,
 * change or remove a potion, and {@code /reload} is what makes the game notice. The three
 * registries are loaded and frozen before this listener runs — the reload event promises it, and
 * the promise is what lets the conversion below walk them without checking that they are ready.
 *
 * <p><b>Why it extends {@link SimplePreparableReloadListener}.</b> That base class is a {@link
 * ContextAwareReloadListener}, which is how a mod-added listener is handed the registry lookup; the
 * event's own accessor is deprecated for removal. It also splits the work in two, and the split is
 * the point: converting registries into records allocates freely and happens off the server thread,
 * and publishing the result happens on it. Nothing immutable ever crosses a thread boundary here
 * except the finished snapshot.
 *
 * <p><b>A failure publishes nothing usable rather than crashing the reload.</b> If the conversion
 * throws — a broken registry, or a listener added before the registries exist — the mod carries on
 * with definitions in which no brew can succeed, which is visible in one glance and impossible to
 * mistake for working content. Taking the server down instead would make a datapack mistake
 * indistinguishable from a broken mod.
 */
public final class DefinitionLoader
    extends SimplePreparableReloadListener<ModDefinitions.Snapshot> {

  private static final Logger LOGGER = LogUtils.getLogger();

  /**
   * The name this listener is registered under, which is also what another mod would use to place a
   * dependency on it.
   */
  private static final Identifier NAME =
      Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "definitions");

  private static final DefinitionLoader INSTANCE = new DefinitionLoader();

  private DefinitionLoader() {}

  /**
   * @param event the reload listeners, open for this mod to add to
   */
  public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
    event.addListener(NAME, INSTANCE);
  }

  @Override
  protected ModDefinitions.Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
    // Read per reload rather than held, so that retuning the fallback effect value takes effect on
    // the next /reload without a restart.
    Tuning tuning = ConfigCache.current();
    try {
      return DefinitionConverters.convert(getRegistryLookup(), tuning);
    } catch (RuntimeException failure) {
      LOGGER.error(
          "Alchemizer could not read its datapack definitions; no brew will succeed until they"
              + " load",
          failure);
      return ModDefinitions.Snapshot.empty(tuning);
    }
  }

  @Override
  protected void apply(
      ModDefinitions.Snapshot snapshot, ResourceManager manager, ProfilerFiller profiler) {
    ModDefinitions.publish(snapshot);
  }
}
