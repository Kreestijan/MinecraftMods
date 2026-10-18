package io.github.kreestijan.alchemizer.core.flowfield;

import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A built navigation field: what every reachable cell costs to walk from, and which way to step.
 *
 * <p>The cost stored against a cell is the cost of getting from that cell <em>to the goal</em>, so
 * the counter is the cheapest cell in the field and the numbers rise as you walk away from it. That
 * direction is the one that makes the field useful to a crowd: one build answers "how do I get to
 * the counter" for every customer, wherever each of them happens to be standing, where a search per
 * customer would answer it for one of them at the price of the whole field.
 *
 * <p><b>Immutable, and deliberately so.</b> A built field is handed to entities that read it every
 * tick while another thread of work may be preparing the next one; a field that could be mutated
 * under them would be a race with no symptom until a customer walked into a wall. The builder keeps
 * its own array and this takes a copy.
 *
 * <p><b>Walking is orthogonal.</b> Six directions — four across, up and down — and no diagonals. A
 * diagonal step has to answer whether it may squeeze past a corner, and there is no answer that is
 * obviously right: a customer that cuts a corner clips through a fence post. Straight steps have no
 * such question, and an entity steering toward the next cell's centre smooths the result out
 * afterwards anyway.
 */
public final class FlowField {

  /**
   * The six directions a step may take, in the order ties are broken.
   *
   * <p>Fixed and written down because "which of two equally good ways round" must not depend on how
   * a hash happened to iterate: two customers in the same cell should always be told the same
   * thing.
   */
  private static final int[][] DIRECTIONS = {
    {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}
  };

  /**
   * The cost written against a cell nothing can reach. Never escapes: queries answer in Optionals.
   */
  private static final int UNREACHABLE = -1;

  private final RelativePos goal;
  private final FieldBounds bounds;
  private final int[] costs;
  private final int reachableCount;

  /**
   * @param goal where the field leads to, and the cell seeded at zero cost
   * @param bounds the box the field covers
   * @param costs the builder's finished cost array, which this copies
   */
  FlowField(RelativePos goal, FieldBounds bounds, int[] costs) {
    this.goal = Objects.requireNonNull(goal, "goal");
    this.bounds = Objects.requireNonNull(bounds, "bounds");
    this.costs = costs.clone();

    int reachable = 0;
    for (int cost : this.costs) {
      if (cost != UNREACHABLE) {
        reachable++;
      }
    }
    this.reachableCount = reachable;
  }

  /**
   * @return where the field leads
   */
  public RelativePos goal() {
    return goal;
  }

  /**
   * @return the box the field covers
   */
  public FieldBounds bounds() {
    return bounds;
  }

  /**
   * @param pos the offset to ask about
   * @return whether a customer standing there could reach the goal
   */
  public boolean reaches(RelativePos pos) {
    return costAt(pos).isPresent();
  }

  /**
   * @param pos the offset to ask about
   * @return what a step from that cell to the goal costs in total, or empty when it cannot be
   *     walked
   */
  public OptionalInt costAt(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    int index = bounds.indexOf(pos);
    if (index < 0 || costs[index] == UNREACHABLE) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(costs[index]);
  }

  /**
   * The cell to step into next, coming from the given one.
   *
   * <p>Whichever neighbour is cheapest to finish from — the field is a gradient, so heading
   * downhill is heading to the counter. Ties are broken by {@link #DIRECTIONS}' own order rather
   * than by anything incidental, so the answer is the same every time it is asked.
   *
   * <p>Empty means "do not move": either that cell cannot reach the counter at all, or it
   * <em>is</em> the counter and the journey is over.
   *
   * @param pos where the customer is standing
   * @return the next cell on the way, or empty when there is nowhere to go
   */
  public Optional<RelativePos> nextStepFrom(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    int index = bounds.indexOf(pos);
    if (index < 0 || costs[index] == UNREACHABLE || pos.equals(goal)) {
      return Optional.empty();
    }

    int here = costs[index];
    RelativePos best = null;
    int bestCost = here;

    for (int[] direction : DIRECTIONS) {
      RelativePos neighbour =
          new RelativePos(pos.x() + direction[0], pos.y() + direction[1], pos.z() + direction[2]);
      int neighbourIndex = bounds.indexOf(neighbour);
      if (neighbourIndex < 0) {
        continue;
      }
      int cost = costs[neighbourIndex];
      if (cost != UNREACHABLE && cost < bestCost) {
        bestCost = cost;
        best = neighbour;
      }
    }
    return Optional.ofNullable(best);
  }

