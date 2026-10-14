package io.github.kreestijan.alchemizer.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Assembling a tier one still, and every way of getting it wrong.
 *
 * <p>A still is one ignition chamber, one pressure chamber sitting on it, and a tank attached to
 * that chamber. The world is a map of strings here, so the rules can be tested without a running
 * game.
 */
class StillValidatorTest {

  private static final String IGNITION = "alchemizer:ignition_chamber";
  private static final String PRESSURE = "alchemizer:pressure_chamber";
  private static final String TANK = "alchemizer:alchemical_tank";

  private static final int RADIUS = 4;

  private static final MachineBlocks BLOCKS =
      new MachineBlocks(Set.of(IGNITION), Set.of(PRESSURE), Set.of(TANK));

  private static StructureView view(Map<RelativePos, String> cells) {
    return pos -> Optional.ofNullable(cells.get(pos));
  }

  /** An ignition chamber at the origin with a pressure chamber on top of it. */
  private static Map<RelativePos, String> still() {
    Map<RelativePos, String> cells = new HashMap<>();
    cells.put(RelativePos.ORIGIN, IGNITION);
    cells.put(new RelativePos(0, 1, 0), PRESSURE);
    return cells;
  }

  private static ValidationResult validate(Map<RelativePos, String> cells) {
    return StillValidator.validate(view(cells), BLOCKS, RADIUS);
  }

