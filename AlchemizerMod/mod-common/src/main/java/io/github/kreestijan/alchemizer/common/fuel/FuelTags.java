package io.github.kreestijan.alchemizer.common.fuel;

import io.github.kreestijan.alchemizer.core.fuel.FuelTier;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Says which rung of the fuel ladder an item belongs to, by way of item tags.
 *
 * <p>{@link FuelTier} names the rungs and knows each one's tag path; this is the other half,
 * turning a tag path into a real {@link TagKey} and answering the question the ignition chamber
 * actually asks: what is this stack worth as fuel, if anything.
 *
 * <p>Which items sit on which rung is not decided in code at all — it is decided by the tag files
 * the mod ships and by anything a datapack or another mod adds to them. That is what lets a player
 * make blast furnaces run on coal and a modpack author decide that their own fuel counts as tier 3.
 */
public final class FuelTags {

  /**
   * The namespace every fuel tag lives under.
   *
   * <p>This duplicates {@code Alchemizer.MOD_ID}, which this module cannot see: it is a library,
   * and the entrypoint that owns that constant lives in the platform module that depends on it. The
   * duplication is intentional and guarded — {@code NamespaceConstantsTest} in the platform module
   * asserts the two are equal, so they cannot drift apart quietly.
   */
  public static final String MOD_NAMESPACE = "alchemizer";

  private static final Map<FuelTier, TagKey<Item>> TAGS = buildTags();

  private FuelTags() {}

  /**
   * @param tier a rung on the fuel ladder
   * @return the item tag that defines that rung
   */
  public static TagKey<Item> tagOf(FuelTier tier) {
    Objects.requireNonNull(tier, "tier");
    return TAGS.get(tier);
  }

  /**
   * Finds the best rung a stack qualifies for.
   *
   * <p>The highest matching tier wins, not the first one found. Tags are additive, so an item can
   * legitimately sit on two rungs at once — a modpack might add blaze rods to the coal tag to make
   * an early-game recipe reachable. Reading that as coal would silently weaken the fuel; reading it
   * as blaze rod is what the player who added the tag meant.
   *
   * @param stack the stack being offered to the ignition chamber; may be empty
   * @return the best tier the stack qualifies for, or empty if it is no fuel at all
   */
  public static Optional<FuelTier> tierOf(ItemStack stack) {
    Objects.requireNonNull(stack, "stack");
    if (stack.isEmpty()) {
      return Optional.empty();
    }
    Holder<Item> holder = stack.typeHolder();
    FuelTier best = null;
    for (FuelTier tier : FuelTier.values()) {
      if (holder.is(TAGS.get(tier)) && (best == null || tier.tier() > best.tier())) {
        best = tier;
      }
    }
    return Optional.ofNullable(best);
  }

  /**
   * @return one tag key per rung, built once
   */
  private static Map<FuelTier, TagKey<Item>> buildTags() {
    Map<FuelTier, TagKey<Item>> tags = new EnumMap<>(FuelTier.class);
    for (FuelTier tier : FuelTier.values()) {
      tags.put(
          tier,
          TagKey.create(
              Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_NAMESPACE, tier.tagPath())));
    }
    return Collections.unmodifiableMap(tags);
  }
}
