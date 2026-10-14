package io.github.kreestijan.alchemizer.event;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.common.item.StackSignatureFactory;
import io.github.kreestijan.alchemizer.core.recipe.IngredientSignature;
import io.github.kreestijan.alchemizer.core.tank.TankMath;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Calls one method in each embedded jar, once, when a server starts.
 *
 * <p>This exists for exactly one reason: nothing else in the mod proves that the two jar-in-jar
 * dependencies actually resolved. A missing declaration produces a mod that builds, loads and then
 * fails the first time a player touches a block — which is the worst possible moment to find out.
 * One call into each module turns that into an error at startup instead.
 *
 * <p><b>Why not the mod constructor.</b> The obvious place for this is {@code Alchemizer}'s
 * constructor, and that is where it started. It cannot live there: constructing an {@link
 * ItemStack} reads the item holder's component map, and in this version those are bound only once
 * the datapacks have loaded. Calling it earlier fails with "Components not bound yet".
 *
 * <p><b>Why not common setup.</b> {@code FMLCommonSetupEvent} is the next obvious answer, and it is
 * also too early — the same binding has still not happened. It is additionally a trap in the data
 * generator, which never fires common setup at all: a probe placed there silently does nothing
 * under {@code runData}, which is how the first version of this class appeared to work.
 *
 * <p>Server start is the first point at which an item stack can be read on both a dedicated server
 * and an integrated one. The call is deliberately allowed to throw: a seam that cannot be reached
 * is a startup failure, not a warning.
 *
 * <p>Note that in a development run the embedded classes sit on the classpath directly, so this
 * proves less here than it does in a shipped jar. The other half of the proof is the shipped jar's
 * own {@code META-INF/jarjar/metadata.json}, which lists both libraries and their files.
 *
 * <p><b>Why this class is registered by hand.</b> It carries no {@code @EventBusSubscriber}
 * annotation, and that is deliberate. That annotation's scanning defines the subscriber class in
 * the plain application loader rather than the one that owns the game's classes, so the first
 * reference to an {@link ItemStack} fails with a loader constraint violation — the same class
 * reachable through two loaders. Registering the same method from {@link Alchemizer} binds it
 * through the loader that owns this package, which is the one that also owns the game.
 */
public final class StartupProbe {

  /**
   * @param event server start, which fires after every registry — built-in and datapack alike — has
   *     been populated and bound
   */
  public static void onServerStarted(ServerStartedEvent event) {
    IngredientSignature probe = StackSignatureFactory.of(new ItemStack(Items.STONE));
    Alchemizer.LOGGER.info(
        "Alchemizer loaded: {} of tank capacity, seam reads {} carrying {} tag(s)",
        TankMath.describe(TankMath.BASIC_TANK_CAPACITY_MB),
        probe.itemId(),
        probe.tagIds().size());
  }

  private StartupProbe() {}
}
