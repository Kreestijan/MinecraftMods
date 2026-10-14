package io.github.kreestijan.alchemizer.core.fuel;

import java.util.Optional;

/**
 * The fuel ladder from {@code DESIGN.md}, as a tier rather than as a list of items.
 *
 * <p>Which items belong to which tier is not decided here — it is decided by item tags, so a
 * datapack or another mod can move an item up the ladder without anything being recompiled. This
 * enum only names the rungs and gives each one the tag it reads from.
 *
 * <p>Tier zero is wood. It burns and it lights the chamber, but no potion can be brewed on it:
 * every recipe has a tier of at least one. Wood is the fuel that teaches a player the machine does
 * not work on sticks alone.
 */
public enum FuelTier {
  WOOD(0),
  COAL(1),
  ALCHEMICAL_COAL(2),
  BLAZE_ROD(3),
  ENHANCED_BLAZE_ROD(4);

  private final int tier;

  FuelTier(int tier) {
    this.tier = tier;
  }

  /**
   * @return the rung on the ladder, from zero upwards
   */
  public int tier() {
    return tier;
  }

  /**
   * @return the path of the item tag that defines this tier, under the mod's namespace
   */
  public String tagPath() {
    return "fuel_tier_" + tier;
  }

  /**
   * @param tier a rung on the ladder
   * @return the fuel tier at that rung, or empty if there is none
   */
  public static Optional<FuelTier> ofTier(int tier) {
    for (FuelTier candidate : values()) {
      if (candidate.tier == tier) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
