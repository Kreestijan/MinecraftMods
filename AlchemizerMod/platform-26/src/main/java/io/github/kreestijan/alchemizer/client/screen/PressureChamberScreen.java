package io.github.kreestijan.alchemizer.client.screen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity.StartResult;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberMenu;
import io.github.kreestijan.alchemizer.brewing.water.WaterContainers;
import io.github.kreestijan.alchemizer.core.machine.StillProblem;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.defs.RecipeBookView;
import io.github.kreestijan.alchemizer.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The brewing screen.
 *
 * <p>It is a view of the machine and nothing else. The hourglass the player has chosen, whether the
 * still is assembled, and what a brew started now would come to all arrive from the server, so the
 * screen cannot show a still as ready when the server would refuse it. What the player sends back
 * is a button id — see {@link PressureChamberMenu#clickMenuButton}.
 *
 * <p>Placement is in screen coordinates throughout, because the panel is drawn in {@code
 * extractBackground}, which runs before the pose is translated. The container screen's own labels
 * are placed in panel coordinates instead; the two conventions are mixed only where the parent
 * class forces it.
 *
 * <p><b>Everything this screen draws is a file in this mod.</b> The panel, the wells cut into it,
 * the recess behind the gauge, the trough the brew bar runs in and the frame around the batch
 * control are the panel texture's own art; what the code adds on top is drawn from sprites beside
 * it. Nothing here is a rectangle built from a colour constant any more, which is what makes every
 * pixel of the screen openable in an image editor.
 *
 * <p><b>A well is art; what fills it is code.</b> A gauge, a bar and a flame are all the same
 * shape: the recess is a fixed piece of the panel and the reading is an amount. So the recess is
 * left to the texture, and the code blits a sub-rectangle of a full-height (or full-width) sprite
 * for however much of it there is — the level rises out of the bottom of the vessel, the bar grows
 * out of the left, and the flame shrinks towards its wick. The colours and the shape therefore come
 * from a file while the amount stays a number.
 *
 * <p>The four hourglass buttons and the start button are {@link SpriteButton}s: our own drawn,
 * texture-backed widgets rather than vanilla's, because a vanilla button's face belongs to the base
 * game and no texture of ours can reach it. They keep the hover state, the press handling, the
 * tooltip and the narration a vanilla button gave them — see that class — and the panel art is left
 * plain where they sit rather than drawing recesses under them.
 *
 * <p><b>The panel is 210 by 240, not vanilla's 176 by 166.</b> Everything the machine draws is
 * stacked: the controls, then three rows of slots with the tank beside them, then the bar, then the
 * status line, then the player's inventory. That does not fit in the standard height, and squeezing
 * it in would have put the bar or the status line through the inventory label. The arrangement is
 * in the constants below, top to bottom, because the order is the part that matters and the pixels
 * are the part that gets nudged.
 *
 * <p><b>Both dimensions scale with the vanilla GUI scale setting, and 240 is the ceiling for
 * that.</b> The panel is measured in GUI units, which vanilla multiplies by the scale on the way to
 * the framebuffer, so a fixed size here is not a fixed size on screen. What the fixed size does
 * decide is how the panel behaves when the <em>viewport</em> is small: at auto scale the window is
 * guaranteed at least 320 by 240 GUI units, because {@code Window.calculateScale} stops stepping
 * the scale up at exactly that point, so 240 tall just fits at 1280 by 720 and 320 is the width
 * past which nothing would. A manually chosen scale skips that guarantee and can be smaller still,
 * at which point the panel overflows its window — which is what every vanilla screen does in the
 * same situation, and is the trade a fixed size makes.
 *
 * <p><b>Nothing on this screen is a caption any more.</b> The water figure that used to hang under
 * the gauge is now the gauge's hover tooltip, in millibuckets, and the same reading is on the
 * tank's own screen and on the tank item — one number, three places, one wording.
 *
 * <p>No {@code @OnlyIn(Dist.CLIENT)}. The annotation stopped stripping classes at runtime in this
 * version, so it no longer protects a screen registered from a client event, and a class reached
 * only from {@code ClientSetup} needs no annotation at all — the same holds for every other screen
 * in this mod.
 */
public class PressureChamberScreen extends AbstractContainerScreen<PressureChamberMenu> {

  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath(
          Alchemizer.MOD_ID, "textures/gui/container/pressure_chamber.png");

  /**
   * The panel's width and height.
   *
   * <p>Larger than vanilla's 176 by 166. The machine needs three slot rows where a furnace needs
   * one, and the order below them is fixed: the bar, then the status line, then the inventory. Laid
   * out at the standard height, the bar would have had to cross the inventory label to stay below
   * the slots, so the panel grows instead and everything under the slots moves down with it.
   *
   * <p>The width is not the inventory's any more. At 176 the nine inventory wells and vanilla's
   * eight-pixel margin filled the panel exactly, which is why every vanilla container is that wide;
   * at 210 the inventory is centred instead, and the machine half spreads into the rest — the slot
   * group and the gauge sit on the new centre, and the bar and the controls row run to the new
   * margins.
   *
   * <p>They go to the parent's five-argument constructor rather than being assigned afterwards,
   * because the parent's size fields are final and it derives the inventory label's line from the
   * height as it constructs: at 240 the label lands on {@code 240 - 94 = 146}, twelve pixels above
   * the inventory's first row at 158 — the same gap vanilla leaves between its own pair. The height
   * and the menu's {@code INVENTORY_Y} are a pair, and are the only two places it is written down.
   */
  private static final int PANEL_WIDTH = 210;

  private static final int PANEL_HEIGHT = 240;

  /**
   * The tank gauge, in the right-hand column beneath the bottle slot.
   *
   * <p>Taller than the sixteen-by-fifty-two the tank's own screen draws, and deliberately so: this
   * one is a level read at a glance while a brew is running, and fifty-two pixels of travel across
   * a few thousand millibuckets moves too little to see. The tank's screen still draws the shorter
   * one, where a player has come to read the gauge and nothing is happening beside it.
   *
   * <p>These numbers are the recess's <b>interior</b>: the panel draws an 18-by-65 well at (185,
   * 61) and the water fills the 16-by-63 inside it, so the level's lowest row meets the well's
   * bottom border instead of covering it.
   */
  private static final int GAUGE_X = 186;

  private static final int GAUGE_Y = 62;

  private static final int GAUGE_WIDTH = 16;

  private static final int GAUGE_HEIGHT = 63;

  /**
   * The brew progress bar: thin, and running the panel's full inner width.
   *
   * <p>Spanning the width rather than sitting beside the gauge is what makes it read as the brew's
   * clock rather than as one more gauge. It is under everything the machine draws — the last slot
   * row and the tank both — so nothing sits between the bar and the machine it is reporting on.
   */
  private static final int BAR_X = 7;

  private static final int BAR_Y = 130;

  private static final int BAR_WIDTH = 195;

  private static final int BAR_HEIGHT = 3;

  /**
   * The four hourglass buttons, one after another, and the start button beside them.
   *
   * <p><b>Wider than they are tall, and that is the label's doing.</b> "15s" is eighteen pixels
   * before any margin, so a button narrower than what it says leaves the label with nowhere to go —
   * which is how the three settings above nine seconds used to read as pieces of themselves, the
   * vanilla button that drew them sliding a too-wide label back and forth within its own bounds
   * rather than clipping it. Twenty-four-wide leaves twenty inside the frame, which every setting
   * fits in with room to spare, and {@link SpriteButton} centres a label that fits and leaves it
   * where it is put.
   *
   * <p>Wider rather than shorter, because the seconds are what the row is for and a bare number
   * beside a batch control's "3x" reads as another multiplier. The four sit between the batch
   * control at their left and the start button at their right.
   *
   * <p><b>The row is spread to the panel's margins.</b> The batch control and the start button are
   * now the same twenty-eight wide, opposite each other at the two ends, and the presets sit
   * between them with six pixels of panel showing between each pair — a spacing wide enough that
   * the four read as four settings rather than as one segmented control. The row runs from six to
   * two hundred and two of a two-hundred-and-ten panel, so the margins are seven either side.
   */
  private static final int PRESET_X = 48;

  private static final int PRESET_Y = 15;

  private static final int PRESET_SPACING = 30;

  private static final int PRESET_WIDTH = 24;

  private static final int PRESET_HEIGHT = 20;

  /**
   * What the setting in force is labelled in.
   *
   * <p>The one hourglass the machine is on has to be told apart from the three it is not. Square
   * brackets did that in text, which made the chosen label the widest of the four — so the
   * highlight became the thing that did not fit, and the more clearly a setting was selected the
   * less of it could be read. A colour says the same thing and takes up no width at all, and it is
   * the amber the batch control and the progress bar already use, so the screen keeps one word for
   * "this is the one that is set".
   */
  private static final int PRESET_SELECTED_COLOR = 0xFFE8A33D;

  private static final int START_X = 175;

  private static final int START_WIDTH = 28;

  /**
   * The batch control: the left end of the controls row, level with the hourglasses.
   *
   * <p><b>Twenty-eight by twenty, which is the start button's own size.</b> The two are the ends of
   * the row and they face each other across it, so a row whose ends disagreed by eight pixels would
   * read as something that had slipped rather than as something arranged. It is no longer square,
   * which is the one thing about it that changed when the row was respread.
   *
   * <p>It sits left of the four hourglasses rather than in among them because it is not a choice
   * about time — it is a choice about how much of everything, and the one control on the screen
   * whose effect is not a number the recipe decides.
   *
   * <p>Drawn rather than added as a widget, like the tank gauge and unlike the buttons. A vanilla
   * button owns its own look and its own press, and neither is wanted here: this control takes the
   * wheel as well as the click, and its face has three states rather than two.
   */
  private static final int BATCH_X = 6;

  private static final int BATCH_Y = 15;

  private static final int BATCH_WIDTH = 28;

  private static final int BATCH_HEIGHT = 20;

  /**
   * The batch control's three faces: switched on, switched off, and locked for a running brew.
   *
   * <p>Three whole sprites rather than a recess and a face laid into it. The recess is part of the
   * control — a face with a frame round it is one drawing — and keeping the two apart was only ever
   * an artefact of building them out of rectangles. The label is not in them: it is a number that
   * changes as the wheel is turned and a word that is translated, and neither belongs in a picture.
   */
  private static final Identifier BATCH_ON = machine("batch_on");

  private static final Identifier BATCH_OFF = machine("batch_off");

  private static final Identifier BATCH_BUSY = machine("batch_busy");

  private static final int BATCH_TEXT_COLOR = 0xFFF0F0F0;

  /**
   * The line the status sits on, directly under the bar.
   *
   * <p>The water figure no longer shares this line. It is now the gauge's hover tooltip — see
   * {@link #extractBackground} — which leaves the line to the machine's health alone: the tier
   * while the still is sound, and the particular part of it that is wrong while it is not.
   */
  private static final int STATUS_Y = 135;

  /**
   * The recipe book: a button just right of the panel's upper edge, and the list that drops below
   * it.
   *
   * <p>Outside the panel rather than on it, because the panel's own width is spoken for — five
   * wells, a gauge and four hourglass buttons — and the book is the one control that is not about
   * this machine's state but about what a player might build next. It is drawn only once a funnel
   * is on top of the chamber, which is the tier the design gives it at, so a tier-1 or tier-2 still
   * shows no button at all rather than a dead one.
   *
   * <p><b>Flush with the panel's own top edge</b>, rather than level with the title text a few
   * pixels down from it. A control hanging off the edge of a frame reads as part of that frame's
   * chrome; the same control set down from the corner reads as something the frame happens to be
   * next to.
   *
   * <p><b>The list is as wide as its longest name.</b> A row is a name, a tier-and-time reading and
   * a star, and the name is the part a player reads the list by, so the box is measured from the
   * book in hand rather than fixed: the widest row decides, clamped between {@link #LIST_MIN_WIDTH}
   * and {@link #LIST_MAX_WIDTH}. A fixed column cannot do this job — the width that fits "Test
   * Potion" cuts "Potion of Fire Resistance" in half, and the width that fits the longest potion is
   * a band of empty grey beside every short one.
   */
  private static final int BOOK_X = PANEL_WIDTH + 2;

  private static final int BOOK_Y = 0;

  private static final int BOOK_SIZE = 20;

  private static final int LIST_X = PANEL_WIDTH + 2;

  /**
   * Where the list hangs from the button, written as the button's underside plus its gap rather
   * than as the number it comes to, so the two stay together: a list that has come adrift from the
   * button it belongs to is the one thing about this dropdown that would look like a fault.
   */
  private static final int LIST_Y = BOOK_Y + BOOK_SIZE + 2;

  /**
   * The floor the list is never narrower than, so a book of short names still draws a box a row
   * reads comfortably in rather than one whose name touches its star.
   */
  private static final int LIST_MIN_WIDTH = 132;

  /**
   * The ceiling, so one potion with a paragraph for a name cannot take the whole screen.
   *
   * <p>What is cut at this width is still cut by {@link #drawRow}, which is the behaviour for
   * anything longer than the list can hold; a datapack with a name past this is a datapack whose
   * names are not readable in a list this size whatever is done about it.
   */
  private static final int LIST_MAX_WIDTH = 260;

  /**
   * The gap the list leaves at the window's right edge.
   *
   * <p>Wide enough that the star at the end of a row is not flush against the edge, which is what
   * makes it look like the list is finished rather than cut off, and wide enough to hold a click
   * that lands a pixel or two past it.
   */
  private static final int LIST_MARGIN = 8;

  private static final int ROW_HEIGHT = 12;

  private static final int LIST_ROWS = 12;

  private static final int LIST_HEIGHT = ROW_HEIGHT * LIST_ROWS;

  /**
   * The list's box sprite, which is drawn at the widest list the screen will ever show.
   *
   * <p>Two more than the widest list and two more than the tallest, because the sprite covers the
   * one-pixel frame the list is drawn inside as well as the list itself — see {@link #drawListBox}.
   * This is not a size the list ever is; it is the surface every list's nine pieces are cut from.
   */
  private static final int LIST_BOX_SPRITE_WIDTH = LIST_MAX_WIDTH + 2;

  private static final int LIST_BOX_SPRITE_HEIGHT = LIST_HEIGHT + 2;

  /**
   * How long the list takes to grow out of the button's corner.
   *
   * <p>Long enough to be seen as an opening rather than a flicker, short enough that a player who
   * clicked to read a name is not made to wait for it. It is a duration rather than a number of
   * frames because a frame rate is not something this can know.
   */
  private static final long BOOK_OPEN_MILLIS = 140L;

  /**
   * The star at the right end of a row, and how far past it a click still counts as a click on it.
   */
  private static final int STAR_SIZE = 9;

  private static final int STAR_MARGIN = 4;

  private static final int STAR_REACH = 2;

  /**
   * The darkened cell behind the star, which is the star plus the reach either side of it.
   *
   * <p>The cell is one sprite rather than a fill because it is a fixed thirteen by twelve however
   * long the list is: only the box around it changes width, and a thing that does not change size
   * has no business being built out of numbers.
   */
  private static final int STAR_CELL_WIDTH = STAR_SIZE + 2 * STAR_REACH;

  /** A row's own margins: the name in from the left edge, and the gap between name and reading. */
  private static final int ROW_PAD = 4;

  private static final int META_GAP = 4;

  private static final Identifier STAR_OUTLINE = book("star_outline");

  private static final Identifier STAR_FILLED = book("star_filled");

  /** The book button's two faces, in the batch control's own language: open, and put away. */
  private static final Identifier BOOK_BUTTON_OPEN = book("button_open");

  private static final Identifier BOOK_BUTTON_CLOSED = book("button_closed");

  /**
   * The list's own art.
   *
   * <p>Its own rather than the panel's, because it is drawn over the world and not over the panel:
   * the panel's recess grey would be invisible against a dark cave and its label grey unreadable
   * against one. Vanilla's container grey is what a player already reads as "a surface a menu is
   * drawn on".
   *
   * <p><b>The box is drawn at its widest and cut down to the width in hand.</b> Its width is
   * measured from the book, so it is not a fixed size and cannot be one sprite; the sprite is
   * therefore the widest list the screen will ever draw, and the four corners, the four edges and
   * the middle are taken out of it at one to one. No piece is ever stretched, so the frame is as
   * thick at one end of a short list as at one end of a long one.
   */
  private static final Sprite LIST_BOX =
      new Sprite(book("list"), LIST_BOX_SPRITE_WIDTH, LIST_BOX_SPRITE_HEIGHT);

  /** The row under the pointer, drawn at its widest and cut to the list's width like the box. */
  private static final Sprite LIST_ROW_HOVER =
      new Sprite(book("row_hover"), LIST_MAX_WIDTH, ROW_HEIGHT);

  /** The rule under the last favourite, cut to the list's width like the box. */
  private static final Sprite LIST_SEPARATOR = new Sprite(book("row_separator"), LIST_MAX_WIDTH, 1);

  /** The star's own cell, darkened under the pointer so the two things a click can do differ. */
  private static final Sprite LIST_STAR_CELL =
      new Sprite(book("star_hover_cell"), STAR_CELL_WIDTH, ROW_HEIGHT);

  /**
   * A sprite and the size of the file it lives in.
   *
   * <p>The two travel together because a blit turns a source rectangle into texture coordinates
   * with the sheet's own dimensions: a piece cut out of the middle of a sprite is in the right
   * place only if they are known. A pair of numbers that has to agree with a file is better stated
   * once beside the file's name than at every place that cuts a piece out of it.
   *
   * @param id where the file is
   * @param sheetWidth how wide that file is
   * @param sheetHeight how tall that file is
   */
  private record Sprite(Identifier id, int sheetWidth, int sheetHeight) {}

  private static final int BOOK_META_COLOR = 0xFF6A6A6A;

  /**
   * The water in the gauge, and what it turns when the water is what a brew is waiting for.
   *
   * <p>Full-height sprites, because the level is an amount rather than a picture: the code takes
   * the bottom of one for however much is in the tank. The gauge's own recess is the panel
   * texture's, so nothing here draws an empty vessel.
   */
  private static final Identifier GAUGE_WATER = machine("gauge_water");

  private static final Identifier GAUGE_WATER_SHORT = machine("gauge_water_short");

  /** The brew bar's amber, cut to the width the brew has got through. */
  private static final Identifier BREW_BAR_FILL = machine("brew_bar_fill");

  /**
   * The flame in the fuel slot's well, drawn furnace-style: a sub-rectangle of the sprite rising
   * from the bottom of the well while the fire burns, behind whatever fuel sits in the slot.
   *
   * <p>The sprite is the size of the well's interior and sits exactly on the slot, so the flame
   * fills the well edge to edge rather than leaving a margin inside it.
   */
  private static final Identifier FLAME_FILL = machine("flame_fill");

  private static final int FLAME_X = PressureChamberMenu.FUEL_X;

  private static final int FLAME_Y = PressureChamberMenu.FUEL_Y;

  private static final int FLAME_SIZE = 16;

  /** The translucent red laid over a slot holding something a brew still needs. */
  private static final Identifier SLOT_MISSING = machine("slot_missing");

  /** Vanilla's own container label grey, a red for a refusal, and a green for a sound machine. */
  private static final int TEXT_COLOR = 0xFF404040;

  private static final int REFUSED_COLOR = 0xFFA02020;

  private static final int WORKING_COLOR = 0xFF2E7D32;

  private final List<SpriteButton> presetButtons = new ArrayList<>();

  private SpriteButton startButton;

  /** Whether the recipe list is showing. Screen state: the server is never told about the list. */
  private boolean bookOpen;

  /**
   * When the list was last opened, on the same clock every other animation in the game runs on.
   *
   * <p>Only read while the list is open, and only to decide how much of it to show.
   */
  private long bookOpenedAt;

  /**
   * How far down the list has been wound. Kept across a close, so reopening returns to the spot.
   */
  private int bookScroll;

  /**
   * The recipe last loaded from the book, or null when none has been.
   *
   * <p>Screen state, and deliberately not a fact about the machine. It is what lets a well the load
   * could not fill be drawn as the stack that would fill it; the server is not told, has no
   * opinion, and would have nothing to do with the answer if it did.
   */
  private @Nullable RecipeBookView.Entry ghost;

  /**
   * The list's width as last measured, and what it was measured against.
   *
   * <p>The book is the cache key because the server replaces it wholesale: a new one is a different
   * instance, so identity is enough and nothing here can go stale against a book that changed
   * underneath it. The window's width is the other half of the key because the measurement is
   * bounded by the room the window has — see {@link #listCeiling()} — so a resize can change the
   * answer without the book changing at all.
   */
  private int listWidth = LIST_MIN_WIDTH;

  private @Nullable RecipeBookView measuredBook;

  private int measuredForWidth = -1;

  /**
   * The only screen here whose panel is not the default size, which is why both dimensions are
   * passed on rather than left to the parent's three-argument constructor.
   *
   * @param menu the brewing menu this screen is a view of
   * @param inventory the player's inventory, which the menu also draws
   * @param title the screen's title, which is the block's own name
   */
  public PressureChamberScreen(PressureChamberMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);

    // All four label positions are the parent's to default and this screen's to place; where each
    // goes is PanelLabels' business, because the other two brewing screens have to agree with this
    // one rather than each deciding for itself.
    this.titleLabelX = PanelLabels.TITLE_X;
    this.titleLabelY = PanelLabels.TITLE_Y;
    this.inventoryLabelX = PanelLabels.inventoryLabelX(PressureChamberMenu.INVENTORY_X);
    this.inventoryLabelY = PanelLabels.inventoryLabelY(PANEL_HEIGHT);
  }

  @Override
  protected void init() {
    super.init();

    // Rebuilt from scratch rather than reused: this runs again on every resize, and the widgets are
    // positioned from the panel's new origin. Anything kept from the previous pass would be left
    // where the screen used to be.
    presetButtons.clear();
    for (HourglassPreset preset : HourglassPreset.values()) {
      int id = preset.ordinal();
      SpriteButton button =
          addRenderableWidget(
              new SpriteButton(
                  leftPos + PRESET_X + id * PRESET_SPACING,
                  topPos + PRESET_Y,
                  PRESET_WIDTH,
                  PRESET_HEIGHT,
                  presetLabel(preset),
                  () -> send(id)));
      // The tooltip is still the one the vanilla button carried: what a setting is worth in
      // seconds is the same fact whether the face is vanilla's or ours.
      button.setTooltip(
          Tooltip.create(
              Component.translatable("gui.alchemizer.hourglass.tooltip", preset.baseSeconds())));
      presetButtons.add(button);
    }

    startButton =
        addRenderableWidget(
            new SpriteButton(
                leftPos + START_X,
                topPos + PRESET_Y,
                START_WIDTH,
                PRESET_HEIGHT,
                Component.translatable("gui.alchemizer.start"),
                () -> send(PressureChamberMenu.BUTTON_START)));

    refreshButtons();
  }

  /**
   * Keeps the buttons honest, once a tick.
   *
   * <p>The state they carry is the machine's, not the screen's: the record of which hourglass is
   * chosen and whether a brew could start both come back from the server through the menu's data
   * slots. Redrawing them from that rather than from what was last clicked is what stops a button
   * from staying lit after the server has moved on. A tick is often enough for a control a player
   * presses by hand, and it keeps this off the redraw path.
   */
  @Override
  protected void containerTick() {
    super.containerTick();
    refreshButtons();
  }

  /**
   * Forgets the visit's own state when the screen goes away.
   *
   * <p>Both fields are the screen's and neither is the machine's, so neither has anywhere to live
   * once the player has walked off. The list being left open would be a curiosity; the ghost would
   * not, because a player who came back later would be shown a recipe they had loaded in a previous
   * session and never asked for again.
   *
   * <p>Resizing does not reach here, which is deliberate: a window dragged smaller with a load in
   * progress must not lose the ghosts it is showing.
   */
  @Override
  public void removed() {
    super.removed();
    setBookOpen(false);
    ghost = null;
  }

  /**
   * Opens or puts away the list, stamping the moment it was opened.
   *
   * <p>The stamp is taken here rather than where the list is drawn, because an animation that reset
   * its own clock on every frame would be a list that never finished opening.
   *
   * @param open whether the list should be showing
   */
  private void setBookOpen(boolean open) {
    if (open == bookOpen) {
      return;
    }
    bookOpen = open;
    if (open) {
      bookOpenedAt = Util.getMillis();
    }
  }

  /**
   * How much of the list has grown out of the button's corner.
   *
   * <p>Wall-clock rather than ticks: a tick is 50 ms, so a 140 ms opening would be three frames
   * with two of them visibly the same. The clock decides nothing but the drawing, so a frame that
   * arrives late shows a finished list rather than a wrong one.
   *
   * @return the fraction of the list to show, between none of it and all of it
   */
  private float bookGrowth() {
    return Math.clamp((Util.getMillis() - bookOpenedAt) / (float) BOOK_OPEN_MILLIS, 0.0F, 1.0F);
  }

  private void refreshButtons() {
    Optional<StartResult> readiness = menu.readiness();
    boolean brewing = readiness.isPresent() && readiness.get() == StartResult.ALREADY_BREWING;
    Optional<HourglassPreset> selected = menu.selectedPreset();

    for (int i = 0; i < presetButtons.size(); i++) {
      SpriteButton button = presetButtons.get(i);
      boolean isSelected = selected.isPresent() && selected.get().ordinal() == i;
      // Colour rather than a longer label: the label is what has to fit the button, and a cue made
      // of text is a cue that can be scrolled off the thing it is marking.
      if (isSelected) {
        button.setFGColor(PRESET_SELECTED_COLOR);
      } else {
        button.clearFGColor();
      }
      // A running brew has already been charged for the time it was started at, so the setting is
      // fixed for its duration. Leaving the buttons live would let a player change the dial on a
      // clock that is no longer reading it.
      button.active = !brewing;
    }

    startButton.active = readiness.isPresent() && readiness.get() == StartResult.STARTED;
  }

  /**
   * The four buttons say one thing each and it never changes, which is the point: which of them is
   * chosen is told by {@link #PRESET_SELECTED_COLOR}, not by the label, so there is one label per
   * setting rather than two and nothing here to keep in step with the machine's state.
   *
   * @param preset the setting the button stands for
   * @return the button's label
   */
  private static Component presetLabel(HourglassPreset preset) {
    return Component.translatable("gui.alchemizer.seconds", preset.baseSeconds());
  }

  /**
   * Asks the server to press one button.
   *
   * <p>A button id is the entire request. Nothing about the machine's state is sent with it,
   * because the server already has the machine.
   *
   * @param buttonId the button's id, as {@link PressureChamberMenu} numbers them
   */
  private void send(int buttonId) {
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
  }

  /**
   * Pours a carried water container into the tanks when the tank gauge is clicked.
   *
   * <p>The gauge is not a slot, so there is no item exchange to hook and this has to be a
   * coordinate test rather than a slot action. The test is deliberately narrow — the gauge's own
   * rectangle, which overlaps no slot and no button — so an ordinary click anywhere else still
   * reaches the parent untouched.
   *
   * <p>The cursor is what is read, so the player picks the bucket up first and then clicks the
   * gauge. The guard on the cursor holding water is a packet-saving one, not the decision: the
   * server re-reads its own cursor and refuses if the client was wrong, so nothing here can make
   * the machine do something the server would not.
   *
   * <p><b>The click's focus is dropped on the way out.</b> Vanilla hands a clicked widget the
   * keyboard focus so that Tab carries on from wherever the mouse left off, and a focused widget is
   * drawn with its highlighted sprite — which for a button is the same grey face inside a one-pixel
   * white frame. That frame outlives the click: it stays on the last button pressed while the mouse
   * moves away and the highlight is looked at somewhere else, and on this screen it says nothing,
   * because no control here is driven by the keyboard. Left alone it reads as a second, competing
   * highlight sitting beside the amber one, and the player has no way to know which of the two the
   * machine is actually set to. Tab still focuses, and still draws the frame, which is the one time
   * it is telling a player something true.
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
      send(PressureChamberMenu.BUTTON_FILL_TANK);
      return true;
    }
    if (event.button() == 0 && isOverBatch(event.x(), event.y())) {
      send(PressureChamberMenu.BUTTON_BATCH_TOGGLE);
      return true;
    }
    if (event.button() == 0 && hasBook()) {
      if (isOverBookButton(event.x(), event.y())) {
        setBookOpen(!bookOpen);
        return true;
      }
      if (bookOpen) {
        int row = bookRowAt(event.x(), event.y());
        if (row >= 0) {
          pressBookRow(row, event.x());
        } else {
          // A click anywhere else puts the list away instead of falling through to the slot beneath
          // it. A dropdown that let the click past would pick an item up on the way out.
          setBookOpen(false);
        }
        return true;
      }
    }
    boolean handled = super.mouseClicked(event, doubleClick);
    if (handled) {
      clearFocus();
    }
    return handled;
  }

  /**
   * Runs one press on the open list: a star toggled, or a recipe loaded.
   *
   * <p>The index sent is the recipe's position in the book the server sent, not in the order the
   * list is drawn in. The two differ as soon as anything is favourited — the favourites are drawn
   * first — and the server resolves the index against its own list, so sending the drawn position
   * would load the wrong recipe the moment a player starred one.
   *
   * @param row the position in the drawn order that was clicked
   * @param x the click's screen x, which decides whether the star or the row was hit
   */
  private void pressBookRow(int row, double x) {
    List<RecipeBookView.Entry> order = book().displayOrder();
    if (row < 0 || row >= order.size()) {
      return;
    }
    RecipeBookView.Entry entry = order.get(row);
    int position = book().indexOf(entry.recipeId());
    if (position < 0) {
      return;
    }

    boolean star = isOverStar(x);
    send(PressureChamberMenu.BUTTON_BOOK_BASE + position * 2 + (star ? 1 : 0));
    if (!star) {
      // Remembered so the wells the load cannot fill are ghosted. Nothing is sent about it: which
      // stack is missing is already on the client, and the server has no use for a picture.
      ghost = entry;
    }
  }

  /**
   * Winds the batch dial when the wheel is used over the control.
   *
   * <p>The wheel is the control's only way of setting a size, and the only wheel this screen has.
   * It is claimed by hover rather than by the screen being open, so a wheel anywhere else still
   * reaches whatever is underneath — which is what will let the recipe book's list take the same
   * gesture without the two fighting over it.
   *
   * <p>Only the direction is acted on. The parent's scroll amount is a fraction of a notch and is
   * already normalised by the time a widget sees it, so a notch up is one step up; the server moves
   * its own number and never learns what the client thought the amount was.
   *
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @param scrollX horizontal scroll, unused
   * @param scrollY how far the wheel turned; positive is away from the player
   * @return whether the scroll was consumed
   */
  @Override
  public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
    if (isOverBatch(x, y) && scrollY != 0.0) {
      send(
          scrollY > 0
              ? PressureChamberMenu.BUTTON_BATCH_UP
              : PressureChamberMenu.BUTTON_BATCH_DOWN);
      return true;
    }
    if (bookOpen && isOverList(x, y) && scrollY != 0.0) {
      // Away from the player is up the list, which is the way every other list in the game moves.
      // The scroll stops at both ends rather than wrapping: a list that jumped from the last recipe
      // back to the first would be a list a player cannot read the end of.
      int step = scrollY > 0 ? -1 : 1;
      bookScroll = Math.max(0, Math.min(bookScroll + step, maxBookScroll()));
      return true;
    }
    return super.mouseScrolled(x, y, scrollX, scrollY);
  }

  /**
   * @return the book the client was last sent, which is empty until the first sync arrives
   */
  private RecipeBookView book() {
    // getData on a client creates the default and asks to sync it, which is a no-op off a server —
    // the same call the entity makes for any other attachment, and the reason the client can read
    // this at all.
    return minecraft == null || minecraft.player == null
        ? RecipeBookView.empty()
        : minecraft.player.getData(ModAttachments.RECIPE_BOOK.get());
  }

  /**
   * The list's width for the book in hand, in the window it is being drawn in.
   *
   * <p>Measured once per book and per window size rather than once per row drawn: the answer
   * depends only on what the book holds and how much window there is, and this is asked by every
   * hit test as well as by the drawing, several times a frame.
   *
   * @return how wide to draw the list, in pixels
   */
  private int listWidth() {
    RecipeBookView current = book();
    if (current != measuredBook || width != measuredForWidth) {
      measuredBook = current;
      measuredForWidth = width;
      listWidth = measureListWidth(current);
    }
    return listWidth;
  }

  /**
   * @param view the book to measure
   * @return the width the widest row needs, held between the list's own bounds
   */
  private int measureListWidth(RecipeBookView view) {
    int widest = 0;
    for (RecipeBookView.Entry entry : view.displayOrder()) {
      // Every column drawRow lays out, in the order it lays them out.
      int row =
          ROW_PAD
              + font.width(entry.result().getHoverName())
              + META_GAP
              + font.width(metaFor(entry))
              + META_GAP
              + STAR_SIZE
              + STAR_MARGIN;
      widest = Math.max(widest, row);
    }
    return Math.clamp(widest, LIST_MIN_WIDTH, listCeiling());
  }

  /**
   * The widest the list may be drawn in this window.
   *
   * <p>{@link #LIST_MAX_WIDTH} is the width past which one long name is not worth more of the
   * screen; this is the width past which the list would leave the screen. The list hangs off the
   * panel's right edge, so its room shrinks along with the window and with every step up in GUI
   * scale — at scale 3 the whole window is narrower than the panel plus {@link #LIST_MAX_WIDTH},
   * and a list drawn at its ceiling would put its own star outside the screen where it cannot be
   * clicked.
   *
   * <p>The floor {@link #LIST_MIN_WIDTH} is returned rather than the honest room when the room is
   * smaller than that, because {@code Math.clamp} refuses bounds that cross and because a list
   * narrower than the floor is unreadable either way: at that point the panel is most of the window
   * and the name is going to be cut whatever this returns.
   *
   * @return the ceiling for the measured width, in pixels
   */
  private int listCeiling() {
    int room = width - (leftPos + LIST_X) - LIST_MARGIN;
    return Math.max(LIST_MIN_WIDTH, Math.min(LIST_MAX_WIDTH, room));
  }

  /**
   * @param entry the recipe
   * @return the reading at the right of its row: the tier it asks for and its hourglass
   */
  private static Component metaFor(RecipeBookView.Entry entry) {
    return Component.translatable(
        "gui.alchemizer.book.tier",
        entry.tier(),
        Component.translatable("gui.alchemizer.seconds", entry.seconds()));
  }

  /**
   * @return whether the still has the funnel that gives it a book, which is what the button waits
   *     for — the tier arrives as a data slot, so it is zero until the first sync and the button
   *     appears with it
   */
  private boolean hasBook() {
    return menu.stillTier() >= PressureChamberMenu.FUNNEL_TIER;
  }

  /**
   * The areas this screen draws outside its own panel, in screen coordinates.
   *
   * <p>This is the one thing about the screen that JEI cannot work out for itself: that it draws
   * outside its own panel, and where. It is public because the plugin that tells JEI about it lives
   * in another package, and it is asked afresh rather than remembered because JEI re-reads the
   * exclusion areas every frame — see {@code AlchemizerJeiPlugin} for why that matters.
   *
   * <p><b>Both the button and the list, button first.</b> JEI lays its ingredient list out in the
   * strip to the right of the panel, which is exactly where the book hangs: the button four pixels
   * past the panel's right edge, the list below it. Leaving the button out of this, on the grounds
   * that holding a column clear for a twenty-pixel control is a poor trade, was reasoning about the
   * wrong trade. JEI does not move its list clear of an area — it marks the individual
   * <em>slots</em> that overlap one and reflows the rest into the slots that are left — so
   * excluding the button costs the two or three icons that would have sat under it and nothing
   * else. At GUI scale 4 the strip beside the panel is narrow enough that the grid does reach it,
   * and icons drawn across a control are worse than a page a little short.
   *
   * <p><b>Nothing without a funnel, and the list only while it is open.</b> The button is drawn
   * only once the still has the Funnel that gives it a book, so below that tier neither area exists
   * and JEI keeps the whole strip. The list's area comes and goes with the dropdown rather than
   * tracking the opening animation: an area that changed on every frame of a 140 ms growth would be
   * a re-layout of JEI's ingredient list on every one of those frames. The animation is the
   * screen's own business and a player watching it is not reading JEI.
   *
   * <p>The list's width is its measured width rather than the widest it could ever be: a book of
   * short names should not block more slots than it draws over.
   *
   * @return the areas to keep clear, in screen coordinates, or an empty list when the screen draws
   *     nothing outside its panel
   */
  public List<Rect2i> guiExtraAreas() {
    if (!hasBook()) {
      return List.of();
    }
    List<Rect2i> areas = new ArrayList<>(2);
    areas.add(new Rect2i(leftPos + BOOK_X, topPos + BOOK_Y, BOOK_SIZE, BOOK_SIZE));
    if (bookOpen) {
      areas.add(new Rect2i(leftPos + LIST_X, topPos + LIST_Y, listWidth(), LIST_HEIGHT));
    }
    return areas;
  }

  /**
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @return whether the point is on the book button, which is only claimed while the list has a
   *     machine to hang off
   */
  private boolean isOverBookButton(double x, double y) {
    return hasBook()
        && x >= leftPos + BOOK_X
        && x < leftPos + BOOK_X + BOOK_SIZE
        && y >= topPos + BOOK_Y
        && y < topPos + BOOK_Y + BOOK_SIZE;
  }

  /**
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @return whether the point is anywhere on the open list, rows and all
   */
  private boolean isOverList(double x, double y) {
    return bookOpen
        && x >= leftPos + LIST_X
        && x < leftPos + LIST_X + listWidth()
        && y >= topPos + LIST_Y
        && y < topPos + LIST_Y + LIST_HEIGHT;
  }

  /**
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @return the position in the drawn order of the row under the point, or -1 when the point is not
   *     on a row that has a recipe behind it
   */
  private int bookRowAt(double x, double y) {
    if (!isOverList(x, y)) {
      return -1;
    }
    int row = bookFirstRow() + (int) ((y - (topPos + LIST_Y)) / ROW_HEIGHT);
    return row < book().displayOrder().size() ? row : -1;
  }

  /**
   * @param x the click's screen x
   * @return whether the click was on the star at the end of a row rather than on the row itself
   */
  private boolean isOverStar(double x) {
    int starLeft = leftPos + LIST_X + listWidth() - STAR_MARGIN - STAR_SIZE;
    return x >= starLeft - STAR_REACH && x < starLeft + STAR_SIZE + STAR_REACH;
  }

  /**
   * @return the first position in the drawn order the list is showing, clamped so that a book that
   *     shrank under a wound-down list still has rows to draw
   */
  private int bookFirstRow() {
    return Math.min(bookScroll, maxBookScroll());
  }

  /**
   * @return how far the list may be wound before its last row is at the bottom of the box
   */
  private int maxBookScroll() {
    return Math.max(0, book().displayOrder().size() - LIST_ROWS);
  }

  /**
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @return whether the point is on the batch control, which is only claimed while it is shown
   */
  private boolean isOverBatch(double x, double y) {
    return menu.batchMaximum() > 1
        && x >= leftPos + BATCH_X
        && x < leftPos + BATCH_X + BATCH_WIDTH
        && y >= topPos + BATCH_Y
        && y < topPos + BATCH_Y + BATCH_HEIGHT;
  }

  /**
   * @param x the click's screen x
   * @param y the click's screen y
   * @return whether the click landed on the tank gauge
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

    // Held once rather than asked for at each use, so every part of one frame agrees about what the
    // machine is doing. An empty result means the first sync has not arrived yet, not that the
    // still
    // is fine; nothing is highlighted until it does.
    Optional<StartResult> readiness = menu.readiness();

    drawTank(graphics, readiness);
    drawBrewBar(graphics);
    drawFlame(graphics);
    drawBatchControl(graphics, readiness);
    drawMissingSlots(graphics, readiness);
    drawStatus(graphics, readiness);
    drawGhosts(graphics);
    drawBookButton(graphics);
    drawBookList(graphics, mouseX, mouseY);

    // Deferred rather than drawn here: this version collects tooltips during extraction and shows
    // them once the frame is assembled, so the request is legal from the background pass. The first
    // request for a frame wins, which is what keeps the slot tooltips the parent asks for later
    // from
    // overwriting this one.
    if (isOverGauge(mouseX, mouseY)) {
      graphics.setTooltipForNextFrame(
          font,
          Component.translatable(
              "gui.alchemizer.water.mb", menu.waterStoredMb(), menu.waterCapacityMb()),
          mouseX,
          mouseY);
    } else if (isOverBatch(mouseX, mouseY)) {
      graphics.setTooltipForNextFrame(font, batchTooltip(readiness), mouseX, mouseY);
    } else if (isOverBookButton(mouseX, mouseY)) {
      graphics.setTooltipForNextFrame(
          font, Component.translatable("gui.alchemizer.book.tooltip"), mouseX, mouseY);
    }
  }

  /**
   * Draws the batch control, which is only there when a condenser is.
   *
   * <p>The control is hidden rather than greyed on a still with no condensers. A machine that
   * cannot batch has no batch setting to show, and a greyed box on every tier-1 still would be a
   * control the player has to be told to ignore — the same argument the design makes for the fuel
   * gate being invisible until it refuses something.
   *
   * <p>The number shown is the one the next brew would actually make, not the dial. They differ
   * whenever the dial has been wound past what the condensers allow, and the machine's answer is
   * the one the player needs: a control reading ten over a still that will hand over two is a
   * control that lies.
   *
   * @param graphics the frame being extracted
   * @param readiness what a brew started now would come to
   */
  private void drawBatchControl(GuiGraphicsExtractor graphics, Optional<StartResult> readiness) {
    if (menu.batchMaximum() <= 1) {
      return;
    }

    // A running brew has already been charged at the batch it started with, so the control is inert
    // for its duration. It is dimmed rather than hidden, so that the size the brew is making stays
    // legible while it is made.
    boolean busy = readiness.isPresent() && readiness.get() == StartResult.ALREADY_BREWING;
    Identifier face = busy ? BATCH_BUSY : menu.batchEnabled() ? BATCH_ON : BATCH_OFF;

    int left = leftPos + BATCH_X;
    int top = topPos + BATCH_Y;
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        face,
        left,
        top,
        0.0F,
        0.0F,
        BATCH_WIDTH,
        BATCH_HEIGHT,
        BATCH_WIDTH,
        BATCH_HEIGHT);

    Component label = Component.translatable("gui.alchemizer.batch.label", menu.batchMultiplier());
    graphics.text(
        font, label, left + BATCH_WIDTH / 2 - font.width(label) / 2, top + 6, BATCH_TEXT_COLOR);
  }

  /**
   * @param readiness what a brew started now would come to
   * @return what the batch control says when hovered
   */
  private Component batchTooltip(Optional<StartResult> readiness) {
    if (readiness.isPresent() && readiness.get() == StartResult.ALREADY_BREWING) {
      return Component.translatable("gui.alchemizer.batch.tooltip.busy", menu.batchMultiplier());
    }
    if (!menu.batchEnabled()) {
      return Component.translatable("gui.alchemizer.batch.tooltip.off");
    }
    return Component.translatable("gui.alchemizer.batch.tooltip.on", menu.batchMultiplier());
  }

  /**
   * Draws the water level, which is the whole of the reading.
   *
   * <p>There is no figure beside it any more. The caption that used to hang under the gauge is gone
   * — a player could not tell what a dose was, and it was this mod's own word rather than anything
   * the game or the design document says — and the exact amount is now on hover, in millibuckets,
   * which is what the tank is actually measured in.
   *
   * <p><b>The vessel is the panel's, the water is the sprite's.</b> The recess the gauge sits in is
   * painted into the panel texture, so nothing is drawn where there is no water: an empty gauge is
   * the well it was always drawn into rather than a black rectangle laid over it. What the code
   * contributes is the level, and it takes it off the bottom of a full-height sprite precisely so
   * that the amount stays a number while the colour and the shading stay a file.
   *
   * @param graphics the frame being extracted
   * @param readiness what a brew started now would come to
   */
  private void drawTank(GuiGraphicsExtractor graphics, Optional<StartResult> readiness) {
    int capacity = menu.waterCapacityMb();
    float filled = capacity <= 0 ? 0.0F : Math.min(1.0F, menu.waterStoredMb() / (float) capacity);
    int filledHeight = Math.round(filled * GAUGE_HEIGHT);
    if (filledHeight <= 0) {
      return;
    }

    // Bottom-up, like the vessel itself: a gauge that drained from the top would read as a progress
    // bar rather than as a level. The source rectangle is the bottom of the sprite, one pixel to
    // one, so the water's surface lands wherever the amount puts it and never moves a row.
    boolean waterShort = readiness.isPresent() && readiness.get() == StartResult.NEEDS_WATER;
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        waterShort ? GAUGE_WATER_SHORT : GAUGE_WATER,
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
   * Draws the brew's progress as a bar that fills from the left.
   *
   * @param graphics the frame being extracted
   */
  private void drawBrewBar(GuiGraphicsExtractor graphics) {
    int filled = Math.round(menu.brewProgress() * BAR_WIDTH);
    if (filled <= 0) {
      // The trough is the panel's own art, so a brew that has not started shows it and nothing
      // else — see drawTank for why the empty half is left to the texture.
      return;
    }
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        BREW_BAR_FILL,
        leftPos + BAR_X,
        topPos + BAR_Y,
        0.0F,
        0.0F,
        filled,
        BAR_HEIGHT,
        BAR_WIDTH,
        BAR_HEIGHT);
  }

  /**
   * Draws the flame inside the fuel slot's well while the chamber's fire burns.
   *
   * <p>Furnace-style: the sprite is cut from the bottom up as the fuel burns down, and the flame
   * sits behind the slot's item because the background pass runs before items render. Nothing is
   * drawn when the chamber is cold — the well's recess is the panel's own art.
   *
   * @param graphics the frame being extracted
   */
  private void drawFlame(GuiGraphicsExtractor graphics) {
    if (!menu.isLit()) {
      return;
    }
    int flameHeight = Math.max(1, Math.round(menu.burnProgress() * FLAME_SIZE));
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

  /**
   * Reds the exact slots a brew is waiting on.
   *
   * <p>Which ones is the server's answer, not the screen's: the same value that refuses a start
   * names the thing that is missing. Only the two slot-shaped reasons are drawn here — a still that
   * is not assembled and a tank that is short are shown by the status line and the gauge, because
   * neither is a slot and pointing at one would be pointing at the wrong thing.
   *
   * @param graphics the frame being extracted
   * @param readiness what a brew started now would come to
   */
  private void drawMissingSlots(GuiGraphicsExtractor graphics, Optional<StartResult> readiness) {
    if (readiness.isEmpty()) {
      return;
    }
    switch (readiness.get()) {
      case NOTHING_TO_BREW ->
          highlightSlots(graphics, 0, PressureChamberBlockEntity.SLOT_INGREDIENT_COUNT);
      case NOT_ENOUGH_INGREDIENTS -> highlightShortIngredients(graphics);
      case NEEDS_BOTTLES ->
          highlightSlots(
              graphics,
              PressureChamberBlockEntity.SLOT_BOTTLE,
              PressureChamberBlockEntity.SLOT_BOTTLE + 1);
      case OUTPUT_FULL ->
          highlightSlots(
              graphics,
              PressureChamberBlockEntity.SLOT_OUTPUT,
              PressureChamberBlockEntity.SLOT_OUTPUT + 1);
      default -> {
        // Every other reason is either not a slot's fault or not a refusal at all.
      }
    }
  }

  /**
   * Reds the wells that cannot fill a batch, and only those.
   *
   * <p>Narrower than {@link #highlightSlots} over the whole range, and it has to be: a player
   * brewing ten at a time usually has some wells stocked and some not, and reddening all five would
   * point at the four that are fine. An empty well is not short — the machine consumes nothing from
   * it — so it is left alone, which is also what makes this agree with the server's own test.
   *
   * <p>Read from the synced slots rather than from a number the server sent, because the count in a
   * well is already on the client and the batch it is measured against is the multiplier the server
   * itself will price a start at.
   *
   * @param graphics the frame being extracted
   */
  private void highlightShortIngredients(GuiGraphicsExtractor graphics) {
    int batch = menu.batchMultiplier();
    for (int index = 0; index < PressureChamberBlockEntity.SLOT_INGREDIENT_COUNT; index++) {
      ItemStack stack = menu.getSlot(index).getItem();
      if (!stack.isEmpty() && stack.getCount() < batch) {
        highlightSlots(graphics, index, index + 1);
      }
    }
  }

  /**
   * @param graphics the frame being extracted
   * @param from the first slot to redden, by the menu's slot index
   * @param to the slot after the last one
   */
  private void highlightSlots(GuiGraphicsExtractor graphics, int from, int to) {
    for (int index = from; index < to; index++) {
      Slot slot = menu.slots.get(index);
      // The panel is drawn before the slots are, so this sits behind the item rather than over it.
      // A filled slot therefore reads as "this one" instead of being hidden by the tint. The sprite
      // is the well's own sixteen pixels, so the tint covers the well and not a pixel of it more —
      // the translucent red is in the file, and a pack that wants a different marking redraws it.
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          SLOT_MISSING,
          leftPos + slot.x,
          topPos + slot.y,
          0.0F,
          0.0F,
          16,
          16,
          16,
          16);
    }
  }

  /**
   * Draws the book button, which is only there once the still has a funnel.
   *
   * <p>One sprite per state, in the same language as the batch control beside the hourglasses: a
   * recess, a face and a spine, all three drawn in the file rather than assembled out of
   * rectangles. The face carries the state — lit while the list is open, grey while it is not — so
   * a player can tell from the button alone whether they left something open, which is the one
   * thing a button anchored outside the panel cannot show by overlapping the panel.
   *
   * @param graphics the frame being extracted
   */
  private void drawBookButton(GuiGraphicsExtractor graphics) {
    if (!hasBook()) {
      return;
    }

    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        bookOpen ? BOOK_BUTTON_OPEN : BOOK_BUTTON_CLOSED,
        leftPos + BOOK_X,
        topPos + BOOK_Y,
        0.0F,
        0.0F,
        BOOK_SIZE,
        BOOK_SIZE,
        BOOK_SIZE,
        BOOK_SIZE);
  }

  /**
   * Draws the open list, clipped to however much of it has grown out of the button yet.
   *
   * <p>The list is drawn in the background pass, over the world and over the panel's edge, because
   * it opens to the right of the panel where the world is.
   *
   * @param graphics the frame being extracted
   * @param mouseX the mouse's screen x
   * @param mouseY the mouse's screen y
   */
  private void drawBookList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    if (!bookOpen || !hasBook()) {
      return;
    }

    int left = leftPos + LIST_X;
    int top = topPos + LIST_Y;

    // The list grows out of the button's corner. The panel is laid out at its full size whatever
    // the growth is and the scissor is what lets only the grown part of it through, so the layout
    // never depends on the animation's progress: no row moves, no row is measured twice, and the
    // frame after the animation ends is drawn by the same code as the frame before it started.
    //
    // A single push and pop around one call, rather than scissors around the pieces, because the
    // rows are what must be cut and the rows are drawn in one order. The scissor sits one pixel
    // outside the border so the border's own thickness appears with the growth rather than before
    // it. Nothing between here and the pop may return early or the scissor would never be lifted.
    float growth = bookGrowth();
    graphics.enableScissor(
        left - 1,
        top - 1,
        left + Math.round(listWidth() * growth) + 1,
        top + Math.round(LIST_HEIGHT * growth) + 1);
    drawBookRows(graphics, left, top, mouseX, mouseY);
    graphics.disableScissor();
  }

  /**
   * Draws the open list at its full size: the panel, the rows, and the line under the favourites.
   *
   * <p>Everything here is drawn as if the list were fully open; the caller's scissor is what makes
   * it look otherwise. That is what keeps {@code drawRow}'s clipping and the star hit tests in
   * agreement — the hit tests ask about the full rectangle, and the animation only ever hides part
   * of it, so a click during the growth lands where the full-size list would put it.
   *
   * <p>Twelve rows are drawn and the rest are reached by winding, so the box is a fixed size
   * whatever the datapack ships.
   *
   * <p>The separator under the last favourite is what makes the pinned block legible as a block.
   * The favourites are drawn first and in the order they were starred, so without a line at the end
   * of them the transition back into the book's own order would read as an accident of sorting.
   *
   * @param graphics the frame being extracted
   * @param left the list's left edge, in screen coordinates
   * @param top the list's top edge, in screen coordinates
   * @param mouseX the mouse's screen x
   * @param mouseY the mouse's screen y
   */
  private void drawBookRows(
      GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {
    int right = left + listWidth();
    int bottom = top + LIST_HEIGHT;
    drawListBox(graphics, left - 1, top - 1, right + 1, bottom + 1);

    List<RecipeBookView.Entry> order = book().displayOrder();
    if (order.isEmpty()) {
      graphics.text(
          font,
          Component.translatable("gui.alchemizer.book.empty"),
          left + ROW_PAD,
          top + ROW_PAD,
          BOOK_META_COLOR);
      return;
    }

    int first = bookFirstRow();
    int visible = Math.min(LIST_ROWS, order.size() - first);
    int favourites = book().favourites().size();
    int hovered = bookRowAt(mouseX, mouseY);
    boolean overStar = isOverStar(mouseX);

    for (int index = 0; index < visible; index++) {
      int position = first + index;
      int rowTop = top + index * ROW_HEIGHT;
      if (position == hovered) {
        // The row is highlighted as a whole and the star's own cell is darkened over it, so the two
        // things a click can do are distinguishable before it is made rather than after. The row's
        // width is the list's, so its sprite is cut to it exactly as the box below is.
        blitPiece(graphics, LIST_ROW_HOVER, left, rowTop, 0, 0, listWidth(), ROW_HEIGHT);
        if (overStar) {
          int starLeft = right - STAR_MARGIN - STAR_SIZE;
          blitPiece(
              graphics,
              LIST_STAR_CELL,
              starLeft - STAR_REACH,
              rowTop,
              0,
              0,
              STAR_CELL_WIDTH,
              ROW_HEIGHT);
        }
      }
      drawRow(graphics, order.get(position), left, right, rowTop);

      if (position == favourites - 1 && position < order.size() - 1) {
        blitPiece(graphics, LIST_SEPARATOR, left, rowTop + ROW_HEIGHT - 1, 0, 0, listWidth(), 1);
      }
    }
  }

  /**
   * Draws the list's box: a frame and the surface inside it, cut out of one sprite.
   *
   * <p><b>Nine pieces, none of them stretched.</b> The list's width is measured from the book, so
   * the box cannot be a single fixed sprite; the sprite is instead the widest box the screen will
   * ever draw, and each piece — four corners, four edges, the middle — is a one-to-one rectangle
   * taken out of it. The corners come from the sprite's own corners, the edges run along its own
   * edges, and only the sides of the middle piece change with the list. Nothing is scaled, so the
   * frame is exactly as thick at the end of a short list as at the end of a long one, and a pack
   * that redraws the sprite gets its corners where it drew them.
   *
   * <p>The right and bottom pieces are cut from the last row and column of the sprite rather than
   * from the edge of the drawn box, because that is where a frame's other side lives. They are
   * still entirely inside the sprite, so no piece is ever sampled past the file.
   *
   * @param graphics the frame being extracted
   * @param left the box's left edge, which is one pixel left of the list's own
   * @param top the box's top edge, which is one pixel above the list's own
   * @param right the box's right edge, one past the list's last column
   * @param bottom the box's bottom edge, one past the list's last row
   */
  private static void drawListBox(
      GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
    int width = right - left;
    int height = bottom - top;
    int far = LIST_BOX_SPRITE_WIDTH - 1;
    int low = LIST_BOX_SPRITE_HEIGHT - 1;
    int innerWidth = width - 2;
    int innerHeight = height - 2;

    blitPiece(graphics, LIST_BOX, left, top, 0, 0, 1, 1);
    blitPiece(graphics, LIST_BOX, left + 1, top, 1, 0, innerWidth, 1);
    blitPiece(graphics, LIST_BOX, right - 1, top, far, 0, 1, 1);

    blitPiece(graphics, LIST_BOX, left, top + 1, 0, 1, 1, innerHeight);
    blitPiece(graphics, LIST_BOX, left + 1, top + 1, 1, 1, innerWidth, innerHeight);
    blitPiece(graphics, LIST_BOX, right - 1, top + 1, far, 1, 1, innerHeight);

    blitPiece(graphics, LIST_BOX, left, bottom - 1, 0, low, 1, 1);
    blitPiece(graphics, LIST_BOX, left + 1, bottom - 1, 1, low, innerWidth, 1);
    blitPiece(graphics, LIST_BOX, right - 1, bottom - 1, far, low, 1, 1);
  }

  /**
   * Blits one rectangle of a sprite at its own size.
   *
   * <p>Asked for by every piece of the list, which is the one thing on this screen whose width is
   * not a constant. The two sprite sizes are passed explicitly because the blit takes the texture's
   * own dimensions to turn the source rectangle into texture coordinates, and a caller cutting a
   * piece out of the middle of a sheet knows them and the graphics object does not.
   *
   * @param graphics the frame being extracted
   * @param sprite the sheet to cut from, and how big it is
   * @param x where the piece's left edge goes, in screen coordinates
   * @param y where the piece's top edge goes, in screen coordinates
   * @param u the piece's left column within the sheet
   * @param v the piece's top row within the sheet
   * @param width the piece's width, which is also how much of the sheet it takes
   * @param height the piece's height, which is also how much of the sheet it takes
   */
  private static void blitPiece(
      GuiGraphicsExtractor graphics,
      Sprite sprite,
      int x,
      int y,
      int u,
      int v,
      int width,
      int height) {
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        sprite.id(),
        x,
        y,
        (float) u,
        (float) v,
        width,
        height,
        sprite.sheetWidth(),
        sprite.sheetHeight());
  }

  /**
   * Draws one recipe: its name, its tier and setting, and its star.
   *
   * <p>The name is the potion's own, taken from the output stack the server sent, so a pack that
   * ships a potion with a custom name gets that name here without this class knowing how it was
   * written. The list was measured to fit the longest of those names, so the cut below is a guard
   * against the one name too long for {@link #LIST_MAX_WIDTH} rather than the usual case; a name
   * that overlapped "T3 15s" would be unreadable at both ends either way.
   *
   * @param graphics the frame being extracted
   * @param entry the recipe to draw
   * @param left the list's left edge, in screen coordinates
   * @param right the list's right edge, in screen coordinates
   * @param rowTop the row's top edge, in screen coordinates
   */
  private void drawRow(
      GuiGraphicsExtractor graphics, RecipeBookView.Entry entry, int left, int right, int rowTop) {
    int textY = rowTop + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
    int starLeft = right - STAR_MARGIN - STAR_SIZE;
    Component meta = metaFor(entry);
    int metaWidth = font.width(meta);

    int nameRoom = starLeft - META_GAP - metaWidth - (left + ROW_PAD);
    if (nameRoom > 0) {
      String name = font.plainSubstrByWidth(entry.result().getHoverName().getString(), nameRoom);
      graphics.text(font, Component.literal(name), left + ROW_PAD, textY, TEXT_COLOR);
    }
    graphics.text(font, meta, starLeft - META_GAP - metaWidth, textY, BOOK_META_COLOR);

    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        book().isFavourite(entry.recipeId()) ? STAR_FILLED : STAR_OUTLINE,
        starLeft,
        rowTop + (ROW_HEIGHT - STAR_SIZE) / 2,
        0.0F,
        0.0F,
        STAR_SIZE,
        STAR_SIZE,
        STAR_SIZE,
        STAR_SIZE);
  }

  /**
   * Draws the wells the last load could not fill as the stacks that would fill them.
   *
   * <p>This is the whole of the "shown and not spent" half of loading a recipe: the load moves what
   * the inventory has and leaves the rest empty, and this is what turns those empty wells back into
   * a list of what to go and fetch. Nothing about it is sent to the server, which is why it
   * survives only as long as the screen does.
   *
   * <p>Drawn in the background pass, which runs before the slot pass — so a well that is already
   * partly stocked draws its own item over the ghost rather than under it, and the ghost reads as
   * the shortfall.
   *
   * <p>{@code fakeItem} rather than {@code item}, which is vanilla's own choice for this exact
   * situation — the beacon's payment slot and the smithing table's two slots are drawn the same
   * way. It renders the stack without an owning entity, which is what a slot no one has filled with
   * anything looks like.
   *
   * <p>A well holding something else is skipped entirely. That is a player's own ingredient, and
   * drawing a ghost over it would be telling them to replace something the machine is perfectly
   * happy to brew with.
   *
   * @param graphics the frame being extracted
   */
  private void drawGhosts(GuiGraphicsExtractor graphics) {
    if (ghost == null) {
      return;
    }

    List<ItemStack> ingredients = ghost.ingredients();
    int batch = menu.batchMultiplier();
    int wells = Math.min(ingredients.size(), PressureChamberBlockEntity.SLOT_INGREDIENT_COUNT);
    for (int index = 0; index < wells; index++) {
      ItemStack required = ingredients.get(index);
      // Empty where the recipe names an ingredient by tag and the tag resolved to nothing. There is
      // no picture of what to fetch, so the well is left as it is.
      if (required.isEmpty()) {
        continue;
      }

      ItemStack held = menu.getSlot(index).getItem();
      if (!held.isEmpty()
          && (!ItemStack.isSameItemSameComponents(required, held) || held.getCount() >= batch)) {
        continue;
      }

      Slot slot = menu.slots.get(index);
      graphics.fakeItem(required, leftPos + slot.x, topPos + slot.y);
    }
  }

  /**
   * Draws the machine's health: which still it is, or which part of it is wrong.
   *
   * <p>This line is about the multiblock and nothing else. Whether the chamber is stocked, whether
   * there is water and whether the output has room are said instead by the things they belong to —
   * the slots a refused brew is waiting on, the gauge that is short, the slot that is full — so a
   * sound machine reads the same whether or not the player has got round to filling it. What is
   * left for this line is the one thing those cannot say: a block of the build that is missing or
   * in the wrong place, which is the only fault a player reads this line to fix.
   *
   * @param graphics the frame being extracted
   * @param readiness what a brew started now would come to
   */
  private void drawStatus(GuiGraphicsExtractor graphics, Optional<StartResult> readiness) {
    // The tick or so before the first sync, when the machine has not said anything yet. Not a state
    // of the machine, so it has no wording of its own. The guard is load-bearing rather than
    // cosmetic: the tier and the problem arrive as data slots that read as their unformed defaults
    // until the sync lands, and those defaults would draw a green "Tier 0".
    if (readiness.isEmpty()) {
      graphics.text(font, Component.literal("..."), leftPos + 8, topPos + STATUS_Y, TEXT_COLOR);
      return;
    }

    // A still is sound exactly when the validator found nothing to say about it, which is the same
    // verdict that makes `readiness` report anything other than NOT_FORMED. So the problem is asked
    // for directly rather than inferred from the refusal: it is the fault itself that the line has
    // to name, and it is empty precisely when there is nothing to fix.
    Optional<StillProblem> problem = menu.stillProblem();
    Component status =
        problem
            .map(PressureChamberScreen::problemText)
            .orElseGet(
                () -> Component.translatable("gui.alchemizer.status.working", menu.stillTier()));
    graphics.text(
        font,
        status,
        leftPos + 8,
        topPos + STATUS_Y,
        problem.isEmpty() ? WORKING_COLOR : REFUSED_COLOR);
  }

  /**
   * The wording for one fault.
   *
   * <p>The switch is exhaustive, so a fault added to the core enum stops this compiling until it
   * has been given words — which is what keeps the words in the adapter, where the player's
   * language lives, rather than in the rule that produced the fault.
   *
   * @param problem the fault to name
   * @return the line the screen shows for it
   */
  private static Component problemText(StillProblem problem) {
    return Component.translatable(
        switch (problem) {
          case NO_PARTS_DEFINED -> "gui.alchemizer.still.problem.no_parts";
          case NO_IGNITION -> "gui.alchemizer.still.problem.no_ignition";
          case MULTIPLE_IGNITION -> "gui.alchemizer.still.problem.multiple_ignition";
          case NO_PRESSURE -> "gui.alchemizer.still.problem.no_pressure";
          case MULTIPLE_PRESSURE -> "gui.alchemizer.still.problem.multiple_pressure";
          case PRESSURE_NOT_ON_IGNITION -> "gui.alchemizer.still.problem.not_on_ignition";
          case NO_TANK -> "gui.alchemizer.still.problem.no_tank";
          case UNKNOWN_TIER -> "gui.alchemizer.still.problem.unknown_tier";
          case NOT_IN_A_WORLD -> "gui.alchemizer.still.problem.not_in_a_world";
          case MULTIPLE_CRYSTAL -> "gui.alchemizer.still.problem.multiple_crystal";
          case TUBING_TOO_LONG -> "gui.alchemizer.still.problem.tubing_too_long";
        });
  }

  /**
   * @param name the file's name under this mod's machine-widget directory
   * @return where that sprite lives
   */
  private static Identifier machine(String name) {
    return Identifier.fromNamespaceAndPath(
        Alchemizer.MOD_ID, "textures/gui/machine/" + name + ".png");
  }

  /**
   * @param name the file's name under this mod's book directory
   * @return where that sprite lives
   */
  private static Identifier book(String name) {
    return Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "textures/gui/book/" + name + ".png");
  }
}
