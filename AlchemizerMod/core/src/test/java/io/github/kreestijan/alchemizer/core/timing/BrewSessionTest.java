package io.github.kreestijan.alchemizer.core.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A brew, and what happens to it when the fire goes out.
 *
 * <p>The assertion that matters is that progress <em>freezes</em> and later resumes. A still that
 * reset its timer every time the fuel ran down would make a long brew impossible to finish, which
 * is the opposite of the machine being demanding.
 */
class BrewSessionTest {

  private static BrewSession advanced(BrewSession session, int ticks, boolean canProgress) {
    BrewSession current = session;
    for (int i = 0; i < ticks; i++) {
      current = current.advance(canProgress);
    }
    return current;
  }

  @Test
  void anIdleStillHasNoBrew() {
    BrewSession idle = BrewSession.idle();

    assertFalse(idle.isRunning());
    assertFalse(idle.isComplete());
    assertEquals(0.0, idle.fraction());
  }

  @Test
  void aBrewAdvancesWhileItCan() {
    BrewSession session = advanced(BrewSession.start(100), 30, true);

    assertEquals(30, session.elapsedTicks());
    assertTrue(session.isRunning());
    assertEquals(0.3, session.fraction(), 1.0e-9);
  }

  @Test
  void progressFreezesWhenTheFuelRunsOut() {
    BrewSession burning = advanced(BrewSession.start(100), 40, true);
    BrewSession starved = advanced(burning, 200, false);

    assertEquals(burning, starved, "an unfuelled still must not lose the work it has done");
    assertEquals(40, starved.elapsedTicks());
  }

  @Test
  void progressResumesFromWhereItStopped() {
    BrewSession session =
        advanced(advanced(advanced(BrewSession.start(100), 40, true), 200, false), 60, true);

    assertEquals(100, session.elapsedTicks());
    assertTrue(session.isComplete());
    assertFalse(session.isRunning());
  }

  @Test
  void aCompletedBrewDoesNotKeepCounting() {
    BrewSession session = advanced(BrewSession.start(20), 500, true);

    assertEquals(20, session.elapsedTicks());
    assertEquals(1.0, session.fraction());
    assertTrue(session.isComplete());
  }

  @Test
  void aSessionCannotBeBuiltOutOfOrder() {
    assertThrows(IllegalArgumentException.class, () -> new BrewSession(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> new BrewSession(10, -1));
    assertThrows(IllegalArgumentException.class, () -> new BrewSession(10, 11));
  }

  @Test
  void aZeroLengthBrewIsIdleRatherThanInstantlyComplete() {
    BrewSession session = BrewSession.start(0);

    assertFalse(session.isComplete());
    assertFalse(session.isRunning());
    assertEquals(0.0, session.fraction());
    assertEquals(session, session.advance(true));
  }

  @Test
  void idleSessionsFromDifferentRoutesAreEqual() {
    assertEquals(BrewSession.idle(), BrewSession.start(0));
  }
}
