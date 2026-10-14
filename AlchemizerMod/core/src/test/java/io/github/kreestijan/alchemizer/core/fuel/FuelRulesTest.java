package io.github.kreestijan.alchemizer.core.fuel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Wood lights the chamber and powers nothing.
 *
 * <p>That sentence is the one place the design document looks self-contradictory: wood must "stay
 * lit" and also be "unusable for potions". Both hold, because being lit and having a fuel tier are
 * two different things.
 */
class FuelRulesTest {

  private static final boolean GATING = TuningDefaults.FUEL_TIER_GATING_ENABLED;

  @Test
  void aColdChamberNeverBrews() {
    assertFalse(FuelRules.mayBrew(Optional.empty(), 1, 1, GATING));
    assertFalse(
        FuelRules.mayBrew(Optional.empty(), 4, 1, GATING),
        "an excellent machine with no fire is still cold");
  }

  @Test
  void evenWithGatingOffAColdChamberDoesNotBrew() {
    assertFalse(FuelRules.mayBrew(Optional.empty(), 4, 1, false));
  }

  @Test
  void woodBurnsButPowersNothing() {
    assertTrue(
        BurnState.burning(300, FuelTier.WOOD).isLit(),
        "wood still lights the chamber and shows the lit state");

    assertFalse(
        FuelRules.mayBrew(Optional.of(FuelTier.WOOD), 1, 1, GATING),
        "a tier zero fuel cannot meet a tier one recipe");
  }

  @Test
  void matchingTiersBrew() {
    assertTrue(FuelRules.mayBrew(Optional.of(FuelTier.COAL), 1, 1, GATING));
    assertTrue(FuelRules.mayBrew(Optional.of(FuelTier.BLAZE_ROD), 4, 3, GATING));
  }

  @Test
  void fuelBetterThanTheRecipeStillNeedsAGoodEnoughMachine() {
    assertFalse(
        FuelRules.mayBrew(Optional.of(FuelTier.BLAZE_ROD), 1, 2, GATING),
        "a tier one still cannot run a tier two recipe, however good the fuel");
  }

  @Test
  void goodEnoughFuelInAGoodEnoughMachineStillNeedsTheFuelToQualify() {
    assertFalse(FuelRules.mayBrew(Optional.of(FuelTier.COAL), 3, 2, GATING));
  }

  @Test
  void gatingCanBeDisabledSoAnyFireBrewsAnything() {
    assertTrue(FuelRules.mayBrew(Optional.of(FuelTier.WOOD), 1, 4, false));
    assertTrue(FuelRules.mayBrew(Optional.of(FuelTier.COAL), 1, 4, false));
  }

  @Test
  void theTuningOverloadAgreesWithTheExplicitOne() {
    boolean explicit =
        FuelRules.mayBrew(
            Optional.of(FuelTier.COAL), 1, 1, TuningDefaults.FUEL_TIER_GATING_ENABLED);
    boolean fromTuning =
        FuelRules.mayBrew(Optional.of(FuelTier.COAL), 1, 1, TuningDefaults.defaultTuning());

    assertTrue(explicit);
    assertTrue(fromTuning);
  }

  @Test
  void aMissingFuelOptionalIsARefusedCallNotAnEmptyChamber() {
    assertThrows(NullPointerException.class, () -> FuelRules.mayBrew(null, 1, 1, GATING));
  }

  @Test
  void tiersAreOrderedAsTheDesignDescribes() {
    assertTrue(FuelTier.WOOD.tier() < FuelTier.COAL.tier());
    assertTrue(FuelTier.COAL.tier() < FuelTier.ALCHEMICAL_COAL.tier());
    assertTrue(FuelTier.ALCHEMICAL_COAL.tier() < FuelTier.BLAZE_ROD.tier());
    assertTrue(FuelTier.BLAZE_ROD.tier() < FuelTier.ENHANCED_BLAZE_ROD.tier());
    assertTrue(FuelTier.ofTier(2).isPresent());
    assertTrue(FuelTier.ofTier(9).isEmpty());
    assertTrue(FuelTier.COAL.tagPath().contains("1"));
  }
}
