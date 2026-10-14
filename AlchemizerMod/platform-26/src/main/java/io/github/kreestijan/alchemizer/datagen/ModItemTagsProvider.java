package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.common.fuel.FuelTags;
import io.github.kreestijan.alchemizer.core.fuel.FuelTier;
import io.github.kreestijan.alchemizer.registry.ModItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

/**
 * Generates the fuel ladder.
 *
 * <p>These five files are the entire fuel system as far as the game is concerned. The mod's code
 * asks {@link FuelTags} what tier a stack is worth and never looks at an item id; the answer comes
 * from here. A datapack that adds an item to one of these tags moves it up or down the ladder
 * without anything being recompiled, and a modpack author who thinks their fuel is worth tier three
 * can say so.
 *
 * <p>The tag keys come from {@link FuelTags} rather than being spelled out, so a tag this provider
 * writes and a tag the game reads cannot be two different tags.
 */
public final class ModItemTagsProvider extends ItemTagsProvider {

  /**
   * @param output the generator's output root
   * @param lookupProvider the registry lookup the generator is running against
   */
  public ModItemTagsProvider(
      PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    super(output, lookupProvider, Alchemizer.MOD_ID);
  }

  @Override
  protected void addTags(HolderLookup.Provider registries) {
    // The vanilla items are named through Items rather than net.minecraft.references.ItemIds, and
    // the appender is handed the item rather than a registry key. Both are 26.1 facts: ItemIds here
    // holds nothing but pumpkin and melon seeds, and IntrinsicHolderTagsProvider.tag returns the
    // value-taking TagAppender. 26.2 changed both, so a port touches exactly these calls.
    //
    // Tier zero: wood and the things made of it. It burns — the vanilla fuel table gives all of
    // these a burn time — and it can never power a brew, because every recipe asks for at least
    // tier one. That is the design's teaching moment, and it only works if the player's obvious
    // first guess is accepted rather than refused.
    //
    // Vanilla also burns wool, saplings, dried kelp and a few odds and ends. They are deliberately
    // left off the ladder: they are not wood, and the design's tier zero is wood.
    tag(FuelTags.tagOf(FuelTier.WOOD))
        .addTag(ItemTags.LOGS)
        .addTag(ItemTags.PLANKS)
        .addTag(ItemTags.WOODEN_STAIRS)
        .addTag(ItemTags.WOODEN_SLABS)
        .addTag(ItemTags.WOODEN_TRAPDOORS)
        .addTag(ItemTags.WOODEN_FENCES)
        .addTag(ItemTags.WOODEN_DOORS)
        .addTag(ItemTags.WOODEN_BUTTONS)
        .addTag(ItemTags.WOODEN_PRESSURE_PLATES)
        .add(Items.STICK);

    // A coal block is not here on purpose. The design's tier one is "coal, charcoal"; a block of
    // coal is nine of them, and whether nine coal's worth of fuel should reach further than the
    // tier that coal reaches is a question the design does not answer.
    tag(FuelTags.tagOf(FuelTier.COAL)).add(Items.COAL).add(Items.CHARCOAL);

    tag(FuelTags.tagOf(FuelTier.ALCHEMICAL_COAL)).add(ModItems.ALCHEMICAL_COAL.get());

    tag(FuelTags.tagOf(FuelTier.BLAZE_ROD)).add(Items.BLAZE_ROD);

    // Tier four exists and is empty. An empty file is the honest way to say "the ladder has this
    // rung and nothing is on it yet": the Enhanced Blaze Rod that belongs here is a later tier's
    // fuel, and writing the tag now means adding it later is one line in one file.
    tag(FuelTags.tagOf(FuelTier.ENHANCED_BLAZE_ROD));
  }
}
