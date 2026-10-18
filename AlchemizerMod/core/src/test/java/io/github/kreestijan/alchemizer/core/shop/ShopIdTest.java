package io.github.kreestijan.alchemizer.core.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/**
 * The shop id, asserted as a format rather than as a value.
 *
 * <p>An id is the key a shop's whole durable state is filed under, so the only thing worth pinning
 * is that the shape is exact: a malformed one does not fail loudly, it quietly finds nothing and
 * mints a second shop. These tests are about what is refused.
 */
class ShopIdTest {

  /** Draws 0, 1, 2, … so a test can predict every id it mints. */
  private static LongSupplier counting() {
    AtomicLong next = new AtomicLong();
    return next::getAndIncrement;
  }

  @Test
  void anIdIsThirtyTwoLowercaseHexCharacters() {
    ShopId id = ShopId.mint(counting());

    assertEquals(ShopId.LENGTH, id.value().length());
    assertTrue(id.value().matches("[0-9a-f]{32}"), () -> "not lowercase hex: " + id.value());
  }

  @Test
  void mintingIsReproducibleFromAKnownSource() {
    assertEquals(ShopId.mint(counting()), ShopId.mint(counting()));
  }

  @Test
  void twoMintsFromOneSourceDiffer() {
    LongSupplier mine = counting();

    assertNotEquals(ShopId.mint(mine), ShopId.mint(mine));
  }

  @Test
  void theShotIsWideEnoughThatASingleDrawWouldNotDo() {
    // Two draws, so ids whose sources differ only in the second half still differ. A single long
    // would make these two collide, which is the whole reason the second draw is there.
    ShopId first =
        ShopId.mint(
            new LongSupplier() {
              private int draw;

              @Override
              public long getAsLong() {
                return draw++ == 0 ? 1L : 2L;
              }
            });
    ShopId second =
        ShopId.mint(
            new LongSupplier() {
              private int draw;

              @Override
              public long getAsLong() {
                return draw++ == 0 ? 1L : 3L;
              }
            });

    assertNotEquals(first, second);
  }

  @Test
  void anIdOfTheWrongLengthIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> new ShopId("abc"));
    assertThrows(IllegalArgumentException.class, () -> new ShopId("0".repeat(31)));
    assertThrows(IllegalArgumentException.class, () -> new ShopId("0".repeat(33)));
  }

  @Test
  void anIdThatIsNotLowercaseHexIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> new ShopId("A".repeat(32)));
    assertThrows(IllegalArgumentException.class, () -> new ShopId("z".repeat(32)));
    assertThrows(IllegalArgumentException.class, () -> new ShopId("0".repeat(31) + "-"));
  }

  @Test
  void anAbsentIdIsNotAnEmptyOne() {
    // The design's "blank id" is the component being absent, never a present-but-empty string: an
    // empty id that parsed would be a key every malformed item shared.
    assertThrows(NullPointerException.class, () -> new ShopId(null));
    assertThrows(IllegalArgumentException.class, () -> new ShopId(""));
  }

  @Test
  void mintingWithNoSourceIsRefused() {
    assertThrows(NullPointerException.class, () -> ShopId.mint(null));
  }
}
