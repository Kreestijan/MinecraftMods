package io.github.kreestijan.alchemizer.core.shop;

import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The name of one shop, minted when its Counter is first placed and carried on the item thereafter.
 *
 * <p><b>Why a string and not a number.</b> The design calls for a unique id generated at first
 * placement and stored on the item, and the thing it has to survive is being copied, carried
 * through a portal, and put down again somewhere else. A counter would collide across worlds; 128
 * bits of randomness will not.
 *
 * <p><b>Why the shape is enforced.</b> A shop's whole durable state is looked up by this value, so
 * a malformed one does not fail — it quietly finds nothing and mints a second shop, or worse, finds
 * the wrong one. Insisting on exactly thirty-two lowercase hex characters means a hand-edited
 * component, a truncated string, or another mod writing junk into the key is rejected at
 * construction with a message naming the value, rather than becoming a shop that cannot find its
 * own records.
 *
 * <p>Minting takes its randomness as a {@link LongSupplier} rather than reaching for a global one,
 * so a test can mint a known id and assert on the ledger without a seam.
 */
public record ShopId(String value) {

  /** How many hex characters an id is: sixteen bytes, written out. */
  public static final int LENGTH = 32;

  public ShopId {
    Objects.requireNonNull(value, "value");
    if (value.length() != LENGTH) {
      throw new IllegalArgumentException(
          "a shop id is " + LENGTH + " characters, was " + value.length() + ": " + value);
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
      if (!hex) {
        throw new IllegalArgumentException(
            "a shop id is lowercase hex, and '" + c + "' is not: " + value);
      }
    }
  }

  /**
   * Mints a new id.
   *
   * <p>Two draws, not one: a single {@code long} is sixty-four bits, and sixty-four bits is few
   * enough that a large server's worth of placed counters would start to collide by birthday. The
   * second draw costs nothing and puts the space out of reach of anything a player will do.
   *
   * @param random the source of the two draws; injected so tests are deterministic
   * @return a fresh id
   */
  public static ShopId mint(LongSupplier random) {
    Objects.requireNonNull(random, "random");
    long high = random.getAsLong();
    long low = random.getAsLong();
    return new ShopId(String.format(Locale.ROOT, "%016x%016x", high, low));
  }

  @Override
  public String toString() {
    return value;
  }
}
