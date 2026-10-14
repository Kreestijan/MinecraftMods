package io.github.kreestijan.alchemizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.kreestijan.alchemizer.common.fuel.FuelTags;
import io.github.kreestijan.alchemizer.core.fuel.FuelTier;
import java.util.Arrays;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;

/**
 * Ties the mod id to the one place that cannot read it.
 *
 * <p>{@code mod-common} is a library: it cannot see {@link Alchemizer#MOD_ID}, because the
 * entrypoint that owns that constant is in the platform module that depends on it. So it repeats
 * the string, and this test is the only thing keeping the two copies together.
 *
 * <p>The consequence of them drifting apart is worth stating, because it is not a crash. Every fuel
 * tier tag would be written under one namespace and read from another, so the tags would load fine
 * and match nothing, and every fuel in the game would silently stop being fuel.
 */
class NamespaceConstantsTest {

  @Test
  void theSeamNamesTheSameNamespaceAsTheMod() {
    assertEquals(Alchemizer.MOD_ID, FuelTags.MOD_NAMESPACE);
  }

  /**
   * The tags the seam hands out have to be in that namespace, not merely the constant beside them.
   * A tag key built from a literal somewhere else in the seam would satisfy the check above and
   * still not be the tag the generated data files populate.
   */
  @Test
  void everyFuelTierTagIsInTheModsNamespace() {
    for (FuelTier tier : FuelTier.values()) {
      TagKey<?> tag = FuelTags.tagOf(tier);
      assertEquals(
          Alchemizer.MOD_ID,
          tag.location().getNamespace(),
          "fuel tier " + tier + " reads a tag outside the mod's namespace");
      assertEquals(tier.tagPath(), tag.location().getPath());
    }
  }

  /**
   * One tag per rung. Two rungs sharing a tag would make the ladder a lie in a way nothing else
   * would notice: the higher of the two would always win the "best match" search, so the lower one
   * would simply never be reachable.
   */
  @Test
  void everyFuelTierHasItsOwnTag() {
    long distinct =
        Arrays.stream(FuelTier.values())
            .map(FuelTags::tagOf)
            .map(TagKey::location)
            .distinct()
            .count();
    assertEquals(FuelTier.values().length, distinct, "two fuel tiers share one tag");
  }
}
