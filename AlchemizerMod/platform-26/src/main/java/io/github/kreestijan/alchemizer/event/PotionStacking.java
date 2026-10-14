package io.github.kreestijan.alchemizer.event;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Lets identical potions share a stack, in place of vanilla's one.
 *
 * <p>Vanilla gives {@code minecraft:potion} a stack size of one. That is a rule about the item, not
 * about this mod's machine, so overriding it here changes potions everywhere — a chest, a brewing
 * stand, a player's hand — which is what was asked for and what makes the mod's own output
 * collectable in the first place. Only {@code minecraft:potion} is touched: splash and lingering
 * potions keep vanilla's limit, and this mod brews neither.
 *
 * <p><b>Why this is safe to do globally.</b> The obvious worry is a container that cannot hold a
 * stack being handed one anyway, and there is exactly one such container that matters: the brewing
 * stand's three potion slots. It cannot be a problem, because a brewing stand cannot hold more than
 * one potion in a slot regardless of what the item says. {@code BrewingStandMenu.PotionSlot}
 * overrides {@code getMaxStackSize} to return one, so a player can neither place nor shift-click a
 * stack into it, and {@code BrewingStandBlockEntity.canPlaceItem} additionally requires the slot to
 * be <em>empty</em> — which is also the method a hopper goes through, and a hopper moves one item
 * at a time, so it fills a slot once and then stops. The stand's brewing logic replaces a slot's
 * contents with a count-one stack, so nothing is destroyed by a stack it can never receive.
 *
 * <p><b>Drinking a stack loses nothing.</b> {@code Consumable} shrinks the stack by one and then
 * {@code ItemStack.finishUsingItem} runs the remainder conversion against the count from
 * <em>before</em> the drink. Sixty-four potions drunk one at a time therefore leave sixty-three
 * potions and one glass bottle, through {@code UseRemainder} — the same component that makes a
 * vanilla single potion leave a bottle. The one exception is creative mode, where no bottle is
 * produced because nothing was consumed.
 *
 * <p><b>When the value is read.</b> Not when this listener is called. {@code modify} only records
 * an initializer; NeoForge runs it later, while item components are being bound from the
 * registries, which is after the config file has been read. Reading {@link ConfigCache} inside the
 * lambda body therefore sees the player's setting, whereas reading it in this method would see the
 * shipping default. If the config had not loaded yet the cache answers with that same default
 * rather than throwing, so the worst case is a stack of sixty-four instead of an error.
 *
 * <p>Registered by hand from {@link Alchemizer} rather than with {@code @EventBusSubscriber}, for
 * the reason {@code StartupProbe} gives. This one is on the mod bus, because binding an item's
 * components is a registration-time concern.
 */
public final class PotionStacking {

  private PotionStacking() {}

  /**
   * @param event the item default components, open for this mod to patch
   */
  public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
    // The config is read inside the lambda, not here: the lambda runs when the potion's components
    // are bound, which is the first moment the answer is known to be the player's rather than the
    // shipping default. `context` and `item` are ignored because neither is needed to answer.
    event.modify(
        Items.POTION,
        (components, context, item) ->
            components.set(DataComponents.MAX_STACK_SIZE, ConfigCache.current().potionStackSize()));
  }
}
