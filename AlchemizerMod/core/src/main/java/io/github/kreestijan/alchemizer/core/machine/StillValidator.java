package io.github.kreestijan.alchemizer.core.machine;

import io.github.kreestijan.alchemizer.core.machine.MachineBlocks.Part;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether the blocks around a chamber form a still, and how good a one.
 *
 * <p>The rules are {@code DESIGN.md} §3.1, and they are cumulative: exactly one ignition chamber,
 * exactly one pressure chamber directly on top of it, and at least one tank within reach make a
 * tier 1 still; valves carrying bellows make it tier 2; a funnel on the chamber and a flue stack on
 * a valve make it tier 3; one resonance crystal touching the machine makes it tier 4. Condensers
 * and tubing are not tier at all — they buy batch size and reach.
 *
 * <p>Nothing here is anchored to an absolute position — every cell is an offset, and the only
 * structural rule is a relative one. Which cell a caller puts at the origin is therefore its own
 * choice. In the mod it is the pressure chamber, because that is the block whose block entity owns
 * the cache and the brewing loop and so is the one that has to ask the question; the tests put the
 * ignition chamber there instead, which exercises the same rule from the other side.
 *
 * <p>Everything is found by scanning a cube around the origin, then filtering. The scan is the only
 * place the world is touched, and it is bounded, so the cost is known in advance and does not
 * depend on how large a player's tank farm has grown.
 *
 * <p>A part that does not satisfy its placement rule is not counted and is not a fault. A valve
 * standing on the ground four blocks from the fire does not break the still it was left beside; it
 * is simply not part of it. Only the two rules a player can satisfy wrongly without noticing — two
 * crystals, and a run of tubing longer than the config allows — are reported.
 */
public final class StillValidator {

  /**
   * How many tanks and condensers one still may draw on before the extra ones are ignored.
   *
   * <p>A bound rather than a rule: it exists so that the walk below cannot be turned into a way to
   * make the server do work by building a very large tank farm.
   */
  private static final int MAX_CONNECTED_CELLS = 64;

  private StillValidator() {}

  /**
   * The verdict on a still, the tanks it draws on and the bellows that count as part of it.
   *
   * <p>The three travel together because they come from one scan. Asking the world twice — once for
   * "is this a still" and once for "which tanks feed it" — would let a block change between the two
   * answers, and would let a caller hold a verdict and a tank list that disagree. The bellows are
   * here for the same reason: they are the exact set the machine counts, so a caller that wants to
   * animate them can do so without re-deriving the placement rule.
   *
   * @param result whether the parts form a still, and at what tier
   * @param tanks the cells of every tank connected to the pressure chamber; empty when the parts do
   *     not form a still, since an unformed machine must not be able to draw water
   * @param bellows the cells of every bellows the machine counts; empty when the parts do not form
   *     a still
   */
  public record Inspection(
      ValidationResult result, Set<RelativePos> tanks, Set<RelativePos> bellows) {

    public Inspection {
      Objects.requireNonNull(result, "result");
      tanks = Set.copyOf(Objects.requireNonNull(tanks, "tanks"));
      bellows = Set.copyOf(Objects.requireNonNull(bellows, "bellows"));
    }
  }

  /**
   * @param view the world, as seen from the chamber doing the looking
   * @param blocks which block ids count as which part
   * @param tuning supplies the scan radius, the tubing limit and the tier cap
   * @return the verdict
   */
  public static ValidationResult validate(StructureView view, MachineBlocks blocks, Tuning tuning) {
    return inspect(view, blocks, tuning).result();
  }

