package io.github.kreestijan.alchemizer.core.flowfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The flow field, asserted by what a walker would do rather than by the array it produces.
 *
 * <p>The interesting properties are the ones a customer depends on: that the field stops at walls
 * instead of walking through them, that it prefers a cheap path to a short one, and — the one an
 * incremental algorithm fails quietly — that stopping and resuming the build changes nothing about
 * the answer.
 */
class FlowFieldTest {

  private static final int FLAT = 10;
  private static final RelativePos GOAL = RelativePos.ORIGIN;

  /** A field one level thick, which is all a flat test needs. */
  private static FieldBounds plane(int radius) {
    return new FieldBounds(radius, 0, 0);
  }

  /**
   * @param byPos what each walkable cell costs; anything absent cannot be stood in
   * @return a view over that map
   */
  private static NavView navOf(Map<RelativePos, Integer> byPos) {
    return pos -> {
      Integer cost = byPos.get(pos);
      return cost == null ? OptionalInt.empty() : OptionalInt.of(cost);
    };
  }

  /**
   * @param radius the field's reach
   * @param blocked cells to leave out of an otherwise open floor
   * @return an open floor at y=0 with those cells missing
   */
  private static NavView floor(int radius, Set<RelativePos> blocked) {
    Map<RelativePos, Integer> byPos = new HashMap<>();
    for (int x = -radius; x <= radius; x++) {
      for (int z = -radius; z <= radius; z++) {
        RelativePos pos = new RelativePos(x, 0, z);
        if (!blocked.contains(pos)) {
          byPos.put(pos, FLAT);
        }
      }
    }
    return navOf(byPos);
  }

  private static FlowField build(FieldBounds bounds, NavView view) {
    return build(GOAL, bounds, view, 0);
  }

  private static FlowField build(
      RelativePos goal, FieldBounds bounds, NavView view, int climbSurcharge) {
    FlowFieldBuilder builder = new FlowFieldBuilder(goal, bounds, view, climbSurcharge);
    builder.advance(Integer.MAX_VALUE);
    return builder.result();
  }

  private static int costAt(FlowField field, RelativePos pos) {
    OptionalInt cost = field.costAt(pos);
    assertTrue(cost.isPresent(), () -> "expected " + pos + " to be reachable");
    return cost.getAsInt();
  }

  /**
   * Walks the field from a cell to the goal, one step at a time.
   *
   * @return how many steps it took, or -1 if it never arrived
   */
  private static int walkToGoal(FlowField field, RelativePos from, int limit) {
    RelativePos at = from;
    for (int steps = 0; steps < limit; steps++) {
      if (at.equals(field.goal())) {
        return steps;
      }
      Optional<RelativePos> next = field.nextStepFrom(at);
      if (next.isEmpty()) {
        return -1;
      }
      RelativePos stepped = next.get();
      assertNotEquals(at, stepped, "a step must move somewhere");
      at = stepped;
    }
    return -1;
  }

  // ---------------------------------------------------------------- the basic shape

  @Test
  void theCounterCostsNothingBecauseItIsWhereEveryoneIsGoing() {
    FlowField field = build(plane(3), floor(3, Set.of()));

    assertEquals(0, costAt(field, GOAL));
  }

  @Test
  void costGrowsWithDistanceAcrossAnOpenFloor() {
    FlowField field = build(plane(4), floor(4, Set.of()));

    assertEquals(FLAT, costAt(field, new RelativePos(1, 0, 0)));
    assertEquals(3 * FLAT, costAt(field, new RelativePos(3, 0, 0)));
    assertEquals(4 * FLAT, costAt(field, new RelativePos(2, 0, 2)));
  }

  @Test
  void aStepFromAnywhereHeadsTowardTheCounter() {
    FlowField field = build(plane(4), floor(4, Set.of()));

    assertEquals(
        Optional.of(new RelativePos(2, 0, 0)), field.nextStepFrom(new RelativePos(3, 0, 0)));
    assertEquals(
        Optional.of(new RelativePos(0, 0, 0)), field.nextStepFrom(new RelativePos(1, 0, 0)));
  }

