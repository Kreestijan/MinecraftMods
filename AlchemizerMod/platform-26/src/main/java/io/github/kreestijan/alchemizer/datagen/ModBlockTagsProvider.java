package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/**
 * Generates the mod's block tags, and its claims on Minecraft's own.
 *
 * <p>The class extended here is NeoForge's, not the identically-named one some tutorials import
 * from Minecraft: the vanilla package no longer ships a plain block tag provider. The NeoForge one
 * takes the mod id explicitly, which is what stops it from sweeping in another mod's blocks.
 *
 * <p>Two kinds of tag are written here. The mining tag is a claim on Minecraft's own, and it is
 * what makes a machine block drop when it is broken with a pickaxe rather than vanish. The
 * machine-part tags are this mod's own, one per still part, and they are the machine's whole
 * definition — see {@link ModTags}.
 */
public final class ModBlockTagsProvider extends BlockTagsProvider {

  /**
   * @param output the generator's output root
   * @param lookupProvider the registry lookup the generator is running against
   */
  public ModBlockTagsProvider(
      PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    super(output, lookupProvider, Alchemizer.MOD_ID);
  }

  @Override
  protected void addTags(HolderLookup.Provider registries) {
    // The appender takes the block. 26.2 kept only the ResourceKey overload, so a port passes
    // getKey() here instead; the tag this writes is the same either way.
    tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .add(ModBlocks.IGNITION_CHAMBER.get())
        .add(ModBlocks.PRESSURE_CHAMBER.get())
        .add(ModBlocks.ALCHEMICAL_TANK.get());

    // One tag per still part. These are the machine's definition: the validator looks up which
    // block ids are in each tag and never asks what a block is called. Each tag holds exactly one
    // block today, and that is not the point — a datapack or another mod adds a line here and its
    // block becomes a still part, with no code change and nothing recompiled.
    //
    // The keys come from ModTags rather than being spelled out, so the tag written here and the tag
    // read at runtime cannot become two different tags. That failure is silent: the tags would load
    // and match nothing, and no still would ever form.
    tag(ModTags.IGNITION_CHAMBER).add(ModBlocks.IGNITION_CHAMBER.get());
    tag(ModTags.PRESSURE_CHAMBER).add(ModBlocks.PRESSURE_CHAMBER.get());
    tag(ModTags.ALCHEMICAL_TANK).add(ModBlocks.ALCHEMICAL_TANK.get());
  }
}
