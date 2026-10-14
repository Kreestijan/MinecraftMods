package io.github.kreestijan.alchemizer.core.timing;

/**
 * A brew in progress: how long it takes and how far along it is.
 *
 * <p>The important behaviour is what happens when the fuel runs out. Progress <em>freezes</em>; it
 * does not reset. The still keeps whatever it had done, and picks up where it left off once the
 * fire is lit again. Losing an hour of brewing to a coal shortage would make the machine feel
 * arbitrary rather than demanding.
 *
 * @param totalTicks the brew time; zero means no brew is running
 * @param elapsedTicks how much of it is done
 */
public record BrewSession(int totalTicks, int elapsedTicks) {

  public BrewSession {
    if (totalTicks < 0) {
      throw new IllegalArgumentException("totalTicks must not be negative, got " + totalTicks);
    }
    if (elapsedTicks < 0) {
      throw new IllegalArgumentException("elapsedTicks must not be negative, got " + elapsedTicks);
    }
    if (elapsedTicks > totalTicks) {
      throw new IllegalArgumentException(
          "elapsedTicks (" + elapsedTicks + ") cannot exceed totalTicks (" + totalTicks + ")");
    }
  }

  /**
   * @return a session with no brew running
   */
  public static BrewSession idle() {
    return new BrewSession(0, 0);
  }

  /**
   * @param totalTicks how long the brew will take
   * @return a fresh session at the start of that brew
   */
  public static BrewSession start(int totalTicks) {
    return new BrewSession(totalTicks, 0);
  }

  /**
   * Advances the brew by one tick, or holds it still.
   *
   * <p>Guarded on {@link #isRunning()} rather than on "not complete", because a still with no brew
   * running at all — a zero-length session — is not something that can be advanced either.
   *
   * @param canProgress whether the still is in a state to keep brewing this tick
   * @return this session advanced, or this session unchanged when it cannot progress
   */
  public BrewSession advance(boolean canProgress) {
    if (!canProgress || !isRunning()) {
      return this;
    }
    return new BrewSession(totalTicks, elapsedTicks + 1);
  }

  /**
   * @return whether the brew has run its full time
   */
  public boolean isComplete() {
    return totalTicks > 0 && elapsedTicks >= totalTicks;
  }

  /**
   * @return whether a brew is under way and unfinished
   */
  public boolean isRunning() {
    return totalTicks > 0 && !isComplete();
  }

  /**
   * @return how much of the brew is done, from zero to one
   */
  public double fraction() {
    if (totalTicks <= 0) {
      return 0.0;
    }
    return Math.min(1.0, elapsedTicks / (double) totalTicks);
  }
}
