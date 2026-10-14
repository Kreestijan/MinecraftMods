package io.github.kreestijan.alchemizer.core.fuel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Burning down, and topping up.
 *
 * <p>The refuel rules are not settled by the design document. What is asserted here is the choice
 * actually made — burn time adds, and the better fuel keeps the tier — so that changing it later is
 * a deliberate edit to a test rather than a surprise.
 */
class BurnTrackerTest {

  @Test
  void aColdChamberStaysCold() {
    BurnState cold = BurnState.cold();

    assertFalse(cold.isLit());
    assertTrue(cold.fuel().isEmpty());
    assertEquals(cold, BurnTracker.tick(cold));
  }

  @Test
  void aBurningChamberCountsDown() {
    BurnState burning = BurnTracker.tick(BurnState.burning(2, FuelTier.COAL));

    assertTrue(burning.isLit());
    assertEquals(1, burning.remainingTicks());
    assertEquals(FuelTier.COAL, burning.fuel().orElseThrow());
  }

  @Test
  void theLastTickExtinguishesTheChamberAndItsTier() {
    BurnState spent = BurnTracker.tick(BurnState.burning(1, FuelTier.BLAZE_ROD));

    assertEquals(BurnState.cold(), spent);
    assertFalse(spent.isLit());
    assertTrue(
        spent.fuel().isEmpty(), "a cold chamber must not still claim to be burning a blaze rod");
  }

  @Test
  void aBurnSurvivesALongRunOfTicksAndEndsExactlyWhenExpected() {
    BurnState state = BurnState.burning(10, FuelTier.COAL);
    for (int i = 0; i < 9; i++) {
      state = BurnTracker.tick(state);
      assertTrue(state.isLit(), "should still be lit after " + (i + 1) + " ticks");
    }

    assertEquals(BurnState.cold(), BurnTracker.tick(state));
  }

  @Test
  void refuellingAColdChamberLightsIt() {
    BurnState lit = BurnTracker.refuel(BurnState.cold(), 1600, FuelTier.COAL);

    assertTrue(lit.isLit());
    assertEquals(1600, lit.remainingTicks());
    assertEquals(FuelTier.COAL, lit.fuel().orElseThrow());
  }

  @Test
  void refuellingALitChamberExtendsTheBurnRatherThanReplacingIt() {
    BurnState lit = BurnTracker.refuel(BurnState.burning(100, FuelTier.COAL), 1600, FuelTier.COAL);

    assertEquals(1700, lit.remainingTicks());
  }

  @Test
  void weakerFuelToppedUpOnTopDoesNotDowngradeTheBurn() {
    BurnState lit = BurnTracker.refuel(BurnState.burning(100, FuelTier.COAL), 300, FuelTier.WOOD);

    assertEquals(400, lit.remainingTicks());
    assertEquals(
        FuelTier.COAL,
        lit.fuel().orElseThrow(),
        "adding wood must not cancel a brew already running on coal");
  }

  @Test
  void betterFuelToppedUpOnTopUpgradesTheBurn() {
    BurnState lit =
        BurnTracker.refuel(BurnState.burning(100, FuelTier.COAL), 2400, FuelTier.BLAZE_ROD);

    assertEquals(FuelTier.BLAZE_ROD, lit.fuel().orElseThrow());
  }

  @Test
  void aBurnStateCannotContradictItself() {
    assertThrows(IllegalArgumentException.class, () -> new BurnState(5, BurnState.NO_FUEL_TIER));
    assertThrows(IllegalArgumentException.class, () -> new BurnState(0, FuelTier.COAL.tier()));
    assertThrows(IllegalArgumentException.class, () -> new BurnState(-1, 1));
    assertThrows(IllegalArgumentException.class, () -> BurnState.burning(0, FuelTier.COAL));
    assertThrows(IllegalArgumentException.class, () -> BurnState.burning(100, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> BurnTracker.refuel(BurnState.cold(), 0, FuelTier.COAL));
  }
}
