package io.github.kreestijan.alchemizer.client.screen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity.StartResult;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberMenu;
import io.github.kreestijan.alchemizer.brewing.water.WaterContainers;
import io.github.kreestijan.alchemizer.core.machine.StillProblem;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

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
 * <p>The gauges are rectangles rather than sprites, and are described in {@link
 * IgnitionChamberScreen}. The four hourglass buttons and the start button are ordinary widgets,
 * which is what gives them their hover state, their press handling and their narration for free;
 * the panel art is therefore left plain where they sit rather than drawing recesses under them.
 *
 * <p><b>The panel is 208 tall, not 166.</b> Everything the machine draws is stacked: the controls,
 * then three rows of slots with the tank beside them, then the bar, then the status line, then the
 * player's inventory. That does not fit in the standard height, and squeezing it in would have put
 * the bar or the status line through the inventory label. The arrangement is in the constants
 * below, top to bottom, because the order is the part that matters and the pixels are the part that
 * gets nudged.
 *
 * <p><b>Nothing on this screen is a caption any more.</b> The water figure that used to hang under
 * the gauge is now the gauge's hover tooltip, in millibuckets, and the same reading is on the
 * tank's own screen and on the tank item — one number, three places, one wording.
 *
 * <p>No {@code @OnlyIn(Dist.CLIENT)}, for the reason given in {@link IgnitionChamberScreen}.
 */
public class PressureChamberScreen extends AbstractContainerScreen<PressureChamberMenu> {

  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath(
          Alchemizer.MOD_ID, "textures/gui/container/pressure_chamber.png");

  /**
   * The panel's height.
   *
   * <p>Taller than vanilla's 166. The machine needs three slot rows where a furnace needs one, and
   * the order below them is fixed: the bar, then the status line, then the inventory. Laid out in
   * the standard height, the bar would have had to cross the inventory label to stay below the
   * slots, so the panel grows instead and everything under the slots moves down with it.
   *
   * <p>It goes to the parent's five-argument constructor rather than being assigned afterwards,
   * because the parent's size fields are final and it derives the inventory label's line from this
   * number as it constructs: at 208 the label lands on {@code 208 - 94 = 114}, twelve pixels above
   * the inventory's first row at 126 — the same gap vanilla leaves between its own pair. This
   * number and the menu's {@code INVENTORY_Y} are a pair, and are the only two places the new
   * height is written down.
   */
  private static final int PANEL_HEIGHT = 208;

  /** The tank gauge, down the right-hand side beside the slots. */
  private static final int GAUGE_X = 152;

  private static final int GAUGE_Y = 41;

  private static final int GAUGE_WIDTH = 16;

  private static final int GAUGE_HEIGHT = 52;

  /**
   * The brew progress bar: thin, and running the panel's full inner width.
   *
   * <p>Spanning the width rather than sitting beside the gauge is what makes it read as the brew's
   * clock rather than as one more gauge. It is under everything the machine draws — the last slot
   * row and the tank both — so nothing sits between the bar and the machine it is reporting on.
   */
  private static final int BAR_X = 8;

  private static final int BAR_Y = 96;

  private static final int BAR_WIDTH = 160;

  private static final int BAR_HEIGHT = 3;

  /** The four hourglass buttons, one after another, and the start button beside them. */
  private static final int PRESET_X = 32;

  private static final int PRESET_Y = 17;

  private static final int PRESET_SPACING = 20;

  private static final int PRESET_SIZE = 20;

  private static final int START_X = 116;

  private static final int START_WIDTH = 28;

  /**
   * The line the status sits on, directly under the bar.
   *
   * <p>The water figure no longer shares this line. It is now the gauge's hover tooltip — see
   * {@link #extractBackground} — which leaves the line to the machine's health alone: the tier
   * while the still is sound, and the particular part of it that is wrong while it is not.
   */
  private static final int STATUS_Y = 102;

  private static final int EMPTY_COLOR = 0xFF1C1C1C;

  private static final int WATER_COLOR = 0xFF2E6FD6;

  /** What the tank gauge turns when the water is what a brew is waiting for. */
  private static final int WATER_SHORT_COLOR = 0xFFA03030;

  private static final int BAR_COLOR = 0xFFE8A33D;

  /** The translucent red laid over a slot holding something a brew still needs. */
  private static final int MISSING_SLOT_COLOR = 0x66FF3030;

  /** Vanilla's own container label grey, a red for a refusal, and a green for a sound machine. */
  private static final int TEXT_COLOR = 0xFF404040;

  private static final int REFUSED_COLOR = 0xFFA02020;

  private static final int WORKING_COLOR = 0xFF2E7D32;

  private final List<Button> presetButtons = new ArrayList<>();

  private Button startButton;