  /**
   * The whole question, asked once.
   *
   * <p>The three numbers this reads out of {@code tuning} arrive as one argument rather than three,
   * because they are all plain ints: a caller who passed the radius where the tubing limit belongs
   * would get a still that works and a bug nobody can see.
   *
   * @param view the world, as seen from the chamber doing the looking
   * @param blocks which block ids count as which part
   * @param tuning supplies the scan radius, the tubing limit and the tier cap
   * @return the verdict, and the tanks that feed it
   */
  public static Inspection inspect(StructureView view, MachineBlocks blocks, Tuning tuning) {
    Objects.requireNonNull(view, "view");
    Objects.requireNonNull(blocks, "blocks");
    Objects.requireNonNull(tuning, "tuning");

    if (blocks.isEmpty()) {
      // Without this the player gets "no ignition chamber found" and goes looking for the wrong
      // problem: the machine is fine, the tags that define it are missing.
      return new Inspection(
          ValidationResult.invalid(StillProblem.NO_PARTS_DEFINED), Set.of(), Set.of());
    }

    Map<Part, Set<RelativePos>> found = scan(view, blocks, tuning.scanRadius());
    List<StillProblem> problems = new ArrayList<>();

    int ignitionCount = found.get(Part.IGNITION).size();
    int pressureCount = found.get(Part.PRESSURE).size();

    if (ignitionCount == 0) {
      problems.add(StillProblem.NO_IGNITION);
    } else if (ignitionCount > 1) {
      problems.add(StillProblem.MULTIPLE_IGNITION);
    }

    if (pressureCount == 0) {
      problems.add(StillProblem.NO_PRESSURE);
    } else if (pressureCount > 1) {
      problems.add(StillProblem.MULTIPLE_PRESSURE);
    }

    if (!problems.isEmpty()) {
      return new Inspection(ValidationResult.invalid(problems), Set.of(), Set.of());
    }

    // Both are present and unique, so both have a cell; the counts above are what guarantee it.
    RelativePos ignition = found.get(Part.IGNITION).iterator().next();
    RelativePos pressure = found.get(Part.PRESSURE).iterator().next();

    if (!pressure.equals(ignition.above())) {
      problems.add(StillProblem.PRESSURE_NOT_ON_IGNITION);
    }

    Reach reach = reach(pressure, found, tuning.maxTubingLength());
    if (reach.tanks().isEmpty()) {
      problems.add(
          reach.tanksBeyondLimit() > 0 ? StillProblem.TUBING_TOO_LONG : StillProblem.NO_TANK);
    }

    Set<RelativePos> valves = attachedValves(ignition, found.get(Part.VALVE));
    Set<RelativePos> bellows = bellowsOn(valves, found.get(Part.BELLOWS));
    Set<RelativePos> flues = flueStack(valves, found.get(Part.FLUE));
    Optional<RelativePos> funnel =
        found.get(Part.FUNNEL).contains(pressure.above())
            ? Optional.of(pressure.above())
            : Optional.empty();

    Set<RelativePos> parts = new HashSet<>();
    parts.add(ignition);
    parts.add(pressure);
    parts.addAll(reach.cells());
    parts.addAll(valves);
    parts.addAll(bellows);
    parts.addAll(flues);
    funnel.ifPresent(parts::add);

    int crystals = touching(found.get(Part.CRYSTAL), parts);
    if (crystals > 1) {
      problems.add(StillProblem.MULTIPLE_CRYSTAL);
    }

    if (!problems.isEmpty()) {
      return new Inspection(ValidationResult.invalid(problems), Set.of(), Set.of());
    }

    PartCounts counts =
        new PartCounts(
            1,
            1,
            reach.tanks().size(),
            valves.size(),
            bellowsOnValves(valves, bellows),
            funnel.isPresent() ? 1 : 0,
            flues.size(),
            crystals,
            reach.condensers().size(),
            reach.advancedCondensers().size());

    return counts
        .tier(tuning.machineTierCap())
        .map(tier -> new Inspection(ValidationResult.valid(tier), reach.tanks(), bellows))
        .orElseGet(
            () ->
                new Inspection(
                    ValidationResult.invalid(StillProblem.UNKNOWN_TIER), Set.of(), Set.of()));
  }

