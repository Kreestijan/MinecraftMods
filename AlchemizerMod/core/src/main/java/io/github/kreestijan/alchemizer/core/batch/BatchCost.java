package io.github.kreestijan.alchemizer.core.batch;

import io.github.kreestijan.alchemizer.core.tank.TankMath;

/**
 * What one brew run consumes.
 *
 * <p>A run makes one or more potions, and everything it spends scales with how many: the same
 * number of each ingredient, that many bottles, and a dose of water each. Asking for the cost in
 * one place is what keeps those three from drifting apart — a machine that spent five of each
 * ingredient and one bottle would be a bug nobody would see until the bottles ran out.
 *
 * @param ingredients how many of each ingredient slot is consumed
 * @param bottles how many glass bottles are consumed
 * @param waterMb how much water is drawn from the tank cluster
 */
public record BatchCost(int ingredients, int bottles, int waterMb) {

  public BatchCost {
    if (ingredients < 0 || bottles < 0 || waterMb < 0) {
      throw new IllegalArgumentException("a batch cost cannot be negative");
    }
  }

  /**
   * @param multiplier how many potions the run produces; below one is treated as one
   * @return the cost of that run, at the shipping dose
   */
  public static BatchCost of(int multiplier) {
    return of(multiplier, TankMath.DOSE_MB);
  }

  /**
   * The same cost at a dose the caller supplies.
   *
   * <p>The dose is configurable, so the machine that actually spends water has to ask for the
   * configured one rather than for the shipping default. Both spellings exist for the reason {@link
   * TankMath#cost(int, int)} gives: the default stays usable where a config is not in reach, and
   * whichever dose is meant is written at the call site instead of assumed.
   *
   * @param multiplier how many potions the run produces; below one is treated as one
   * @param doseMb the water one potion takes; zero or less costs no water, though it still costs
   *     the ingredients and the bottles
   * @return the cost of that run
   */
  public static BatchCost of(int multiplier, int doseMb) {
    int batches = Math.max(1, multiplier);
    return new BatchCost(batches, batches, TankMath.cost(batches, doseMb));
  }

  /**
   * @return the cost of a single potion
   */
  public static BatchCost single() {
    return of(1);
  }

  /**
   * @return how many potions this run produces
   */
  public int multiplier() {
    return bottles;
  }
}