  /**
   * The only screen here whose panel is not the default size, which is why the height is passed on
   * rather than left to the parent's three-argument constructor.
   *
   * @param menu the brewing menu this screen is a view of
   * @param inventory the player's inventory, which the menu also draws
   * @param title the screen's title, which is the block's own name
   */
  public PressureChamberScreen(PressureChamberMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title, DEFAULT_IMAGE_WIDTH, PANEL_HEIGHT);
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
      presetButtons.add(
          addRenderableWidget(
              Button.builder(presetLabel(preset, false), button -> send(id))
                  .bounds(
                      leftPos + PRESET_X + id * PRESET_SPACING,
                      topPos + PRESET_Y,
                      PRESET_SIZE,
                      PRESET_SIZE)
                  .tooltip(
                      Tooltip.create(
                          Component.translatable(
                              "gui.alchemizer.hourglass.tooltip", preset.baseSeconds())))
                  .build()));
    }

    startButton =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("gui.alchemizer.start"),
                    button -> send(PressureChamberMenu.BUTTON_START))
                .bounds(leftPos + START_X, topPos + PRESET_Y, START_WIDTH, PRESET_SIZE)
                .build());

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

  private void refreshButtons() {
    Optional<StartResult> readiness = menu.readiness();
    boolean brewing = readiness.isPresent() && readiness.get() == StartResult.ALREADY_BREWING;
    Optional<HourglassPreset> selected = menu.selectedPreset();

    for (int i = 0; i < presetButtons.size(); i++) {
      Button button = presetButtons.get(i);
      boolean isSelected = selected.isPresent() && selected.get().ordinal() == i;
      button.setMessage(presetLabel(HourglassPreset.values()[i], isSelected));
      // A running brew has already been charged for the time it was started at, so the setting is
      // fixed for its duration. Leaving the buttons live would let a player change the dial on a
      // clock that is no longer reading it.
      button.active = !brewing;
    }

    startButton.active = readiness.isPresent() && readiness.get() == StartResult.STARTED;
  }

  /**
   * @param preset the setting the button stands for
   * @param selected whether it is the one the machine is set to
   * @return the button's label, bracketed while it is the chosen one
   */
  private static Component presetLabel(HourglassPreset preset, boolean selected) {
    Component seconds = Component.translatable("gui.alchemizer.seconds", preset.baseSeconds());
    return selected
        ? Component.translatable("gui.alchemizer.hourglass.selected", seconds)
        : seconds;
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
    return super.mouseClicked(event, doubleClick);
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
    drawMissingSlots(graphics, readiness);
    drawStatus(graphics, readiness);

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
    }
  }

  /**
   * Draws the water level, which is the whole of the reading.
   *
   * <p>There is no figure beside it any more. The caption that used to hang under the gauge is gone
   * — a player could not tell what a dose was, and it was this mod's own word rather than anything
   * the game or the design document says — and the exact amount is now on hover, in millibuckets,
   * which is what the tank is actually measured in.
   *
   * @param graphics the frame being extracted
   * @param readiness what a brew started now would come to
   */
  private void drawTank(GuiGraphicsExtractor graphics, Optional<StartResult> readiness) {
    int left = leftPos + GAUGE_X;
    int top = topPos + GAUGE_Y;
    int right = left + GAUGE_WIDTH;
    int bottom = top + GAUGE_HEIGHT;
    graphics.fill(left, top, right, bottom, EMPTY_COLOR);

    int capacity = menu.waterCapacityMb();
    float filled = capacity <= 0 ? 0.0F : Math.min(1.0F, menu.waterStoredMb() / (float) capacity);
    int filledHeight = Math.round(filled * GAUGE_HEIGHT);
    if (filledHeight > 0) {
      // Bottom-up, like the vessel itself: a gauge that drained from the top would read as a
      // progress bar rather than as a level.
      boolean waterShort = readiness.isPresent() && readiness.get() == StartResult.NEEDS_WATER;
      graphics.fill(
          left, bottom - filledHeight, right, bottom, waterShort ? WATER_SHORT_COLOR : WATER_COLOR);
    }
  }

  /**
   * Draws the brew's progress as a bar that fills from the left.
   *
   * @param graphics the frame being extracted
   */
  private void drawBrewBar(GuiGraphicsExtractor graphics) {
    int left = leftPos + BAR_X;
    int top = topPos + BAR_Y;
    graphics.fill(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, EMPTY_COLOR);

    int filled = Math.round(menu.brewProgress() * BAR_WIDTH);
    if (filled > 0) {
      graphics.fill(left, top, left + filled, top + BAR_HEIGHT, BAR_COLOR);
    }
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
   * @param graphics the frame being extracted
   * @param from the first slot to redden, by the menu's slot index
   * @param to the slot after the last one
   */
  private void highlightSlots(GuiGraphicsExtractor graphics, int from, int to) {
    for (int index = from; index < to; index++) {
      Slot slot = menu.slots.get(index);
      // The panel is drawn before the slots are, so this sits behind the item rather than over it.
      // A filled slot therefore reads as "this one" instead of being hidden by the tint.
      graphics.fill(
          leftPos + slot.x,
          topPos + slot.y,
          leftPos + slot.x + 16,
          topPos + slot.y + 16,
          MISSING_SLOT_COLOR);
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
        });
  }
}
