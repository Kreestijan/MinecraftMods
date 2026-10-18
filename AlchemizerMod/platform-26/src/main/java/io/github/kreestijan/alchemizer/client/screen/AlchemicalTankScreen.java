package io.github.kreestijan.alchemizer.client.screen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.menu.AlchemicalTankMenu;
import io.github.kreestijan.alchemizer.brewing.water.WaterContainers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The tank's screen: one vessel and the player's inventory.
 *
 * <p><b>The gauge is the whole of the machine's half.</b> A tank has no slots, so there is nothing
 * to arrange, and a screen that drew a figure beside the gauge would be repeating what the gauge
 * already shows — the level is the reading, and the exact number is on hover (see {@link
 * #extractBackground}).
 *
 * <p>Water goes in through the same gesture the pressure chamber's gauge answers: pick up a bucket
 * or a water bottle, then click the gauge. That is a coordinate test rather than a slot action,
 * because the gauge is not a slot and there is nothing to exchange — see {@link
 * AlchemicalTankMenu#clickMenuButton} for what the server does with the press, and why the client
 * cannot make it pour water the player is not carrying.
 *
 * <p>Placement is in screen coordinates throughout, because the panel is drawn in {@code
 * extractBackground}, which runs before the pose is translated. The container screen's own labels
 * are placed in panel coordinates instead; the two conventions are mixed only where the parent
 * class forces it.
 *
 * <p>No {@code @OnlyIn(Dist.CLIENT)}, for the reason given in {@link PressureChamberScreen}.
 */
public class AlchemicalTankScreen extends AbstractContainerScreen<AlchemicalTankMenu> {

  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath(
          Alchemizer.MOD_ID, "textures/gui/container/alchemical_tank.png");

  /**
   * The gauge, centred in the panel.
   *
   * <p>Deliberately the same size as the pressure chamber's, down to the pixel: the two are the
   * same vessel seen from two screens, and a player who has learnt to read one should not have to
   * learn the other. Its width centres it — {@code (176 - 16) / 2} — and it sits between the title
   * and the inventory label.
   */
  private static final int GAUGE_X = 80;

  private static final int GAUGE_Y = 17;

  private static final int GAUGE_WIDTH = 16;

  private static final int GAUGE_HEIGHT = 52;

  /**
   * The water itself, drawn full height and cut down to the level in the tank.
   *
   * <p>There is no "short" red beside it: a tank is never waiting on anything, so this screen has
   * one face where the pressure chamber's gauge has two. The recess it sits in is the panel
   * texture's own, which is why nothing is drawn above the water — see {@link
   * PressureChamberScreen} for the same arrangement on the larger gauge.
   */
  private static final Identifier WATER = machine("tank_water");

  /**
   * @param menu the tank menu this screen is a view of
   * @param inventory the player's inventory, which the menu also draws
   * @param title the screen's title, which is the block's own name
   */
  public AlchemicalTankScreen(AlchemicalTankMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);

    // The panel is vanilla's standard size, so the two labels are the only geometry this screen
    // places for itself — and where they go is PanelLabels' business rather than this screen's, so
    // that the three brewing screens agree without each restating the same distances.
    this.titleLabelX = PanelLabels.TITLE_X;
    this.titleLabelY = PanelLabels.TITLE_Y;
    this.inventoryLabelX = PanelLabels.inventoryLabelX(AlchemicalTankMenu.INVENTORY_X);
    this.inventoryLabelY = PanelLabels.inventoryLabelY(DEFAULT_IMAGE_HEIGHT);
  }

  /**
   * Pours a carried water container in when the gauge is clicked.
   *
   * <p>The same narrow coordinate test the pressure chamber makes, and for the same reasons: the
   * gauge overlaps no slot, so a click anywhere else still reaches the parent untouched, and the
   * guard on the cursor holding water saves a packet rather than deciding anything — the server
   * re-reads its own cursor and refuses if the client was wrong.
   *
   * @param event the click
   * @param doubleClick whether this was the second click of a double
   * @return whether the click was consumed
   */
  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() == 0
        && isOverGauge(event.x(), event.y())
        && WaterContainers.waterMb(menu.getCarried()) > 0) {
      minecraft.gameMode.handleInventoryButtonClick(
          menu.containerId, AlchemicalTankMenu.BUTTON_FILL_TANK);
      return true;
    }
    return super.mouseClicked(event, doubleClick);
  }

  /**
   * @param x the click's screen x
   * @param y the click's screen y
   * @return whether the click landed on the gauge
   */
  private boolean isOverGauge(double x, double y) {
    return x >= leftPos + GAUGE_X
        && x < leftPos + GAUGE_X + GAUGE_WIDTH
        && y >= topPos + GAUGE_Y
        && y < topPos + GAUGE_Y + GAUGE_HEIGHT;
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

    drawGauge(graphics);

    // Deferred rather than drawn here: this version collects tooltips during extraction and shows
    // them once the frame is assembled, so the request is legal from the background pass. The first
    // request for a frame wins, which is what keeps the slot tooltips the parent asks for later
    // from
    // overwriting this one.
    if (isOverGauge(mouseX, mouseY)) {
      graphics.setTooltipForNextFrame(font, waterText(), mouseX, mouseY);
    }
  }

  /**
   * @param graphics the frame being extracted
   */
  private void drawGauge(GuiGraphicsExtractor graphics) {
    int capacity = menu.waterCapacityMb();
    float filled = capacity <= 0 ? 0.0F : Math.min(1.0F, menu.waterStoredMb() / (float) capacity);
    int filledHeight = Math.round(filled * GAUGE_HEIGHT);
    if (filledHeight <= 0) {
      return;
    }

    // Bottom-up, like the vessel itself: a gauge that drained from the top would read as a progress
    // bar rather than as a level. The source rectangle is the bottom of the sprite, one pixel to
    // one, so the surface lands wherever the amount puts it and no row of the art is ever scaled.
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        WATER,
        leftPos + GAUGE_X,
        topPos + GAUGE_Y + GAUGE_HEIGHT - filledHeight,
        0.0F,
        (float) (GAUGE_HEIGHT - filledHeight),
        GAUGE_WIDTH,
        filledHeight,
        GAUGE_WIDTH,
        GAUGE_HEIGHT);
  }

  /**
   * The hover figure, which is where the exact numbers live.
   *
   * <p>Millibuckets rather than doses. A dose is what a brew spends, which is a word that means
   * something on the pressure chamber's screen, where a brew is the subject; this screen is a view
   * of a vessel, and a vessel is measured. The same figure and the same wording are shown on the
   * chamber's gauge, so the two readings of one tank agree.
   *
   * @return the amount and capacity, ready to draw
   */
  private Component waterText() {
    return Component.translatable(
        "gui.alchemizer.water.mb", menu.waterStoredMb(), menu.waterCapacityMb());
  }

  /**
   * @param name the file's name under this mod's machine-widget directory
   * @return where that sprite lives
   */
  private static Identifier machine(String name) {
    return Identifier.fromNamespaceAndPath(
        Alchemizer.MOD_ID, "textures/gui/machine/" + name + ".png");
  }
}