  /**
   * The reach of the machine through tanks and tubing.
   *
   * @param cells every connected tank, condenser and tubing cell; the parts a crystal can touch
   * @param tanks the connected tanks, which are what the still draws water from
   * @param condensers the connected condensers
   * @param advancedCondensers the connected advanced condensers
   * @param tanksBeyondLimit how many tanks are attached, but only by a run of tubing longer than
   *     the config allows; they are not counted, and they are the reason a machine with no tank in
   *     {@code tanks} says its tubing is too long rather than that it has no tank
   */
  private record Reach(
      Set<RelativePos> cells,
      Set<RelativePos> tanks,
      Set<RelativePos> condensers,
      Set<RelativePos> advancedCondensers,
      int tanksBeyondLimit) {}

  /**
   * Reads the whole search cube once and sorts what it finds by which part it is.
   *
   * <p>The sets are frozen copies: membership was decided here, and every walk below trusts it
   * rather than touching the world again.
   *
   * @param view the world
   * @param blocks which block ids count as which part
   * @param radius how far from the origin to search, in blocks, on every axis
   * @return the cells of each part, every part present as a key
   */
  private static Map<Part, Set<RelativePos>> scan(
      StructureView view, MachineBlocks blocks, int radius) {
    Map<Part, Set<RelativePos>> found = new EnumMap<>(Part.class);
    for (Part part : Part.values()) {
      if (part != Part.NONE) {
        found.put(part, new HashSet<>());
      }
    }

    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          RelativePos pos = new RelativePos(x, y, z);
          Optional<String> blockId = view.blockIdAt(pos);
          // An unreadable cell — unloaded, or outside the world — is not a machine part and not an
          // error. Reading it as "empty" would make a still straddling a chunk border incomplete,
          // which is what the player sees, rather than a crash.
          if (blockId.isEmpty()) {
            continue;
          }
          Part part = blocks.partOf(blockId.get());
          if (part != Part.NONE) {
            found.get(part).add(pos);
          }
        }
      }
    }

    Map<Part, Set<RelativePos>> frozen = new EnumMap<>(Part.class);
    found.forEach((part, cells) -> frozen.put(part, Set.copyOf(cells)));
    return frozen;
  }

  /**
   * Walks outward from the pressure chamber through tanks and tubing.
   *
   * <p>Tanks are walked through as well as counted, so tanks behind tanks count, and a run of
   * tubing counts the same as the tanks it joins. Condensers are only ever an endpoint: a farm of
   * them hanging off one another is not a longer run.
   *
   * <p>Membership was decided by the scan, so a cell in these sets is already known to be loaded
   * and to be that block. Nothing here re-reads the world: doing so could only confirm what the
   * scan established, and would put the same decision in two places.
   *
   * @param pressure where the pressure chamber is
   * @param found every part the scan found
   * @param maxTubingLength how many tubing blocks a run may be before its far end stops counting
   * @return what the machine reaches
   */
  private static Reach reach(
      RelativePos pressure, Map<Part, Set<RelativePos>> found, int maxTubingLength) {
    Set<RelativePos> tanks = found.get(Part.TANK);
    Set<RelativePos> tubing = found.get(Part.TUBING);

    // The distance recorded for a cell is how many tubing blocks are on the shortest path to it,
    // which is zero for a tank touching the chamber and rises by one for each tubing block walked.
    // Breadth-first means the first time a cell is reached is by its shortest path, so a tank that
    // can be reached both directly and round a long way is not refused for the long way.
    //
    // Which kind each cell is needs no second map: the scan's own sets say whether a cell is a tank
    // or a piece of tubing, and those are the only two kinds worth walking.
    Map<RelativePos, Integer> distance = new HashMap<>();
    Deque<RelativePos> queue = new ArrayDeque<>();

    for (RelativePos neighbour : faceNeighbours(pressure)) {
      boolean isTank = tanks.contains(neighbour);
      if (!isTank && !tubing.contains(neighbour)) {
        continue;
      }
      if (distance.putIfAbsent(neighbour, isTank ? 0 : 1) == null) {
        queue.add(neighbour);
      }
    }

    while (!queue.isEmpty() && distance.size() < MAX_CONNECTED_CELLS) {
      RelativePos current = queue.poll();
      int here = distance.get(current);
      for (RelativePos neighbour : faceNeighbours(current)) {
        if (distance.size() >= MAX_CONNECTED_CELLS) {
          break;
        }
        if (distance.containsKey(neighbour)) {
          continue;
        }
        if (tanks.contains(neighbour)) {
          distance.put(neighbour, here);
          queue.add(neighbour);
        } else if (tubing.contains(neighbour)) {
          distance.put(neighbour, here + 1);
          queue.add(neighbour);
        }
      }
    }

    Set<RelativePos> connected = new HashSet<>();
    Set<RelativePos> connectedTanks = new HashSet<>();
    int tanksBeyondLimit = 0;
    for (Map.Entry<RelativePos, Integer> entry : distance.entrySet()) {
      boolean isTank = tanks.contains(entry.getKey());
      if (entry.getValue() > maxTubingLength) {
        if (isTank) {
          tanksBeyondLimit++;
        }
        continue;
      }
      connected.add(entry.getKey());
      if (isTank) {
        connectedTanks.add(entry.getKey());
      }
    }

    Set<RelativePos> condensers = new HashSet<>(found.get(Part.CONDENSER));
    Set<RelativePos> advanced = new HashSet<>(found.get(Part.ADVANCED_CONDENSER));
    Set<RelativePos> connectedCondensers = new HashSet<>();
    Set<RelativePos> connectedAdvanced = new HashSet<>();

    // §3.1: a condenser touches the pressure chamber or tubing connected to it, and nothing else.
    // A cell in `connected` is one of those tanks or that tubing, so the two loops below are the
    // whole rule: what touches the chamber, and what touches what the chamber reaches.
    for (RelativePos neighbour : faceNeighbours(pressure)) {
      if (condensers.contains(neighbour)) {
        connectedCondensers.add(neighbour);
      } else if (advanced.contains(neighbour)) {
        connectedAdvanced.add(neighbour);
      }
    }
    for (RelativePos cell : connected) {
      for (RelativePos neighbour : faceNeighbours(cell)) {
        if (condensers.contains(neighbour)) {
          connectedCondensers.add(neighbour);
        } else if (advanced.contains(neighbour)) {
          connectedAdvanced.add(neighbour);
        }
      }
    }

    Set<RelativePos> cells = new HashSet<>(connected);
    cells.addAll(connectedCondensers);
    cells.addAll(connectedAdvanced);
    return new Reach(
        Set.copyOf(cells),
        Set.copyOf(connectedTanks),
        Set.copyOf(connectedCondensers),
        Set.copyOf(connectedAdvanced),
        tanksBeyondLimit);
  }

  /**
   * The valves attached to the ignition chamber, directly or through other valves.
   *
   * <p>The walk starts from the chamber's four side faces and never looks at the cell above or
   * below it, which is the "never top or bottom" rule. A chain of valves can carry on round a
   * corner, as it should — the rule is about how a valve attaches, not about where it ends up.
   *
   * @param ignition where the ignition chamber is
   * @param valves every valve the scan found
   * @return the valves that are part of the machine
   */
  private static Set<RelativePos> attachedValves(RelativePos ignition, Set<RelativePos> valves) {
    Set<RelativePos> attached = new HashSet<>();
    Deque<RelativePos> queue = new ArrayDeque<>();
    for (RelativePos side : sideNeighbours(ignition)) {
      if (valves.contains(side) && attached.add(side)) {
        queue.add(side);
      }
    }
    while (!queue.isEmpty()) {
      RelativePos current = queue.poll();
      for (RelativePos neighbour : faceNeighbours(current)) {
        if (valves.contains(neighbour) && attached.add(neighbour)) {
          queue.add(neighbour);
        }
      }
    }
    return attached;
  }

  /**
   * The bellows sitting on a valve's side faces.
   *
   * <p>"A valve has 6 valve faces; bellows attach to its 4 side faces" is written for a valve that
   * knows which way it is pointing, and this one is a plain block with no orientation. The reading
   * taken here is the one a player can see: the four horizontal neighbours take bellows and the top
   * and bottom do not — which is also what keeps the top face free for a flue stack.
   *
   * @param valves the valves that are part of the machine
   * @param bellows every bellows the scan found
   * @return the bellows that are part of the machine
   */
  private static Set<RelativePos> bellowsOn(Set<RelativePos> valves, Set<RelativePos> bellows) {
    Set<RelativePos> attached = new HashSet<>();
    for (RelativePos valve : valves) {
      for (RelativePos side : sideNeighbours(valve)) {
        if (bellows.contains(side)) {
          attached.add(side);
        }
      }
    }
    return attached;
  }

  /**
   * How many valves are worth a bellows.
   *
   * <p>§3.3's exponent is per valve that has a bellows, not per bellows: four bellows on one valve
   * save a tenth, the same as one. This is the number that goes into the brew-time curve.
   *
   * @param valves the valves that are part of the machine
   * @param bellows the bellows that are part of the machine
   * @return how many of those valves carry at least one bellows
   */
  private static int bellowsOnValves(Set<RelativePos> valves, Set<RelativePos> bellows) {
    int count = 0;
    for (RelativePos valve : valves) {
      for (RelativePos side : sideNeighbours(valve)) {
        if (bellows.contains(side)) {
          count++;
          break;
        }
      }
    }
    return count;
  }

  /**
   * The flue blocks standing on the valves.
   *
   * <p>A flue goes on the top face of a valve, and a flue on top of a flue is the same stack
   * getting taller, so the walk climbs. It cannot wander: each step is one cell higher than the
   * last.
   *
   * @param valves the valves that are part of the machine
   * @param flues every flue the scan found
   * @return the flue cells that are part of the machine
   */
  private static Set<RelativePos> flueStack(Set<RelativePos> valves, Set<RelativePos> flues) {
    Set<RelativePos> counted = new HashSet<>();
    Deque<RelativePos> queue = new ArrayDeque<>();
    for (RelativePos valve : valves) {
      RelativePos top = valve.above();
      if (flues.contains(top) && counted.add(top)) {
        queue.add(top);
      }
    }
    while (!queue.isEmpty()) {
      RelativePos above = queue.poll().above();
      if (flues.contains(above) && counted.add(above)) {
        queue.add(above);
      }
    }
    return counted;
  }

  /**
   * @param crystals every crystal the scan found
   * @param parts the cells of the machine
   * @return how many crystals touch one of them
   */
  private static int touching(Set<RelativePos> crystals, Set<RelativePos> parts) {
    int count = 0;
    for (RelativePos crystal : crystals) {
      for (RelativePos neighbour : faceNeighbours(crystal)) {
        if (parts.contains(neighbour)) {
          count++;
          break;
        }
      }
    }
    return count;
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
   * @param pos a cell
   * @return the four cells beside it, and not the two above and below
   */
  private static List<RelativePos> sideNeighbours(RelativePos pos) {
    return List.of(pos.plus(1, 0, 0), pos.plus(-1, 0, 0), pos.plus(0, 0, 1), pos.plus(0, 0, -1));
  }

  /**
   * @return the number of blocks a still scans at a given radius, for documentation and tests
   */
  public static int scanVolume(int radius) {
    int side = 2 * radius + 1;
    return side * side * side;
  }
}
