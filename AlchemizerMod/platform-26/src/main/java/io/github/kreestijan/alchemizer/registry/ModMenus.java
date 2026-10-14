package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.menu.AlchemicalTankMenu;
import io.github.kreestijan.alchemizer.brewing.menu.IgnitionChamberMenu;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's container menus.
 *
 * <p>Every menu here is built through the plain constructor, which takes the two-argument factory
 * the client can call and nothing else. That is the whole reason none of the three machines sends a
 * payload to open its screen: everything the screen draws arrives over the ordinary container
 * synchroniser, so there is no custom packet to keep in step with the menu, and no window that can
 * be opened with a stale copy of the machine's state.
 *
 * <p>{@code DEFAULT_FLAGS} rather than the vanilla set, because a mod menu is not part of the
 * vanilla feature set and a menu registered with too narrow a flag set is refused by the client
 * rather than shown.
 */
public final class ModMenus {

  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(BuiltInRegistries.MENU, Alchemizer.MOD_ID);

  public static final DeferredHolder<MenuType<?>, MenuType<IgnitionChamberMenu>> IGNITION_CHAMBER =
      MENUS.register(
          "ignition_chamber",
          () -> new MenuType<>(IgnitionChamberMenu::new, FeatureFlags.DEFAULT_FLAGS));

  public static final DeferredHolder<MenuType<?>, MenuType<PressureChamberMenu>> PRESSURE_CHAMBER =
      MENUS.register(
          "pressure_chamber",
          () -> new MenuType<>(PressureChamberMenu::new, FeatureFlags.DEFAULT_FLAGS));

  public static final DeferredHolder<MenuType<?>, MenuType<AlchemicalTankMenu>> ALCHEMICAL_TANK =
      MENUS.register(
          "alchemical_tank",
          () -> new MenuType<>(AlchemicalTankMenu::new, FeatureFlags.DEFAULT_FLAGS));

  private ModMenus() {}
}
