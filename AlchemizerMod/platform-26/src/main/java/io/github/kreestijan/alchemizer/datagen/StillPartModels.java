package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.brewing.block.BellowsBlock;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplate;

/**
 * The seven still parts' texture mappings and their registration.
 *
 * <p>Each part names the sprites its model samples. The valve, crystal and flue are one texture on
 * every face; the bellows adds a mount, the funnel an inside and a top, the condensers a cap and a
 * glass band. The two condensers share the basic condenser's glass, so the band reads the same on
 * both, and only that material is forced translucent — the metal around it stays opaque.
 *
 * <p>{@code TexturedModel.createDefault} keeps the model at the block's default location, which is
 * where the auto-generated block-item definition looks for it. That is why the bellows can go
 * through {@code createHorizontallyRotatedBlock} and still have an inventory icon.
 */
final class StillPartModels {

  /**
   * @param block the part whose textures these are
   * @param mapping the mapping to give a particle texture
   * @return the same mapping, with the particle slot pointed at the block's own texture
   */
  private static TextureMapping withParticle(Block block, TextureMapping mapping) {
    return mapping.putForced(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block));
  }

  /** The valve and the crystal wear one texture on every face. */
  private static final TexturedModel.Provider VALVE =
      TexturedModel.createDefault(
          block ->
              withParticle(
                  block,
                  TextureMapping.singleSlot(
                      TextureSlot.TEXTURE, TextureMapping.getBlockTexture(block))),
          StillPartTemplates.VALVE);

  private static final TexturedModel.Provider CRYSTAL =
      TexturedModel.createDefault(
          block ->
              withParticle(
                  block,
                  TextureMapping.singleSlot(
                      TextureSlot.TEXTURE, TextureMapping.getBlockTexture(block))),
          StillPartTemplates.CRYSTAL);

  private static final TexturedModel.Provider BELLOWS =
      TexturedModel.createDefault(
          block ->
              withParticle(
                  block,
                  new TextureMapping()
                      .put(TextureSlot.TEXTURE, TextureMapping.getBlockTexture(block))
                      .put(TextureSlot.NORTH, TextureMapping.getBlockTexture(block, "_mount"))
                      .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_plank"))),
          StillPartTemplates.BELLOWS);

  private static final TexturedModel.Provider FUNNEL =
      TexturedModel.createDefault(
          block ->
              withParticle(
                  block,
                  new TextureMapping()
                      .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block))
                      .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                      .put(TextureSlot.INSIDE, TextureMapping.getBlockTexture(block, "_inside"))),
          StillPartTemplates.FUNNEL);

  private static final TexturedModel.Provider FLUE =
      TexturedModel.createDefault(
          block ->
              withParticle(
                  block,
                  new TextureMapping()
                      .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block))
                      .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))),
          StillPartTemplates.FLUE);

  /**
   * Both condensers wear the same glass, so the glass sprite is taken from the basic condenser
   * whatever block is being generated.
   */
  private static final TexturedModel.Provider CONDENSER =
      TexturedModel.createDefault(
          block ->
              withParticle(
                  block,
                  new TextureMapping()
                      .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block))
                      .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                      .put(
                          TextureSlot.INSIDE,
                          TextureMapping.getBlockTexture(ModBlocks.CONDENSER.get(), "_glass")
                              .withForceTranslucent(true))),
          StillPartTemplates.CONDENSER);

  /**
   * Writes the six fixed part models and the bellows' four facing variants.
   *
   * <p>The bellows is the only one whose model depends on a state; its dispatch is written by
   * {@link #registerBellows}. The others need no state. Every helper writes the model at the
   * default location the block item points at.
   *
   * @param blockModels the generator whose model and block state outputs these are written to
   */
  static void register(BlockModelGenerators blockModels) {
    blockModels.createTrivialBlock(ModBlocks.AIR_PRESSURE_VALVE.get(), VALVE);
    registerBellows(blockModels);
    blockModels.createTrivialBlock(ModBlocks.FUNNEL.get(), FUNNEL);
    blockModels.createTrivialBlock(ModBlocks.FLUE_STACK.get(), FLUE);
    blockModels.createTrivialBlock(ModBlocks.RESONANCE_CRYSTAL.get(), CRYSTAL);
    blockModels.createTrivialBlock(ModBlocks.CONDENSER.get(), CONDENSER);
    blockModels.createTrivialBlock(ModBlocks.ADVANCED_CONDENSER.get(), CONDENSER);
  }

  /**
   * Writes the bellows' whole model, its three renderer parts and the middle-plate model, then the
   * dispatch between them.
   *
   * <p>{@code part} is a rendering selector the client renderer sets on a copy of the state to
   * fetch one piece's model. The world always uses {@code part=whole}; the cap and pleat variants
   * exist only to be drawn separately with their own transforms. The whole model is at the default
   * location because the block item's model is derived from there; while the bellows is moving the
   * world uses the middle-plate model, and the renderer draws the caps and pleats around it.
   *
   * @param blockModels the generator whose model and block state outputs these are written to
   */
  private static void registerBellows(BlockModelGenerators blockModels) {
    Block bellows = ModBlocks.BELLOWS.get();
    TexturedModel textured = BELLOWS.get(bellows);

    MultiVariant whole =
        BlockModelGenerators.plainVariant(textured.create(bellows, blockModels.modelOutput));
    MultiVariant still =
        partModel(blockModels, bellows, textured, StillPartTemplates.BELLOWS_STATIC, "_static");
    MultiVariant cap =
        partModel(blockModels, bellows, textured, StillPartTemplates.BELLOWS_CAP, "_cap");
    MultiVariant lower =
        partModel(
            blockModels,
            bellows,
            textured,
            StillPartTemplates.BELLOWS_LOWER_PLEATS,
            "_lower_pleats");
    MultiVariant upper =
        partModel(
            blockModels,
            bellows,
            textured,
            StillPartTemplates.BELLOWS_UPPER_PLEATS,
            "_upper_pleats");

    blockModels.blockStateOutput.accept(
        MultiVariantGenerator.dispatch(bellows)
            .with(
                PropertyDispatch.initial(
                        BellowsBlock.FACING, BellowsBlock.ACTIVE, BellowsBlock.PART)
                    .generate(
                        (facing, active, part) ->
                            rotate(
                                switch (part) {
                                  case CAP -> cap;
                                  case LOWER_PLEATS -> lower;
                                  case UPPER_PLEATS -> upper;
                                  case WHOLE -> active ? still : whole;
                                },
                                facing))));
  }

  /**
   * @param blockModels the generator
   * @param block the bellows block
   * @param textured the bellows' texture mapping
   * @param template the part's geometry
   * @param suffix the part's model suffix
   * @return the part's variant
   */
  private static MultiVariant partModel(
      BlockModelGenerators blockModels,
      Block block,
      TexturedModel textured,
      ExtendedModelTemplate template,
      String suffix) {
    return BlockModelGenerators.plainVariant(
        template.createWithSuffix(block, suffix, textured.getMapping(), blockModels.modelOutput));
  }

  /**
   * @param variant a bellows model
   * @param facing the direction the bellows' mount points
   * @return the model turned to that direction
   */
  private static MultiVariant rotate(MultiVariant variant, Direction facing) {
    return switch (facing) {
      case EAST -> variant.with(BlockModelGenerators.Y_ROT_90);
      case SOUTH -> variant.with(BlockModelGenerators.Y_ROT_180);
      case WEST -> variant.with(BlockModelGenerators.Y_ROT_270);
      default -> variant;
    };
  }

  private StillPartModels() {}
}
