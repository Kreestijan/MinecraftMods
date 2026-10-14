package io.github.kreestijan.alchemizer.core.machine;

import java.util.Objects;
import java.util.Set;

/**
 * Which block ids count as which part of a still.
 *
 * <p>Populated from block tags, not from a hardcoded list, so another mod's block — or a datapack —
 * can be declared part of a still without this class changing.
 *
 * <p>The ids are block ids with their namespace, exactly as {@code BuiltInRegistries.BLOCK} renders
 * them ({@code alchemizer:pressure_chamber}, {@code minecraft:furnace}). A block may belong to more
 * than one set; when it does, {@link #partOf} reports the more specific part first, because a block
 * that is somehow both a chamber and a tank is a chamber.
 *
 * @param ignition the ids that count as ignition chambers
 * @param pressure the ids that count as pressure chambers
 * @param tanks the ids that count as alchemical tanks
 */
public record MachineBlocks(Set<String> ignition, Set<String> pressure, Set<String> tanks) {

  public MachineBlocks {
    ignition = Set.copyOf(Objects.requireNonNull(ignition, "ignition"));
    pressure = Set.copyOf(Objects.requireNonNull(pressure, "pressure"));
    tanks = Set.copyOf(Objects.requireNonNull(tanks, "tanks"));
  }

  /** Which role a block plays in a still. */
  public enum Part {
    IGNITION,
    PRESSURE,
    TANK,
    /** Not part of a still. */
    NONE
  }

  /**
   * @return a set of parts in which nothing is a machine part, for an empty datapack
   */
  public static MachineBlocks empty() {
    return new MachineBlocks(Set.of(), Set.of(), Set.of());
  }

  /**
   * @param blockId a namespaced block id
   * @return the role that block plays, or {@link Part#NONE}
   */
  public Part partOf(String blockId) {
    Objects.requireNonNull(blockId, "blockId");
    if (ignition.contains(blockId)) {
      return Part.IGNITION;
    }
    if (pressure.contains(blockId)) {
      return Part.PRESSURE;
    }
    if (tanks.contains(blockId)) {
      return Part.TANK;
    }
    return Part.NONE;
  }

  /**
   * @return whether any part is defined at all
   */
  public boolean isEmpty() {
    return ignition.isEmpty() && pressure.isEmpty() && tanks.isEmpty();
  }
}
