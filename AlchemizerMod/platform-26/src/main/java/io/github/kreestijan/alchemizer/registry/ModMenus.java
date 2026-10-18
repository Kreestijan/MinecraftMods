package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.menu.AlchemicalTankMenu;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberMenu;
import io.github.kreestijan.alchemizer.customer.menu.CustomerDialogueMenu;
import io.github.kreestijan.alchemizer.shop.menu.CounterMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's container menus.
 *
 * <p>Every machine menu here is built through the plain constructor, which takes the two-argument
 * factory the client can call and nothing else. That is the whole reason none of the machines sends
 * a payload to open its screen: everything the screen draws arrives over the ordinary container
 * synchroniser, so there is no custom packet to keep in step with the menu, and no window that can
 * be opened with a stale copy of the machine's state.
 *
 * <p><b>The Counter is the one exception, and it is about a position rather than about state.</b> A
 * shop's state is numbers, and numbers travel in the synchroniser; the names a Counter draws do
 * not, so it has a payload — see {@code CounterNetwork} — and its screen has to know <em>which</em>
 * Counter the names under its position belong to. Vanilla's open-screen packet carries no position,
 * so the Counter's type is registered through {@code IMenuTypeExtension} and opened with one, which
 * makes the loader send NeoForge's richer packet and hand the position to the factory below.
 *
 * <p>{@code DEFAULT_FLAGS} rather than the vanilla set, because a mod menu is not part of the
 * vanilla feature set and a menu registered with too narrow a flag set is refused by the client
 * rather than shown.
 */
public final class ModMenus {

  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(BuiltInRegistries.MENU, Alchemizer.MOD_ID);

  public static final DeferredHolder<MenuType<?>, MenuType<PressureChamberMenu>> PRESSURE_CHAMBER =
      MENUS.register(
          "pressure_chamber",
          () -> new MenuType<>(PressureChamberMenu::new, FeatureFlags.DEFAULT_FLAGS));

  public static final DeferredHolder<MenuType<?>, MenuType<AlchemicalTankMenu>> ALCHEMICAL_TANK =
      MENUS.register(
          "alchemical_tank",
          () -> new MenuType<>(AlchemicalTankMenu::new, FeatureFlags.DEFAULT_FLAGS));

  /**
   * The Counter's menu, which is the one type here opened with data attached.
   *
   * <p>{@code IMenuTypeExtension.create} rather than a plain constructor, because the client's menu
   * is handed the Counter's position along with the window — see this class's own note.
   */
  public static final DeferredHolder<MenuType<?>, MenuType<CounterMenu>> COUNTER =
      MENUS.register("counter", () -> IMenuTypeExtension.create(CounterMenu::new));

  /**
   * The customer dialogue's menu: two buttons and no slots.
   *
   * <p>Registered through the same extension as the Counter's, for a different reason. The Counter
   * needs it because its screen has to be told <em>which</em> block it is looking at; this one
   * needs it because it is opened for something that is not a block at all, and the extension's
   * factory is what lets a menu be built with something other than an inventory attached. The
   * client's side of it reads nothing from the buffer it is offered — see {@code
   * CustomerDialogueMenu} — but the shape is what the loader looks for.
   */
  public static final DeferredHolder<MenuType<?>, MenuType<CustomerDialogueMenu>>
      CUSTOMER_DIALOGUE =
          MENUS.register(
              "customer_dialogue", () -> IMenuTypeExtension.create(CustomerDialogueMenu::new));

  private ModMenus() {}
}
