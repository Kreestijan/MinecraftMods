package io.github.kreestijan.alchemizer.core.fuel;

import java.util.Optional;

/**
 * What the ignition chamber has alight, and for how much longer.
 *
 * <p>A cold chamber carries no tier at all, so the tier is stored as an {@code int} with {@link
 * #NO_FUEL_TIER} for "nothing burning" rather than as a nullable field. That keeps the record
 * comparable, serialisable and free of null checks at every call site, while {@link #fuel()} still
 * hands back an {@link Optional} for the places that want one.
 *
 * @param remainingTicks how much longer the current fuel burns; zero means cold
 * @param fuelTier the tier of the fuel alight, or {@link #NO_FUEL_TIER} when cold
 */
public record BurnState(int remainingTicks, int fuelTier) {

  /** The tier value meaning "nothing is burning". No real tier is negative. */
  public static final int NO_FUEL_TIER = -1;

  public BurnState {
    if (remainingTicks < 0) {
      throw new IllegalArgumentException(
          "remainingTicks must not be negative, got " + remainingTicks);
    }
    if (fuelTier < NO_FUEL_TIER) {
      throw new IllegalArgumentException(
          "fuelTier must be >= " + NO_FUEL_TIER + ", got " + fuelTier);
    }
    if (remainingTicks == 0 != (fuelTier == NO_FUEL_TIER)) {
      throw new IllegalArgumentException(
          "remainingTicks and fuelTier disagree: " + remainingTicks + " ticks at tier " + fuelTier);
    }
  }

  /**
   * @return a chamber with nothing burning
   */
  public static BurnState cold() {
    return new BurnState(0, NO_FUEL_TIER);
  }

  /**
   * @param burnTicks how long the fuel will burn for
   * @param fuel the fuel that has just been lit
   * @return a chamber burning that fuel
   */
  public static BurnState burning(int burnTicks, FuelTier fuel) {
    if (fuel == null) {
      throw new IllegalArgumentException("fuel must not be null; use cold() for an empty chamber");
    }
    if (burnTicks <= 0) {
      throw new IllegalArgumentException("burnTicks must be positive, got " + burnTicks);
    }
    return new BurnState(burnTicks, fuel.tier());
  }

  /**
   * @return whether anything is currently alight
   */
  public boolean isLit() {
    return remainingTicks > 0;
  }

  /**
   * @return the fuel alight, or empty when the chamber is cold
   */
  public Optional<FuelTier> fuel() {
    return FuelTier.ofTier(fuelTier);
  }
}
