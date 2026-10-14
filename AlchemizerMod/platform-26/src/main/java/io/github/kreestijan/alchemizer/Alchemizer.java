package io.github.kreestijan.alchemizer;

import com.mojang.logging.LogUtils;
import io.github.kreestijan.alchemizer.config.AlchemizerConfig;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import io.github.kreestijan.alchemizer.defs.DefinitionLoader;
import io.github.kreestijan.alchemizer.defs.ModRegistries;
import io.github.kreestijan.alchemizer.event.PotionStacking;
import io.github.kreestijan.alchemizer.event.StartupProbe;
import io.github.kreestijan.alchemizer.event.StillInvalidation;
import io.github.kreestijan.alchemizer.event.TankTooltip;
import io.github.kreestijan.alchemizer.gametest.BrewGameTests;
import io.github.kreestijan.alchemizer.gametest.MenuGameTests;
import io.github.kreestijan.alchemizer.gametest.StillGameTests;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModCreativeTabs;
import io.github.kreestijan.alchemizer.registry.ModItems;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import io.github.kreestijan.alchemizer.registry.ModMobEffects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Mod entrypoint. This class only wires things together — config, registries and the startup check.
 * Game logic belongs in feature classes reached from here, never in this class.
 *
 * <p>The constructor parameters are injected by the loader, which recognises the event bus and the
 * mod container by type.
 */
@Mod(Alchemizer.MOD_ID)
public final class Alchemizer {

  public static final String MOD_ID = "alchemizer";

  public static final Logger LOGGER = LogUtils.getLogger();

  /**
   * @param modEventBus the bus this mod's registration and lifecycle events arrive on
   * @param modContainer this mod's container, which is where the config file is registered
   */
  public Alchemizer(IEventBus modEventBus, ModContainer modContainer) {
    // Fail fast on inconsistent shipping defaults. This costs nothing at startup and turns a broken
    // default into an error here rather than a strange number in a player's game much later.
    //
    // This is also the one seam call that may happen this early. It touches nothing but core's own
    // types, so it is safe before any registry exists — unlike the item-stack probe, which needs
    // registries that are not bound yet and lives in StartupProbe for that reason.
    TuningDefaults.sanityCheck();

    ModBlocks.BLOCKS.register(modEventBus);
    ModItems.ITEMS.register(modEventBus);
    ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
    ModMenus.MENUS.register(modEventBus);
    ModMobEffects.MOB_EFFECTS.register(modEventBus);
    ModCreativeTabs.CREATIVE_TABS.register(modEventBus);

    // The three datapack registries the definitions live in. Registered on the mod bus, on both
    // sides, because a registry has to exist before either side can read a datapack that fills it.
    modEventBus.addListener(ModRegistries::onNewRegistry);

    // Raises the potion item's stack size above vanilla's one. On the mod bus because it patches an
    // item's default components, which are bound during registry loading.
    modEventBus.addListener(PotionStacking::onModifyDefaultComponents);

    // The game test bodies. Registered on every launch rather than only when tests are being run,
    // because the registry they live in is frozen before the game knows whether it is running
    // tests; the tests themselves are only given a form when it is. See StillGameTests.
    StillGameTests.register(modEventBus);
    modEventBus.addListener(StillGameTests::onRegisterGameTests);
    BrewGameTests.register(modEventBus);
    modEventBus.addListener(BrewGameTests::onRegisterGameTests);
    MenuGameTests.register(modEventBus);
    modEventBus.addListener(MenuGameTests::onRegisterGameTests);

    // COMMON, because every value in it changes a rule rather than a picture. The config cache is
    // filled by ConfigEvents once the file has actually been read.
    modContainer.registerConfig(ModConfig.Type.COMMON, AlchemizerConfig.SPEC);

    // Registered by hand rather than with @EventBusSubscriber, which defines the subscriber in the
    // wrong classloader for a class that touches game types. See StartupProbe for the whole story.
    NeoForge.EVENT_BUS.addListener(StartupProbe::onServerStarted);
    NeoForge.EVENT_BUS.addListener(StillInvalidation::onNeighborNotify);
    NeoForge.EVENT_BUS.addListener(TankTooltip::onItemTooltip);

    // The recipes and effect values, read from datapacks once the server's registries are frozen
    // and
    // again after every reload. On the game bus because a reload is a game event, not a
    // registration one.
    NeoForge.EVENT_BUS.addListener(DefinitionLoader::onAddServerReloadListeners);
  }
}
