package io.github.kreestijan.alchemizer.core.machine;

import java.util.Optional;

/**
 * What the validator is allowed to ask about the world.
 *
 * <p>An interface rather than a {@code Level}, so the whole of {@link StillValidator} can be tested
 * against a map of strings, and so the caller decides what "out of the world" means.
 *
 * <p>The contract that matters: a cell that is unloaded, outside the world, or otherwise
 * unavailable returns {@link Optional#empty()} — never throws, and never reports a block name. The
 * validator treats an empty answer as "not a machine part", so a still straddling a chunk border is
 * incomplete rather than crashed.
 */
@FunctionalInterface
public interface StructureView {

  /**
   * @param pos the offset to look at
   * @return the namespaced id of the block there, or empty if that cell cannot be read
   */
  Optional<String> blockIdAt(RelativePos pos);
}
