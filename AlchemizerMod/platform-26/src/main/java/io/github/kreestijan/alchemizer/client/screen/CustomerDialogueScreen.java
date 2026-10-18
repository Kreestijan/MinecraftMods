package io.github.kreestijan.alchemizer.client.screen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.customer.menu.CustomerDialogueMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Serve/Refuse dialogue, as a small panel with two buttons on it.
 *
 * <p><b>A dialogue rather than an inventory, which is why this is not a container screen.</b> The
 * menu it belongs to has no slots and the player has nothing to put in it, so drawing the player's
 * own wells under the panel — which is most of what {@code AbstractContainerScreen} does — would be
 * a picture of somewhere to put things that does not exist. What the window type buys is the
 * server's button-intent channel, and that is all that is used of it: this screen implements {@link
 * MenuAccess} so the loader can hand its menu over, and draws its own panel.
 *
 * <p><b>The panel is art, and the buttons are the mod's own.</b> The frame is one sprite, cut at
 * one size, and the two buttons are {@link SpriteButton} — the same widget the brewing screens'
 * controls use, which is a face this mod ships rather than the base game's. That is the standing
 * rule for this mod's screens: everything a player sees is a file that can be redrawn, and nothing
 * on this one is a filled rectangle.
 *
 * <p>Presses travel as button ids and nothing else, exactly as they do from the Counter's screen:
 * the client says which button, and the server re-derives the customer, the shop and the player's
 * right to act there.
 *
 * <p>No {@code @OnlyIn(Dist.CLIENT)}, for the reason given in {@link PressureChamberScreen}.
 */
public class CustomerDialogueScreen extends Screen implements MenuAccess<CustomerDialogueMenu> {

  /**
   * PLACEHOLDER: the panel's size, in pixels.
   *
   * <p>Small on purpose — this is a question with two answers — and the two buttons are laid out
   * from it rather than from numbers of their own, so a redrawn panel of another size moves them
   * with it.
   */
  private static final int PANEL_WIDTH = 140;

  private static final int PANEL_HEIGHT = 72;

  /** The line the title sits on, measured from the panel's top edge. */
  private static final int TITLE_Y = 6;

  /** How far the two buttons sit above the panel's bottom edge, and how far apart they are. */
  private static final int BUTTON_BOTTOM = 10;

  private static final int BUTTON_GAP = 8;

  /** The button size, which is the size of the sprite the widget draws. */
  private static final int BUTTON_WIDTH = 28;

  private static final int BUTTON_HEIGHT = 20;

  /** Where the panel's art lives. A placeholder, drawn to be redrawn. */
  private static final Identifier PANEL =
      Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "textures/gui/customer/dialogue.png");

  /** Vanilla's own label grey, which is what the brewing panels' titles are drawn in. */
  private static final int TEXT_COLOR = 0xFF404040;

  private final CustomerDialogueMenu menu;

  private int leftPos;

  private int topPos;

  /**
   * @param menu the dialogue this screen is a view of
   * @param inventory the player's inventory, which this screen does not draw
   * @param title the screen's title, which is the window's own title and not used here
   */
  public CustomerDialogueScreen(CustomerDialogueMenu menu, Inventory inventory, Component title) {
    super(title);
    this.menu = menu;
  }

  /**
   * @return the menu this screen answers button presses through, which is what {@link MenuAccess}
   *     is for
   */
  @Override
  public CustomerDialogueMenu getMenu() {
    return menu;
  }

  /**
   * Places the panel and puts the two buttons on it.
   *
   * <p>Centred rather than positioned from a corner, and the two buttons are placed from the
   * panel's own edges, so the panel is the only thing that has to be redrawn when the layout
   * changes.
   */
  @Override
  protected void init() {
    super.init();
    leftPos = (width - PANEL_WIDTH) / 2;
    topPos = (height - PANEL_HEIGHT) / 2;

    int buttonsWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
    int buttonsLeft = leftPos + (PANEL_WIDTH - buttonsWidth) / 2;
    int buttonsTop = topPos + PANEL_HEIGHT - BUTTON_BOTTOM - BUTTON_HEIGHT;

    addRenderableWidget(
        new SpriteButton(
            buttonsLeft,
            buttonsTop,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            Component.translatable("gui.alchemizer.customer.serve"),
            () -> press(CustomerDialogueMenu.BUTTON_SERVE)));
    addRenderableWidget(
        new SpriteButton(
            buttonsLeft + BUTTON_WIDTH + BUTTON_GAP,
            buttonsTop,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            Component.translatable("gui.alchemizer.customer.refuse"),
            () -> press(CustomerDialogueMenu.BUTTON_REFUSE)));
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
        PANEL_HEIGHT,
        PANEL_WIDTH,
        PANEL_HEIGHT);

    Component title = getTitle();
    graphics.text(
        font, title, leftPos + (PANEL_WIDTH - font.width(title)) / 2, topPos + TITLE_Y, TEXT_COLOR);
  }

  /**
   * Asks the server to press one of the dialogue's buttons.
   *
   * <p>A button id is the entire request, and the window closes when the server has finished with
   * it: the customer is dismissed, which is what closes the dialogue on both sides at once.
   *
   * @param buttonId the button's id, as {@link CustomerDialogueMenu} numbers them
   */
  private void press(int buttonId) {
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
  }
}
