package io.github.kreestijan.alchemizer.client.customer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The customer's model: the game's own player mesh, in an ordinary humanoid model.
 *
 * <p>The mesh is not re-derived here. {@code ModelLayers.PLAYER_SLIM} is the slim player mesh,
 * baked by the game before any mod is asked anything, so this class is handed that root and wears
 * it: the sleeves, the pants and the jacket are children of the arms, the legs and the body, and
 * they are drawn without being named. Which is the whole of what a customer's "clothing" is — the
 * second layer of the texture it is drawn with.
 *
 * <p><b>Why this class exists at all, which is the interesting part.</b> The obvious model to hand
 * a renderer is vanilla's {@code PlayerModel}. It is also a trap: {@code PlayerModel} is written
 * against {@code AvatarRenderState}, and {@code
 * EntityRenderDispatcher.getRenderer(EntityRenderState)} re-routes <em>any</em> render state that
 * is an {@code AvatarRenderState} to the player renderer, whatever entity produced it and whatever
 * renderer was registered for that entity. A customer borrowing the avatar's state was therefore
 * drawn by the player renderer, from the player's own skin — the default one, since nothing had
 * ever set a skin — and this mod's texture was never asked for. See {@code CustomerRenderer}.
 *
 * <p>So the render state is an ordinary {@link HumanoidRenderState}, which the dispatcher looks up
 * by entity type like any other mob's, and this model is typed for it. The mesh is still the
 * player's, because the design asks for slim player models and the game already has one.
 *
 * <p>{@code entityTranslucent} is the player's own render type, kept for the same reason: a skin's
 * second layer is allowed to be translucent, and a customer drawn with a cutout shader would show a
 * transparent hat as a hole.
 */
public final class CustomerModel extends HumanoidModel<HumanoidRenderState> {

  /**
   * @param root the baked slim player mesh, which the caller asks the game for by name
   */
  public CustomerModel(ModelPart root) {
    super(root, RenderTypes::entityTranslucent);
  }
}
