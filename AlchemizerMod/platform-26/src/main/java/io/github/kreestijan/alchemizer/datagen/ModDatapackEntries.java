package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.effect.EffectId;
import io.github.kreestijan.alchemizer.core.effect.EffectSpec;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.recipe.SlotMatcher;
import io.github.kreestijan.alchemizer.defs.BrewRecipeDef;
import io.github.kreestijan.alchemizer.defs.EffectValueDef;
import io.github.kreestijan.alchemizer.defs.ModRegistries;
import io.github.kreestijan.alchemizer.defs.PotionDef;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The mod's own content for the three datapack registries, written out by {@code runData}.
 *
 * <p>This is not itself a {@link net.minecraft.data.DataProvider}. The registry entries are written
 * by {@link net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider}, which the gather
 * event creates from a builder; what this class holds is that builder, so that the entries live
 * next to the codec'd shapes they are made of rather than inline in an event subscriber.
 *
 * <p><b>What is shipped here is a starting set, not the mod's content.</b> Every entry below is one
 * a player could have written by hand, and any of them can be overridden or dropped by a datapack
 * without touching this file. The point of generating them is that the shipped set cannot drift
 * from the codecs: a field renamed in {@code PotionDef} breaks this file at compile time, where a
 * hand-written JSON would go on quietly failing to load.
 *
 * <p>The two potions are the ones the still's own tests brew, plus one that carries an effect so
 * that the effect half of the loop is exercised by something a player can make. Everything else a
 * still can brew arrives as datapack data.
 */
public final class ModDatapackEntries {

  /** The {@code minecraft:speed} effect, which the swiftness recipe's potion carries. */
  private static final String SPEED = "minecraft:speed";

  /** What speed is worth before potency and duration are applied. */
  private static final int SPEED_BASE_VALUE = 12;

  /** The effect of the swiftness potion, at the weakest potency, for three minutes. */
  private static final int SWIFTNESS_DURATION_TICKS = 3600;

  /**
   * The registries this mod generates, and what goes in them.
   *
   * <p>Built once as a constant so that the event subscriber stays a list of providers. The order
   * of the {@code add} calls is the order the entries are written in, and nothing depends on it: a
   * recipe names its potion by id rather than by holder, so no registry here has to be written
   * before another.
   */
  public static final RegistrySetBuilder BUILDER =
      new RegistrySetBuilder()
          .add(ModRegistries.EFFECT_VALUE, ModDatapackEntries::effectValues)
          .add(ModRegistries.POTION, ModDatapackEntries::potions)
          .add(ModRegistries.BREW_RECIPE, ModDatapackEntries::brewRecipes);

  private ModDatapackEntries() {}

  /**
   * @param context the registry being generated
   */
  private static void effectValues(BootstrapContext<EffectValueDef> context) {
    context.register(effectValue("speed"), new EffectValueDef(SPEED_BASE_VALUE));
  }

  /**
   * @param context the registry being generated
   */
  private static void potions(BootstrapContext<PotionDef> context) {
    // The Test Potion: drinkable, does nothing. Its empty effect list is the whole point of it, and
    // it is unlocked by default so that it keeps working once discovery gates the rest.
    context.register(potion("test_potion"), new PotionDef(List.of(), true));

    context.register(
        potion("swiftness"),
        new PotionDef(List.of(spec(SPEED, 0, SWIFTNESS_DURATION_TICKS)), false));
  }

  /**
   * @param context the registry being generated
   */
  private static void brewRecipes(BootstrapContext<BrewRecipeDef> context) {
    context.register(
        recipe("test_potion"),
        new BrewRecipeDef(
            List.of(item("minecraft:spider_eye"), item("minecraft:dandelion")),
            HourglassPreset.FIVE,
            potionId("test_potion"),
            1));

    context.register(
        recipe("swiftness"),
        new BrewRecipeDef(
            List.of(item("minecraft:nether_wart"), item("minecraft:sugar")),
            HourglassPreset.FIFTEEN,
            potionId("swiftness"),
            1));
  }

  /**
   * @param effect the effect's namespaced id
   * @param potency the amplifier, before the game's own clamp
   * @param durationTicks how long it lasts; zero for an instant effect
   * @return one effect of a potion
   */
  private static EffectSpec spec(String effect, int potency, int durationTicks) {
    return new EffectSpec(new EffectId(effect), potency, durationTicks);
  }

  /**
   * @param id the item's namespaced id
   * @return a slot that accepts that item and nothing else
   */
  private static SlotMatcher item(String id) {
    return new SlotMatcher.ByItem(id);
  }

  /**
   * @param path the entry's path within its registry
   * @return the potion's id, which is what a brew recipe names it by and what its name key is built
   *     from
   */
  private static Identifier potionId(String path) {
    return Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, path);
  }

  /**
   * @param path the entry's path within its registry
   * @return a potion registry key
   */
  private static ResourceKey<PotionDef> potion(String path) {
    return key(ModRegistries.POTION, path);
  }

  /**
   * @param path the entry's path within its registry
   * @return a brew recipe registry key
   */
  private static ResourceKey<BrewRecipeDef> recipe(String path) {
    return key(ModRegistries.BREW_RECIPE, path);
  }

  private static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
    return ResourceKey.create(registry, potionId(path));
  }

  /**
   * @param path the entry's path within its registry
   * @return a key in an effect value registry
   */
  private static ResourceKey<EffectValueDef> effectValue(String path) {
    return key(ModRegistries.EFFECT_VALUE, path);
  }
}
