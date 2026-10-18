package io.github.kreestijan.alchemizer.core.pathing;

import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * How often each cell has been walked over, and which cells have been walked over enough.
 *
 * <p>{@code DESIGN.md} §5.2: repeated walking turns grass and dirt into path. This is the counting
 * half of that rule and nothing else — it is told that a cell was walked on, it adds one to that
 * cell's tally, and it answers whether the tally has just reached the point where the cell should
 * become a path. What a path <em>is</em> — which blocks may turn into which, and whether the cell
 * above is free — is a question about block ids, which {@code core} cannot see, so the caller asks
 * this class <em>whether</em> and decides <em>what</em>.
 *
 * <p><b>Per cell, and per shop.</b> One of these belongs to one Counter, because "repeated" is
 * relative to a shop: the line customers stand in is the shop's, and a well-worn cell is worn by
 * the people who came to that counter. Counts are kept per cell rather than per shop, so one busy
 * corner of the queue becomes a path while the rest of the street stays grass, which is what a real
 * footpath looks like.
 *
 * <p><b>A cell converts once.</b> The answer is true on the pass that reaches the threshold and
 * false on every pass after it, so a caller that converts on a true answer does not have to
 * remember which cells it has already converted — and a cell whose conversion the caller refused
 * (because something is standing on it) is not converted on the next pass either, rather than being
 * retried twenty times a minute.
 *
 * <p><b>In memory, and deliberately not saved.</b> A shop's wear is thrown away with whatever holds
 * this object. Persisting it would mean a per-cell counter written into the world's saved data for
 * a threshold that is a handful of passes, which is a great deal of stored state to keep for a
 * number that a player would not notice being reset.
 */
public final class PathWear {

  /** The lowest threshold there can be: a cell that becomes a path on its first footstep. */
  public static final int MIN_THRESHOLD = 1;

  /** How many times each cell has been walked over. Bounded by the threshold, so it cannot grow. */
  private final Map<RelativePos, Integer> passes = new HashMap<>();

  private final int threshold;
  private final boolean enabled;

  /**
   * @param threshold how many passes wear a cell into a path; at least {@link #MIN_THRESHOLD}
   * @param enabled whether walking wears anything at all, which is the mod's config switch
   * @throws IllegalArgumentException when the threshold is below {@link #MIN_THRESHOLD}
   */
  public PathWear(int threshold, boolean enabled) {
    if (threshold < MIN_THRESHOLD) {
      throw new IllegalArgumentException(
          "threshold must be at least " + MIN_THRESHOLD + ", was " + threshold);
    }
    this.threshold = threshold;
    this.enabled = enabled;
  }

  /**
   * @param pos the cell that was walked over
   * @return how many times it has been walked over, or empty when it never has been
   */
  public OptionalInt passesAt(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    Integer count = passes.get(pos);
    if (count == null) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(count);
  }

  /**
   * Records one pass over a cell.
   *
   * <p>Counted up to the threshold and no further. Past that point the cell has had its answer —
   * whether or not the caller acted on it — and a count that went on rising would be a number
   * nothing reads, growing for as long as customers keep queueing, in a map kept in memory for the
   * life of a block entity.
   *
   * @param pos the cell that was walked over
   * @return whether this pass is the one that reached the threshold, which is the caller's cue to
   *     turn the cell into a path
   */
  public boolean recordPass(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    if (!enabled) {
      // Counted not at all rather than counted and ignored: a switch that is off should cost
      // nothing, and a count collected while it is off would convert a street the moment it was
      // turned on.
      return false;
    }

    int walked = passes.getOrDefault(pos, 0);
    if (walked >= threshold) {
      return false;
    }
    int now = walked + 1;
    passes.put(pos, now);
    return now == threshold;
  }
}
