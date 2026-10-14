package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's creative tab.
 *
 * <p>The tab's contents are filled by the generator passed to {@code displayItems} rather than by a
 * {@code BuildCreativeModeTabContentsEvent} handler. The event is the escape hatch for adding to
 * another mod's tab; for this mod's own tab the generator is the shorter path and it keeps the
 * tab's contents visible in one place instead of spread over an event subscriber.
 *
 * <p>The generator is a lambda over the item registry, so an item added in a later milestone shows
 * up here as soon as it is registered, with no change to this file.
 */
public final class ModCreativeTabs {

  public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Alchemizer.MOD_ID);

  public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ALCHEMIZER =
      CREATIVE_TABS.register(
          "alchemizer",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup." + Alchemizer.MOD_ID))
                  .icon(() -> new ItemStack(ModItems.ALCHEMICAL_TANK.get()))
                  .displayItems(
                      (parameters, output) -> {
                        output.accept(ModItems.IGNITION_CHAMBER.get());
                        output.accept(ModItems.PRESSURE_CHAMBER.get());
                        output.accept(ModItems.ALCHEMICAL_TANK.get());
                        output.accept(ModItems.ALCHEMICAL_COAL.get());
                        output.accept(ModItems.MURKY_MESS.get());
                      })
                  .build());

  private ModCreativeTabs() {}
}
