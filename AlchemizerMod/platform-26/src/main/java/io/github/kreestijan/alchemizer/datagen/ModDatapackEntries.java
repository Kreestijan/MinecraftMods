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
 * <p>The five potions are a starting set with a shape to it rather than a catalogue: the Test
 * Potion, which the still's own tests brew and which carries no effect at all, and then one potion
 * per rung of the fuel ladder, each brewed at the tier whose fire it asks for. That is what makes
 * the gate visible — a player who brews the strength recipe and then tries the fire resistance one
 * on the same coal is told, by the machine refusing to move, that the fire is what is short.
 * Everything else a still can brew arrives as datapack data.
 */
public final class ModDatapackEntries {

  /** The {@code minecraft:speed} effect, which the swiftness recipe's potion carries. */
  private static final String SPEED = "minecraft:speed";

  /** The {@code minecraft:strength} effect, which the tier two recipe's potion carries. */
  private static final String STRENGTH = "minecraft:strength";

  /** The {@code minecraft:fire_resistance} effect, which the tier three recipe's potion carries. */
  private static final String FIRE_RESISTANCE = "minecraft:fire_resistance";

  /** The {@code minecraft:regeneration} effect, which the tier four recipe's potion carries. */
  private static final String REGENERATION = "minecraft:regeneration";

  /** What speed is worth before potency and duration are applied. */
  private static final int SPEED_BASE_VALUE = 12;

  /** What strength is worth. Priced above speed: it decides a fight, where speed decides a walk. */
  private static final int STRENGTH_BASE_VALUE = 16;

  /** What fire resistance is worth, which is a great deal to anyone standing in lava. */
  private static final int FIRE_RESISTANCE_BASE_VALUE = 24;

  /**
   * What regeneration is worth, which is the most of anything here.
   *
   * <p>The top of the ladder is also where the pricing stops being about a number of seconds and
   * starts being about what the effect is for; this and the duration below are the pair that says
   * so, and they are a placeholder like every other number in this file.
   */
  private static final int REGENERATION_BASE_VALUE = 32;

  /** The effect of the swiftness potion, at the weakest potency, for three minutes. */
  private static final int SWIFTNESS_DURATION_TICKS = 3600;

  /** The same three minutes for strength, at the same potency. */
  private static final int STRENGTH_DURATION_TICKS = 3600;

  /** And for fire resistance: long enough to cross the thing it is for and come back. */
  private static final int FIRE_RESISTANCE_DURATION_TICKS = 3600;

  /**
   * Regeneration is priced the other way round — the shortest of the four, because it is worth the
   * most. Fifteen seconds of it undoes a mistake; three minutes of it would undo a fight.
   */
  private static final int REGENERATION_DURATION_TICKS = 900;

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
    context.register(effectValue("strength"), new EffectValueDef(STRENGTH_BASE_VALUE));
    context.register(
        effectValue("fire_resistance"), new EffectValueDef(FIRE_RESISTANCE_BASE_VALUE));
    context.register(effectValue("regeneration"), new EffectValueDef(REGENERATION_BASE_VALUE));
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

    // One potion per tier above the first, so that the fuel gate is a thing a player can watch
    // rather than a thing this code asserts. Like the machine recipes, they are placeholders: the
    // effects are real vanilla ones and the numbers are invented.
    //
    // None of them is unlocked by default, which today changes nothing — discovery is not
    // implemented, so every recipe is known. The flag is authored now because it is the one thing
    // discovery will read, and writing it with the potion is cheaper than coming back to add it.
    context.register(
        potion("strength"),
        new PotionDef(List.of(spec(STRENGTH, 0, STRENGTH_DURATION_TICKS)), false));

    context.register(
        potion("fire_resistance"),
        new PotionDef(List.of(spec(FIRE_RESISTANCE, 0, FIRE_RESISTANCE_DURATION_TICKS)), false));

    context.register(
        potion("regeneration"),
        new PotionDef(List.of(spec(REGENERATION, 0, REGENERATION_DURATION_TICKS)), false));
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

    // The tier two, three and four recipes. Each names, in its ingredients, the fuel its tier
    // burns: redstone for Alchemical Coal, blaze powder for a blaze rod, a ghast's tear for the
    // rung above it. That is the only place a player can read what a recipe is about to ask of the
    // fire, and a recipe that refused to run with no way to have known would be the machine being
    // cryptic rather than demanding.
    //
    // The times climb with the tiers and so do the numbers. They are placeholders in exactly the
    // sense the machine recipes are, and the CHANGELOG says so.
    context.register(
        recipe("strength"),
        new BrewRecipeDef(
            List.of(item("minecraft:glowstone_dust"), item("minecraft:redstone")),
            HourglassPreset.FIFTEEN,
            potionId("strength"),
            2));

    context.register(
        recipe("fire_resistance"),
        new BrewRecipeDef(
            List.of(item("minecraft:magma_cream"), item("minecraft:blaze_powder")),
            HourglassPreset.THIRTY,
            potionId("fire_resistance"),
            3));

    context.register(
        recipe("regeneration"),
        new BrewRecipeDef(
            List.of(item("minecraft:ghast_tear"), item("minecraft:glistering_melon_slice")),
            HourglassPreset.FORTY_FIVE,
            potionId("regeneration"),
            4));
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
