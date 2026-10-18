package io.github.kreestijan.alchemizer.core.machine;

import java.util.Objects;

/**
 * What tier a machine reached, and the parts it reached it with.
 *
 * <p>{@link #NONE} is a real value rather than a null: a pile of parts that does not form a still
 * still has counts, and a validator that has to hand back "no tier" should hand back a thing, not
 * an absence. Callers ask {@link #isFormed()} instead of null-checking.
 *
 * <p>The counts live in {@link PartCounts} rather than here. The tier ladder and the batch
 * multiplier both read them, and neither belongs on a type whose name says "which tier".
 *
 * @param tier the tier reached; zero for an unformed still
 * @param counts the parts that were counted, whether or not they add up to a still
 */
public record StillTier(int tier, PartCounts counts) {

  /** An unformed still: the parts are there, the machine is not. */
  public static final StillTier NONE = new StillTier(0, PartCounts.NONE);

  public StillTier {
    Objects.requireNonNull(counts, "counts");
    if (tier < 0) {
      throw new IllegalArgumentException("tier must not be negative, got " + tier);
    }
  }

  /**
   * @return whether this describes a working still
   */
  public boolean isFormed() {
    return tier > 0;
  }

  /**
   * @return how many potions one brew run may produce, from the condensers that were counted
   */
  public int maxBatch() {
    return counts.maxBatch();
  }
}
