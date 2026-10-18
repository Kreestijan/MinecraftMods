package io.github.kreestijan.alchemizer.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.machine.MachineBlocks.Part;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Assembling a still, tier by tier, and every way of getting it wrong.
 *
 * <p>A tier one still is one ignition chamber, one pressure chamber sitting on it, and a tank
 * attached to that chamber; each tier above adds parts whose placement rules are checked here. The
 * world is a map of strings, so the rules can be tested without a running game.
 */
class StillValidatorTest {

  private static final String IGNITION = "alchemizer:ignition_chamber";
  private static final String PRESSURE = "alchemizer:pressure_chamber";
  private static final String TANK = "alchemizer:alchemical_tank";
  private static final String VALVE = "alchemizer:air_pressure_valve";
  private static final String BELLOWS = "alchemizer:bellows";
  private static final String FUNNEL = "alchemizer:funnel";
  private static final String FLUE = "alchemizer:flue_stack";
  private static final String CRYSTAL = "alchemizer:resonance_crystal";
  private static final String CONDENSER = "alchemizer:condenser";
  private static final String ADVANCED_CONDENSER = "alchemizer:advanced_condenser";
  private static final String TUBING = "alchemizer:alchemical_tubing";

  private static final Tuning TUNING = TuningDefaults.defaultTuning();

  private static final MachineBlocks BLOCKS =
      MachineBlocks.of(
          Map.ofEntries(
              Map.entry(Part.IGNITION, Set.of(IGNITION)),
              Map.entry(Part.PRESSURE, Set.of(PRESSURE)),
              Map.entry(Part.TANK, Set.of(TANK)),
              Map.entry(Part.VALVE, Set.of(VALVE)),
              Map.entry(Part.BELLOWS, Set.of(BELLOWS)),
              Map.entry(Part.FUNNEL, Set.of(FUNNEL)),
              Map.entry(Part.FLUE, Set.of(FLUE)),
              Map.entry(Part.CRYSTAL, Set.of(CRYSTAL)),
              Map.entry(Part.CONDENSER, Set.of(CONDENSER)),
              Map.entry(Part.ADVANCED_CONDENSER, Set.of(ADVANCED_CONDENSER)),
              Map.entry(Part.TUBING, Set.of(TUBING))));

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

