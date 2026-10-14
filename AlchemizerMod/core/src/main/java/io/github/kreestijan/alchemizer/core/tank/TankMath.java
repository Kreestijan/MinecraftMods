package io.github.kreestijan.alchemizer.core.tank;

/**
 * The arithmetic of water and bottles, in one place.
 *
 * <p>Millibuckets throughout. Zero represents an empty tank, and is not a valid tank capacity — a
 * tank that holds nothing is a decoration.
 *
 * <p>{@link #BUCKET_MB} is the one number here that must agree with Minecraft rather than with this
 * mod. {@code core} cannot see {@code FluidType.BUCKET_VOLUME}, so the constant is duplicated, and
 * {@code platform-26} carries a test asserting the two are equal. That test is the only thing
 * stopping them drifting apart.
 */
public final class TankMath {

  /** Millibuckets in one bucket. Must equal {@code FluidType.BUCKET_VOLUME}. */
  public static final int BUCKET_MB = 1000;

  /** Millibuckets one potion takes from the tank, at one times batch. */
  public static final int DOSE_MB = 100;

  /** Millibuckets a single alchemical tank holds: sixteen buckets. */
  public static final int BASIC_TANK_CAPACITY_MB = 16 * BUCKET_MB;

  private TankMath() {}

  /**
   * @param tanks how many tanks are connected; negative counts as none
   * @return the combined capacity of that many tanks
   */
  public static int capacityOf(int tanks) {
    if (tanks <= 0) {
      return 0;
    }
    return (int) Math.min((long) tanks * BASIC_TANK_CAPACITY_MB, Integer.MAX_VALUE);
  }

  /**
   * @param bottles how many potions are being brewed; negative counts as none
   * @return the millibuckets that costs
   */
  public static int cost(int bottles) {
    return cost(bottles, DOSE_MB);
  }

  /**
   * The same arithmetic at a dose the caller supplies.
   *
   * <p>The dose is configurable, so the place that actually spends water has to ask for the
   * configured one rather than for the shipping default. Both spellings exist so that the default
   * stays usable where a config is not in reach, and so that whichever dose is meant is written at
   * the call site instead of assumed.
   *
   * @param bottles how many potions are being brewed; negative counts as none
   * @param doseMb the water one potion takes; zero or less counts as none
   * @return the millibuckets that costs
   */
  public static int cost(int bottles, int doseMb) {
    if (bottles <= 0 || doseMb <= 0) {
      return 0;
    }
    return (int) Math.min((long) bottles * doseMb, Integer.MAX_VALUE);
  }

  /**
   * @param milliBuckets an amount of water; negative counts as none
   * @return how many potions that amount will brew, rounded down
   */
  public static int dosesIn(int milliBuckets) {
    return dosesIn(milliBuckets, DOSE_MB);
  }

  /**
   * @param milliBuckets an amount of water; negative counts as none
   * @param doseMb the water one potion takes; zero or less yields none
   * @return how many potions that amount will brew, rounded down
   */
  public static int dosesIn(int milliBuckets, int doseMb) {
    if (milliBuckets <= 0 || doseMb <= 0) {
      return 0;
    }
    return milliBuckets / doseMb;
  }

  /**
   * @param milliBuckets an amount of water
   * @return that amount written the way a player would say it, in buckets, rounded to two decimals
   */
  public static String describe(int milliBuckets) {
    return String.format("%.2f B", milliBuckets / (double) BUCKET_MB);
  }
}
