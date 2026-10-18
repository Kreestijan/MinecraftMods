package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
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
        .add(ModBlocks.ALCHEMICAL_TANK.get())
        .add(ModBlocks.AIR_PRESSURE_VALVE.get())
        .add(ModBlocks.BELLOWS.get())
        .add(ModBlocks.FUNNEL.get())
        .add(ModBlocks.FLUE_STACK.get())
        .add(ModBlocks.RESONANCE_CRYSTAL.get())
        .add(ModBlocks.CONDENSER.get())
        .add(ModBlocks.ADVANCED_CONDENSER.get())
        .add(ModBlocks.ALCHEMICAL_TUBING.get());

    // The Counter is a wooden box, so it is an axe job rather than a pickaxe one.
    tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.COUNTER.get());

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
    tag(ModTags.AIR_PRESSURE_VALVE).add(ModBlocks.AIR_PRESSURE_VALVE.get());
    tag(ModTags.BELLOWS).add(ModBlocks.BELLOWS.get());
    tag(ModTags.FUNNEL).add(ModBlocks.FUNNEL.get());
    tag(ModTags.FLUE_STACK).add(ModBlocks.FLUE_STACK.get());
    tag(ModTags.RESONANCE_CRYSTAL).add(ModBlocks.RESONANCE_CRYSTAL.get());
    tag(ModTags.CONDENSER).add(ModBlocks.CONDENSER.get());
    tag(ModTags.ADVANCED_CONDENSER).add(ModBlocks.ADVANCED_CONDENSER.get());
    tag(ModTags.ALCHEMICAL_TUBING).add(ModBlocks.ALCHEMICAL_TUBING.get());

    // The ground a customer prefers to walk on. Vanilla's dirt path is the whole of it today, and
    // the mod ships no path block of its own: the point of the tag is that a shopkeeper lays down
    // whatever their world already has, and a datapack or another mod may add to it.
    tag(ModTags.SHOP_PATH_PREFERRED).add(Blocks.DIRT_PATH);
  }
}
