package io.github.kreestijan.alchemizer.client.screen;

import io.github.kreestijan.alchemizer.Alchemizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A button whose face is this mod's own texture rather than vanilla's.
 *
 * <p><b>Why this exists at all.</b> A vanilla {@code Button} owns its own appearance: its face is
 * baked into the {@code widget/button} sprites in the {@code gui} atlas, which belong to the base
 * game and to whatever resource pack is loaded, and no mod texture can reach them. That is fine
 * until the requirement is that <em>everything a player sees is a file in this mod</em> — at which
 * point the vanilla face is the one thing on the screen nobody can redraw. So the four hourglass
 * settings and the start button are drawn here instead.
 *
 * <p><b>Everything except the drawing is still vanilla's, deliberately.</b> This extends {@link
 * AbstractButton}, so the click sound, the hover state, the focus path, the tooltip holder and the
 * narration all come from the same code they came from before. The only method that differs from a
 * plain button is the one that draws, which is the point: who draws the face changes, and nothing
 * about what pressing it does.
 *
 * <p><b>One sheet, three faces, cut into three pieces.</b> The two buttons this is used for are
 * both twenty tall and twenty-four and twenty-eight wide, so one sprite serves both — but only if
 * the ends are not stretched, which is what a naive full-width blit would do to the frame drawn
 * around them. The sheet is therefore laid out with its left cap in column zero, its right cap in
 * the last column, and a uniform face between: the caps go down one-to-one and only the face
 * between them is taken at the width in hand. Both widths are narrower than the sheet, so the right
 * cap is always cut from the same column and no pixel is ever interpolated.
 *
 * <p>The face is chosen the way vanilla chooses its own sprite — disabled first, then hovered or
 * focused, then plain — so a widget that cannot be pressed never shows a hover it will not answer.
 */
final class SpriteButton extends AbstractButton {

  /**
   * The sheet's two dimensions, which the caps' source rectangles are measured against.
   *
   * <p>The width is the widest button this is used for, so that a narrower one has the right cap's
   * column to cut from without the sheet having to be sampled past its own edge.
   */
  private static final int SHEET_WIDTH = 28;

  private static final int SHEET_HEIGHT = 20;

  private static final Identifier FACE_NORMAL = sheet("button");

  private static final Identifier FACE_HOVER = sheet("button_hover");

  private static final Identifier FACE_DISABLED = sheet("button_disabled");

  /** What a press does, which is the same thing it did while this was a vanilla button. */
  private final Runnable onPress;

  /**
   * @param x the button's left edge, in screen coordinates
   * @param y the button's top edge, in screen coordinates
   * @param width the button's width, which must not exceed {@link #SHEET_WIDTH}
   * @param height the button's height
   * @param message what the button says, which is never a command: it is a number of seconds or the
   *     word "Start"
   * @param onPress what to do when it is pressed
   */
  SpriteButton(int x, int y, int width, int height, Component message, Runnable onPress) {
    super(x, y, width, height, message);
    this.onPress = onPress;
  }

  @Override
  public void onPress(InputWithModifiers input) {
    onPress.run();
  }

  @Override
  public void updateWidgetNarration(NarrationElementOutput output) {
    // The same line a vanilla button narrates itself with, from the same method: this widget is
    // still a button to a screen reader, whatever it looks like.
    defaultButtonNarrationText(output);
  }

  @Override
  protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    Identifier face = !active ? FACE_DISABLED : isHoveredOrFocused() ? FACE_HOVER : FACE_NORMAL;

    int x = getX();
    int y = getY();
    int middle = width - 2;

    graphics.blit(
        RenderPipelines.GUI_TEXTURED, face, x, y, 0.0F, 0.0F, 1, height, SHEET_WIDTH, SHEET_HEIGHT);
    if (middle > 0) {
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          face,
          x + 1,
          y,
          1.0F,
          0.0F,
          middle,
          height,
          SHEET_WIDTH,
          SHEET_HEIGHT);
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          face,
          x + width - 1,
          y,
          (float) (SHEET_WIDTH - 1),
          0.0F,
          1,
          height,
          SHEET_WIDTH,
          SHEET_HEIGHT);
    }

    // Centred rather than scrolled. Vanilla slides a label too wide for its button back and forth
    // within it, which is what made the four settings read as pieces of themselves before the row
    // was respread; every label this is given now fits, and one that fits is centred and still.
    //
    // The colour is vanilla's own rule, restated because this draws its own label: a foreground
    // colour that has been set wins over the message's own style, and one that has not leaves the
    // message reading as itself. An hourglass the player has chosen is drawn in the amber the batch
    // control uses, and it stays amber while a running brew has greyed the button out — which is
    // exactly what the vanilla button this replaced did with the same two inputs.
    Font font = Minecraft.getInstance().font;
    int foreground = getFGColor();
    Component label = getMessage();
    if (foreground != UNSET_FG_COLOR) {
      label = label.copy().withStyle(style -> style.withColor(foreground));
    }
    graphics.text(
        font,
        label,
        x + (width - font.width(label)) / 2,
        y + (height - font.lineHeight) / 2 + 1,
        foreground);
  }

  /**
   * @param name the sheet's file name under this mod's widget directory
   * @return where that sheet lives
   */
  private static Identifier sheet(String name) {
    return Identifier.fromNamespaceAndPath(
        Alchemizer.MOD_ID, "textures/gui/widget/" + name + ".png");
  }
}
