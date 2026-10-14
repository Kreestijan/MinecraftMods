package io.github.kreestijan.alchemizer.core.machine;

import java.util.Objects;
import java.util.Optional;

/**
 * What was actually assembled, and what it is worth.
 *
 * <p>{@link #NONE} is a real value rather than a null: a pile of parts that does not form a still
 * still has counts, and a validator that has to hand back "no tier" should hand back a thing, not
 * an absence. Callers ask {@link #isFormed()} instead of null-checking.
 *
 * <p>The counts are carried even though only tier 1 is in scope. Tier 2 and above are described by
 * the same counts plus bellows and valves — which is why those two fields exist here already and
 * are always zero at baseline, rather than being bolted on later.
 *
 * @param tier the tier reached; zero for an unformed still
 * @param ignitionCount how many ignition chambers were counted
 * @param pressureCount how many pressure chambers were counted
 * @param tankCount how many tanks are connected to the pressure chamber
 * @param valves how many air pressure valves feed the still
 * @param bellows how many bellows feed the still
 */
public record StillTier(
    int tier, int ignitionCount, int pressureCount, int tankCount, int valves, int bellows) {

  /** An unformed still: the parts are there, the machine is not. */
  public static final StillTier NONE = new StillTier(0, 0, 0, 0, 0, 0);

  public StillTier {
    if (tier < 0
        || ignitionCount < 0
        || pressureCount < 0
        || tankCount < 0
        || valves < 0
        || bellows < 0) {
      throw new IllegalArgumentException("no component of a still tier may be negative");
    }
  }

  /**
   * @return whether this describes a working still
   */
  public boolean isFormed() {
    return tier > 0;
  }

  /**
   * @return the total number of pressure sources feeding the still
   */
  public int pressureSources() {
    return valves + bellows;
  }

  /**
   * Works out the tier from the parts counted.
   *
   * <p>Only tier 1 exists at baseline: exactly one ignition chamber, exactly one pressure chamber
   * sitting on it, and at least one tank attached to that chamber. Anything else has no tier at all
   * — two pressure chambers are not a better still, they are a mistake the player should be told
   * about.
   *
   * @param ignitionCount how many ignition chambers were found
   * @param pressureCount how many pressure chambers were found
   * @param tankCount how many tanks are connected
   * @return the tier those counts describe, or empty if they describe no still
   */
  public static Optional<StillTier> forCounts(int ignitionCount, int pressureCount, int tankCount) {
    if (ignitionCount == 1 && pressureCount == 1 && tankCount >= 1) {
      return Optional.of(new StillTier(1, ignitionCount, pressureCount, tankCount, 0, 0));
    }
    return Optional.empty();
  }

  /**
   * @param valid whether a still was formed
   * @return the tier for a valid still, or {@link #NONE}
   */
  public static StillTier orNone(Optional<StillTier> valid) {
    Objects.requireNonNull(valid, "valid");
    return valid.orElse(NONE);
  }
}
