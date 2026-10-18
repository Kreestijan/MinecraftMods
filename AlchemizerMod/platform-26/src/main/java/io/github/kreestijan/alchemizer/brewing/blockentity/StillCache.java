package io.github.kreestijan.alchemizer.brewing.blockentity;

import io.github.kreestijan.alchemizer.common.machine.LevelStructureView;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import io.github.kreestijan.alchemizer.core.machine.StillValidator;
import io.github.kreestijan.alchemizer.core.machine.ValidationResult;
import io.github.kreestijan.alchemizer.registry.ModTags;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What a pressure chamber last found around itself, and whether that answer is still good.
 *
 * <p>Validating a still reads a cube of {@code scanRadius} blocks, which is far too much work to do
 * on every tick or every screen frame. So the answer is kept, and the only question asked in
 * between is whether anything has changed. A machine is marked dirty when a block near it changes;
 * the next reader pays for one scan and every reader after that is free.
 *
 * <p><b>What is kept is positions, never block entities.</b> The connected tanks are stored as
 * {@link BlockPos} and looked up again at the moment they are used. Holding a reference to another
 * block entity would keep a removed one alive, and would hand out a tank that has since been broken
 * and replaced. A position can only ever be stale in a way the lookup itself discovers.
 *
 * <p>This is state belonging to one block entity and is not shared or synchronised; every call
 * happens on the server thread that owns the block entity.
 */
final class StillCache {

  /** The last scan, or null when there has not been one since the machine was last disturbed. */
  private @Nullable StillValidator.Inspection inspection;

  /** Marks the remembered answer as no longer trustworthy. */
  void markDirty() {
    inspection = null;
  }

  /**
   * @param level the world the chamber is in
   * @param origin the chamber's own position, which the validator treats as the origin
   * @return whether the parts around the chamber form a still
   */
  ValidationResult validation(Level level, BlockPos origin) {
    return current(level, origin).result();
  }

  /**
   * @param level the world the chamber is in
   * @param origin the chamber's own position, which the validator treats as the origin
   * @return where the tanks feeding this still are, in no particular order; empty when the parts do
   *     not form a still
   */
  Set<BlockPos> tankPositions(Level level, BlockPos origin) {
    Set<RelativePos> cells = current(level, origin).tanks();
    Set<BlockPos> positions = new HashSet<>(cells.size());
    for (RelativePos cell : cells) {
      positions.add(origin.offset(cell.x(), cell.y(), cell.z()));
    }
    return Set.copyOf(positions);
  }

  private StillValidator.Inspection current(Level level, BlockPos origin) {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(origin, "origin");
    if (inspection == null) {
      inspection =
          StillValidator.inspect(
              new LevelStructureView(level, origin),
              ModTags.machineBlocks(),
              ConfigCache.current());
    }
    return inspection;
  }
}
