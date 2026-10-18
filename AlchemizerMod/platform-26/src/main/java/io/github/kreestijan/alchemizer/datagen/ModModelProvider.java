package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.block.IgnitionChamberBlock;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;

/**
 * Generates block states, block models and item models.
 *
 * <p>There is no block state provider in this version of Minecraft; the model generator took its
 * place and emits the {@code items/} definitions as well. That is why this class produces four
 * kinds of file from one place.
 *
 * <p>The generator validates as it goes: it refuses to finish if a block in this mod has no block
 * state, or an item in this mod has no model. Registering a block and forgetting its model is
 * therefore a {@code runData} failure rather than a purple-and-black cube in a player's world.
 *
 * <p>Only the declared blocks are generated. The base class would otherwise sweep the whole
 * registry by namespace, which happens to give the same answer today but would quietly start
 * generating models for anything else that ever lands in this namespace.
 */
public final class ModModelProvider extends ModelProvider {

  /**
   * @param output the generator's output root
   */
  public ModModelProvider(PackOutput output) {
    super(output, Alchemizer.MOD_ID);
  }

  @Override
  protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
    createIgnitionChamberModels(blockModels);

    // Top, bottom and side are mapped to three separate textures. The tank reads as a vessel rather
    // than as a cube because its rim, its base and its glass face are drawn differently, and a
    // single-texture cube cannot say that.
    blockModels.createTrivialBlock(ModBlocks.ALCHEMICAL_TANK.get(), TexturedModel.CUBE_TOP_BOTTOM);

    // A cube that turns with the way it faces, because the Counter has a front: its queue is drawn
    // straight out from the face the player placed toward them, so the block needs a block state
    // that says which way it is looking. This is vanilla's own helper for exactly that — one cube
    // model, dispatched with the y rotation each of the four horizontal facings needs — rather than
    // the hand-built dispatch the chamber above uses, which is the pattern for a block that has
    // more
    // than one model to switch between. The art is still one texture on all six faces, so the turn
    // is not visible yet; the model is rotated first so that the drawing of the front can be.
    blockModels.createHorizontallyRotatedBlock(ModBlocks.COUNTER.get(), TexturedModel.CUBE);

    // The same mapping as the tank, for the same reason: a lid, a base and a glass vessel are three
    // different pictures. Unlike the chamber it needs no second model, because it has no state to
    // switch on.
    blockModels.createTrivialBlock(ModBlocks.PRESSURE_CHAMBER.get(), TexturedModel.CUBE_TOP_BOTTOM);

    // The still parts. A single texture on all six faces is honest for all of them while the art is
    // placeholder, and it is the shape these blocks will keep: a valve, a bellows and a condenser
    // are metal boxes with a picture on them. Tubing is here too, and will stop being a cube when
    // someone draws the connected pipe model — see the note on the block.
    blockModels.createTrivialBlock(ModBlocks.AIR_PRESSURE_VALVE.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.BELLOWS.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.FUNNEL.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.FLUE_STACK.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.RESONANCE_CRYSTAL.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.CONDENSER.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.ADVANCED_CONDENSER.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.ALCHEMICAL_TUBING.get(), TexturedModel.CUBE);

    itemModels.generateFlatItem(ModItems.ALCHEMICAL_COAL.get(), ModelTemplates.FLAT_ITEM);
    itemModels.generateFlatItem(ModItems.ENHANCED_BLAZE_ROD.get(), ModelTemplates.FLAT_ITEM);

    // A brew's failure output, drawn like any other item: a flask on a plain background. Listed
    // here rather than inferred because the generator only derives a model for a *block* item — a
    // plain item with no entry here is exactly the "missing item model definitions" failure this
    // generator raises.
    itemModels.generateFlatItem(ModItems.MURKY_MESS.get(), ModelTemplates.FLAT_ITEM);

    // The block items' models are not registered here on purpose. The generator derives each one
    // from its block's model automatically, and registering them as well would be a duplicate
    // definition error rather than a harmless override.
  }

  /**
   * The chamber's two models and the switch between them.
   *
   * <p>Written out rather than taken from {@code createTrivialBlock} because a trivial block gets
   * no block state to switch on, and this block has one. The shape is the same one vanilla uses for
   * a furnace: a base model from the standard top-bottom-side mapping, a second model identical to
   * it but with the side texture swapped for the glowing one, and a boolean dispatch between them.
   *
   * @param blockModels the generator whose model and block state outputs these are written to
   */
  private static void createIgnitionChamberModels(BlockModelGenerators blockModels) {
    Block chamber = ModBlocks.IGNITION_CHAMBER.get();
    TexturedModel.Provider shape = TexturedModel.CUBE_TOP_BOTTOM;

    MultiVariant cold =
        BlockModelGenerators.plainVariant(shape.create(chamber, blockModels.modelOutput));

    // Only the side lights up. The top is a plate and the bottom is against whatever the chamber
    // stands on, so neither has anywhere for a glow to be seen.
    MultiVariant lit =
        BlockModelGenerators.plainVariant(
            shape
                .get(chamber)
                .updateTextures(
                    textures ->
                        textures.put(
                            TextureSlot.SIDE, TextureMapping.getBlockTexture(chamber, "_side_lit")))
                .createWithSuffix(chamber, "_lit", blockModels.modelOutput));

    blockModels.blockStateOutput.accept(
        MultiVariantGenerator.dispatch(chamber)
            .with(
                BlockModelGenerators.createBooleanModelDispatch(
                    IgnitionChamberBlock.LIT, lit, cold)));
  }
}
