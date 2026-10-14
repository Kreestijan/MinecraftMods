package io.github.kreestijan.alchemizer.client.screen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.menu.IgnitionChamberMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The fuel screen: one slot, a flame, and a bar.
 *
 * <p>The panel is drawn in {@code extractBackground}, which in this version of Minecraft runs
 * before the screen's widgets and slots and with no translation applied, so everything below is
 * placed in screen coordinates by adding {@code leftPos} and {@code topPos} to a panel coordinate.
 * Keeping to one convention matters here: the labels the container screen draws for itself are
 * placed in panel coordinates instead, and mixing the two quietly offsets half the screen.
 *
 * <p>Both gauges are rectangles rather than sprites. A sprite has to be found in the {@code gui}
 * atlas, which is a question about how a mod's GUI textures are collected, and a rectangle has no
 * such question: it is drawn from a colour and works whatever the resource pack is doing. The two
 * gauges are a flame's worth of vertical fill and a bar, both of which are a rectangle either way.
 *
 * <p>There is no {@code @OnlyIn(Dist.CLIENT)} on this class. The annotation no longer strips
 * anything at load time in this version, so it would be decoration — and the loader now reports
 * every use of it in a mod as an error, on the grounds that anyone relying on the old behaviour
 * would otherwise find out the hard way. What keeps this class off a dedicated server is that the
 * only thing naming it is a {@code Dist.CLIENT} event subscriber.
 */
public class IgnitionChamberScreen extends AbstractContainerScreen<IgnitionChamberMenu> {

  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath(
          Alchemizer.MOD_ID, "textures/gui/container/ignition_chamber.png");

  /** The flame, sitting under the fuel slot. */
  private static final int FLAME_X = 80;

  private static final int FLAME_Y = 44;

  private static final int FLAME_SIZE = 14;

  /** The burn bar, a full-width readout of what is left rather than a 14-pixel column. */
  private static final int BAR_X = 64;

  private static final int BAR_Y = 64;

  private static final int BAR_WIDTH = 48;

  private static final int BAR_HEIGHT = 5;

  /** A recess, which is what both gauges are until something is in them. */
  private static final int EMPTY_COLOR = 0xFF1C1C1C;

  /** The flame when the chamber is alight. */
  private static final int FLAME_COLOR = 0xFFF08A24;

  private static final int BAR_COLOR = 0xFFE8A33D;

  /**
   * @param menu the fuel menu this screen is a view of
   * @param inventory the player's inventory, which the menu also draws
   * @param title the screen's title, which is the block's own name
   */
  public IgnitionChamberScreen(IgnitionChamberMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    super.extractBackground(graphics, mouseX, mouseY, a);
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        TEXTURE,
        leftPos,
        topPos,
        0.0F,
        0.0F,
        imageWidth,
        imageHeight,
        256,
        256);

    // The recess is drawn whether or not anything is alight, so it is a fixed piece of the panel
    // rather than something that appears and disappears as fuel comes and goes.
    float burn = menu.burnProgress();
    graphics.fill(
        leftPos + FLAME_X,
        topPos + FLAME_Y,
        leftPos + FLAME_X + FLAME_SIZE,
        topPos + FLAME_Y + FLAME_SIZE,
        EMPTY_COLOR);
    if (menu.isLit()) {
      // Bottom-anchored, like every fuel gauge in the game: the flame shrinks towards the wick as
      // the fuel runs out rather than draining from the top.
      int flameHeight = Math.max(1, Math.round(burn * FLAME_SIZE));
      graphics.fill(
          leftPos + FLAME_X,
          topPos + FLAME_Y + FLAME_SIZE - flameHeight,
          leftPos + FLAME_X + FLAME_SIZE,
          topPos + FLAME_Y + FLAME_SIZE,
          FLAME_COLOR);
    }

    int barFilled = menu.isLit() ? Math.round(burn * BAR_WIDTH) : 0;
    graphics.fill(
        leftPos + BAR_X,
        topPos + BAR_Y,
        leftPos + BAR_X + BAR_WIDTH,
        topPos + BAR_Y + BAR_HEIGHT,
        EMPTY_COLOR);
    if (barFilled > 0) {
      graphics.fill(
          leftPos + BAR_X,
          topPos + BAR_Y,
          leftPos + BAR_X + barFilled,
          topPos + BAR_Y + BAR_HEIGHT,
          BAR_COLOR);
    }
  }
}
