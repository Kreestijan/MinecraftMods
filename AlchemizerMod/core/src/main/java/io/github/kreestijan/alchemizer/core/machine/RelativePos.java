package io.github.kreestijan.alchemizer.core.machine;

/**
 * A cell of the multiblock, as an offset from whichever chamber is doing the looking.
 *
 * <p>Offsets rather than world coordinates, because {@code core} cannot name {@code BlockPos} and
 * because a structure described relative to its own origin is the same description wherever it is
 * built.
 *
 * @param x the east-west offset
 * @param y the up-down offset
 * @param z the north-south offset
 */
public record RelativePos(int x, int y, int z) {

  /** The origin of the search: the block doing the looking. */
  public static final RelativePos ORIGIN = new RelativePos(0, 0, 0);

  /**
   * @param dx east-west
   * @param dy up-down
   * @param dz north-south
   * @return the offset from this one
   */
  public RelativePos plus(int dx, int dy, int dz) {
    return new RelativePos(x + dx, y + dy, z + dz);
  }

  /**
   * @return the cell directly above this one
   */
  public RelativePos above() {
    return plus(0, 1, 0);
  }

  /**
   * @return the cell directly below this one
   */
  public RelativePos below() {
    return plus(0, -1, 0);
  }

  /**
   * @return the squared distance from the origin, for cheap radius comparisons
   */
  public int distanceSquared() {
    return x * x + y * y + z * z;
  }
}
