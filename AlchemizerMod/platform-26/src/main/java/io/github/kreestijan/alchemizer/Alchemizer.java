package io.github.kreestijan.alchemizer;

import com.mojang.logging.LogUtils;
import io.github.kreestijan.alchemizer.config.AlchemizerConfig;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import io.github.kreestijan.alchemizer.customer.CustomerDialogue;
import io.github.kreestijan.alchemizer.customer.NightSkip;
import io.github.kreestijan.alchemizer.customer.StakeholderLogout;
import io.github.kreestijan.alchemizer.defs.DefinitionLoader;
import io.github.kreestijan.alchemizer.defs.ModRegistries;
import io.github.kreestijan.alchemizer.event.CounterProtection;
import io.github.kreestijan.alchemizer.event.PathInvalidation;
import io.github.kreestijan.alchemizer.event.PotionStacking;
import io.github.kreestijan.alchemizer.event.RecipeBookSync;
import io.github.kreestijan.alchemizer.event.StartupProbe;
import io.github.kreestijan.alchemizer.event.StillInvalidation;
import io.github.kreestijan.alchemizer.event.TankTooltip;
import io.github.kreestijan.alchemizer.gametest.BrewGameTests;
import io.github.kreestijan.alchemizer.gametest.CounterGameTests;
import io.github.kreestijan.alchemizer.gametest.CustomerDialogueGameTests;
import io.github.kreestijan.alchemizer.gametest.CustomerGameTests;
import io.github.kreestijan.alchemizer.gametest.MenuGameTests;
import io.github.kreestijan.alchemizer.gametest.StillGameTests;
import io.github.kreestijan.alchemizer.gametest.StillPartGameTests;
import io.github.kreestijan.alchemizer.registry.ModAttachments;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModCreativeTabs;
import io.github.kreestijan.alchemizer.registry.ModDataComponents;
import io.github.kreestijan.alchemizer.registry.ModEntities;
import io.github.kreestijan.alchemizer.registry.ModItems;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import io.github.kreestijan.alchemizer.registry.ModMobEffects;
import io.github.kreestijan.alchemizer.shop.menu.CounterMenu;
import io.github.kreestijan.alchemizer.shop.network.CounterNetwork;
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
    // The customer. Its attributes are not part of the registration and cannot be: they arrive
    // through their own event once the registries are being built, which is the listener just
    // below.
    ModEntities.ENTITIES.register(modEventBus);
    modEventBus.addListener(ModEntities::onEntityAttributeCreation);
    ModMenus.MENUS.register(modEventBus);
    ModMobEffects.MOB_EFFECTS.register(modEventBus);
    ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
    // Player state, which is where the recipe book's favourites live. Registered on the mod bus
    // because it is a registry, and on both sides because a client has to be able to receive the
    // value the server sends it.
    ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

    // The mod's own data components. The Counter's shop id is the only one, and it is registered on
    // both sides because a component has to exist wherever an item carrying it is read.
    ModDataComponents.DATA_COMPONENTS.register(modEventBus);

    // The mod's only payload: a Counter's shop, sent from the server that owns it to the one player
    // looking at it. On the mod bus, because payload registration is a registration event, and
    // registered on both sides even though only the client ever handles it — a type that exists on
    // one side and not the other is a handshake the other end refuses.
    CounterNetwork.register(modEventBus);

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
    StillPartGameTests.register(modEventBus);
    modEventBus.addListener(StillPartGameTests::onRegisterGameTests);
    BrewGameTests.register(modEventBus);
    modEventBus.addListener(BrewGameTests::onRegisterGameTests);
    MenuGameTests.register(modEventBus);
    modEventBus.addListener(MenuGameTests::onRegisterGameTests);
    CounterGameTests.register(modEventBus);
    modEventBus.addListener(CounterGameTests::onRegisterGameTests);
    CustomerGameTests.register(modEventBus);
    modEventBus.addListener(CustomerGameTests::onRegisterGameTests);
    CustomerDialogueGameTests.register(modEventBus);
    modEventBus.addListener(CustomerDialogueGameTests::onRegisterGameTests);

    // COMMON, because every value in it changes a rule rather than a picture. The config cache is
    // filled by ConfigEvents once the file has actually been read.
    modContainer.registerConfig(ModConfig.Type.COMMON, AlchemizerConfig.SPEC);

    // Registered by hand rather than with @EventBusSubscriber, which defines the subscriber in the
    // wrong classloader for a class that touches game types. See StartupProbe for the whole story.
    NeoForge.EVENT_BUS.addListener(StartupProbe::onServerStarted);
    NeoForge.EVENT_BUS.addListener(StillInvalidation::onNeighborNotify);
    // The other half of the same idea, for the other cache: a still remembers what the blocks
    // around it are, and a shop remembers what the ground around it is. Both are told when
    // something changes rather than being asked again on every tick.
    NeoForge.EVENT_BUS.addListener(PathInvalidation::onNeighborNotify);
    // Customers are visitors with a shop's opening hours, so the night skip takes them away.
    NeoForge.EVENT_BUS.addListener(NightSkip::onSleepFinished);
    // And right-clicking one opens the Serve/Refuse dialogue, which is the shopkeeper's only way to
    // deal with a customer until serving means something.
    NeoForge.EVENT_BUS.addListener(CustomerDialogue::onEntityInteract);
    // And so does the shopkeeper leaving: a shop whose last owner or co-owner has signed off is
    // shut
    // in fact, whatever its record says (A6).
    NeoForge.EVENT_BUS.addListener(StakeholderLogout::onPlayerLoggedOut);
    NeoForge.EVENT_BUS.addListener(TankTooltip::onItemTooltip);
    NeoForge.EVENT_BUS.addListener(CounterProtection::onBreakBlock);
    // The Counter's screen is told who is in its shop when its window opens, which is the one
    // moment the server knows both the shop and the player rather than only one of them.
    NeoForge.EVENT_BUS.addListener(CounterMenu::onContainerOpen);

    // The recipes and effect values, read from datapacks once the server's registries are frozen
    // and
    // again after every reload. On the game bus because a reload is a game event, not a
    // registration one.
    NeoForge.EVENT_BUS.addListener(DefinitionLoader::onAddServerReloadListeners);

    // The recipe book, handed to each player after the definitions above have been read — which is
    // the same event on the same bus for the same reason: a book is a reading of the definitions,
    // and it can only be taken once they are current.
    NeoForge.EVENT_BUS.addListener(RecipeBookSync::onDatapackSync);
  }
}