  @Test
  void standingOnTheCounterThereIsNowhereLeftToGo() {
    FlowField field = build(plane(3), floor(3, Set.of()));

    assertFalse(field.nextStepFrom(GOAL).isPresent());
  }

  // ---------------------------------------------------------------- blocked

  @Test
  void aWallIsNotWalkedThrough() {
    // A full wall along z = 1, so the far half of the floor is cut off from the counter.
    Set<RelativePos> wall = new HashSet<>();
    for (int x = -3; x <= 3; x++) {
      wall.add(new RelativePos(x, 0, 1));
    }
    FlowField field = build(plane(3), floor(3, wall));

    assertTrue(field.reaches(new RelativePos(0, 0, 0)));
    assertFalse(field.reaches(new RelativePos(0, 0, 2)), "the far side of a wall is unreachable");
    assertFalse(field.reaches(new RelativePos(3, 0, 3)));
  }

  @Test
  void theCounterItselfNeedNotBeSomethingACustomerCouldStandOn() {
    // The counter is a solid block, so the view says nothing can stand anywhere — including on it.
    // The goal is still seeded, because the journey ends beside the counter rather than in it.
    NavView nothingIsWalkable = pos -> OptionalInt.empty();
    FlowField field = build(plane(2), nothingIsWalkable);

    assertEquals(1, field.reachableCount(), "only the seeded counter is reachable");
    assertEquals(0, costAt(field, GOAL));
    assertFalse(field.reaches(new RelativePos(1, 0, 0)));
    assertTrue(field.spawnCandidates().isEmpty(), "nothing can reach a spawn point");
  }

  @Test
  void aCostThatIsNotPositiveIsTreatedAsBlockedRatherThanFree() {
    // The fail-safe contract on NavView: a caller that has not answered must not read as a road.
    NavView zeroCost = pos -> OptionalInt.of(0);
    FlowFieldBuilder builder = new FlowFieldBuilder(GOAL, plane(2), zeroCost, 0);
    builder.advance(Integer.MAX_VALUE);
    FlowField field = builder.result();

    assertEquals(1, field.reachableCount());
    assertFalse(field.reaches(new RelativePos(1, 0, 0)));
  }

  @Test
  void theFieldCoversOnlyItsOwnBox() {
    FlowField field = build(plane(2), floor(2, Set.of()));

    assertFalse(field.reaches(new RelativePos(3, 0, 0)), "past the eastern edge");
    assertFalse(field.reaches(new RelativePos(0, 1, 0)), "above a field one level thick");
  }

  // ---------------------------------------------------------------- weighted

  @Test
  void aCheapDetourBeatsAnExpensiveShortcut() {
    // The heart of it: two ways to the same cell, and the field must price the cheap one. A plain
    // breadth-first search would report the shortcut, because it is fewer cells.
    Map<RelativePos, Integer> byPos = new HashMap<>();
    byPos.put(new RelativePos(1, 0, 0), 1000); // the shortcut, ruinously dear
    byPos.put(new RelativePos(0, 0, 1), 1);
    byPos.put(new RelativePos(1, 0, 1), 1);
    byPos.put(new RelativePos(2, 0, 1), 1);
    byPos.put(new RelativePos(2, 0, 0), 1);

    FlowField field = build(plane(3), navOf(byPos));

    // Goal -> (0,0,1) -> (1,0,1) -> (2,0,1) -> (2,0,0) is four steps of one, against 1001 direct.
    assertEquals(4, costAt(field, new RelativePos(2, 0, 0)));
    assertEquals(
        Optional.of(new RelativePos(2, 0, 1)),
        field.nextStepFrom(new RelativePos(2, 0, 0)),
        "the first step should take the cheap way round");
  }

