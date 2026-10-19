package io.github.kreestijan.alchemizer.client.bellows;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kreestijan.alchemizer.brewing.block.BellowsBlock;
import io.github.kreestijan.alchemizer.brewing.blockentity.BellowsBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the bellows breathing.
 *
 * <p>This is the whole reason the bellows has a block entity. A Java block model cannot move by
 * itself, and swapping block states every frame would rebuild the chunk section every frame.
 *
 * <p><b>Only the pleats deform.</b> The three full squares are rigid: the middle plate is part of
 * the world model and never redrawn, and the two caps are submitted with a translation only, so
 * their textures are never squeezed. The pleat bands are submitted with a vertical scale, which is
 * the one place compression is wanted.
 *
 * <p>The parts are ordinary block models selected by a rendering-only {@code part} state property.
 * Asking the block-state model set for a part variant is what lets each piece be lit like a block
 * without hand-built geometry, and the state's facing rotates each piece as usual.
 *
 * <p>The phase comes from the client's game time and a start time kept on the block entity. The
 * cosine is the ease: the caps leave and return to the open pose slowly and move fastest halfway.
 */
public class BellowsRenderer
    implements BlockEntityRenderer<BellowsBlockEntity, BellowsRenderState> {

  /** One full breath, in ticks. A little over three seconds. */
  private static final float PERIOD_TICKS = 64.0F;

  /** How far each cap travels at full compression, in pixels. */
  private static final float TRAVEL = 3.0F;

  /** The pleat gap at rest, in pixels. The scale is the remaining gap over this. */
  private static final float PLEAT_GAP = 4.0F;

  /**
   * @param context the renderer's context, unused
   */
  public BellowsRenderer(BlockEntityRendererProvider.Context context) {}

  @Override
  public BellowsRenderState createRenderState() {
    return new BellowsRenderState();
  }

  @Override
  public void extractRenderState(
      BellowsBlockEntity blockEntity,
      BellowsRenderState state,
      float partialTicks,
      Vec3 cameraPosition,
      ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(
        blockEntity, state, partialTicks, cameraPosition, breakProgress);

    state.state = blockEntity.getBlockState();
    state.animating = state.state.getValue(BellowsBlock.ACTIVE);
    if (!state.animating) {
      blockEntity.clearAnimationStart();
      return;
    }
    if (!(blockEntity.getLevel() instanceof ClientLevel level)) {
      return;
    }

    long now = level.getGameTime();
    if (blockEntity.animationStart() < 0) {
      blockEntity.setAnimationStart(now);
    }
    float ticks = (now - blockEntity.animationStart()) + partialTicks;
    float phase = (float) (Math.PI * 2.0 * (ticks / PERIOD_TICKS));
    state.lift = TRAVEL * (1.0F - (float) Math.cos(phase)) / 2.0F;

    BlockPos pos = blockEntity.getBlockPos();
    if (state.cap == null) {
      state.cap = part(level, pos, state.state, BellowsBlock.Part.CAP);
      state.lowerPleats = part(level, pos, state.state, BellowsBlock.Part.LOWER_PLEATS);
      state.upperPleats = part(level, pos, state.state, BellowsBlock.Part.UPPER_PLEATS);
    }
  }

  @Override
  public void submit(
      BellowsRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera) {
    if (!state.animating || state.cap == null) {
      return;
    }
    float lift = state.lift;
    float pleatScale = (PLEAT_GAP - lift) / PLEAT_GAP;

    // The caps are rigid boards: translate, never scale.
    cap(poseStack, collector, state.cap, lift);
    cap(poseStack, collector, state.cap, 14.0F - lift);

    // The pleats shrink between the caps and the middle plate. Their outer edges move with the
    // caps; their inner edges stay on the middle plate at y=6 and y=10.
    pleats(poseStack, collector, state.lowerPleats, 6.0F, pleatScale);
    pleats(poseStack, collector, state.upperPleats, 10.0F, pleatScale);
  }

  /**
   * @param poseStack the current pose
   * @param collector the submit collector
   * @param model a cap model
   * @param y the cap's bottom edge, in pixels
   */
  private static void cap(
      PoseStack poseStack, SubmitNodeCollector collector, MovingBlockRenderState model, float y) {
    poseStack.pushPose();
    poseStack.translate(0.0F, y / 16.0F, 0.0F);
    collector.submitMovingBlock(poseStack, model);
    poseStack.popPose();
  }

  /**
   * @param poseStack the current pose
   * @param collector the submit collector
   * @param model the pleat model, authored in its rest position
   * @param anchor the y the pleats must stay attached to, in pixels
   * @param scale how much of the rest gap remains
   */
  private static void pleats(
      PoseStack poseStack,
      SubmitNodeCollector collector,
      MovingBlockRenderState model,
      float anchor,
      float scale) {
    poseStack.pushPose();
    poseStack.translate(0.0F, anchor / 16.0F, 0.0F);
    poseStack.scale(1.0F, scale, 1.0F);
    poseStack.translate(0.0F, -anchor / 16.0F, 0.0F);
    collector.submitMovingBlock(poseStack, model);
    poseStack.popPose();
  }

  /**
   * @param level the client level, for the biome tint and the light engine
   * @param pos the block's position
   * @param state the bellows' state
   * @param part which piece to resolve
   * @return a moving-block state whose model is that piece, rotated to the bellows' facing
   */
  private static MovingBlockRenderState part(
      ClientLevel level, BlockPos pos, BlockState state, BellowsBlock.Part part) {
    MovingBlockRenderState moving = new MovingBlockRenderState();
    moving.randomSeedPos = pos;
    moving.blockPos = pos;
    moving.blockState = state.setValue(BellowsBlock.PART, part);
    moving.biome = level.getBiome(pos);
    moving.cardinalLighting = level.cardinalLighting();
    moving.lightEngine = level.getLightEngine();
    return moving;
  }
}
