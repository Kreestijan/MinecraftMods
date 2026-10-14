package io.github.kreestijan.alchemizer.core.fuel;

import java.util.Objects;

/**
 * The two ways a burn state changes: it burns down, or more fuel goes in.
 *
 * <p>Both are pure functions on {@link BurnState}, so the awkward cases — running out mid-brew,
 * topping up with a weaker fuel, topping up with a stronger one — are decided by unit tests rather
 * than by whatever the block entity's tick method happens to do.
 */
public final class BurnTracker {

  private BurnTracker() {}

  /**
   * Burns one tick's worth of fuel.
   *
   * @param state the current burn state
   * @return the state one tick later; a cold chamber stays cold
   */
  public static BurnState tick(BurnState state) {
    Objects.requireNonNull(state, "state");
    if (!state.isLit()) {
      return state;
    }
    int remaining = state.remainingTicks() - 1;
    if (remaining <= 0) {
      // The last tick takes the tier with it. Leaving a stale tier behind would let a cold chamber
      // claim it could still brew at coal tier.
      return BurnState.cold();
    }
    return new BurnState(remaining, state.fuelTier());
  }

  /**
   * Puts fresh fuel in the chamber.
   *
   * <p>Two rules, neither of which {@code DESIGN.md} settles, chosen to be kind to the player
   * without being exploitable:
   *
   * <ul>
   *   <li><strong>Burn time adds.</strong> Fuel put into a lit chamber extends the fire rather than
   *       replacing it, so a player is never punished for topping up early.
   *   <li><strong>The better fuel sets the tier.</strong> A brew that is already running at coal
   *       tier keeps that tier if wood is shovelled in on top, and is upgraded if a blaze rod
   *       follows. Taking the newest fuel's tier instead would let a single stick silently kill a
   *       running brew, and taking the oldest would make upgrades impossible.
   * </ul>
   *
   * @param state the current burn state
   * @param burnTicks how long the new fuel burns for
   * @param fuel the fuel being added
   * @return the state with the new fuel accounted for
   */
  public static BurnState refuel(BurnState state, int burnTicks, FuelTier fuel) {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(fuel, "fuel");
    if (burnTicks <= 0) {
      throw new IllegalArgumentException("burnTicks must be positive, got " + burnTicks);
    }

    long extended = (long) state.remainingTicks() + burnTicks;
    int capped = (int) Math.min(extended, Integer.MAX_VALUE);
    int tier = Math.max(state.fuelTier(), fuel.tier());
    return new BurnState(capped, tier);
  }
}
