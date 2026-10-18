package io.github.kreestijan.alchemizer.core.machine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Which block ids count as which part of a still.
 *
 * <p>Populated from block tags, not from a hardcoded list, so another mod's block — or a datapack —
 * can be declared part of a still without this class changing.
 *
 * <p>Held as one id-to-part map rather than a set per part, and the reason is the shape of the
 * constructor it replaces: eleven kinds of part would be eleven {@code Set<String>} arguments, and
 * a caller who transposes two of them gets a still that validates against the wrong blocks with the
 * compiler saying nothing. A map also makes the question actually asked — "what is this block" —
 * the lookup it is, instead of a chain of membership tests.
 *
 * <p>The ids are block ids with their namespace, exactly as {@code BuiltInRegistries.BLOCK} renders
 * them ({@code alchemizer:pressure_chamber}, {@code minecraft:furnace}). A block may be declared in
 * more than one part's tag; when it is, {@link Part}'s own order settles it and the earlier part
 * wins, because a block that is somehow both a chamber and a tank is a chamber.
 *
 * @param byId which part each block id is; never holds {@link Part#NONE}
 */
public record MachineBlocks(Map<String, Part> byId) {

  public MachineBlocks {
    byId = Map.copyOf(Objects.requireNonNull(byId, "byId"));
    if (byId.containsValue(Part.NONE)) {
      throw new IllegalArgumentException("NONE is the absence of a part and cannot be declared");
    }
  }

  /**
   * Which role a block plays in a still.
   *
   * <p>The order is the order of precedence: when one block id appears in more than one part's tag,
   * the part declared earlier in this list is the one reported. It is the order {@link #of}
   * flattens in, so it is also the order of specificity — a chamber outranks a tank, and an
   * advanced condenser outranks the plain one it is built from.
   */
  public enum Part {
    IGNITION,
    PRESSURE,
    TANK,
    VALVE,
    BELLOWS,
    FUNNEL,
    FLUE,
    CRYSTAL,
    ADVANCED_CONDENSER,
    CONDENSER,
    TUBING,
    /** Not part of a still. */
    NONE
  }

  /**
   * Builds the table from a set of ids per part.
   *
   * <p>Parts absent from the map, and parts mapped to an empty collection, define nothing. A block
   * id named by two parts belongs to whichever of them {@link Part} declares first.
   *
   * @param idsByPart the block ids of each part, as the datapack currently has them
   * @return the table
   */
  public static MachineBlocks of(Map<Part, ? extends Collection<String>> idsByPart) {
    Objects.requireNonNull(idsByPart, "idsByPart");
    Map<String, Part> byId = new LinkedHashMap<>();
    for (Part part : Part.values()) {
      if (part == Part.NONE) {
        continue;
      }
      Collection<String> ids = idsByPart.get(part);
      if (ids == null) {
        continue;
      }
      for (String id : ids) {
        byId.putIfAbsent(Objects.requireNonNull(id, "block id"), part);
      }
    }
    return new MachineBlocks(byId);
  }

  /**
   * @return a set of parts in which nothing is a machine part, for an empty datapack
   */
  public static MachineBlocks empty() {
    return new MachineBlocks(Map.of());
  }

  /**
   * @param blockId a namespaced block id
   * @return the role that block plays, or {@link Part#NONE}
   */
  public Part partOf(String blockId) {
    Objects.requireNonNull(blockId, "blockId");
    return byId.getOrDefault(blockId, Part.NONE);
  }

  /**
   * @return whether any part is defined at all
   */
  public boolean isEmpty() {
    return byId.isEmpty();
  }
}
