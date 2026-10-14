package io.github.kreestijan.alchemizer.core.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether the blocks around a chamber form a still.
 *
 * <p>The rule at tier 1: exactly one ignition chamber, exactly one pressure chamber directly on top
 * of it, and at least one alchemical tank touching that pressure chamber. Tanks reach through other
 * tanks, so a column of tanks counts as one supply.
 *
 * <p>Nothing here is anchored to an absolute position — every cell is an offset, and the only
 * structural rule is that the pressure chamber is the cell above the ignition chamber. Which cell a
 * caller puts at the origin is therefore its own choice. In the mod it is the pressure chamber,
 * because that is the block whose block entity owns the cache and the brewing loop and so is the
 * one that has to ask the question; the tests here put the ignition chamber there instead, which
 * exercises the same rule from the other side.
 *
 * <p>Everything is found by scanning a cube of {@code radius} around the origin, then filtering.
 * The scan is the only place the world is touched, and it is bounded, so the cost is known in
 * advance and does not depend on how large a player's tank farm has grown.
 *
 * <p>Valves and bellows are counted as zero. They arrive with tier 2, and the fields already exist
 * on {@link StillTier} so that adding them is a change to this class alone.
 */
public final class StillValidator {

  /** How many tanks a single still may draw on before the extra ones are ignored. */
  private static final int MAX_CONNECTED_TANKS = 64;

  private StillValidator() {}

  /**
   * The verdict on a still, and the tanks it draws on.
   *
   * <p>The two travel together because they come from one scan. Asking the world twice — once for
   * "is this a still" and once for "which tanks feed it" — would let a block change between the two
   * answers, and would let a caller hold a verdict and a tank list that disagree.
   *
   * @param result whether the parts form a still, and at what tier
   * @param tanks the cells of every tank connected to the pressure chamber; empty when the parts do
   *     not form a still, since an unformed machine must not be able to draw water
   */
  public record Inspection(ValidationResult result, Set<RelativePos> tanks) {

    public Inspection {
      Objects.requireNonNull(result, "result");
      tanks = Set.copyOf(Objects.requireNonNull(tanks, "tanks"));
    }
  }

  /**
   * @param view the world, as seen from the chamber doing the looking
   * @param blocks which block ids count as which part
   * @param radius how far from the origin to search, in blocks, on every axis
   * @return the verdict
   */
  public static ValidationResult validate(StructureView view, MachineBlocks blocks, int radius) {
    return inspect(view, blocks, radius).result();
  }

  /**
   * @param view the world, as seen from the chamber doing the looking
   * @param blocks which block ids count as which part
   * @param radius how far from the origin to search, in blocks, on every axis
   * @return the verdict, and the tanks that feed it
   * @throws IllegalArgumentException if {@code radius} is less than one
   */
  public static Inspection inspect(StructureView view, MachineBlocks blocks, int radius) {
    Objects.requireNonNull(view, "view");
    Objects.requireNonNull(blocks, "blocks");
    if (radius < 1) {
      throw new IllegalArgumentException("radius must be at least 1, got " + radius);
    }

    if (blocks.isEmpty()) {
      // Without this the player gets "no ignition chamber found" and goes looking for the wrong
      // problem: the machine is fine, the tags that define it are missing.
      return new Inspection(ValidationResult.invalid(StillProblem.NO_PARTS_DEFINED), Set.of());
    }

    Parts parts = scan(view, blocks, radius);
    List<StillProblem> problems = new ArrayList<>();

    if (parts.ignitionCount() == 0) {
      problems.add(StillProblem.NO_IGNITION);
    } else if (parts.ignitionCount() > 1) {
      problems.add(StillProblem.MULTIPLE_IGNITION);
    }

    if (parts.pressureCount() == 0) {
      problems.add(StillProblem.NO_PRESSURE);
    } else if (parts.pressureCount() > 1) {
      problems.add(StillProblem.MULTIPLE_PRESSURE);
    }

    if (!problems.isEmpty()) {
      return new Inspection(ValidationResult.invalid(problems), Set.of());
    }

    // Both are present and unique, so neither is null here; the counts above are what guarantee it.
    if (!parts.pressure().equals(parts.ignition().above())) {
      problems.add(StillProblem.PRESSURE_NOT_ON_IGNITION);
    }

    Set<RelativePos> tanks = connectedTanks(parts.pressure(), parts.tankCells());
    if (tanks.isEmpty()) {
      problems.add(StillProblem.NO_TANK);
    }

    if (!problems.isEmpty()) {
      return new Inspection(ValidationResult.invalid(problems), Set.of());
    }

    return StillTier.forCounts(parts.ignitionCount(), parts.pressureCount(), tanks.size())
        .map(tier -> new Inspection(ValidationResult.valid(tier), tanks))
        .orElseGet(
            () -> new Inspection(ValidationResult.invalid(StillProblem.UNKNOWN_TIER), Set.of()));
  }