  /**
   * The line a queue forms in: the cells running straight out from the goal's face.
   *
   * <p>{@code DESIGN.md} §5.2 asks waiting customers to stand in a neat line along the route from
   * the counter, one block apart, and this is that line drawn from the direction a Counter faces
   * rather than from the field's own gradient. The gradient was the first attempt, and walking
   * outward taking the cheapest neighbour at each step is exactly the walk that showed why it does
   * not do: over symmetric ground a field has no reason to prefer any one way round, so the line it
   * produced could bend round a corner or leave in a second direction, and players saw a shop's
   * customers queuing three ways at once. A Counter that faces somewhere has a front, and a queue
   * stands in front of it.
   *
   * <p><b>One direction, no turns.</b> The cells are the goal's neighbour one step along the face,
   * then one step further the same way, and so on. A line is a <em>sequence</em> — the first cell
   * is the one in front of the counter and each cell after it is where the next person back stands
   * — and this is that sequence by arithmetic rather than by search: every cell is one orthogonal
   * step from the one before it, all of them are on the goal's own level, and asking twice gives
   * the same answer because nothing in the walk depends on anything but the goal and the direction.
   *
   * <p><b>The direction arrives as two numbers.</b> {@code core} cannot name Minecraft's {@code
   * Direction}, so a caller that has one — the Counter reads its own block state — passes its
   * horizontal step instead, which is all the walk needs. A pair that is not a single horizontal
   * step is refused rather than guessed at: a line that went nowhere, or diagonally, would not be a
   * line.
   *
   * <p><b>A queue cannot have a hole in it, so the line stops at the first cell the field does not
   * reach.</b> This is the other thing the gradient walk could not say: stepping over an
   * unreachable cell and carrying on past it would put a place to stand inside a wall, or on the
   * far side of one, with the people behind it queuing in the rock. A wall across the front of the
   * shop therefore shortens the line to the cells between the shop and the wall, which is the
   * design's cap made honest by the ground: the cap is the most that may wait, and somewhere to
   * stand is what makes one of those places real.
   *
   * <p>Empty is an ordinary answer, not a failure: a Counter whose face is against a wall has no
   * cell in front of it, and a shop with no line has nobody waiting at it.
   *
   * @param dx the east-west step of the direction the goal faces, which is -1, 0 or 1
   * @param dz the north-south step of it, which is -1, 0 or 1
   * @param maxLength the most cells the line may hold, which is the shop's own cap on how many
   *     people may wait at once
   * @return the cells of the line, the first of them in front of the goal; empty when there is
   *     nowhere to stand
   * @throws IllegalArgumentException when the pair is not one step along one horizontal axis, or
   *     when maxLength is negative
   */
  public List<RelativePos> laneFacing(int dx, int dz, int maxLength) {
    if (Math.abs(dx) + Math.abs(dz) != 1) {
      throw new IllegalArgumentException(
          "a facing has to be one step along one horizontal axis, was (" + dx + ", " + dz + ")");
    }
    if (maxLength < 0) {
      throw new IllegalArgumentException("maxLength must not be negative, was " + maxLength);
    }

    List<RelativePos> lane = new ArrayList<>();
    RelativePos at = goal;
    while (lane.size() < maxLength) {
      at = at.plus(dx, 0, dz);
      if (!reaches(at)) {
        break;
      }
      lane.add(at);
    }
    return List.copyOf(lane);
  }

  /**
   * @return how many cells, the goal included, a customer could reach the counter from
   */
  public int reachableCount() {
    return reachableCount;
  }

  /**
   * The cells a customer may appear in: reachable ones on the field's outer ring.
   *
   * <p>Reachable is the whole point. The design's rule is that a customer is only placed where a
   * route to the counter has been proved to exist, and the field is that proof — a ring cell the
   * field never reached is on the far side of a wall, in mid-air, or in a chunk that was not loaded
   * when the field was built, and spawning there would strand a customer.
   *
   * @return every reachable cell on the outer ring, in no particular order
   */
  public List<RelativePos> spawnCandidates() {
    List<RelativePos> candidates = new ArrayList<>();
    for (int index = 0; index < costs.length; index++) {
      if (costs[index] == UNREACHABLE) {
        continue;
      }
      RelativePos pos = bounds.posOf(index);
      if (bounds.isPerimeter(pos)) {
        candidates.add(pos);
      }
    }
    return List.copyOf(candidates);
  }
}