  @Test
  void aClimbIsChargedOnTopOfTheGroundItLandsOn() {
    // Two cells out from the counter at the same level, then one cell above the second. The climb
    // surcharge applies to the step that changes height and to nothing else.
    Map<RelativePos, Integer> byPos = new HashMap<>();
    byPos.put(new RelativePos(0, 0, 1), 1);
    byPos.put(new RelativePos(0, 1, 1), 1);

    FieldBounds bounds = new FieldBounds(2, 0, 1);
    FlowField level = build(GOAL, bounds, navOf(byPos), 0);
    FlowField climbing = build(GOAL, bounds, navOf(byPos), 100);

    assertEquals(1, costAt(level, new RelativePos(0, 0, 1)), "one level step");
    assertEquals(1, costAt(climbing, new RelativePos(0, 0, 1)), "a level step is never charged");

    assertEquals(2, costAt(level, new RelativePos(0, 1, 1)), "two level steps");
    assertEquals(102, costAt(climbing, new RelativePos(0, 1, 1)), "two steps, one of them a climb");
  }

  // ---------------------------------------------------------------- interruption

  @Test
  void buildingInSlicesProducesTheSameFieldAsBuildingInOneGo() {
    // The property an incremental algorithm gets wrong quietly, so it is asserted directly: the
    // answer must not depend on where the build was interrupted.
    FieldBounds bounds = plane(6);
    NavView view = floor(6, Set.of(new RelativePos(0, 0, 3), new RelativePos(1, 0, 3)));

    FlowField whole = build(bounds, view);

    FlowFieldBuilder bitByBit = new FlowFieldBuilder(GOAL, bounds, view, 0);
    int guard = 0;
    while (!bitByBit.advance(1)) {
      assertTrue(++guard < 10_000, "the build should finish in a bounded number of steps");
    }
    FlowField sliced = bitByBit.result();

    assertEquals(whole.reachableCount(), sliced.reachableCount());
    for (int x = -6; x <= 6; x++) {
      for (int z = -6; z <= 6; z++) {
        RelativePos pos = new RelativePos(x, 0, z);
        assertEquals(whole.costAt(pos), sliced.costAt(pos), () -> "cost disagreed at " + pos);
        assertEquals(
            whole.nextStepFrom(pos),
            sliced.nextStepFrom(pos),
            () -> "the next step disagreed at " + pos);
      }
    }
  }

  @Test
  void advancingWithNoBudgetChangesNothing() {
    FieldBounds bounds = plane(3);
    NavView view = floor(3, Set.of());
    FlowFieldBuilder builder = new FlowFieldBuilder(GOAL, bounds, view, 0);

    assertFalse(builder.isComplete(), "nothing has been done yet");
    assertFalse(builder.advance(0), "an empty slice does no work");
    assertEquals(0, builder.settledCount());

    builder.advance(Integer.MAX_VALUE);
    assertTrue(builder.isComplete());
    assertThrows(IllegalArgumentException.class, () -> builder.advance(-1));
  }

  @Test
  void askingForTheResultBeforeTheBuildFinishesIsRefused() {
    FlowFieldBuilder builder = new FlowFieldBuilder(GOAL, plane(4), floor(4, Set.of()), 0);

    assertThrows(IllegalStateException.class, builder::result);
  }

  @Test
  void theSameInputAlwaysGivesTheSameDirections() {
    FieldBounds bounds = plane(4);
    NavView view = floor(4, Set.of(new RelativePos(1, 0, 0), new RelativePos(1, 0, 1)));

    FlowField first = build(bounds, view);
    FlowField second = build(bounds, view);

    assertEquals(
        first.nextStepFrom(new RelativePos(2, 0, 0)),
        second.nextStepFrom(new RelativePos(2, 0, 0)));
    assertEquals(
        first.nextStepFrom(new RelativePos(0, 0, 2)),
        second.nextStepFrom(new RelativePos(0, 0, 2)));
  }

