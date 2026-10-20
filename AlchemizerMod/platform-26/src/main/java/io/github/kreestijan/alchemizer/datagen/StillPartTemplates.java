package io.github.kreestijan.alchemizer.datagen;

import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.generators.template.ElementBuilder;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplate;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
import net.neoforged.neoforge.client.model.generators.template.FaceBuilder;

/**
 * The still parts' model geometry.
 *
 * <p>One template per shape, kept out of {@code ModModelProvider} so that the provider stays a
 * provider. All elements are axis-aligned and stay inside 0..16, in the pixel-art spirit of
 * vanilla's hopper and brewing stand. Every face is mapped across the whole sheet: without that, a
 * 4- or 6-pixel face would sample only the middle of its texture. The funnel is the one exception:
 * it leaves UVs unset so the baker derives them from each element's position, which is how
 * vanilla's hopper gives every band its own strip of the texture instead of squashing the sheet
 * onto it.
 *
 * <p>The coordinates here are the same ones {@code PartShapes} uses for collision and selection, so
 * pointing at a part points at what was drawn.
 */
final class StillPartTemplates {

  /**
   * A still part's element: all six faces sample the whole of one texture.
   *
   * @param slot the texture every face uses
   * @return the element builder for that box
   */
  private static Consumer<ElementBuilder> box(
      float fromX, float fromY, float fromZ, float toX, float toY, float toZ, TextureSlot slot) {
    return element ->
        element
            .from(fromX, fromY, fromZ)
            .to(toX, toY, toZ)
            .allFaces((direction, face) -> full(face, slot));
  }

  /**
   * @param face the face to map
   * @param slot the texture slot that face samples
   * @return the same face, now texture-mapped across all 16 pixels
   */
  static FaceBuilder full(FaceBuilder face, TextureSlot slot) {
    return face.texture(slot).uvs(0.0F, 0.0F, 16.0F, 16.0F);
  }

  /**
   * One wall of a funnel band: the outward face is the part's side, the face into the bore is the
   * inside, the top edge is the rim you see looking down, and the bottom is inside.
   *
   * <p>No UVs are set. The baker derives them from this element's position, so each band samples
   * the strip of its sprite that matches the band's height and width instead of stretching the
   * whole sheet over a 16x2 face. The funnel's three sprites are therefore painted as orthographic
   * projections: side elevation, plan and bowl interior.
   *
   * @param outward the direction the wall's outer face points
   * @return the element builder for that wall
   */
  private static Consumer<ElementBuilder> wall(
      float fromX, float fromY, float fromZ, float toX, float toY, float toZ, Direction outward) {
    Direction inward = outward.getOpposite();
    return element ->
        element
            .from(fromX, fromY, fromZ)
            .to(toX, toY, toZ)
            .allFacesExcept(
                (direction, face) -> face.texture(TextureSlot.SIDE),
                Set.of(outward, inward, Direction.UP, Direction.DOWN))
            .face(outward, face -> face.texture(TextureSlot.SIDE))
            .face(inward, face -> face.texture(TextureSlot.INSIDE))
            .face(Direction.UP, face -> face.texture(TextureSlot.TOP))
            .face(Direction.DOWN, face -> face.texture(TextureSlot.INSIDE));
  }

