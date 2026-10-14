package io.github.kreestijan.alchemizer.core.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import org.junit.jupiter.api.Test;

/**
 * The brew-time curve, and the floor under it.
 *
 * <p>The floor is the part worth testing: repeated multiplication by {@code 0.9} underflows, and
 * rounding eventually reaches zero ticks. The design document asks for a brew time that never
 * reaches zero, so the floor makes that true rather than leaving it to arithmetic that does not.
 */
class BrewTimeTest {

  private static final Tuning TUNING = TuningDefaults.defaultTuning();

  @Test
  void theFiveSecondPresetTakesOneHundredTicks() {
    assertEquals(100, BrewTime.ticks(HourglassPreset.FIVE, 0, TUNING));
  }

  @Test
  void eachPresetScalesWithItsOwnBaseTime() {
    assertEquals(100, BrewTime.ticks(HourglassPreset.FIVE, 0, TUNING));
    assertEquals(300, BrewTime.ticks(HourglassPreset.FIFTEEN, 0, TUNING));
    assertEquals(600, BrewTime.ticks(HourglassPreset.THIRTY, 0, TUNING));
    assertEquals(900, BrewTime.ticks(HourglassPreset.FORTY_FIVE, 0, TUNING));
  }

  @Test
  void oneSetOfBellowsTakesATenth() {
    assertEquals(90, BrewTime.ticks(HourglassPreset.FIVE, 1, TUNING));
    assertEquals(81, BrewTime.ticks(HourglassPreset.FIVE, 2, TUNING));
  }

  @Test
  void bellowsHaveDiminishingReturns() {
    int previous = BrewTime.ticks(HourglassPreset.THIRTY, 0, TUNING);
    int firstSaving = previous - BrewTime.ticks(HourglassPreset.THIRTY, 1, TUNING);
    int lastSaving = 0;

    for (int valves = 1; valves <= 10; valves++) {
      int current = BrewTime.ticks(HourglassPreset.THIRTY, valves, TUNING);
      assertTrue(current <= previous, "time must never rise as bellows are added");
      lastSaving = previous - current;
      previous = current;
    }

    assertTrue(lastSaving < firstSaving, "the tenth set of bellows must save less than the first");
  }

  @Test
  void absurdNumbersOfBellowsStillLeaveABrewTime() {
    int floored = BrewTime.ticks(HourglassPreset.FIVE, 500, TUNING);

    assertEquals(TuningDefaults.MIN_BREW_TICKS, floored);
    assertTrue(floored >= 20, "a brew may never take less than a second");
  }

  @Test
  void theFloorIsTheFloorEvenAtTheShortestPreset() {
    assertEquals(
        TuningDefaults.MIN_BREW_TICKS, BrewTime.ticks(HourglassPreset.FIVE, 1_000_000, TUNING));
  }

  @Test
  void aFloorOverrideIsHonoured() {
    assertEquals(5, BrewTime.ticks(1, 40, 0.5, 5));
  }

  @Test
  void absurdInputsAreRefusedRatherThanGuessedAt() {
    assertThrows(IllegalArgumentException.class, () -> BrewTime.ticks(0, 0, 0.9, 20));
    assertThrows(IllegalArgumentException.class, () -> BrewTime.ticks(-5, 0, 0.9, 20));
    assertThrows(IllegalArgumentException.class, () -> BrewTime.ticks(5, 0, 0.9, 0));
    assertThrows(IllegalArgumentException.class, () -> BrewTime.ticks(5, 0, 0.0, 20));
    assertThrows(IllegalArgumentException.class, () -> BrewTime.ticks(5, 0, 1.5, 20));
    assertThrows(IllegalArgumentException.class, () -> BrewTime.ticks(5, 0, Double.NaN, 20));
  }

  @Test
  void negativeBellowsCountAsNoneRatherThanSpeedingTheBrew() {
    assertEquals(100, BrewTime.ticks(HourglassPreset.FIVE, -3, TUNING));
  }
}