  /**
   * Reads the whole search cube once and sorts what it finds into parts.
   *
   * <p>The cell recorded for a kind is the last one seen, which only ever matters when there are
   * several: a still with two ignition chambers is refused on the count before anyone looks at
   * which cell was kept.
   *
   * @param ignition the last ignition chamber seen, or null if there was none
   * @param ignitionCount how many ignition chambers were found
   * @param pressure the last pressure chamber seen, or null if there was none
   * @param pressureCount how many pressure chambers were found
   * @param tankCells every tank cell found
   */
  private record Parts(
      RelativePos ignition,
      int ignitionCount,
      RelativePos pressure,
      int pressureCount,
      Set<RelativePos> tankCells) {}

  private static Parts scan(StructureView view, MachineBlocks blocks, int radius) {
    RelativePos ignition = null;
    int ignitionCount = 0;
    RelativePos pressure = null;
    int pressureCount = 0;
    Set<RelativePos> tankCells = new HashSet<>();

    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          RelativePos pos = new RelativePos(x, y, z);
          Optional<String> blockId = view.blockIdAt(pos);
          if (blockId.isEmpty()) {
            // Unloaded, or outside the world. Neither is a machine part, and neither is an error.
            continue;
          }
          switch (blocks.partOf(blockId.get())) {
            case IGNITION -> {
              ignition = pos;
              ignitionCount++;
            }
            case PRESSURE -> {
              pressure = pos;
              pressureCount++;
            }
            case TANK -> tankCells.add(pos);
            case NONE -> {
              // Not our business.
            }
          }
        }
      }
    }

    return new Parts(ignition, ignitionCount, pressure, pressureCount, tankCells);
  }

  /**
   * Walks outward from the pressure chamber through tanks, so that tanks behind tanks count.
   *
   * <p>Membership was decided by the scan, so a cell in {@code tankCells} is already known to be
   * loaded and to be a tank. Nothing here re-reads the world: doing so could only confirm what the
   * scan established, and would put the same decision in two places.
   *
   * @param pressure where the pressure chamber is
   * @param tankCells every tank cell the scan found
   * @return the cells of the tanks connected to the pressure chamber
   */
  private static Set<RelativePos> connectedTanks(RelativePos pressure, Set<RelativePos> tankCells) {
    if (tankCells.isEmpty()) {
      return Set.of();
    }

    Set<RelativePos> seen = new HashSet<>();
    Deque<RelativePos> queue = new ArrayDeque<>();
    for (RelativePos neighbour : faceNeighbours(pressure)) {
      if (tankCells.contains(neighbour) && seen.add(neighbour)) {
        queue.add(neighbour);
      }
    }

    while (!queue.isEmpty() && seen.size() < MAX_CONNECTED_TANKS) {
      RelativePos current = queue.poll();
      for (RelativePos neighbour : faceNeighbours(current)) {
        if (seen.size() >= MAX_CONNECTED_TANKS) {
          break;
        }
        if (seen.contains(neighbour)) {
          continue;
        }
        if (tankCells.contains(neighbour)) {
          seen.add(neighbour);
          queue.add(neighbour);
        }
      }
    }

    return Set.copyOf(seen);
  }

  /**
   * @param pos a cell
   * @return the six cells sharing a face with it
   */
  private static List<RelativePos> faceNeighbours(RelativePos pos) {
    return List.of(
        pos.plus(1, 0, 0),
        pos.plus(-1, 0, 0),
        pos.plus(0, 1, 0),
        pos.plus(0, -1, 0),
        pos.plus(0, 0, 1),
        pos.plus(0, 0, -1));
  }

  /**
   * @return the number of blocks a still scans at a given radius, for documentation and tests
   */
  public static int scanVolume(int radius) {
    int side = 2 * radius + 1;
    return side * side * side;
  }
}