  // ---------------------------------------------------------------- walking the field

  @Test
  void followingTheGradientArrivesAtTheCounterFromEveryReachableCell() {
    // The end-to-end property: whatever the field says about costs, a walker that just keeps taking
    // the next step must end up at the counter rather than in a loop.
    Set<RelativePos> blocked = Set.of(new RelativePos(0, 0, 1), new RelativePos(1, 0, 1));
    FieldBounds bounds = plane(5);
    FlowField field = build(bounds, floor(5, blocked));

    int checked = 0;
    for (int x = -5; x <= 5; x++) {
      for (int z = -5; z <= 5; z++) {
        RelativePos from = new RelativePos(x, 0, z);
        if (!field.reaches(from)) {
          continue;
        }
        int steps = walkToGoal(field, from, field.reachableCount() + 1);
        assertTrue(steps >= 0, () -> "walking from " + from + " never reached the counter");
        checked++;
      }
    }
    assertTrue(checked > 40, "the floor should be mostly reachable, only " + checked + " were");
  }

  // ---------------------------------------------------------------- where customers may appear

  @Test
  void spawnCandidatesAreOnlyReachableCellsOnTheOuterRing() {
    // A wall along z = 1, one row out from the counter, cuts the floor in two and leaves the
    // counter
    // on the near side. The far half is still on the ring — but nothing there has a route, so no
    // customer may be placed there. Note the wall cannot sit on the counter's own row: a cell
    // beside
    // the counter is one step from it whatever is underfoot, so a wall there would not divide
    // anything.
    Set<RelativePos> wall = new HashSet<>();
    for (int x = -4; x <= 4; x++) {
      wall.add(new RelativePos(x, 0, 1));
    }
    FieldBounds bounds = plane(4);
    FlowField field = build(bounds, floor(4, wall));

    List<RelativePos> candidates = field.spawnCandidates();

    assertFalse(candidates.isEmpty(), "the counter's own side of the wall is reachable");
    for (RelativePos candidate : candidates) {
      assertTrue(bounds.isPerimeter(candidate), () -> candidate + " is not on the ring");
      assertTrue(field.reaches(candidate), () -> candidate + " is on the ring but unreachable");
      assertTrue(candidate.z() <= 0, () -> candidate + " is on the far side of the wall");
    }
    assertTrue(
        candidates.stream().anyMatch(candidate -> candidate.z() == -4),
        "the ring should be reached on the counter's own side");
  }

  @Test
  void aCustomerDroppedOnAnySpawnCandidateCanReachTheCounter() {
    FieldBounds bounds = plane(5);
    FlowField field = build(bounds, floor(5, Set.of()));

    List<RelativePos> candidates = field.spawnCandidates();

    assertFalse(candidates.isEmpty());
    for (RelativePos candidate : candidates) {
      assertTrue(field.reaches(candidate), () -> candidate + " is a candidate with no route");
    }
  }

  // ---------------------------------------------------------------- bounds

