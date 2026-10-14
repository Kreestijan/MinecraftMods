package io.github.kreestijan.alchemizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.kreestijan.alchemizer.core.tank.TankMath;
import net.neoforged.neoforge.fluids.FluidType;
import org.junit.jupiter.api.Test;

/**
 * The one number {@code core} cannot look up for itself.
 *
 * <p>Millibuckets are Minecraft's unit, and the conversion lives in {@link FluidType}. The
 * Minecraft-free {@code core} module cannot see that class, so it repeats the constant. This test
 * is the only thing keeping the two copies together: without it, a change to either side would
 * silently turn every tank in the mod into a different size.
 */
class TankConstantsTest {

  @Test
  void theCoreBucketMatchesMinecrafts() {
    assertEquals(FluidType.BUCKET_VOLUME, TankMath.BUCKET_MB);
  }

  @Test
  void aTankHoldsSixteenBucketsAndBrewsOneHundredAndSixtyPotions() {
    assertEquals(16 * FluidType.BUCKET_VOLUME, TankMath.BASIC_TANK_CAPACITY_MB);
    assertEquals(160, TankMath.dosesIn(TankMath.BASIC_TANK_CAPACITY_MB));
  }
}
