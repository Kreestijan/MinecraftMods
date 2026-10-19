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
 * 4- or 6-pixel face would sample only the middle of its texture.
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
   * The funnel, shaped like vanilla's hopper: a 2-pixel rim around an open bowl, the bowl floor, an
   * 8-pixel body and the 4-pixel throat that meets the chamber below.
   */
  static final ExtendedModelTemplate FUNNEL =
      ExtendedModelTemplateBuilder.builder()
          .parent(ModModelProvider.BLOCK_PARENT)
          .requiredTextureSlot(TextureSlot.SIDE)
          .requiredTextureSlot(TextureSlot.TOP)
          .requiredTextureSlot(TextureSlot.INSIDE)
          .ambientOcclusion(false)
          .element(
              element ->
                  element
                      .from(0.0F, 10.0F, 0.0F)
                      .to(16.0F, 11.0F, 16.0F)
                      .face(Direction.UP, face -> full(face, TextureSlot.INSIDE))
                      .face(Direction.DOWN, face -> full(face, TextureSlot.INSIDE))
                      .face(Direction.NORTH, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.SOUTH, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.EAST, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.WEST, face -> full(face, TextureSlot.SIDE)))
          .element(
              element ->
                  element
                      .from(0.0F, 11.0F, 0.0F)
                      .to(2.0F, 16.0F, 16.0F)
                      .face(Direction.UP, face -> full(face, TextureSlot.TOP))
                      .face(Direction.DOWN, face -> full(face, TextureSlot.INSIDE))
                      .face(Direction.NORTH, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.SOUTH, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.WEST, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.EAST, face -> full(face, TextureSlot.INSIDE)))
          .element(
              element ->
                  element
                      .from(14.0F, 11.0F, 0.0F)
                      .to(16.0F, 16.0F, 16.0F)
                      .face(Direction.UP, face -> full(face, TextureSlot.TOP))
                      .face(Direction.DOWN, face -> full(face, TextureSlot.INSIDE))
                      .face(Direction.NORTH, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.SOUTH, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.EAST, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.WEST, face -> full(face, TextureSlot.INSIDE)))
          .element(
              element ->
                  element
                      .from(2.0F, 11.0F, 0.0F)
                      .to(14.0F, 16.0F, 2.0F)
                      .face(Direction.UP, face -> full(face, TextureSlot.TOP))
                      .face(Direction.DOWN, face -> full(face, TextureSlot.INSIDE))
                      .face(Direction.NORTH, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.SOUTH, face -> full(face, TextureSlot.INSIDE))
                      .face(Direction.EAST, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.WEST, face -> full(face, TextureSlot.SIDE)))
          .element(
              element ->
                  element
                      .from(2.0F, 11.0F, 14.0F)
                      .to(14.0F, 16.0F, 16.0F)
                      .face(Direction.UP, face -> full(face, TextureSlot.TOP))
                      .face(Direction.DOWN, face -> full(face, TextureSlot.INSIDE))
                      .face(Direction.NORTH, face -> full(face, TextureSlot.INSIDE))
                      .face(Direction.SOUTH, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.EAST, face -> full(face, TextureSlot.SIDE))
                      .face(Direction.WEST, face -> full(face, TextureSlot.SIDE)))
          .element(
              element ->
                  element
                      .from(4.0F, 4.0F, 4.0F)
                      .to(12.0F, 10.0F, 12.0F)
                      .allFacesExcept(
                          (direction, face) -> full(face, TextureSlot.SIDE), Set.of(Direction.DOWN))
                      .face(Direction.DOWN, face -> full(face, TextureSlot.INSIDE)))
          .element(
              element ->
                  element
                      .from(6.0F, 0.0F, 6.0F)
                      .to(10.0F, 4.0F, 10.0F)
                      .allFacesExcept(
                          (direction, face) -> full(face, TextureSlot.SIDE), Set.of(Direction.DOWN))
                      .face(Direction.DOWN, face -> full(face, TextureSlot.INSIDE)))
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
