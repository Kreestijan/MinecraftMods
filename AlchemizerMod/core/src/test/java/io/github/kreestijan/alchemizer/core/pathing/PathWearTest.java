package io.github.kreestijan.alchemizer.core.pathing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * The wear of a street, asserted by what a caller is told rather than by the map behind it.
 *
 * <p>The interesting properties are the ones the caller relies on to stay simple: that a cell
 * converts on exactly one pass, so that converting on a {@code true} answer needs no bookkeeping,
 * that a cell which cannot be converted is not asked about again and again, and that two cells'
 * counts are each their own.
 */
class PathWearTest {

  private static final int THRESHOLD = 3;
  private static final RelativePos CELL = new RelativePos(1, 0, 1);

  private static PathWear enabled() {
    return new PathWear(THRESHOLD, true);
  }

  @Test
  void nothingHappensBelowTheThreshold() {
    PathWear wear = enabled();

    assertFalse(wear.recordPass(CELL), "the first pass is not enough");
    assertFalse(wear.recordPass(CELL), "nor the second");
    assertEquals(OptionalInt.of(2), wear.passesAt(CELL), "and both were counted");
  }

  @Test
  void theThresholdPassConvertsOnceAndThePassesAfterItDoNot() {
    PathWear wear = enabled();

    wear.recordPass(CELL);
    wear.recordPass(CELL);
    assertTrue(wear.recordPass(CELL), "the pass that reaches the threshold is the one to act on");

    assertFalse(wear.recordPass(CELL), "a converted cell must not be converted a second time");
    assertFalse(wear.recordPass(CELL));
    assertEquals(
        OptionalInt.of(THRESHOLD),
        wear.passesAt(CELL),
        "the count stops at the threshold rather than rising for ever");
  }

  @Test
  void nothingConvertsWhenWearIsTurnedOff() {
    // The config switch. Twenty passes over the same cell must leave the caller never once told to
    // convert, which is what makes the switch a switch rather than a hint.
    PathWear wear = new PathWear(THRESHOLD, false);

    for (int pass = 0; pass < 20; pass++) {
      assertFalse(wear.recordPass(CELL), "a disabled wear never converts");
    }
    assertEquals(
        OptionalInt.empty(), wear.passesAt(CELL), "and does not even count while it is off");
  }

  @Test
  void countsAreKeptPerCell() {
    PathWear wear = enabled();
    RelativePos elsewhere = new RelativePos(2, 0, 1);

    wear.recordPass(CELL);
    wear.recordPass(CELL);
    wear.recordPass(elsewhere);

    assertEquals(OptionalInt.of(2), wear.passesAt(CELL), "one cell's passes are its own");
    assertEquals(OptionalInt.of(1), wear.passesAt(elsewhere), "and so are its neighbour's");
    assertFalse(wear.recordPass(elsewhere), "the neighbour is nowhere near its threshold");
    assertTrue(wear.recordPass(CELL), "while the busy cell has just reached its own");
  }

  @Test
  void aCellNobodyHasWalkedOnHasNoCount() {
    PathWear wear = enabled();

    assertEquals(OptionalInt.empty(), wear.passesAt(CELL));
  }

  @Test
  void aThresholdBelowOneIsRefused() {
    // Zero would mean every cell converts the moment anyone stood on it, and a negative threshold
    // would mean something nobody can state.
    assertThrows(IllegalArgumentException.class, () -> new PathWear(0, true));
    assertThrows(IllegalArgumentException.class, () -> new PathWear(-1, true));
    assertEquals(1, PathWear.MIN_THRESHOLD, "one pass is the most eager setting there is");
  }
}
