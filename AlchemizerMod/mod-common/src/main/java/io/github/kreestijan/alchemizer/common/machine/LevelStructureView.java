package io.github.kreestijan.alchemizer.common.machine;

import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import io.github.kreestijan.alchemizer.core.machine.StructureView;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A still's surroundings, read out of a real world.
 *
 * <p>This is the adapter between the game's idea of a place and {@link StructureView}'s: it turns
 * an offset from an origin into a world position, asks the world what is there, and answers with a
 * namespaced block id — the only vocabulary {@code core} has.
 *
 * <p><strong>Unreadable cells answer empty rather than guessing.</strong> {@link
 * Level#isLoaded(BlockPos)} is checked before the block is read, so a cell in a chunk that is not
 * loaded is reported as unknown instead of as air. Air and "cannot see" are different answers to
 * the validator: air means the cell is part of the machine and is empty, while unknown means the
 * machine cannot be judged at all. Reading it as air would quietly turn a half-loaded still into a
 * broken one, and to a player standing at a chunk border those look identical.
 *
 * <p>Reading never forces a chunk to load. A still that straddles an unloaded border is incomplete
 * until the player walks over there, which is the same answer the validator already gives for any
 * missing part.
 *
 * <p><b>Not thread-safe, and not meant to be shared.</b> One instance belongs to one scan: the
 * cursor below is reused for every cell so that a scan of a few hundred cells allocates once rather
 * than a few hundred times, which is only safe because a scan runs on one thread from start to
 * finish.
 */
public final class LevelStructureView implements StructureView {

  private final Level level;
  private final BlockPos origin;
  private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

  /**
   * @param level the world to read from
   * @param origin the world position that offset {@code (0, 0, 0)} refers to
   */
  public LevelStructureView(Level level, BlockPos origin) {
    this.level = Objects.requireNonNull(level, "level");
    // Snapshotted rather than held. The caller may well have handed over its own mutable position,
    // and a view whose idea of the origin moved under it would validate a machine that is not
    // there.
    this.origin = Objects.requireNonNull(origin, "origin").immutable();
  }

  @Override
  public Optional<String> blockIdAt(RelativePos pos) {
    Objects.requireNonNull(pos, "pos");
    cursor.set(origin.getX() + pos.x(), origin.getY() + pos.y(), origin.getZ() + pos.z());
    if (!level.isLoaded(cursor)) {
      return Optional.empty();
    }

    // A block placed in a world is always in the registry, so the key is present in practice. The
    // empty answer is kept anyway: "this mod cannot name that block" is exactly the case the
    // validator already handles, and it handles it better than an exception would.
    Block block = level.getBlockState(cursor).getBlock();
    Identifier id = BuiltInRegistries.BLOCK.getKey(block);
    return Optional.ofNullable(id).map(Identifier::toString);
  }
}
