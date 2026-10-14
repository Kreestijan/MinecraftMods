package io.github.kreestijan.alchemizer.client;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.client.screen.AlchemicalTankScreen;
import io.github.kreestijan.alchemizer.client.screen.IgnitionChamberScreen;
import io.github.kreestijan.alchemizer.client.screen.PressureChamberScreen;
import io.github.kreestijan.alchemizer.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Tells the client which screen to draw for each of the mod's menus.
 *
 * <p>Menus and screens are registered on the same bus but not on the same side, and this class is
 * the whole of the client half: the menus themselves are known to both sides, and only the picture
 * of them is client-only.
 *
 * <p>The {@code Dist.CLIENT} filter is required rather than decorative. This event is a mod bus
 * event, so without the filter it would be delivered in the dedicated server too, where {@code
 * AbstractContainerScreen} and everything below it do not exist. For the same reason this is the
 * mod's only client-only subscriber: one class to be sure about is worth more than several to
 * check. See {@code StartupProbe} for what a mis-scoped subscriber costs.
 *
 * <p>Registering nothing here is not a harmless omission. Every menu type is checked for a screen
 * at client startup, and a menu without one fails to open — the click reaches the server, the
 * window is created, and nothing appears to show it.
 */
@EventBusSubscriber(modid = Alchemizer.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {

  private ClientSetup() {}

  /**
   * @param event the client's chance to claim a menu, before any of them can be opened
   */
  @SubscribeEvent
  public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
    event.register(ModMenus.IGNITION_CHAMBER.get(), IgnitionChamberScreen::new);
    event.register(ModMenus.PRESSURE_CHAMBER.get(), PressureChamberScreen::new);
    event.register(ModMenus.ALCHEMICAL_TANK.get(), AlchemicalTankScreen::new);
  }
}
