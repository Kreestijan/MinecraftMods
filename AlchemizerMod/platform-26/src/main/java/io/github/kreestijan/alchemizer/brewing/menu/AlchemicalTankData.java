package io.github.kreestijan.alchemizer.brewing.menu;

import io.github.kreestijan.alchemizer.brewing.blockentity.AlchemicalTankBlockEntity;
import java.util.Objects;
import net.minecraft.world.inventory.ContainerData;

/**
 * The pair of numbers a tank screen needs, read straight from the tank.
 *
 * <p>Shaped after {@link IgnitionChamberData}, and for the same reason: the indices below are the
 * screen's business, so a tank that carried them would have to change whenever a gauge moved.
 *
 * <p>Only the server ever holds one of these. The client's menu is handed a plain mutable store of
 * the same length and the server's values are written into it by the container synchroniser, which
 * is why {@link #set} is unreachable here rather than implemented.
 */
public final class AlchemicalTankData implements ContainerData {

  /** Millibuckets of water in the tank. */
  public static final int STORED_MB = 0;

  /** How much that tank can hold, so the gauge can be a fraction of something. */
  public static final int CAPACITY_MB = 1;

  /** How many numbers this view has; also the size of the client's store. */
  public static final int COUNT = 2;

  private final AlchemicalTankBlockEntity tank;

  /**
   * @param tank the tank whose level this view reports
   */
  public AlchemicalTankData(AlchemicalTankBlockEntity tank) {
    this.tank = Objects.requireNonNull(tank, "tank");
  }

  @Override
  public int get(int dataId) {
    return switch (dataId) {
      // Through level() rather than the raw field, so the screen and the machine agree about a tank
      // holding more than a shrunken config says it can: both report it as full.
      case STORED_MB -> tank.level().storedMb();
      case CAPACITY_MB -> tank.level().capacityMb();
      default ->
          throw new IndexOutOfBoundsException(
              "Alchemical tank data has " + COUNT + " entries, got " + dataId);
    };
  }

  /**
   * Unreachable on purpose: both numbers above are read from the tank, and neither is something a
   * client may say.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void set(int dataId, int value) {
    throw new UnsupportedOperationException(
        "Alchemical tank data is read-only; the client is sent data id " + dataId);
  }

  @Override
  public int getCount() {
    return COUNT;
  }
}
