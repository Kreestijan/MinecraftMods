package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.shop.ShopCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own data components — of which there is one, and it is the Counter's shop id.
 *
 * <p><b>Why the Counter needed a component rather than the tank's trick.</b> The Alchemical Tank
 * carries its water by writing its block-entity save data onto the dropped item, which works and is
 * the reason its loot table is deliberately empty. That is the right shape when the payload is
 * "this block entity's state" — an opaque bag of whatever the block happens to save. A shop id is
 * not that: it is one fact about the <em>item</em>, it is the key a shop's whole durable state is
 * filed under, and it wants to be validated rather than copied. Registered as a component, vanilla
 * copies it through the loot table and puts it back on placement without either half being
 * hand-written — see {@code CounterBlock} for where those two hooks land.
 *
 * <p>{@code cacheEncoding} because the value is read on every tooltip, drop and placement and is a
 * short immutable string; {@code persistent} alone would re-encode it each time for no reason.
 */
public final class ModDataComponents {

  public static final DeferredRegister.DataComponents DATA_COMPONENTS =
      DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Alchemizer.MOD_ID);

  /**
   * Which shop a Counter item belongs to.
   *
   * <p>Absent is meaningful and is the ordinary case: a Counter crafted a moment ago belongs to no
   * shop yet, and gets one the first time it is placed. There is deliberately no "blank" id — an
   * item carrying no component and an item carrying an empty one would otherwise be two spellings
   * of the same thing, and only one of them can be a key.
   */
  public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShopId>> SHOP_ID =
      DATA_COMPONENTS.registerComponentType(
          "shop_id", builder -> builder.persistent(ShopCodecs.SHOP_ID).cacheEncoding());

  private ModDataComponents() {}
}
