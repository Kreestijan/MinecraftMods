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
 * The fuel screen: one slot, a flame, a bar, and the seconds that bar stands for.
 *
 * <p>The two gauges and the countdown are three readings of one thing — the fuel alight and how
 * much of it is left. The flame says whether there is a fire, the bar says how far through it is,
 * and the number says how long that is in the unit a player plans in.
 *
 * <p>The panel is drawn in {@code extractBackground}, which in this version of Minecraft runs
 * before the screen's widgets and slots and with no translation applied, so everything below is
 * placed in screen coordinates by adding {@code leftPos} and {@code topPos} to a panel coordinate.
 * Keeping to one convention matters here: the labels the container screen draws for itself are
 * placed in panel coordinates instead, and mixing the two quietly offsets half the screen.
 *
 * <p><b>The recesses are the panel's art and the readings are sprites beside it.</b> Both gauges
 * are an amount rather than a picture — a flame's worth of vertical fill and a bar's worth of
 * horizontal — so neither can be one fixed image. What the code blits is a sub-rectangle of a
 * full-size sprite: the flame off the bottom of its own column and the bar off the left of its own
 * row. The colours and the shading therefore come from a file while the amount stays a number, and
 * the well each sits in is left to the texture, which already draws it — see {@link
 * PressureChamberScreen} for the same arrangement on the brewing screen.
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

  /**
   * The countdown, in the clear space to the right of the fuel slot.
   *
   * <p><b>Not under the bar, which is where it was.</b> The bar and the number are one reading, but
   * the reading is of the fuel <em>in that slot</em> — and sitting centred under the bar put the
   * seconds on the panel's decoration: the separator rule runs at y71 and the countdown was drawn
   * at the same y, so a lit chamber painted its countdown across the line. Beside the slot it is
   * both clear of the rule and next to the thing it counts.
   *
   * <p>Its x is nineteen past the slot's own, which is two clear pixels right of the slot's well:
   * the well runs from {@code slotX - 1} to {@code slotX + 16}, and two pixels is the gap vanilla
   * itself leaves between one well and the next. Its y centres the nine-pixel line on the well's
   * eighteen, so the slot and its countdown read as one object.
   */
  private static final int COUNTDOWN_X = IgnitionChamberMenu.SLOT_X + 19;

  private static final int COUNTDOWN_Y = 23;

  /**
   * The flame when the chamber is alight, and the amber the burn bar runs in.
   *
   * <p>Both are drawn at their full size and cut down to however much fuel is left, so the two
   * shades a gradient needs cannot be flattened into one colour at each end by a clipping
   * rectangle: the bottom of the flame column is the hot part and the top is the tip, and which
   * rows of it a player sees is the reading.
   */
  private static final Identifier FLAME_FILL = machine("flame_fill");

  private static final Identifier BURN_BAR_FILL = machine("burn_bar_fill");

  /** Dimmer than the bar, so the picture stays the louder of the two readings. */
  private static final int TEXT_COLOR = 0xFFB0B0B0;

  /**
   * @param menu the fuel menu this screen is a view of
   * @param inventory the player's inventory, which the menu also draws
   * @param title the screen's title, which is the block's own name
   */
  public IgnitionChamberScreen(IgnitionChamberMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);

    // The panel is vanilla's standard size, so the two labels are the only geometry this screen
    // places for itself — and where they go is PanelLabels' business rather than this screen's, so
    // that the three brewing screens agree without each restating the same distances.
    this.titleLabelX = PanelLabels.TITLE_X;
    this.titleLabelY = PanelLabels.TITLE_Y;
    this.inventoryLabelX = PanelLabels.inventoryLabelX(IgnitionChamberMenu.INVENTORY_X);
    this.inventoryLabelY = PanelLabels.inventoryLabelY(DEFAULT_IMAGE_HEIGHT);
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

    // The recess is the panel's own art, drawn whether or not anything is alight, so a cold chamber
    // shows the well the flame sits in and nothing is painted over it. Only the flame itself is
    // drawn here, and only while there is one.
    float burn = menu.burnProgress();
    if (menu.isLit()) {
      // Bottom-anchored, like every fuel gauge in the game: the flame shrinks towards the wick as
      // the fuel runs out rather than draining from the top. The source rectangle is the bottom of
      // the sprite at one to one, so the tip of the flame is wherever the fuel puts it.
      int flameHeight = Math.max(1, Math.round(burn * FLAME_SIZE));
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          FLAME_FILL,
          leftPos + FLAME_X,
          topPos + FLAME_Y + FLAME_SIZE - flameHeight,
          0.0F,
          (float) (FLAME_SIZE - flameHeight),
          FLAME_SIZE,
          flameHeight,
          FLAME_SIZE,
          FLAME_SIZE);
    }

    // The trough the bar runs in is the panel's too, so an unlit chamber shows it unfilled.
    int barFilled = menu.isLit() ? Math.round(burn * BAR_WIDTH) : 0;
    if (barFilled > 0) {
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          BURN_BAR_FILL,
          leftPos + BAR_X,
          topPos + BAR_Y,
          0.0F,
          0.0F,
          barFilled,
          BAR_HEIGHT,
          BAR_WIDTH,
          BAR_HEIGHT);
    }

    // Only while something is burning. A cold chamber has no fuel piece to count down, and a
    // standing "0s" beside an empty slot would be a reading of nothing. The string is the one the
    // hourglass buttons use rather than a second key holding the same text: both are a number of
    // seconds, and two keys for one string is two things to keep in step.
    if (menu.isLit()) {
      Component countdown =
          Component.translatable("gui.alchemizer.seconds", menu.burnSecondsRemaining());
      graphics.text(font, countdown, leftPos + COUNTDOWN_X, topPos + COUNTDOWN_Y, TEXT_COLOR);
    }
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
