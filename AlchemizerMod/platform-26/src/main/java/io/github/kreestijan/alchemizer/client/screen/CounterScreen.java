package io.github.kreestijan.alchemizer.client.screen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.shop.ShopRole;
import io.github.kreestijan.alchemizer.shop.CounterView;
import io.github.kreestijan.alchemizer.shop.menu.CounterMenu;
import io.github.kreestijan.alchemizer.shop.network.CounterNetwork;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * The Counter's screen: who owns the shop, who else may trade at it, and whether it is trading.
 *
 * <p>It is a view of the shop and nothing else. Which controls a player is shown is read from the
 * role the server told them they hold, so a stranger is offered no button they would be refused,
 * and two people looking at one Counter see it differently for the right reason: each has their own
 * menu, with their own numbers in it.
 *
 * <p><b>The panel is art, cut to a fixed size.</b> It used to be filled rectangles, on the grounds
 * that a panel laid out from numbers that only exist at runtime cannot be a fixed picture. That is
 * true of the rows inside it — how many names there are and which of them the viewer may act on is
 * the server's answer, not the file's — and it is exactly why the rows are not in the texture. What
 * <em>is</em> fixed is the frame around them, the rule under the title and the player's own wells,
 * and those belong in a file like every other panel in this mod: a Counter that cannot be redrawn
 * is a Counter that stays grey forever.
 *
 * <p>The two controls on the rows and the Open/Closed bar are sprites for the same reason at a
 * smaller scale: each has two faces and a fixed size, neither of which a rectangle drawn from a
 * colour constant can carry. Only the colours of the text are left in code, and a text colour is
 * the one thing a file cannot hold.
 *
 * <p><b>Two kinds of news, arriving separately.</b> The numbers come from the menu's data slots and
 * are live; the names come from the server's own payload and are as old as the last time it sent
 * them. A name list a moment behind shows a row that has just gone, or misses one that has just
 * arrived, and pressing either is safe: the server rebuilds its list when the button is pressed and
 * refuses an index it no longer has. See {@code CounterMenu#clickMenuButton}.
 *
 * <p><b>Placement is in screen coordinates throughout.</b> The panel is drawn in {@code
 * extractBackground}, which runs before the pose is translated; the container screen's own labels
 * are placed in panel coordinates instead. The two are mixed only where the parent class forces it,
 * exactly as on the brewing screens.
 *
 * <p>No {@code @OnlyIn(Dist.CLIENT)}, for the reason given in {@link PressureChamberScreen}.
 */
public class CounterScreen extends AbstractContainerScreen<CounterMenu> {

  /**
   * The panel's width.
   *
   * <p>Vanilla's own, because this panel is vanilla's own width. Its height is deliberately not
   * stated here: the menu owns it, since the menu is what places the player's inventory on the
   * panel, and a second copy of it on the screen is how the two came apart — see {@code
   * CounterMenu.PANEL_HEIGHT}.
   */
  private static final int PANEL_WIDTH = 176;

  /** A row's height, and the pitch every row below is placed on. */
  private static final int ROW_HEIGHT = 12;

  /** How far in from the panel's edges the content runs, and how far in from that a name starts. */
  private static final int CONTENT_X = 8;

  private static final int CONTENT_RIGHT = PANEL_WIDTH - CONTENT_X;

  private static final int TEXT_INSET = 2;

  /** The owner's line, directly under the title. */
  private static final int OWNER_Y = 16;

  /** The Open/Closed control, laid across the panel under the owner's line. */
  private static final int TOGGLE_Y = 26;

  private static final int TOGGLE_HEIGHT = 12;

  /** The co-owner block: a heading at {@value #CO_OWNER_HEADER_Y} and the names under it. */
  private static final int CO_OWNER_HEADER_Y = 44;

  private static final int CO_OWNER_ROWS_TOP = 56;

  /**
   * How many names of each list are drawn.
   *
   * <p>Three and three, which is what fits between the toggle and the player's inventory. A shop
   * with more people than this holds them and the server knows them — an add or remove button
   * indexes the server's full list rather than these rows — but they are not on this panel. That is
   * a limitation of the placeholder panel, not of the rules behind it, and it is why the two
   * headings carry counts: a heading reading four above three rows is the panel saying so.
   */
  private static final int CO_OWNER_MAX_ROWS = 3;

  private static final int CANDIDATE_HEADER_Y = 96;

  private static final int CANDIDATE_ROWS_TOP = 106;

  private static final int CANDIDATE_MAX_ROWS = 3;

  /**
   * The control at the right end of a row: the square a press has to land in.
   *
   * <p>Measured from the right edge rather than from the left, so that the controls of both lists
   * line up however long the names in front of them are. A column that stepped in and out with the
   * names beside it would read as two lists rather than one panel.
   */
  private static final int CONTROL_RIGHT = CONTENT_RIGHT;

  private static final int CONTROL_WIDTH = 12;

  private static final int CONTROL_LEFT = CONTROL_RIGHT - CONTROL_WIDTH;

  /**
   * The panel, drawn once and used at one size.
   *
   * <p>It carries the frame, the rule the inventory label sits under and the player's own wells,
   * and nothing else: the shop's lines are drawn over it because none of them is a fixed thing.
   */
  private static final Identifier PANEL =
      Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "textures/gui/container/counter.png");

  /** The Open/Closed bar's two faces, in the same language as the brewing screens' controls. */
  private static final Identifier TOGGLE_OPEN = counter("toggle_open");

  private static final Identifier TOGGLE_CLOSED = counter("toggle_closed");

  /** The face an add or a remove control wears, which differ only in the glyph drawn over them. */
  private static final Identifier CONTROL_ADD = counter("add_control");

  private static final Identifier CONTROL_REMOVE = counter("remove_control");

  /** Vanilla's own label grey, a dimmer one for the headings, and a dimmer one again for notes. */
  private static final int TEXT_COLOR = 0xFF404040;

  private static final int HEADING_COLOR = 0xFF6A6A6A;

  private static final int QUIET_COLOR = 0xFF8A8A8A;

  private static final int CONTROL_TEXT_COLOR = 0xFFF0F0F0;

  /**
   * @param menu the Counter menu this screen is a view of
   * @param inventory the player's inventory, which the menu also draws
   * @param title the screen's title, which is the block's own name
   */
  public CounterScreen(CounterMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title, PANEL_WIDTH, CounterMenu.PANEL_HEIGHT);

    // Where all four labels go is PanelLabels' business rather than this screen's, so that this
    // panel's chrome agrees with the three brewing panels' without a fourth copy of the distances.
    this.titleLabelX = PanelLabels.TITLE_X;
    this.titleLabelY = PanelLabels.TITLE_Y;
    this.inventoryLabelX = PanelLabels.inventoryLabelX(CounterMenu.INVENTORY_X);
    this.inventoryLabelY = PanelLabels.inventoryLabelY(CounterMenu.PANEL_HEIGHT);
  }

  /**
   * Forgets the view this screen was drawn from when it goes away.
   *
   * <p>The store holds one Counter's worth of names, and nothing would take them out again if the
   * screen did not: the next Counter a player opens looks under its own position and would find
   * nothing there, but the names would otherwise be held for the rest of the session. See {@code
   * CounterNetwork.CounterViewStore}.
   */
  @Override
  public void removed() {
    super.removed();
    CounterNetwork.CounterViewStore.clear();
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    super.extractBackground(graphics, mouseX, mouseY, a);

    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        PANEL,
        leftPos,
        topPos,
        0.0F,
        0.0F,
        PANEL_WIDTH,
        CounterMenu.PANEL_HEIGHT,
        256,
        256);

    if (!menu.hasShop()) {
      // A Counter nobody has claimed has nothing to show and nothing it may be asked to do. That is
      // a real state rather than an error — see CounterBlock — so it is named rather than left as
      // an empty panel.
      drawNote(graphics, Component.translatable("gui.alchemizer.counter.no_shop"), OWNER_Y);
      return;
    }

    Optional<CounterView> view = view();
    if (view.isEmpty()) {
      // The tick or so between the window opening and the server's payload arriving. Not a state of
      // the shop, so it has no wording of its own.
      drawNote(graphics, Component.literal("..."), OWNER_Y);
      return;
    }

    drawOwner(graphics, view.get());
    drawToggle(graphics);
    drawCoOwners(graphics, view.get());
    drawCandidates(graphics, view.get());
  }

  /**
   * Runs a click on one of the screen's own controls.
   *
   * <p>The panel is drawn rather than built from widgets, so every control here is a rectangle this
   * class owns and a hit test that has to agree with it. What a press does is send a button id and
   * nothing else: the server re-reads the shop and re-derives the index, so a client that made one
   * up asks for something it was already allowed to ask for, or is refused.
   *
   * <p>A press outside every control falls through to the parent, so picking items up out of the
   * player's own inventory still works.
   *
   * @param event the click
   * @param doubleClick whether this was the second click of a double
   * @return whether the click was consumed
   */
  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() == 0 && menu.hasShop() && view().isPresent()) {
      if (isOverToggle(event.x(), event.y()) && mayToggle()) {
        send(CounterMenu.BUTTON_TOGGLE_OPEN);
        return true;
      }
      if (mayManageOwners()) {
        int coOwner = coOwnerRowAt(event.x(), event.y());
        if (coOwner >= 0) {
          send(CounterMenu.BUTTON_REMOVE_CO_OWNER_BASE + coOwner);
          return true;
        }
        int candidate = candidateRowAt(event.x(), event.y());
        if (candidate >= 0) {
          send(CounterMenu.BUTTON_ADD_CANDIDATE_BASE + candidate);
          return true;
        }
      }
    }
    return super.mouseClicked(event, doubleClick);
  }

  /**
   * Asks the server to press one button.
   *
   * <p>A button id is the entire request. Nothing about the shop is sent with it, because the
   * server already has the shop.
   *
   * @param buttonId the button's id, as {@link CounterMenu} numbers them
   */
  private void send(int buttonId) {
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
  }

  /**
   * @return the view the server last sent for this Counter, or empty when none has arrived
   */
  private Optional<CounterView> view() {
    return CounterNetwork.CounterViewStore.forCounter(menu.pos());
  }

  /**
   * @return whether the viewer may open and close the shop, which any stakeholder may (addendum A6)
   */
  private boolean mayToggle() {
    return menu.role().filter(ShopRole::isStakeholder).isPresent();
  }

  /**
   * @return whether the viewer may add and remove co-owners, which the owner alone may
   */
  private boolean mayManageOwners() {
    return menu.role().filter(ShopRole::mayManageOwners).isPresent();
  }

  /**
   * Draws the owner's line.
   *
   * <p>An empty name is drawn as a word rather than as nothing: the server says nothing when it has
   * never heard of the id a shop is owned by, which a world copied between servers can produce, and
   * "Owner:" followed by a gap would read as a fault in the screen.
   *
   * @param graphics the frame being extracted
   * @param view the shop as the server last described it
   */
  private void drawOwner(GuiGraphicsExtractor graphics, CounterView view) {
    Component name =
        view.ownerName().isEmpty()
            ? Component.translatable("gui.alchemizer.counter.unnamed")
            : Component.literal(view.ownerName());
    graphics.text(
        font,
        Component.translatable("gui.alchemizer.counter.owner", name),
        leftPos + CONTENT_X + TEXT_INSET,
        topPos + OWNER_Y,
        TEXT_COLOR);
  }

  /**
   * Draws the Open/Closed control.
   *
   * <p>Drawn for everyone and pressable only by a stakeholder, because whether the shop is trading
   * is the one thing on this panel a stranger is entitled to read: a closed shop is why no customer
   * comes, so a player standing at somebody else's Counter can see that it is shut.
   *
   * @param graphics the frame being extracted
   */
  private void drawToggle(GuiGraphicsExtractor graphics) {
    int left = leftPos + CONTENT_X;
    int right = leftPos + CONTENT_RIGHT;
    int top = topPos + TOGGLE_Y;
    int bottom = top + TOGGLE_HEIGHT;
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        menu.open() ? TOGGLE_OPEN : TOGGLE_CLOSED,
        left,
        top,
        0.0F,
        0.0F,
        right - left,
        bottom - top,
        right - left,
        bottom - top);

    Component label =
        Component.translatable(
            menu.open() ? "gui.alchemizer.counter.open" : "gui.alchemizer.counter.closed");
    graphics.text(
        font, label, left + (right - left - font.width(label)) / 2, top + 2, CONTROL_TEXT_COLOR);
  }

  /**
   * Draws the co-owner block: the heading, and as many names as the panel has room for.
   *
   * @param graphics the frame being extracted
   * @param view the shop as the server last described it
   */
  private void drawCoOwners(GuiGraphicsExtractor graphics, CounterView view) {
    drawHeading(
        graphics, "gui.alchemizer.counter.coowners", menu.coOwnerCount(), CO_OWNER_HEADER_Y);

    List<String> coOwners = view.coOwnerNames();
    int rows = Math.min(coOwners.size(), CO_OWNER_MAX_ROWS);
    for (int index = 0; index < rows; index++) {
      // The remove control is the owner's alone, so nobody else is shown one they would be refused.
      drawRow(
          graphics,
          coOwners.get(index),
          CO_OWNER_ROWS_TOP + index * ROW_HEIGHT,
          mayManageOwners() ? CONTROL_REMOVE : null);
    }
  }

  /**
   * Draws the candidate block: the heading, and as many names as the panel has room for.
   *
   * @param graphics the frame being extracted
   * @param view the shop as the server last described it
   */
  private void drawCandidates(GuiGraphicsExtractor graphics, CounterView view) {
    drawHeading(
        graphics, "gui.alchemizer.counter.candidates", menu.candidateCount(), CANDIDATE_HEADER_Y);

    List<String> candidates = view.candidateNames();
    int rows = Math.min(candidates.size(), CANDIDATE_MAX_ROWS);
    for (int index = 0; index < rows; index++) {
      drawRow(
          graphics,
          candidates.get(index),
          CANDIDATE_ROWS_TOP + index * ROW_HEIGHT,
          mayManageOwners() ? CONTROL_ADD : null);
    }
  }

  /**
   * Draws one section heading, with how many the shop has of what it names.
   *
   * <p>The count is the live one from the menu's numbers rather than the length of the list drawn
   * below it, which is what makes a section longer than its three rows visible: the heading says
   * four, and three names are under it.
   *
   * @param graphics the frame being extracted
   * @param key the heading's translation key
   * @param count how many the shop has
   * @param y the heading's line
   */
  private void drawHeading(GuiGraphicsExtractor graphics, String key, int count, int y) {
    graphics.text(
        font,
        Component.translatable(key, count),
        leftPos + CONTENT_X + TEXT_INSET,
        topPos + y,
        HEADING_COLOR);
  }

  /**
   * Draws one name, and the control at the end of its row when there is one.
   *
   * <p>The name is cut to the room left of the control rather than being allowed to run under it: a
   * name overlapping a button is a name and a button both unreadable.
   *
   * <p><b>The glyph is in the sprite.</b> A minus and a plus are one character each and neither is
   * translated, so there is nothing for a lang file to hold and nothing for this method to place:
   * the face a control wears is one drawing, sign and all, and a pack that wants a cross where the
   * minus is redraws the minus.
   *
   * @param graphics the frame being extracted
   * @param name the player's name
   * @param y the row's top edge, in panel coordinates
   * @param control the face to draw at the end of the row, or null when the viewer may not press
   *     one
   */
  private void drawRow(
      GuiGraphicsExtractor graphics, String name, int y, @Nullable Identifier control) {
    int rowTop = topPos + y;
    int nameRoom = leftPos + CONTROL_LEFT - 2 - (leftPos + CONTENT_X + TEXT_INSET);
    graphics.text(
        font,
        Component.literal(font.plainSubstrByWidth(name, nameRoom)),
        leftPos + CONTENT_X + TEXT_INSET,
        rowTop + 2,
        TEXT_COLOR);

    if (control == null) {
      return;
    }
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        control,
        leftPos + CONTROL_LEFT,
        rowTop,
        0.0F,
        0.0F,
        CONTROL_WIDTH,
        ROW_HEIGHT,
        CONTROL_WIDTH,
        ROW_HEIGHT);
  }

  /**
   * Draws a line of plain text where a section would be.
   *
   * @param graphics the frame being extracted
   * @param note what to say
   * @param y the line, in panel coordinates
   */
  private void drawNote(GuiGraphicsExtractor graphics, Component note, int y) {
    graphics.text(font, note, leftPos + CONTENT_X + TEXT_INSET, topPos + y, QUIET_COLOR);
  }

  /**
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @return whether the point is on the Open/Closed control
   */
  private boolean isOverToggle(double x, double y) {
    return x >= leftPos + CONTENT_X
        && x < leftPos + CONTENT_RIGHT
        && y >= topPos + TOGGLE_Y
        && y < topPos + TOGGLE_Y + TOGGLE_HEIGHT;
  }

  /**
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @return the co-owner whose remove control is under the point, or -1 for none
   */
  private int coOwnerRowAt(double x, double y) {
    return rowAt(x, y, CO_OWNER_ROWS_TOP, CO_OWNER_MAX_ROWS, viewLength(true));
  }

  /**
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @return the candidate whose add control is under the point, or -1 for none
   */
  private int candidateRowAt(double x, double y) {
    return rowAt(x, y, CANDIDATE_ROWS_TOP, CANDIDATE_MAX_ROWS, viewLength(false));
  }

  /**
   * Finds the row a point is on, in one of the two lists.
   *
   * <p>Asked only about the control at the end of a row rather than the whole row, so that a click
   * on a name is not a press: the two lists are made of text a player may want to read, and a panel
   * where reading a name risked removing its owner would be a panel nobody dared look at.
   *
   * <p>The count comes from the view rather than from the menu's numbers, because the view is what
   * was drawn — a row indexed past what was drawn would send an intent for a name that is not on
   * the screen.
   *
   * @param x the mouse's screen x
   * @param y the mouse's screen y
   * @param rowsTop the first row's top edge, in panel coordinates
   * @param maxRows how many rows of that list the panel draws
   * @param rows how many names the view holds
   * @return the row's index, or -1 when the point is on no control
   */
  private int rowAt(double x, double y, int rowsTop, int maxRows, int rows) {
    if (x < leftPos + CONTROL_LEFT || x >= leftPos + CONTROL_RIGHT) {
      return -1;
    }
    int drawn = Math.min(rows, maxRows);
    for (int index = 0; index < drawn; index++) {
      int rowTop = topPos + rowsTop + index * ROW_HEIGHT;
      if (y >= rowTop && y < rowTop + ROW_HEIGHT) {
        return index;
      }
    }
    return -1;
  }

  /**
   * @param coOwners whether the co-owner list is wanted rather than the candidate list
   * @return how many names the view holds for that list, or none when no view has arrived
   */
  private int viewLength(boolean coOwners) {
    return view()
        .map(view -> coOwners ? view.coOwnerNames().size() : view.candidateNames().size())
        .orElse(0);
  }

  /**
   * @param name the file's name under this mod's Counter-widget directory
   * @return where that sprite lives
   */
  private static Identifier counter(String name) {
    return Identifier.fromNamespaceAndPath(
        Alchemizer.MOD_ID, "textures/gui/counter/" + name + ".png");
  }
}
