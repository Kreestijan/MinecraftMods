package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.block.AlchemicalTubingBlock;
import io.github.kreestijan.alchemizer.brewing.block.IgnitionChamberBlock;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModItems;
import java.util.Set;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplate;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
import net.neoforged.neoforge.client.model.generators.template.FaceBuilder;

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

  /** Vanilla's block parent, for the standard display transforms and side lighting. */
  private static final Identifier BLOCK_PARENT = Identifier.withDefaultNamespace("block/block");

  /**
   * One pipe arm, authored toward -Z. Five faces: the four sides and the outer end, which is culled
   * against whatever it meets. The inner end is open; the cap on the far side of the core closes a
   * dead end, and an arm continuing through the core closes a run.
   */
  private static final ExtendedModelTemplate TUBING_ARM =
      ExtendedModelTemplateBuilder.builder()
          .parent(BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.TEXTURE)
          .element(
              element ->
                  element
                      .from(6.0F, 6.0F, 0.0F)
                      .to(10.0F, 10.0F, 6.0F)
                      .allFacesExcept((direction, face) -> fullFace(face), Set.of(Direction.SOUTH))
                      .face(Direction.NORTH, face -> fullFace(face).cullface(Direction.NORTH)))
          .build();

  /**
   * The face that closes the core where no arm leaves it. A hairline plate rather than a zero-width
   * element, because the baker needs a volume before it will give the quad a side.
   */
  private static final ExtendedModelTemplate TUBING_CAP =
      ExtendedModelTemplateBuilder.builder()
          .parent(BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.TEXTURE)
          .element(
              element ->
                  element
                      .from(6.0F, 6.0F, 5.99F)
                      .to(10.0F, 10.0F, 6.0F)
                      .face(Direction.NORTH, ModModelProvider::fullFace))
          .build();

  /** The straight run used as the item icon: the core and two arms on the Z axis. */
  private static final ExtendedModelTemplate TUBING_INVENTORY =
      ExtendedModelTemplateBuilder.builder()
          .parent(BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.TEXTURE)
          .element(
              element ->
                  element
                      .from(6.0F, 6.0F, 6.0F)
                      .to(10.0F, 10.0F, 10.0F)
                      .allFaces((direction, face) -> fullFace(face)))
          .element(
              element ->
                  element
                      .from(6.0F, 6.0F, 0.0F)
                      .to(10.0F, 10.0F, 6.0F)
                      .allFacesExcept((direction, face) -> fullFace(face), Set.of(Direction.SOUTH)))
          .element(
              element ->
                  element
                      .from(6.0F, 6.0F, 10.0F)
                      .to(10.0F, 10.0F, 16.0F)
                      .allFacesExcept((direction, face) -> fullFace(face), Set.of(Direction.NORTH)))
          .build();

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

    // The still parts. A single texture on all six faces is honest for all of them: a valve, a
    // bellows and a condenser are metal boxes with a picture on them. Tubing is not one of these;
    // it has a connected shape and is generated separately below.
    blockModels.createTrivialBlock(ModBlocks.AIR_PRESSURE_VALVE.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.BELLOWS.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.FUNNEL.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.FLUE_STACK.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.RESONANCE_CRYSTAL.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.CONDENSER.get(), TexturedModel.CUBE);
    blockModels.createTrivialBlock(ModBlocks.ADVANCED_CONDENSER.get(), TexturedModel.CUBE);
    createAlchemicalTubingModels(blockModels);

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
   * The tubing's models and the multipart switch between them.
   *
   * <p>One arm and one cap cover every side. Both are authored pointing north, and the block-state
   * rotations do the rest: y for the four horizontal sides, x for up and down. The cap is a single
   * north-facing plate at the near face of the 4x4x4 core. It is drawn when that side has no arm,
   * and six of them together are the core itself — which is why there is no separate core model.
   *
   * <p>The arm has no face on its inner end, and its outer end is culled, so a run of pipes reads
   * as one hollow tube rather than a row of closed boxes. Every quad is translucent because the
   * texture is; {@link TextureMapping#forceAllTranslucent()} also says so in the generated JSON, so
   * that replacing the art with something opaque cannot silently take the pipe out of the
   * translucent pass.
   *
   * @param blockModels the generator whose model and block state outputs these are written to
   */
  private static void createAlchemicalTubingModels(BlockModelGenerators blockModels) {
    Block tubing = ModBlocks.ALCHEMICAL_TUBING.get();
    Material texture = TextureMapping.getBlockTexture(tubing);
    TextureMapping mapping =
        TextureMapping.singleSlot(TextureSlot.TEXTURE, texture)
            .putForced(TextureSlot.PARTICLE, texture)
            .forceAllTranslucent();

    MultiVariant arm =
        BlockModelGenerators.plainVariant(
            TUBING_ARM.createWithSuffix(tubing, "_arm", mapping, blockModels.modelOutput));
    MultiVariant cap =
        BlockModelGenerators.plainVariant(
            TUBING_CAP.createWithSuffix(tubing, "_cap", mapping, blockModels.modelOutput));

    // The item model is the straight run through the block. The block item's definition is
    // generated from the block's default model location automatically, so this name has to remain
    // block/alchemical_tubing.
    TUBING_INVENTORY.create(tubing, mapping, blockModels.modelOutput);

    blockModels.blockStateOutput.accept(
        MultiPartGenerator.multiPart(tubing)
            .with(condition(AlchemicalTubingBlock.NORTH, true), arm)
            .with(
                condition(AlchemicalTubingBlock.EAST, true),
                arm.with(BlockModelGenerators.Y_ROT_90))
            .with(
                condition(AlchemicalTubingBlock.SOUTH, true),
                arm.with(BlockModelGenerators.Y_ROT_180))
            .with(
                condition(AlchemicalTubingBlock.WEST, true),
                arm.with(BlockModelGenerators.Y_ROT_270))
            .with(
                condition(AlchemicalTubingBlock.UP, true), arm.with(BlockModelGenerators.X_ROT_270))
            .with(
                condition(AlchemicalTubingBlock.DOWN, true),
                arm.with(BlockModelGenerators.X_ROT_90))
            .with(condition(AlchemicalTubingBlock.NORTH, false), cap)
            .with(
                condition(AlchemicalTubingBlock.EAST, false),
                cap.with(BlockModelGenerators.Y_ROT_90))
            .with(
                condition(AlchemicalTubingBlock.SOUTH, false),
                cap.with(BlockModelGenerators.Y_ROT_180))
            .with(
                condition(AlchemicalTubingBlock.WEST, false),
                cap.with(BlockModelGenerators.Y_ROT_270))
            .with(
                condition(AlchemicalTubingBlock.UP, false),
                cap.with(BlockModelGenerators.X_ROT_270))
            .with(
                condition(AlchemicalTubingBlock.DOWN, false),
                cap.with(BlockModelGenerators.X_ROT_90)));
  }

  /**
   * Points a face at the whole texture. Without this the baker derives UVs from the element's place
   * in the block, so a 4-pixel pipe face would sample only the middle 4x4 of the texture and the
   * metal frame drawn around its edge would never be seen.
   *
   * @param face the face to map
   * @return the same face, now texture-mapped across all 16 pixels
   */
  private static FaceBuilder fullFace(FaceBuilder face) {
    return face.texture(TextureSlot.TEXTURE).uvs(0.0F, 0.0F, 16.0F, 16.0F);
  }

  /**
   * @param property a tubing connection property
   * @param value whether that side has an arm
   * @return the multipart condition for it
   */
  private static ConditionBuilder condition(BooleanProperty property, boolean value) {
    return new ConditionBuilder().term(property, value);
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
