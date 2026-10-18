package io.github.kreestijan.alchemizer.core.flowfield;

import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import java.util.Objects;

/**
 * The box of cells a flow field covers, measured outward from the counter at its centre.
 *
 * <p>A box rather than "everything reachable", because a field that grew to fit whatever it found
 * would grow without limit the first time a customer could walk to the horizon, and the whole point
 * of the thing is that its cost is known before it is built.
 *
 * <p><b>Flat in x and z, asymmetric in y.</b> The horizontal extent is one radius in every
 * direction, because a customer walks out in whatever direction the terrain allows. The vertical is
 * not: a counter sits on the ground, and while a route up a hill is ordinary, a route down a
 * mineshaft is not, so the caller says separately how far the field may reach up and down. Both
 * default to the same value in practice, but they are two numbers because they are two decisions.
 *
 * <p>The cell count is bounded and checked here rather than at the allocation, so a config file
 * with a radius of ten thousand fails at construction with a message naming the number instead of
 * exhausting the heap some ticks later.
 */
public record FieldBounds(int radius, int yBelow, int yAbove) {

  /** The smallest useful field: the counter and its four neighbours. */
  public static final int MIN_RADIUS = 1;

  /**
   * The largest horizontal reach, checked separately from the cell count so that a nonsensical
   * radius is refused as a radius rather than reported as an arithmetic consequence of itself.
   */
  public static final int MAX_RADIUS = 128;

  /**
   * The most cells one field may hold.
   *
   * <p>A field is allocated afresh on every rebuild, so this is a bound on a recurring allocation
   * rather than on a single one. A million cells is a little over four megabytes of {@code int}, is
   * more than a radius of three hundred on one level, and is far past anything a shop needs.
   */
  public static final int MAX_CELLS = 1 << 20;

  public FieldBounds {
    if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
      throw new IllegalArgumentException(
          "radius must be between " + MIN_RADIUS + " and " + MAX_RADIUS + ", was " + radius);
    }
    if (yBelow < 0) {
      throw new IllegalArgumentException("yBelow must not be negative, was " + yBelow);
    }
    if (yAbove < 0) {
      throw new IllegalArgumentException("yAbove must not be negative, was " + yAbove);
    }
    long cells = (long) side(radius) * side(radius) * (yBelow + yAbove + 1);
    if (cells > MAX_CELLS) {
      throw new IllegalArgumentException(
          "a field of radius "
              + radius
              + " reaching "
              + yBelow
              + " down and "
              + yAbove
              + " up covers "
              + cells
              + " cells, past the limit of "
              + MAX_CELLS);
    }
  }

  /**
   * @param radius the horizontal reach
   * @return how many cells across the field is, on both the x and the z axis
   */
  private static int side(int radius) {
    return 2 * radius + 1;
  }

  /**
   * @return how many cells across the field is, on both the x and the z axis
   */
  public int side() {
    return side(radius);
  }

  /**
   * @return how many levels the field spans
   */
  public int layers() {
    return yBelow + yAbove + 1;
  }

  /**
   * @return how many cells the field holds
   */
  public int cellCount() {
    return side() * side() * layers();
  }

  /**
   * @param pos the offset to test
   * @return whether the field covers that cell
   */
  public boolean contains(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    return Math.abs(pos.x()) <= radius
        && Math.abs(pos.z()) <= radius
        && pos.y() >= -yBelow
        && pos.y() <= yAbove;
  }

  /**
   * The flat index of a cell, which is how the builder addresses its cost array.
   *
   * <p>Returns -1 for a cell outside the field rather than throwing, because every caller is
   * walking a neighbour by an offset and the edge of the field is an ordinary answer to that — the
   * builder would otherwise have to test {@link #contains} before every lookup it makes.
   *
   * @param pos the offset to index
   * @return the index, or -1 when the field does not cover that cell
   */
  public int indexOf(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    if (!contains(pos)) {
      return -1;
    }
    return indexOf(pos.x(), pos.y(), pos.z());
  }

  /**
   * @param x the east-west offset
   * @param y the up-down offset
   * @param z the north-south offset
   * @return the index, which is only meaningful for coordinates already known to be inside
   */
  public int indexOf(int x, int y, int z) {
    int side = side();
    return ((y + yBelow) * side + (z + radius)) * side + (x + radius);
  }

  /**
   * @param index a flat index into this field
   * @return the cell that index stands for
   */
  public RelativePos posOf(int index) {
    if (index < 0 || index >= cellCount()) {
      throw new IllegalArgumentException(
          "index " + index + " is outside a field of " + cellCount() + " cells");
    }
    int side = side();
    int x = index % side;
    int rest = index / side;
    int z = rest % side;
    int y = rest / side;
    return new RelativePos(x - radius, y - yBelow, z - radius);
  }

  /**
   * Whether a cell sits on the field's horizontal edge.
   *
   * <p>The edge is where a customer may appear: a cell one step inside it would be reachable from a
   * cell the field cannot see, so spawning there would be spawning on a route that has not been
   * proved to exist.
   *
   * @param pos the offset to test
   * @return whether the cell is on the outer ring
   */
  public boolean isPerimeter(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    return contains(pos) && (Math.abs(pos.x()) == radius || Math.abs(pos.z()) == radius);
  }
}
