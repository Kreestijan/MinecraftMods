package io.github.kreestijan.alchemizer.core.flowfield;

import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Builds a {@link FlowField} a slice at a time.
 *
 * <p><b>Why it is not a single method.</b> The field spreads outward from the counter until it runs
 * out of walkable world, and on open ground that is a large fraction of a million cells. Doing that
 * in one call would put the whole cost into one server tick, and a tick that takes a hundred
 * milliseconds is a tick every player feels. So the work is advanced by {@link #advance(int)} in
 * bounded steps and the state lives here between calls, which lets the caller spend what it can
 * afford this tick and carry on next tick without losing what it has already worked out.
 *
 * <p><b>The algorithm is Dijkstra, from the counter outward.</b> From the counter rather than
 * toward it because one field then serves every customer at once — each cell already knows its own
 * cost to the counter, so a customer's next step is a lookup rather than a search. A breadth-first
 * search would do if every step cost the same, and it does not: a path block is cheaper than grass,
 * and so the field prefers the paths a shopkeeper laid down.
 *
 * <p><b>Interruption is free.</b> The frontier is a heap that persists between calls, so advancing
 * the build in ten slices of a hundred steps produces exactly the field that one slice of a
 * thousand would. That equality is the property the tests pin, because it is the one an incremental
 * algorithm gets wrong quietly.
 *
 * <p>Not thread-safe, and not meant to be: a field is built on the server thread, and the finished
 * {@link FlowField} it hands out is immutable so that everything else may read it freely.
 */
public final class FlowFieldBuilder {

  /**
   * The six directions a step may take, in the order ties are broken.
   *
   * <p>The same six {@link FlowField} walks, so the builder and the field cannot come to disagree
   * about which neighbours exist.
   */
  private static final int[][] DIRECTIONS = {
    {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}
  };

  /** A cell the build has not reached yet. Real costs are never negative, so one flag serves. */
  private static final int UNREACHED = -1;

  private final RelativePos goal;
  private final FieldBounds bounds;
  private final NavView view;
  private final int climbSurcharge;
  private final int goalIndex;

  private final int[] costs;

  /** The frontier, as {@code (cost << 32) | index} so that a heap of longs orders by cost first. */
  private long[] heap;

  private int heapSize;
  private int settledCount;

  /**
   * @param goal the counter, and the one cell seeded at zero cost
   * @param bounds the box to build over
   * @param view what the world is willing to say about a cell
   * @param climbSurcharge what a step up or down costs on top of the ground it lands on; zero
   *     treats a staircase as free, which flattens a hillside into one distance
   */
  public FlowFieldBuilder(RelativePos goal, FieldBounds bounds, NavView view, int climbSurcharge) {
    this.goal = Objects.requireNonNull(goal, "goal");
    this.bounds = Objects.requireNonNull(bounds, "bounds");
    this.view = Objects.requireNonNull(view, "view");
    if (climbSurcharge < 0) {
      throw new IllegalArgumentException(
          "climbSurcharge must not be negative, was " + climbSurcharge);
    }
    this.climbSurcharge = climbSurcharge;

    this.goalIndex = bounds.indexOf(goal);
    if (goalIndex < 0) {
      throw new IllegalArgumentException("the goal " + goal + " is outside " + bounds);
    }

    this.costs = new int[bounds.cellCount()];
    Arrays.fill(this.costs, UNREACHED);
    this.costs[goalIndex] = 0;

    this.heap = new long[Math.max(16, Math.min(bounds.cellCount(), 4096))];
    push(0, goalIndex);
  }

  /**
   * Does up to {@code maxSteps} steps of work.
   *
   * <p>One step is one cell settled. The count is a step budget rather than a time budget because a
   * step's cost varies with the machine and the number of cells does not: a caller that wants a
   * predictable share of a tick wants the thing that is actually fixed.
   *
   * @param maxSteps the most cells to settle before returning; zero only reports
   * @return whether the build has finished
   */
  public boolean advance(int maxSteps) {
    if (maxSteps < 0) {
      throw new IllegalArgumentException("maxSteps must not be negative, was " + maxSteps);
    }
    int steps = 0;
    while (steps < maxSteps && heapSize > 0) {
      long entry = popMin();
      steps++;

      int cost = (int) (entry >>> 32);
      int index = (int) entry;

      // A cell can be pushed more than once, when a cheaper route to it is found after a dearer one
      // was already queued. Only the cheapest entry is worth expanding, and the array is what says
      // which that was.
      if (cost > costs[index]) {
        continue;
      }

      settledCount++;
      expand(index, cost);
    }
    return isComplete();
  }

  /**
   * @return whether the build has finished, which is true once the frontier empties
   */
  public boolean isComplete() {
    return heapSize == 0;
  }

  /**
   * @return how many cells have been settled so far, for a caller sizing its budget
   */
  public int settledCount() {
    return settledCount;
  }

  /**
   * @return the finished field
   * @throws IllegalStateException when the build has not finished
   */
  public FlowField result() {
    if (!isComplete()) {
      throw new IllegalStateException(
          "the field is still building: " + settledCount + " cells settled, frontier not empty");
    }
    return new FlowField(goal, bounds, costs);
  }

  /**
   * Relaxes every edge out of one settled cell.
   *
   * @param index the cell just settled
   * @param cost what reaching it cost
   */
  private void expand(int index, int cost) {
    RelativePos here = bounds.posOf(index);
    for (int[] direction : DIRECTIONS) {
      RelativePos neighbour = here.plus(direction[0], direction[1], direction[2]);
      int neighbourIndex = bounds.indexOf(neighbour);
      if (neighbourIndex < 0) {
        continue;
      }

      OptionalInt entryCost = view.entryCostAt(neighbour);
      if (entryCost.isEmpty()) {
        continue;
      }
      int step = entryCost.getAsInt();

      // A cost that is not positive is a caller that has not answered the question, so the cell is
      // treated as unenterable rather than as free. Silently reading it as zero would make a broken
      // view look like a very good road.
      if (step <= 0) {
        continue;
      }

      if (neighbour.y() != here.y()) {
        step += climbSurcharge;
      }

      int newCost = cost + step;
      // Refused rather than wrapped. A sum that overflows would sort as a negative cost and quietly
      // become the best route in the field.
      if (newCost < 0) {
        continue;
      }

      int existing = costs[neighbourIndex];
      if (existing == UNREACHED || newCost < existing) {
        costs[neighbourIndex] = newCost;
        push(newCost, neighbourIndex);
      }
    }
  }

  /**
   * @param cost the cost to sort by
   * @param index the cell the cost belongs to
   */
  private void push(int cost, int index) {
    if (heapSize == heap.length) {
      heap = Arrays.copyOf(heap, heap.length * 2);
    }
    long entry = ((long) cost << 32) | (index & 0xFFFFFFFFL);
    int at = heapSize++;
    heap[at] = entry;
    while (at > 0) {
      int parent = (at - 1) >>> 1;
      if (heap[parent] <= heap[at]) {
        break;
      }
      swap(parent, at);
      at = parent;
    }
  }

  /**
   * @return the cheapest entry on the frontier
   */
  private long popMin() {
    long top = heap[0];
    heap[0] = heap[--heapSize];
    int at = 0;
    while (true) {
      int left = 2 * at + 1;
      int right = left + 1;
      int smallest = at;
      if (left < heapSize && heap[left] < heap[smallest]) {
        smallest = left;
      }
      if (right < heapSize && heap[right] < heap[smallest]) {
        smallest = right;
      }
      if (smallest == at) {
        break;
      }
      swap(smallest, at);
      at = smallest;
    }
    return top;
  }

  private void swap(int a, int b) {
    long held = heap[a];
    heap[a] = heap[b];
    heap[b] = held;
  }
}