  @Test
  void nonsenseBoundsAreRefusedByName() {
    assertThrows(IllegalArgumentException.class, () -> new FieldBounds(0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new FieldBounds(129, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new FieldBounds(4, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new FieldBounds(4, 0, -1));
    assertThrows(IllegalArgumentException.class, () -> new FieldBounds(128, 128, 128));
  }

  @Test
  void aFieldKnowsItsOwnExtent() {
    FieldBounds bounds = new FieldBounds(2, 1, 3);

    assertEquals(5, bounds.side());
    assertEquals(5, bounds.layers());
    assertEquals(125, bounds.cellCount());
    assertTrue(bounds.contains(new RelativePos(-2, -1, 2)));
    assertFalse(bounds.contains(new RelativePos(2, -2, 0)), "one below the bottom");
    assertFalse(bounds.contains(new RelativePos(0, 4, 0)), "one above the top");
  }

  @Test
  void aGoalOutsideTheFieldIsRefusedRatherThanSilentlyDropped() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FlowFieldBuilder(new RelativePos(9, 0, 0), plane(2), floor(2, Set.of()), 0));
  }

  // ---------------------------------------------------------------- the queue's line

  /**
   * @param from one cell
   * @param to another
   * @return how many orthogonal steps apart they are
   */
  private static int steps(RelativePos from, RelativePos to) {
    return Math.abs(from.x() - to.x()) + Math.abs(from.y() - to.y()) + Math.abs(from.z() - to.z());
  }

  @Test
  void theLaneRunsOutFromTheFaceAndNeitherBackNorAcrossIt() {
    // The whole point of the facing: the line leaves the counter in one direction and carries on in
    // it. Written as an exact list rather than as a property, because the failure this replaces is
    // one a property cannot see — a walk over symmetric ground may bend, double back or set off
    // sideways and still be one cell at a time, all reachable, and entirely the wrong queue.
    FlowField field = build(plane(6), floor(6, Set.of()));

    assertEquals(
        List.of(
            new RelativePos(1, 0, 0),
            new RelativePos(2, 0, 0),
            new RelativePos(3, 0, 0),
            new RelativePos(4, 0, 0)),
        field.laneFacing(1, 0, 4),
        "the line has to leave the counter in the direction it faces and stay on that line");

    assertEquals(
        List.of(
            new RelativePos(-1, 0, 0),
            new RelativePos(-2, 0, 0),
            new RelativePos(-3, 0, 0),
            new RelativePos(-4, 0, 0)),
        field.laneFacing(-1, 0, 4),
        "and a counter facing the other way queues the other way, in the same cells reversed");

    assertEquals(
        List.of(
            new RelativePos(0, 0, -1),
            new RelativePos(0, 0, -2),
            new RelativePos(0, 0, -3),
            new RelativePos(0, 0, -4)),
        field.laneFacing(0, -1, 4),
        "a face along the other axis is a line along the other axis");
  }

  @Test
  void theLaneStartsInFrontOfTheGoalAndNeverInIt() {
    FlowField field = build(plane(4), floor(4, Set.of()));

    List<RelativePos> lane = field.laneFacing(1, 0, 4);

    assertFalse(lane.isEmpty(), "an open floor has somewhere to stand in front of the counter");
    assertEquals(1, steps(GOAL, lane.get(0)), "the first cell of the line is one step from it");
    assertFalse(lane.contains(GOAL), "and the line is where people wait, not the counter itself");
  }

  @Test
  void theLaneIsOneCellAtATimeAndAlwaysReachable() {
    // The design's "one block apart" as arithmetic: each cell is a single orthogonal step from the
    // one before it, so nobody in the line is standing two cells from their neighbour and there is
    // no cell in the line a customer could not walk to.
    FlowField field = build(plane(5), floor(5, Set.of(new RelativePos(3, 0, 0))));

    List<RelativePos> lane = field.laneFacing(1, 0, 6);

    for (int index = 1; index < lane.size(); index++) {
      RelativePos behind = lane.get(index - 1);
      RelativePos ahead = lane.get(index);
      assertEquals(1, steps(behind, ahead), () -> ahead + " is not one step from " + behind);
    }
    for (RelativePos cell : lane) {
      assertTrue(field.reaches(cell), () -> cell + " is on the line but has no route to the shop");
    }
  }

  @Test
  void theSameFieldAlwaysGivesTheSameLane() {
    // Two customers standing in the same queue must be told the same thing about where the line
    // runs, and a line that changed between one build and the next would shuffle a queue nobody had
    // touched.
    FieldBounds bounds = plane(4);
    NavView view = floor(4, Set.of(new RelativePos(1, 0, 1), new RelativePos(2, 0, 0)));
    FlowField first = build(bounds, view);

    assertEquals(
        first.laneFacing(1, 0, 5), first.laneFacing(1, 0, 5), "asked twice, the same line");
    assertEquals(
        first.laneFacing(1, 0, 5),
        build(bounds, view).laneFacing(1, 0, 5),
        "and the same line from the same world built again");
  }

  @Test
  void theLaneStopsAtItsBound() {
    FlowField field = build(plane(6), floor(6, Set.of()));

    List<RelativePos> unbounded = field.laneFacing(1, 0, 6);
    List<RelativePos> capped = field.laneFacing(1, 0, 2);

    assertEquals(6, unbounded.size(), "an open floor has room for a line of six");
    assertEquals(2, capped.size(), "a cap of two is a line of two");
    assertEquals(unbounded.subList(0, 2), capped, "and it is the head of the same line");
    assertTrue(field.laneFacing(1, 0, 0).isEmpty(), "a cap of nothing is nobody waiting");
    assertThrows(IllegalArgumentException.class, () -> field.laneFacing(1, 0, -1));
  }

  @Test
  void aCellOffTheLaneEndsItRatherThanBeingJumped() {
    // A corridor of two cells running east out of the counter and an unreachable cell beyond them.
    // The line has to stop there: a queue with a hole in it is not a queue, and carrying on past
    // the hole would put a place to stand where nobody could walk to it.
    Map<RelativePos, Integer> byPos = new HashMap<>();
    byPos.put(new RelativePos(1, 0, 0), FLAT);
    byPos.put(new RelativePos(2, 0, 0), FLAT);
    byPos.put(new RelativePos(4, 0, 0), FLAT);
    byPos.put(new RelativePos(5, 0, 0), FLAT);
    FlowField field = build(plane(6), navOf(byPos));

    assertEquals(
        List.of(new RelativePos(1, 0, 0), new RelativePos(2, 0, 0)),
        field.laneFacing(1, 0, 8),
        "the line stops at the cell with no route behind it");
  }

  @Test
  void aFaceWithNowhereToStandHasNoLane() {
    // A counter whose face is against a wall. Empty is an answer, not a failure: the shop has no
    // place to put anybody, and the same state is a shop whose field has not been built yet.
    Map<RelativePos, Integer> byPos = new HashMap<>();
    byPos.put(new RelativePos(0, 0, 1), FLAT);
    FlowField field = build(plane(3), navOf(byPos));

    assertTrue(field.laneFacing(1, 0, 4).isEmpty(), "nothing can stand east of this counter");
    assertEquals(
        List.of(new RelativePos(0, 0, 1)),
        field.laneFacing(0, 1, 4),
        "and the side that can be walked to is the side the line starts on");
  }

  @Test
  void aDirectionThatIsNotAFacingIsRefused() {
    // Guessing would be worse than refusing: a pair that steps nowhere, steps twice, or steps
    // diagonally is a caller that has misunderstood what it is handing over, and every one of them
    // would produce a line that is not in front of anything.
    FlowField field = build(plane(3), floor(3, Set.of()));

    assertThrows(IllegalArgumentException.class, () -> field.laneFacing(0, 0, 4));
    assertThrows(IllegalArgumentException.class, () -> field.laneFacing(1, 1, 4));
    assertThrows(IllegalArgumentException.class, () -> field.laneFacing(2, 0, 4));
    assertThrows(IllegalArgumentException.class, () -> field.laneFacing(0, -3, 4));
  }

  @Test
  void cellsAreIndexedAndRecoveredSymmetrically() {
    FieldBounds bounds = new FieldBounds(3, 2, 2);

    for (int x = -3; x <= 3; x++) {
      for (int y = -2; y <= 2; y++) {
        for (int z = -3; z <= 3; z++) {
          RelativePos pos = new RelativePos(x, y, z);
          assertEquals(pos, bounds.posOf(bounds.indexOf(pos)));
        }
      }
    }
    assertEquals(-1, bounds.indexOf(new RelativePos(4, 0, 0)));
  }
}
