package io.github.kreestijan.alchemizer.common.tag;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Turns a block tag into the set of block ids it contains.
 *
 * <p>The rest of the mod identifies blocks by a name — {@code alchemizer:pressure_chamber} — and
 * never by a {@code Block} instance, because the machine rules live in {@code core}, which cannot
 * name a Minecraft type at all. This is where the two meet: a tag is what a datapack or another mod
 * can add to, and a set of names is what the rules can read.
 *
 * <p>The ids are rendered exactly as {@link BuiltInRegistries#BLOCK} renders them, which is also
 * how the scan in {@code core} names what it finds. Both sides therefore compare the same strings,
 * and neither has to know how the other got them.
 *
 * <p>Resolved on each call rather than cached. A tag's contents can change on {@code /reload}, so a
 * cached copy would need invalidating at exactly the right moment; the tags involved here are a
 * handful of entries, and this runs when a machine is revalidated, not per tick.
 */
public final class BlockTagIds {

  private BlockTagIds() {}

  /**
   * @param tag the tag to resolve
   * @return the namespaced ids of every block in it; empty for a tag nothing declares
   */
  public static Set<String> idsIn(TagKey<Block> tag) {
    Objects.requireNonNull(tag, "tag");

    Set<String> ids = new HashSet<>();
    for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
      // Through the holder's key rather than through getKey(value), so that the answer does not
      // depend on the block still being reachable from the registry by value.
      holder.unwrapKey().map(ResourceKey::identifier).map(Identifier::toString).ifPresent(ids::add);
    }
    return Set.copyOf(ids);
  }
}
