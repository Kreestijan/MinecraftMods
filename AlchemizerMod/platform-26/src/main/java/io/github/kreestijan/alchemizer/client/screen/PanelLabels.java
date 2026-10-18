package io.github.kreestijan.alchemizer.client.screen;

/**
 * Where the two labels a container panel draws sit, in panel coordinates.
 *
 * <p>The brewing screens are three views of one machine family, and a player moves between them
 * without expecting the chrome to move too — so where the title and the inventory's label sit is
 * one fact about the mod rather than three facts about three panels. It is written once here rather
 * than in each screen because three copies of a distance are three chances for one of them to be
 * nudged on its own, which is exactly how the screens disagreed before this existed.
 *
 * <p>The pressure chamber's panel is the one the others are drawn to match, so its numbers are the
 * benchmark and the rest are derived to agree with it. Nothing here is invented for the smallest
 * panel and then applied to the largest.
 *
 * <p>The title is tucked into the panel's corner — two in on both axes, where vanilla leaves eight
 * across and six down. The inventory's label sits two below the panel's separator rule, and lines
 * up with the leftmost <em>well</em> rather than with the slot standing in it: the well is the edge
 * a player sees, and vanilla's own choice, the slot, hangs the label a pixel inside the box it
 * names.
 */
final class PanelLabels {

  /** The title's corner, measured from the panel's top and left edges. */
  static final int TITLE_X = 2;

  static final int TITLE_Y = 2;

  /**
   * The inventory label's line, measured up from the panel's bottom edge.
   *
   * <p>Vanilla derives its own line as {@code height - 94}; this is {@code height - 93}, one lower,
   * which is what puts the label two pixels under the separator rather than one. The separator is
   * panel art rather than a number a screen holds, so it is drawn at {@code height - 95} by whoever
   * paints the texture and cannot be stated here — see the note on the screens' textures.
   */
  private static final int LABEL_LINE_FROM_BOTTOM = 93;

  private PanelLabels() {}

  /**
   * @param panelHeight the panel's height
   * @return the inventory label's line, in panel coordinates
   */
  static int inventoryLabelY(int panelHeight) {
    return panelHeight - LABEL_LINE_FROM_BOTTOM;
  }

  /**
   * @param inventorySlotX the x of the first player-inventory slot
   * @return the inventory label's x, in panel coordinates
   */
  static int inventoryLabelX(int inventorySlotX) {
    return inventorySlotX - 1;
  }
}
