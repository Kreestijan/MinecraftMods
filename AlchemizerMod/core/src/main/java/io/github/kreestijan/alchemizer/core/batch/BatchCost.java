package io.github.kreestijan.alchemizer.core.batch;

import io.github.kreestijan.alchemizer.core.tank.TankMath;

/**
 * What one brew run consumes.
 *
 * <p>At baseline the multiplier is always one, because condensers arrive with a later tier. It is a
 * field rather than an assumption so that the cost of a batch is asked for in one place from the
 * start — the alternative is a scattering of {@code × 1}s to hunt down when batch brewing lands.
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
   * @return the cost of that run
   */
  public static BatchCost of(int multiplier) {
    int batches = Math.max(1, multiplier);
    return new BatchCost(batches, batches, TankMath.cost(batches));
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