  /**
   * The valve hub: a 4-pixel core with a 10x10x2 flange on each face. The flange is deliberately
   * the size the flue collar and the bellows mount use, so the parts meet face to face.
   */
  static final ExtendedModelTemplate VALVE =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.TEXTURE)
          .element(box(4.0F, 4.0F, 4.0F, 12.0F, 12.0F, 12.0F, TextureSlot.TEXTURE))
          .element(box(3.0F, 3.0F, 0.0F, 13.0F, 13.0F, 2.0F, TextureSlot.TEXTURE))
          .element(box(3.0F, 3.0F, 14.0F, 13.0F, 13.0F, 16.0F, TextureSlot.TEXTURE))
          .element(box(0.0F, 3.0F, 3.0F, 2.0F, 13.0F, 13.0F, TextureSlot.TEXTURE))
          .element(box(14.0F, 3.0F, 3.0F, 16.0F, 13.0F, 13.0F, TextureSlot.TEXTURE))
          .element(box(3.0F, 0.0F, 3.0F, 13.0F, 2.0F, 13.0F, TextureSlot.TEXTURE))
          .element(box(3.0F, 14.0F, 3.0F, 13.0F, 16.0F, 13.0F, TextureSlot.TEXTURE))
          .build();

  /**
   * The bellows at rest: a sandwich of thick square boards.
   *
   * <p>A 2-pixel cap, two inset pleat bands, a full 16x16 plate through the middle, two more pleat
   * bands and a second cap. The nozzle is a 4x4 port on the middle plate's north face. The moving
   * version is not a second model: the client renderer scales this one toward its centre, so this
   * is the only bellows model the datagen needs.
   */
  static final ExtendedModelTemplate BELLOWS =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.TEXTURE)
          .requiredTextureSlot(TextureSlot.NORTH)
          .requiredTextureSlot(TextureSlot.SIDE)
          // Bottom cap.
          .element(box(0.0F, 0.0F, 0.0F, 16.0F, 2.0F, 16.0F, TextureSlot.SIDE))
          // Lower pleats, inset a little more as they approach the cap.
          .element(box(2.0F, 2.0F, 2.0F, 14.0F, 4.0F, 14.0F, TextureSlot.TEXTURE))
          .element(box(1.0F, 4.0F, 1.0F, 15.0F, 6.0F, 15.0F, TextureSlot.TEXTURE))
          // The middle plate: one box. The nozzle is painted on its north face.
          .element(
              element ->
                  element
                      .from(0.0F, 6.0F, 0.0F)
                      .to(16.0F, 10.0F, 16.0F)
                      .allFacesExcept(
                          (direction, face) -> full(face, TextureSlot.SIDE),
                          Set.of(Direction.NORTH))
                      .face(Direction.NORTH, face -> full(face, TextureSlot.NORTH)))
          // Upper pleats, mirroring the lower ones.
          .element(box(1.0F, 10.0F, 1.0F, 15.0F, 12.0F, 15.0F, TextureSlot.TEXTURE))
          .element(box(2.0F, 12.0F, 2.0F, 14.0F, 14.0F, 14.0F, TextureSlot.TEXTURE))
          // Top cap.
          .element(box(0.0F, 14.0F, 0.0F, 16.0F, 16.0F, 16.0F, TextureSlot.SIDE))
          .build();

  /**
   * The middle plate alone. It is the world model while the bellows is moving, so it stays in the
   * chunk mesh and is never redrawn by the renderer.
   */
  static final ExtendedModelTemplate BELLOWS_STATIC =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.SIDE)
          .requiredTextureSlot(TextureSlot.NORTH)
          .element(
              element ->
                  element
                      .from(0.0F, 6.0F, 0.0F)
                      .to(16.0F, 10.0F, 16.0F)
                      .allFacesExcept(
                          (direction, face) -> full(face, TextureSlot.SIDE),
                          Set.of(Direction.NORTH))
                      .face(Direction.NORTH, face -> full(face, TextureSlot.NORTH)))
          .build();

  /** One cap, authored at the bottom of the block. The renderer uses it twice, translated. */
  static final ExtendedModelTemplate BELLOWS_CAP =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.SIDE)
          .element(box(0.0F, 0.0F, 0.0F, 16.0F, 2.0F, 16.0F, TextureSlot.SIDE))
          .build();

  /** The two lower pleat bands, authored in their rest positions. */
  static final ExtendedModelTemplate BELLOWS_LOWER_PLEATS =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.TEXTURE)
          .element(box(2.0F, 2.0F, 2.0F, 14.0F, 4.0F, 14.0F, TextureSlot.TEXTURE))
          .element(box(1.0F, 4.0F, 1.0F, 15.0F, 6.0F, 15.0F, TextureSlot.TEXTURE))
          .build();

  /** The two upper pleat bands, authored in their rest positions. */
  static final ExtendedModelTemplate BELLOWS_UPPER_PLEATS =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.TEXTURE)
          .element(box(1.0F, 10.0F, 1.0F, 15.0F, 12.0F, 15.0F, TextureSlot.TEXTURE))
          .element(box(2.0F, 12.0F, 2.0F, 14.0F, 14.0F, 14.0F, TextureSlot.TEXTURE))
          .build();

  /**
   * The funnel: a squat stepped cone that sits directly on the block below.
   *
   * <p>Three wall bands step inward as they descend, 16 wide at the mouth and then 14 and 12, so
   * the silhouette reads as a cone instead of vanilla's hopper. The bore narrows with them, 12 then
   * 10 then 8, which keeps every wall 2 pixels thick. The 12-pixel bowl floor is the part's base:
   * it lies at y0 and touches whatever the funnel is placed on, so there is no neck between the
   * bowl and the machine.
   *
   * <p>The whole part stays in the block's lower six pixels. Above it is clear air, so the funnel
   * sits on the machine rather than looming over it; nothing in the still's rules reads the shape,
   * so the height is purely visual.
   */
  static final ExtendedModelTemplate FUNNEL =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.SIDE)
          .requiredTextureSlot(TextureSlot.TOP)
          .requiredTextureSlot(TextureSlot.INSIDE)
          .ambientOcclusion(false)
          // Mouth band: 16 wide, 12-pixel bore.
          .element(wall(0.0F, 4.0F, 0.0F, 16.0F, 6.0F, 2.0F, Direction.NORTH))
          .element(wall(0.0F, 4.0F, 14.0F, 16.0F, 6.0F, 16.0F, Direction.SOUTH))
          .element(wall(0.0F, 4.0F, 2.0F, 2.0F, 6.0F, 14.0F, Direction.WEST))
          .element(wall(14.0F, 4.0F, 2.0F, 16.0F, 6.0F, 14.0F, Direction.EAST))
          // Middle band: 14 wide, 10-pixel bore.
          .element(wall(1.0F, 2.0F, 1.0F, 15.0F, 4.0F, 3.0F, Direction.NORTH))
          .element(wall(1.0F, 2.0F, 13.0F, 15.0F, 4.0F, 15.0F, Direction.SOUTH))
          .element(wall(1.0F, 2.0F, 3.0F, 3.0F, 4.0F, 13.0F, Direction.WEST))
          .element(wall(13.0F, 2.0F, 3.0F, 15.0F, 4.0F, 13.0F, Direction.EAST))
          // Lower band: 12 wide, 8-pixel bore.
          .element(wall(2.0F, 1.0F, 2.0F, 14.0F, 2.0F, 4.0F, Direction.NORTH))
          .element(wall(2.0F, 1.0F, 12.0F, 14.0F, 2.0F, 14.0F, Direction.SOUTH))
          .element(wall(2.0F, 1.0F, 4.0F, 4.0F, 2.0F, 12.0F, Direction.WEST))
          .element(wall(12.0F, 1.0F, 4.0F, 14.0F, 2.0F, 12.0F, Direction.EAST))
          // Bowl floor and base: its up face is the inside seen down the bore.
          .element(
              element ->
                  element
                      .from(2.0F, 0.0F, 2.0F)
                      .to(14.0F, 1.0F, 14.0F)
                      .allFacesExcept(
                          (direction, face) -> face.texture(TextureSlot.SIDE),
                          Set.of(Direction.UP, Direction.DOWN))
                      .face(Direction.UP, face -> face.texture(TextureSlot.INSIDE))
                      .face(Direction.DOWN, face -> face.texture(TextureSlot.INSIDE)))
          .build();

  /**
   * The flue: a 6-pixel tube between two 10-pixel collars. The top collar is a ring rather than a
   * plate, so the tube's dark opening is still visible when the stack ends here.
   */
  static final ExtendedModelTemplate FLUE =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.SIDE)
          .requiredTextureSlot(TextureSlot.TOP)
          .element(box(3.0F, 0.0F, 3.0F, 13.0F, 2.0F, 13.0F, TextureSlot.SIDE))
          .element(
              element ->
                  element
                      .from(5.0F, 0.0F, 5.0F)
                      .to(11.0F, 16.0F, 11.0F)
                      .allFacesExcept(
                          (direction, face) -> full(face, TextureSlot.SIDE), Set.of(Direction.UP))
                      .face(Direction.UP, face -> full(face, TextureSlot.TOP)))
          .element(box(3.0F, 14.0F, 3.0F, 13.0F, 16.0F, 5.0F, TextureSlot.SIDE))
          .element(box(3.0F, 14.0F, 11.0F, 13.0F, 16.0F, 13.0F, TextureSlot.SIDE))
          .element(box(3.0F, 14.0F, 5.0F, 5.0F, 16.0F, 11.0F, TextureSlot.SIDE))
          .element(box(11.0F, 14.0F, 5.0F, 13.0F, 16.0F, 11.0F, TextureSlot.SIDE))
          .build();

  /** The crystal: one tall stepped shard with three shorter ones around its base. */
  static final ExtendedModelTemplate CRYSTAL =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.TEXTURE)
          .element(box(6.0F, 0.0F, 6.0F, 10.0F, 10.0F, 10.0F, TextureSlot.TEXTURE))
          .element(box(7.0F, 10.0F, 7.0F, 9.0F, 16.0F, 9.0F, TextureSlot.TEXTURE))
          .element(box(3.0F, 0.0F, 7.0F, 6.0F, 5.0F, 10.0F, TextureSlot.TEXTURE))
          .element(box(4.0F, 5.0F, 8.0F, 6.0F, 8.0F, 10.0F, TextureSlot.TEXTURE))
          .element(box(10.0F, 0.0F, 4.0F, 13.0F, 4.0F, 7.0F, TextureSlot.TEXTURE))
          .element(box(11.0F, 4.0F, 5.0F, 13.0F, 7.0F, 7.0F, TextureSlot.TEXTURE))
          .element(box(9.0F, 0.0F, 10.0F, 12.0F, 3.0F, 13.0F, TextureSlot.TEXTURE))
          .build();

  /**
   * The condenser canister: a cap at each end with the glass band recessed one pixel between two
   * body bands. The advanced condenser shares the geometry and changes only its textures.
   */
  static final ExtendedModelTemplate CONDENSER =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.SIDE)
          .requiredTextureSlot(TextureSlot.TOP)
          .requiredTextureSlot(TextureSlot.INSIDE)
          .element(box(2.0F, 0.0F, 2.0F, 14.0F, 2.0F, 14.0F, TextureSlot.SIDE))
          .element(box(2.0F, 2.0F, 2.0F, 14.0F, 5.0F, 14.0F, TextureSlot.SIDE))
          .element(
              element ->
                  element
                      .from(3.0F, 5.0F, 3.0F)
                      .to(13.0F, 11.0F, 13.0F)
                      .allFacesExcept(
                          (direction, face) -> full(face, TextureSlot.INSIDE),
                          Set.of(Direction.UP, Direction.DOWN))
                      .face(Direction.UP, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.DOWN, face -> full(face, TextureSlot.SIDE)))
          .element(box(2.0F, 11.0F, 2.0F, 14.0F, 14.0F, 14.0F, TextureSlot.SIDE))
          .element(
              element ->
                  element
                      .from(2.0F, 14.0F, 2.0F)
                      .to(14.0F, 16.0F, 14.0F)
                      .allFacesExcept(
                          (direction, face) -> full(face, TextureSlot.SIDE), Set.of(Direction.UP))
                      .face(Direction.UP, face -> full(face, TextureSlot.TOP)))
          .build();

  private StillPartTemplates() {}
}
