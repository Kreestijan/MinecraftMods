package io.github.kreestijan.alchemizer.client.customer;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.customer.Customer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws a customer as the slim player model the design asks for.
 *
 * <p>{@code DESIGN.md} §5.2: customers are slim player models with shipped textures. So the mesh is
 * the game's own player mesh rather than one of the mod's, and it is the vanilla slim one rather
 * than a copy: it is already baked by the game under {@code ModelLayers.PLAYER_SLIM}, which means
 * there is no model layer to register and no mesh to build — the layer is asked for by name and the
 * game has one waiting.
 *
 * <p><b>The render state is a plain humanoid's, and that is the whole reason this renderer
 * works.</b> {@code EntityRenderDispatcher} picks the renderer for a frame from the <em>render
 * state</em>, not only from the entity type: a state that is an {@code AvatarRenderState} is handed
 * to the player renderer, whoever produced it. A customer that reused the avatar's state was
 * therefore drawn as a player, from the player's own skin — the default one — and this class's
 * {@code getTextureLocation} was never called at all. Borrowing {@code PlayerModel}, which is
 * written against the avatar state, is what led there. So the state is an ordinary {@link
 * HumanoidRenderState} and the model is {@link CustomerModel}, whose mesh is still the player's.
 *
 * <p>The overlay parts — the jacket, the sleeves, the pants, the hat — need no switching on here.
 * They belong to {@code AvatarRenderState} as flags because the game sets them from a player's skin
 * before this code runs; on a plain humanoid model they are simply drawn, which for a customer is
 * exactly right: the second layer of its texture is its clothing.
 */
public class CustomerRenderer
    extends HumanoidMobRenderer<Customer, HumanoidRenderState, CustomerModel> {

  /** Where a customer's skin lives. A placeholder, drawn to be redrawn. */
  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "textures/entity/customer.png");

  /**
   * PLACEHOLDER: how big the shadow under a customer is.
   *
   * <p>A player's, which is what the model is. It is named rather than inlined because it is the
   * only number in this class and it is a decision rather than arithmetic.
   */
  private static final float SHADOW_RADIUS = 0.5F;

  /**
   * @param context the renderer's context, which is where the baked model comes from
   */
  public CustomerRenderer(EntityRendererProvider.Context context) {
    super(context, new CustomerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), SHADOW_RADIUS);
  }

  @Override
  public HumanoidRenderState createRenderState() {
    return new HumanoidRenderState();
  }

  @Override
  public Identifier getTextureLocation(HumanoidRenderState state) {
    return TEXTURE;
  }
}
