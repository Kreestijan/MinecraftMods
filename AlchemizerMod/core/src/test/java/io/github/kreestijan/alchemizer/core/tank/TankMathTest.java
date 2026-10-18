package io.github.kreestijan.alchemizer.core.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kreestijan.alchemizer.core.batch.BatchCost;
import org.junit.jupiter.api.Test;

/**
 * Water, bottles and the arithmetic between them.
 *
 * <p>The number worth pinning is the one the design document states outright: a sixteen bucket tank
 * brews one hundred and sixty potions.
 */
class TankMathTest {

  private static final int CAPACITY = TankMath.BASIC_TANK_CAPACITY_MB;

  @Test
  void theConstantsMatchTheDesign() {
    assertEquals(1000, TankMath.BUCKET_MB);
    assertEquals(100, TankMath.DOSE_MB);
    assertEquals(16_000, TankMath.BASIC_TANK_CAPACITY_MB);
  }

  @Test
  void aSixteenBucketTankBrewsOneHundredAndSixtyPotions() {
    assertEquals(160, TankMath.dosesIn(TankMath.capacityOf(1)));
  }

  @Test
  void capacitiesSumAndNonsenseCountsAsNone() {
    assertEquals(0, TankMath.capacityOf(0));
    assertEquals(0, TankMath.capacityOf(-4));
    assertEquals(CAPACITY, TankMath.capacityOf(1));
    assertEquals(4 * CAPACITY, TankMath.capacityOf(4));
  }

  @Test
  void aBatchCostsOneDosePerPotion() {
    assertEquals(100, TankMath.cost(1));
    assertEquals(1600, TankMath.cost(16));
    assertEquals(0, TankMath.cost(0));
    assertEquals(0, TankMath.cost(-1));
  }

  @Test
  void aPartlyUsedDoseIsNotAPotion() {
    assertEquals(0, TankMath.dosesIn(99));
    assertEquals(1, TankMath.dosesIn(100));
    assertEquals(1, TankMath.dosesIn(199));
    assertEquals(0, TankMath.dosesIn(0));
  }

  @Test
  void waterIsShownInBuckets() {
    assertEquals("1.00 B", TankMath.describe(1000));
    assertEquals("0.10 B", TankMath.describe(100));
  }

  @Test
  void aNewTankIsEmptyAndFullOfRoom() {
    TankLevel tank = TankLevel.empty(CAPACITY);

    assertTrue(tank.isEmpty());
    assertFalse(tank.isFull());
    assertEquals(CAPACITY, tank.room());
    assertEquals(0.0, tank.fraction());
    assertEquals(0, tank.doses());
  }

  @Test
  void fillingAndDrainingAreImmutable() {
    TankLevel empty = TankLevel.empty(CAPACITY);
    TankLevel filled = empty.fill(250);

    assertEquals(0, empty.storedMb(), "filling a tank must not change the original");
    assertEquals(250, filled.storedMb());
    assertEquals(0, filled.withdraw(250).storedMb());
    assertEquals(250, filled.storedMb(), "draining a tank must not change the original");
  }

  @Test
  void overfillingIsClampedRatherThanRefused() {
    TankLevel tank = TankLevel.empty(CAPACITY).fill(CAPACITY + 5000);

    assertTrue(tank.isFull());
    assertEquals(CAPACITY, tank.storedMb());
    assertEquals(0, tank.room());
    assertEquals(1.0, tank.fraction());
  }

  @Test
  void drainingMoreThanIsThereLeavesAnEmptyTank() {
    TankLevel tank = TankLevel.empty(CAPACITY).fill(300).withdraw(9000);

    assertTrue(tank.isEmpty());
    assertEquals(0, tank.storedMb());
  }

  @Test
  void negativeAmountsChangeNothing() {
    TankLevel tank = TankLevel.empty(CAPACITY).fill(500);

    assertEquals(tank, tank.fill(-100));
    assertEquals(tank, tank.withdraw(-100));
    assertEquals(tank, tank.withdraw(0));
  }

  @Test
  void askingWhetherThereIsEnoughWaterIsAnsweredHonestly() {
    TankLevel tank = TankLevel.empty(CAPACITY).fill(250);

    assertTrue(tank.canWithdraw(200));
    assertTrue(tank.canWithdraw(250));
    assertFalse(tank.canWithdraw(251));
    assertFalse(tank.canWithdraw(-1));
  }

  @Test
  void aTankReportsHowManyPotionsItCouldStillBrew() {
    assertEquals(2, TankLevel.empty(CAPACITY).fill(250).doses());
  }

  @Test
  void impossibleTanksAreRefused() {
    assertThrows(IllegalArgumentException.class, () -> TankLevel.empty(0));
    assertThrows(IllegalArgumentException.class, () -> TankLevel.empty(-1));
    assertThrows(IllegalArgumentException.class, () -> new TankLevel(-1, CAPACITY));
    assertThrows(IllegalArgumentException.class, () -> new TankLevel(CAPACITY + 1, CAPACITY));
  }

  @Test
  void aBatchCostsOneIngredientAndOneBottlePerPotion() {
    BatchCost single = BatchCost.single();

    assertEquals(1, single.ingredients());
    assertEquals(1, single.bottles());
    assertEquals(100, single.waterMb());
    assertEquals(1, single.multiplier());
  }

  @Test
  void aLargerBatchCostsProportionallyMore() {
    BatchCost triple = BatchCost.of(3);

    assertEquals(3, triple.ingredients());
    assertEquals(3, triple.bottles());
    assertEquals(300, triple.waterMb());
    assertEquals(3, triple.multiplier());
  }

  @Test
  void aBatchOfZeroOrLessIsStillOnePotion() {
    assertEquals(BatchCost.single(), BatchCost.of(0));
    assertEquals(BatchCost.single(), BatchCost.of(-2));
  }

  @Test
  void aBatchAtAConfiguredDoseSpendsThatDosePerPotion() {
    BatchCost triple = BatchCost.of(3, 250);

    assertEquals(3, triple.ingredients());
    assertEquals(3, triple.bottles());
    assertEquals(750, triple.waterMb());
  }

  @Test
  void aBatchAtADoseOfNothingStillCostsItsIngredients() {
    // A dose of zero is a legal setting — it makes the still free to run — and it must not turn the
    // ingredients and the bottles off with it. The three costs are one decision, so this is the
    // case that would go wrong if they were ever computed apart.
    BatchCost free = BatchCost.of(4, 0);

    assertEquals(4, free.ingredients());
    assertEquals(4, free.bottles());
    assertEquals(0, free.waterMb());
  }
}