  /** A still with a tank on the chamber: the whole of tier one. */
  private static Map<RelativePos, String> tierOne() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);
    return cells;
  }

  /** A still built all the way: every tier's parts, placed the way the design asks. */
  private static Map<RelativePos, String> tierFour() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 0, 0), VALVE);
    cells.put(new RelativePos(1, 0, 1), BELLOWS);
    cells.put(new RelativePos(0, 2, 0), FUNNEL);
    cells.put(new RelativePos(1, 1, 0), FLUE);
    cells.put(new RelativePos(-1, 0, 0), CRYSTAL);
    return cells;
  }

  private static ValidationResult validate(Map<RelativePos, String> cells) {
    return StillValidator.validate(view(cells), BLOCKS, TUNING);
  }

  private static ValidationResult validate(Map<RelativePos, String> cells, Tuning tuning) {
    return StillValidator.validate(view(cells), BLOCKS, tuning);
  }

  // --- tier one ---------------------------------------------------------------------------------

  @Test
  void oneOfEachPartWithATankIsATierOneStill() {
    ValidationResult result = validate(tierOne());

    assertTrue(result.valid());
    assertTrue(result.problems().isEmpty());
    assertEquals(1, result.tier().tier());
    assertEquals(1, result.tier().counts().tanks());
    assertEquals(1, result.tier().counts().ignition());
    assertEquals(1, result.tier().counts().pressure());
  }

  @Test
  void tanksReachThroughOtherTanks() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);
    cells.put(new RelativePos(0, 1, 2), TANK);
    cells.put(new RelativePos(0, 2, 2), TANK);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(3, result.tier().counts().tanks());
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
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 1, 0), PRESSURE);

    ValidationResult result = validate(cells);

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.MULTIPLE_PRESSURE), result.problems());
  }

  @Test
  void twoIgnitionChambersAreRefused() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 0, 0), IGNITION);

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
    // still", not an exception, and not a claim about a block that was never seen.
    StructureView blind = pos -> Optional.empty();

    ValidationResult result = StillValidator.validate(blind, BLOCKS, TUNING);

    assertFalse(result.valid());
    assertEquals(StillTier.NONE, result.tier());
  }

  @Test
  void aStillSpanningAnUnloadedBorderIsIncompleteNotBroken() {
    Map<RelativePos, String> cells = tierOne();
    // The tank is there, but the chunk it is in is not loaded, so the view cannot read it back.
    StructureView partial =
        pos -> pos.equals(new RelativePos(0, 1, 1)) ? Optional.empty() : view(cells).blockIdAt(pos);

    ValidationResult result = StillValidator.validate(partial, BLOCKS, TUNING);

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
    ValidationResult result =
        StillValidator.validate(view(tierOne()), MachineBlocks.empty(), TUNING);

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.NO_PARTS_DEFINED), result.problems());
  }

  @Test
  void unrelatedBlocksNearbyAreIgnored() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(2, 0, 2), "minecraft:stone");
    cells.put(new RelativePos(2, 1, 2), "minecraft:water");

    assertTrue(validate(cells).valid());
  }

  @Test
  void theScanVolumeIsKnown() {
    assertEquals(9 * 9 * 9, StillValidator.scanVolume(4));
  }

  // --- tier two: valves and bellows -------------------------------------------------------------

  @Test
  void aValveCarryingABellowsIsTierTwo() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 0, 0), VALVE);
    cells.put(new RelativePos(1, 0, 1), BELLOWS);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(2, result.tier().tier());
    assertEquals(1, result.tier().counts().valves());
    assertEquals(1, result.tier().counts().bellowsValves());
  }

  @Test
  void aValveWithNoBellowsLeavesTheStillAtTierOne() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 0, 0), VALVE);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(1, result.tier().tier(), "a valve on its own does not shorten a brew");
  }

  @Test
  void twoBellowsOnOneValveAreStillWorthOne() {
    // The design counts valves with a bellows, not bellows: "v = valves with bellows".
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 0, 0), VALVE);
    cells.put(new RelativePos(1, 0, 1), BELLOWS);
    cells.put(new RelativePos(1, 0, -1), BELLOWS);

    assertTrue(validate(cells).valid());
    assertEquals(1, validate(cells).tier().counts().bellowsValves());
  }

  @Test
  void aValveOnTopOfTheFireIsNotAttached() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(0, -1, 0), VALVE);
    cells.put(new RelativePos(0, -1, 1), BELLOWS);

    ValidationResult result = validate(cells);

    assertEquals(1, result.tier().tier(), "valves never attach to the top or bottom of the fire");
    assertEquals(0, result.tier().counts().valves());
  }

  @Test
  void aValveAttachedToAnotherValveCounts() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 0, 0), VALVE);
    cells.put(new RelativePos(2, 0, 0), VALVE);
    cells.put(new RelativePos(2, 0, 1), BELLOWS);

    ValidationResult result = validate(cells);

    assertEquals(2, result.tier().tier());
    assertEquals(2, result.tier().counts().valves());
    assertEquals(1, result.tier().counts().bellowsValves());
  }

  @Test
  void aBellowsOnTheTopOfAValveDoesNotCount() {
    // The top face is where a flue stack goes, and the four that take bellows are the sides.
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 0, 0), VALVE);
    cells.put(new RelativePos(1, 1, 0), BELLOWS);

    assertEquals(1, validate(cells).tier().tier());
  }

  @Test
  void aStrayValveDoesNotBreakAWorkingStill() {
    // A part that does not satisfy its placement rule is not counted, and is not a fault either:
    // a player who leaves a valve on the ground beside the machine still has a machine.
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(3, 0, 3), VALVE);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(1, result.tier().tier());
    assertEquals(0, result.tier().counts().valves());
  }

  // --- tier three: the funnel and the flue stack ------------------------------------------------

  @Test
  void aFunnelOnTheChamberAndAFlueOnAValveAreTierThree() {
    ValidationResult result = validate(tierFour());

    assertTrue(result.valid());
    assertEquals(4, result.tier().tier(), "the crystal takes it one further");
    assertEquals(1, result.tier().counts().funnels());
    assertEquals(1, result.tier().counts().flues());
  }

  @Test
  void aFunnelAndAFlueWithoutBellowsStayAtTierOne() {
    // The tiers are cumulative: a machine that has grown a funnel but never had a bellows has not
    // skipped a tier, it has not reached one.
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(0, 2, 0), FUNNEL);
    cells.put(new RelativePos(1, 0, 0), VALVE);
    cells.put(new RelativePos(1, 1, 0), FLUE);

    assertEquals(1, validate(cells).tier().tier());
  }

  @Test
  void aFunnelThatIsNotOnTheChamberIsNotCounted() {
    Map<RelativePos, String> cells = tierFour();
    cells.remove(new RelativePos(0, 2, 0));
    cells.put(new RelativePos(0, 3, 0), FUNNEL);

    ValidationResult result = validate(cells);

    assertEquals(2, result.tier().tier(), "tier three wants the funnel on the chamber");
    assertEquals(0, result.tier().counts().funnels());
  }

  @Test
  void aFlueStackCountsEveryBlockItIsTall() {
    Map<RelativePos, String> cells = tierFour();
    cells.put(new RelativePos(1, 2, 0), FLUE);

    assertEquals(2, validate(cells).tier().counts().flues());
  }

  @Test
  void aFlueOnSomethingOtherThanAValveIsNotCounted() {
    Map<RelativePos, String> cells = tierFour();
    cells.remove(new RelativePos(1, 1, 0));
    cells.put(new RelativePos(-1, 1, 0), FLUE);

    ValidationResult result = validate(cells);

    assertEquals(2, result.tier().tier(), "tier three wants the flue on a valve");
    assertEquals(0, result.tier().counts().flues());
  }

  // --- tier four: the resonance crystal ---------------------------------------------------------

  @Test
  void oneCrystalTouchingTheMachineIsTierFour() {
    ValidationResult result = validate(tierFour());

    assertTrue(result.valid());
    assertEquals(4, result.tier().tier());
    assertEquals(1, result.tier().counts().crystals());
  }

  @Test
  void twoCrystalsAreRefused() {
    Map<RelativePos, String> cells = tierFour();
    cells.put(new RelativePos(0, 1, -1), CRYSTAL);

    ValidationResult result = validate(cells);

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.MULTIPLE_CRYSTAL), result.problems());
  }

  @Test
  void aCrystalThatTouchesNothingIsNotCounted() {
    Map<RelativePos, String> cells = tierFour();
    cells.remove(new RelativePos(-1, 0, 0));
    cells.put(new RelativePos(-1, 3, 0), CRYSTAL);

    ValidationResult result = validate(cells);

    assertEquals(3, result.tier().tier());
    assertEquals(0, result.tier().counts().crystals());
  }

  // --- the tier cap -----------------------------------------------------------------------------

  @Test
  void aMachineBuiltPastTheCapReadsAsTheCappedTier() {
    // Not "validates as four and refuses tier three recipes": one number, read by the status line,
    // the fuel gate and the brew-time term alike.
    ValidationResult result = validate(tierFour(), TUNING.withMachineTierCap(2));

    assertTrue(result.valid());
    assertEquals(2, result.tier().tier());
    assertEquals(
        1,
        result.tier().counts().crystals(),
        "the parts are still counted; only the tier is capped");
  }

  @Test
  void aCapOfOneLeavesTierOneMachinesAlone() {
    ValidationResult result = validate(tierOne(), TUNING.withMachineTierCap(1));

    assertTrue(result.valid());
    assertEquals(1, result.tier().tier());
  }

  // --- tubing -----------------------------------------------------------------------------------

  @Test
  void aTankBehindTubingIsConnected() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TUBING);
    cells.put(new RelativePos(0, 1, 2), TUBING);
    cells.put(new RelativePos(0, 1, 3), TANK);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(1, result.tier().counts().tanks());
  }

  @Test
  void aRunLongerThanTheLimitIsRefusedByItsOwnName() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TUBING);
    cells.put(new RelativePos(0, 1, 2), TUBING);
    cells.put(new RelativePos(0, 1, 3), TUBING);
    cells.put(new RelativePos(0, 1, 4), TANK);

    ValidationResult result = validate(cells, TUNING.withMaxTubingLength(2));

    assertFalse(result.valid());
    assertEquals(List.of(StillProblem.TUBING_TOO_LONG), result.problems());
  }

  @Test
  void aRunExactlyAtTheLimitIsAccepted() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TUBING);
    cells.put(new RelativePos(0, 1, 2), TUBING);
    cells.put(new RelativePos(0, 1, 3), TANK);

    ValidationResult result = validate(cells, TUNING.withMaxTubingLength(2));

    assertTrue(result.valid());
    assertEquals(1, result.tier().counts().tanks());
  }

  @Test
  void aFarTankIsIgnoredWhenOneIsWithinReach() {
    // The same principle as the stray valve: a tank the machine cannot reach is not a fault, it is
    // just not part of the machine. Only a machine with no tank at all has to explain itself.
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);
    cells.put(new RelativePos(0, 0, 1), TUBING);
    cells.put(new RelativePos(0, 0, 2), TUBING);
    cells.put(new RelativePos(0, 0, 3), TUBING);
    cells.put(new RelativePos(0, 0, 4), TANK);

    ValidationResult result = validate(cells, TUNING.withMaxTubingLength(2));

    assertTrue(result.valid());
    assertEquals(1, result.tier().counts().tanks());
  }

  @Test
  void aTankReachedBothWaysIsReachedTheShortWay() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TANK);
    cells.put(new RelativePos(0, 1, 2), TUBING);
    cells.put(new RelativePos(0, 1, 3), TANK);

    ValidationResult result = validate(cells, TUNING.withMaxTubingLength(1));

    assertTrue(result.valid());
    assertEquals(
        2, result.tier().counts().tanks(), "the second tank is one block behind the first");
  }

  @Test
  void tubingOnItsOwnIsNotATank() {
    Map<RelativePos, String> cells = still();
    cells.put(new RelativePos(0, 1, 1), TUBING);

    assertEquals(List.of(StillProblem.NO_TANK), validate(cells).problems());
  }

  // --- condensers and batch size
  // ------------------------------------------------------------------

  @Test
  void noCondensersMeansOnePotionAtATime() {
    assertEquals(1, validate(tierOne()).tier().maxBatch());
  }

  @Test
  void eachCondenserDoublesAndEachAdvancedOneQuadruples() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 1, 0), CONDENSER);
    cells.put(new RelativePos(-1, 1, 0), CONDENSER);
    cells.put(new RelativePos(0, 1, -1), ADVANCED_CONDENSER);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(2, result.tier().counts().condensers());
    assertEquals(1, result.tier().counts().advancedCondensers());
    assertEquals(8, result.tier().maxBatch(), "two condensers and an advanced one add to eight");
  }

  @Test
  void theDesignsOwnArithmeticHolds() {
    // "3 basic + 1 advanced = 10x" — they add, they do not compound.
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 1, 0), CONDENSER);
    cells.put(new RelativePos(-1, 1, 0), CONDENSER);
    cells.put(new RelativePos(0, 1, -1), CONDENSER);
    cells.put(new RelativePos(0, 2, 0), ADVANCED_CONDENSER);

    assertEquals(10, validate(cells).tier().maxBatch());
  }

  @Test
  void aCondenserHangingOffAnotherCondenserIsNotConnected() {
    // §3.1: "Condensers and tanks must touch the Pressure Chamber or tubing connected to it." A
    // bank of them stacked on one another is not a bank the machine can reach.
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 1, 0), CONDENSER);
    cells.put(new RelativePos(2, 1, 0), CONDENSER);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(1, result.tier().counts().condensers());
    assertEquals(2, result.tier().maxBatch());
  }

  @Test
  void anAttachedCondenserIsWorthItsFullValueOnItsOwn() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(0, 1, 2), CONDENSER);

    assertEquals(2, validate(cells).tier().maxBatch(), "the first condenser is not a half share");
  }

  @Test
  void aCondenserBehindTubingStillCounts() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(0, 1, 2), TUBING);
    cells.put(new RelativePos(0, 1, 3), ADVANCED_CONDENSER);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(4, result.tier().maxBatch());
  }

  @Test
  void aCondenserOutOfReachIsNotCountedAndIsNotAFault() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(3, 0, 3), CONDENSER);

    ValidationResult result = validate(cells);

    assertTrue(result.valid());
    assertEquals(1, result.tier().maxBatch());
  }

  @Test
  void condensersDoNotChangeTheTier() {
    // "Optional blocks (no tier effect)". A machine with ten condensers is still a tier 1 still.
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 1, 0), CONDENSER);
    cells.put(new RelativePos(-1, 1, 0), ADVANCED_CONDENSER);

    assertEquals(1, validate(cells).tier().tier());
  }

  // --- the verdict itself ---------------------------------------------------------------------

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
    ValidationResult result = validate(tierOne());

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

    StillValidator.Inspection inspection = StillValidator.inspect(view(cells), BLOCKS, TUNING);

    assertTrue(inspection.result().valid());
    assertEquals(
        Set.of(new RelativePos(0, 1, 1), new RelativePos(0, 1, 2)),
        inspection.tanks(),
        "the tanks behind the first one are part of the same cluster");
  }

  @Test
  void anUnformedStillOffersNoTanksToDrawFrom() {
    Map<RelativePos, String> cells = tierOne();
    cells.put(new RelativePos(1, 1, 0), PRESSURE);

    StillValidator.Inspection inspection = StillValidator.inspect(view(cells), BLOCKS, TUNING);

    assertFalse(inspection.result().valid());
    assertTrue(inspection.tanks().isEmpty(), "an unformed machine must not be able to draw water");
  }
}
