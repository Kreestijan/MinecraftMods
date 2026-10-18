package io.github.kreestijan.alchemizer.core.shop;

/**
 * The bounds a shop is kept inside, gathered so the config's advertised range and the rule that
 * enforces it cannot drift apart.
 *
 * <p>The same shape as {@code TuningDefaults} and the other core tuning types, for the same reason:
 * a bound that exists in two places is a bound that will eventually be two different numbers, and
 * the config file's comment is the one a player reads.
 *
 * @param maxCoOwners how many players the owner of one shop may add
 */
public record ShopLimits(int maxCoOwners) {

  /** The largest a shop's co-owner list may be configured to. */
  public static final int MAX_CO_OWNERS_CEILING = 64;

  public ShopLimits {
    if (maxCoOwners < 0) {
      throw new IllegalArgumentException("maxCoOwners must not be negative, was " + maxCoOwners);
    }
    if (maxCoOwners > MAX_CO_OWNERS_CEILING) {
      throw new IllegalArgumentException(
          "maxCoOwners must be at most " + MAX_CO_OWNERS_CEILING + ", was " + maxCoOwners);
    }
  }

  /**
   * @return the bounds a shop runs under when nothing has been configured, which is also what the
   *     config file's own default is written from
   */
  public static ShopLimits defaults() {
    return new ShopLimits(8);
  }
}
