package io.github.kreestijan.alchemizer.defs;

import io.github.kreestijan.alchemizer.Alchemizer;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The three registries this mod reads its content from.
 *
 * <p>None of them holds game objects. They hold <em>definitions</em> — what an effect is worth,
 * what a potion is made of, what ingredients brew it — which is what makes adding a potion a data
 * change rather than a code change. The registries themselves are the mechanism; the content is
 * anyone's.
 *
 * <p>Files are read from {@code data/<datapack namespace>/alchemizer/<registry name>/}, so a
 * datapack adds to this mod's content by using its own namespace in the folder's <em>first</em>
 * segment and this mod's in the second.
 *
 * <p><b>All three are registered without a network codec, which is deliberate.</b> Data is not sent
 * to clients. Brewing happens on the server and nowhere else — a client reads a chest, not a tank —
 * so a client that has this mod but not the server's datapacks needs to know nothing about them,
 * and a server running datapack potions does not require its players to install anything. The cost
 * is that the client cannot name a brewed potion by anything but the translation key the potion id
 * spells, which is exactly how the potion item is built anyway.
 */
public final class ModRegistries {

  /** What one mob effect is worth. Named after the effect. */
  public static final ResourceKey<Registry<EffectValueDef>> EFFECT_VALUE = key("effect_value");

  /** What a potion is made of. Named after the potion. */
  public static final ResourceKey<Registry<PotionDef>> POTION = key("potion");

  /** What ingredients brew what, at which hourglass setting. */
  public static final ResourceKey<Registry<BrewRecipeDef>> BREW_RECIPE = key("brew_recipe");

  private ModRegistries() {}

  /**
   * @param event the datapack registries, open for this mod to add to
   */
  public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
    event.dataPackRegistry(EFFECT_VALUE, EffectValueDef.CODEC);
    event.dataPackRegistry(POTION, PotionDef.CODEC);
    event.dataPackRegistry(BREW_RECIPE, BrewRecipeDef.CODEC);
  }

  private static <T> ResourceKey<Registry<T>> key(String path) {
    return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, path));
  }
}