  @Test
  void oneOfEachPartWithATankIsATierOneStill() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertTrue(result.problems().isEmpty());
    assertEquals(1, result.tier().tier());
    assertEquals(1, result.tier().tankCount());
    assertEquals(1, result.tier().ignitionCount());
    assertEquals(1, result.tier().pressureCount());
  }

  @Test
  void tanksReachThroughOtherTanks() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);
    cells.put(new RelativePos(0, 1, 2), TANK);
    cells.put(new RelativePos(0, 2, 2), TANK);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(3, result.tier().tankCount());
  }

  @Test
  void aFloatingTankIsNotConnected() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 3, 3), TANK);

    ValidationResult result = validate(cells);

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.NO_TANK), result.problems());
  }

  @Test
  void aPressureChamberThatIsNotOnTopOfTheFireIsRefused() {
    Map<RelativePos, String> cells = new HashMap<>();
    cells.put(RelativePos.ORIGIN, IGNITION);
    cells.put(new RelativePos(0, 2, 0), PRESSURE);
    cells.put(new RelativePos(0, 2, 1), TANK);

    ValidationResult result = validate(cells);

    assertFalse(result.valid());
    assertEquals(StillTier.NONE, result.tier());
    assertEquals(List.of(StillProblem.PRESSURE_NOT_ON_IGNITION), result.problems());
  }

  @Test
  void twoPressureChambersAreAMistakeNotABetterStill() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(1, 1, 0), PRESSURE);
    cells.put(new RelativePos(0, 1, 1), TANK);

    ValidationResult result = validate(cells);

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.MULTIPLE_PRESSURE), result.problems());
  }

  @Test
  void twoIgnitionChambersAreRefused() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(1, 0, 0), IGNITION);
    cells.put(new RelativePos(0, 1, 1), TANK);

    assertFalse(validate(cells).valid());
  }

  @Test
  void aStillWithNoTankHasNoWaterSupply() {
    ValidationResult result = validate(still());

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.NO_TANK), result.problems());
  }

  @Test
  void anEmptyWorldSaysWhatIsMissingRatherThanCrashing() {
    ValidationResult result = validate(new HashMap<>());

    assertFalse(result.valid());
    assertEquals(2, result.problems().size(), "both chambers are missing");
    assertEquals(StillProblem.NO_IGNITION, result.problems().get(0));
    assertEquals(StillProblem.NO_PRESSURE, result.problems().get(1));
  }

  @Test
  void unreadableCellsAreSkippedRatherThanReported() {
    // A view that can read nothing at all — every chunk unloaded. The verdict must be "not a
    // still",
    // not an exception, and not a claim about a block that was never seen.
    StructureView blind = pos -> Optional.empty();

    ValidationResult result = StillValidator.validate(blind, BLOCKS, RADIUS);

    assertFalse(result.valid());
    assertEquals(StillTier.NONE, result.tier());
  }

  @Test
  void aStillSpanningAnUnloadedBorderIsIncompleteNotBroken() {
    Map<RelativePos, String> cells = still();
    // The tank is there, but the chunk it is in is not loaded, so the view cannot read it back.
    StructureView partial =
        pos -> pos.equals(new RelativePos(0, 1, 1)) ? Optional.empty() : view(cells).blockIdAt(pos);

    ValidationResult result = StillValidator.validate(partial, BLOCKS, RADIUS);

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.NO_TANK), result.problems());
  }

  @Test
  void blocksOutsideTheScanRadiusAreNotSeen() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 9), TANK);

    assertFalse(validate(cells).valid(), "a tank beyond the scan radius cannot be counted");
  }

  @Test
  void aWorldWithNoMachinePartsDefinedSaysSoInsteadOfBlamingThePlayer() {
    ValidationResult result = StillValidator.validate(view(still()), MachineBlocks.empty(), RADIUS);

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.NO_PARTS_DEFINED), result.problems());
  }

  @Test
  void unrelatedBlocksNearbyAreIgnored() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);
    cells.put(new RelativePos(2, 0, 2), "minecraft:stone");
    cells.put(new RelativePos(2, 1, 2), "minecraft:water");

    assertTrue(validate(cells).valid());
  }

  @Test
  void theScanRadiusIsValidatedAndItsVolumeIsKnown() {
    assertThrows(
        IllegalArgumentException.class, () -> StillValidator.validate(view(still()), BLOCKS, 0));
    assertEquals(9 * 9 * 9, StillValidator.scanVolume(4));
  }

  @Test
  void valvesAndBellowsAreZeroAtTierOne() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);

    StillTier tier = validate(cells).tier();

    assertEquals(0, tier.valves());
    assertEquals(0, tier.bellows());
    assertEquals(0, tier.pressureSources(), "tier two's bellows are not counted yet");
  }

  @Test
  void aVerdictCannotBeBuiltWithoutSayingWhy() {
    assertThrows(IllegalArgumentException.class, () -> ValidationResult.invalid(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ValidationResult(true, StillTier.NONE, List.of(StillProblem.NO_TANK)));
    assertThrows(IllegalArgumentException.class, () -> ValidationResult.valid(StillTier.NONE));
  }

  @Test
  void aMissingPartIsReportedBeforeAStructuralOne() {
    // Nothing at all: the missing-chamber messages are the useful ones, and the "not on top" check
    // must not fire on a chamber that does not exist.
    ValidationResult result = validate(new HashMap<>());

    assertFalse(result.problems().contains(StillProblem.PRESSURE_NOT_ON_IGNITION));
  }

  @Test
  void theFirstProblemIsTheOneWorthFixingFirst() {
    // A machine with nothing built has two things wrong with it, and the screen has room for one
    // line: it must be the first thing to do, not whichever the scan happened to notice last.
    ValidationResult result = validate(new HashMap<>());

    assertEquals(Optional.of(StillProblem.NO_IGNITION), result.firstProblem());
  }

  @Test
  void aFormedStillHasNoProblemToReport() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);

    ValidationResult result = validate(cells);

    assertTrue(result.firstProblem().isEmpty());
  }

  @Test
  void aProblemSurvivesBeingSentAsAnOrdinal() {
    // What crosses the menu's data slots is an int, so the round trip is the contract the screen
    // depends on. An ordinal that names nothing has to come back empty rather than throw.
    for (StillProblem problem : StillProblem.values()) {
      assertEquals(Optional.of(problem), StillProblem.ofOrdinal(problem.ordinal()));
    }
    assertTrue(StillProblem.ofOrdinal(StillProblem.NONE_ORDINAL).isEmpty());
    assertTrue(StillProblem.ofOrdinal(StillProblem.values().length).isEmpty());
  }

  @Test
  void anInspectionNamesTheTanksTheStillDrawsOn() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);
    cells.put(new RelativePos(0, 1, 2), TANK);
    // Beyond the scan radius, so it is not seen at all — let alone counted as connected.
    cells.put(new RelativePos(0, 1, 9), TANK);

    StillValidator.Inspection inspection = StillValidator.inspect(view(cells), BLOCKS, RADIUS);

    assertTrue(inspection.result().valid());
    assertEquals(
        Set.of(new RelativePos(0, 1, 1), new RelativePos(0, 1, 2)),
        inspection.tanks(),
        "the tanks behind the first one are part of the same cluster");
  }

  @Test
  void anUnformedStillOffersNoTanksToDrawFrom() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);
    cells.put(new RelativePos(1, 1, 0), PRESSURE);

    StillValidator.Inspection inspection = StillValidator.inspect(view(cells), BLOCKS, RADIUS);

    assertFalse(inspection.result().valid());
    assertTrue(inspection.tanks().isEmpty(), "an unformed machine must not be able to draw water");
  }
}
