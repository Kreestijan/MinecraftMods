package io.github.kreestijan.alchemizer.brewing.menu;

import io.github.kreestijan.alchemizer.brewing.blockentity.IgnitionChamberBlockEntity;
import java.util.Objects;
import net.minecraft.world.inventory.ContainerData;

/**
 * The handful of numbers a fuel screen needs, read straight from the chamber.
 *
 * <p>This exists instead of the block entity implementing {@link ContainerData} itself so that the
 * block entity is not shaped by the menu: the indices below are the screen's business, and a block
 * entity that had to carry them would have to change whenever a gauge moved.
 *
 * <p>Only the server ever holds one of these. The client's menu is handed a plain mutable store of
 * the same length and the server's values are written into it by the container synchroniser, which
 * is why {@link #set} is unreachable here rather than implemented.
 */
public final class IgnitionChamberData implements ContainerData {

  /** Ticks of burn left on the fuel alight. */
  public static final int BURN_REMAINING = 0;

  /** How long that fuel was going to burn for, so the gauge can be a fraction of something. */
  public static final int BURN_TOTAL = 1;

  /** One when something is alight, zero when the chamber is cold. */
  public static final int LIT = 2;

  /** How many numbers this view has; also the size of the client's store. */
  public static final int COUNT = 3;

  private final IgnitionChamberBlockEntity chamber;

  /**
   * @param chamber the chamber whose burn this view reports
   */
  public IgnitionChamberData(IgnitionChamberBlockEntity chamber) {
    this.chamber = Objects.requireNonNull(chamber, "chamber");
  }

  @Override
  public int get(int dataId) {
    return switch (dataId) {
      case BURN_REMAINING -> chamber.burnRemainingTicks();
      case BURN_TOTAL -> chamber.burnTotalTicks();
      case LIT -> chamber.isLit() ? 1 : 0;
      default ->
          throw new IndexOutOfBoundsException(
              "Ignition chamber data has " + COUNT + " entries, got " + dataId);
    };
  }

  /**
   * Unreachable on purpose: every number above is derived from the chamber, and none of them is
   * something a client may say.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void set(int dataId, int value) {
    throw new UnsupportedOperationException(
        "Ignition chamber data is read-only; the client is sent data id " + dataId);
  }

  @Override
  public int getCount() {
    return COUNT;
  }
}
