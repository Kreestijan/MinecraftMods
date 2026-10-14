package io.github.kreestijan.alchemizer.core.fuel;

import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether the still is allowed to work, given what is burning and how good the machine is.
 *
 * <p>Two ideas that look like one in the design doc, and are worth keeping apart:
 *
 * <ul>
 *   <li><strong>Lit</strong> — something is burning. Wood counts; it lights the chamber and shows
 *       the lit block state.
 *   <li><strong>Lit tier</strong> — how good the fuel currently burning is. Wood is tier zero.
 * </ul>
 *
 * <p>A brew needs fuel burning <em>and</em>, unless the config disables the check, a fuel tier and
 * a machine tier both at least the recipe's own. Wood therefore lights the fire and powers nothing,
 * which is exactly what the design asks for without contradicting itself.
 */
public final class FuelRules {

  private FuelRules() {}

  /**
   * @param burningFuel the fuel currently alight, or empty if the chamber is cold
   * @param machineTier the tier the assembled still reaches
   * @param recipeTier the tier the recipe demands
   * @param gatingEnabled whether the tier comparisons are enforced at all
   * @return whether a brew may advance this tick
   */
  public static boolean mayBrew(
      Optional<FuelTier> burningFuel, int machineTier, int recipeTier, boolean gatingEnabled) {
    Objects.requireNonNull(burningFuel, "burningFuel");

    if (burningFuel.isEmpty()) {
      // A cold chamber stops the brew whatever the config says: the tier checks are tunable, having
      // no fire at all is not.
      return false;
    }
    if (!gatingEnabled) {
      return true;
    }
    return burningFuel.get().tier() >= recipeTier && machineTier >= recipeTier;
  }

  /**
   * @param burningFuel the fuel currently alight, or empty if the chamber is cold
   * @param machineTier the tier the assembled still reaches
   * @param recipeTier the tier the recipe demands
   * @param tuning supplies whether gating is enabled
   * @return whether a brew may advance this tick
   */
  public static boolean mayBrew(
      Optional<FuelTier> burningFuel, int machineTier, int recipeTier, Tuning tuning) {
    Objects.requireNonNull(tuning, "tuning");
    return mayBrew(burningFuel, machineTier, recipeTier, tuning.fuelTierGatingEnabled());
  }
}
