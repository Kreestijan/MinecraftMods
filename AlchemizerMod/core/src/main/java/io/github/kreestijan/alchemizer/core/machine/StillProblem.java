package io.github.kreestijan.alchemizer.core.machine;

import java.util.Optional;

/**
 * Why an assembled machine is not a still.
 *
 * <p>These are codes rather than sentences, and that is the point. A sentence written here would be
 * player-visible text living in a module that has no idea what language the player reads, and this
 * module is deliberately free of Minecraft so that its rules can be tested without a world. The
 * words belong to the adapter, which turns one of these into a translation key; the rule that
 * produced it belongs here.
 *
 * <p>An invalid still always carries at least one of these, which {@link ValidationResult}
 * enforces. The first is the one worth showing: the problems are added in the order a builder would
 * want to fix them, so a machine missing both a tank and an ignition chamber is reported as missing
 * the ignition chamber, and the tank is left for after.
 */
public enum StillProblem {
  /**
   * No block is tagged as any still part.
   *
   * <p>Reported ahead of everything else, because it is the one problem the player cannot fix by
   * building. The machine may be laid out perfectly; the tags that say which blocks are parts are
   * missing, which is a datapack or install fault.
   */
  NO_PARTS_DEFINED,

  /** There is no ignition chamber near the chamber. */
  NO_IGNITION,

  /** There is more than one ignition chamber near the chamber; a still uses exactly one. */
  MULTIPLE_IGNITION,

  /** There is no pressure chamber near the origin. */
  NO_PRESSURE,

  /** There is more than one pressure chamber near the origin; a still uses exactly one. */
  MULTIPLE_PRESSURE,

  /** The pressure chamber is not in the cell directly above the ignition chamber. */
  PRESSURE_NOT_ON_IGNITION,

  /** No alchemical tank touches the pressure chamber, directly or through other tanks. */
  NO_TANK,

  /**
   * The parts that are present are the right ones and in the right places, but they are not a still
   * any tier describes.
   *
   * <p>Not reachable at tier 1, where one of each is the only shape. It exists because the tier
   * table is data and a combination it does not recognise has to answer something.
   */
  UNKNOWN_TIER,

  /**
   * The chamber is not in a world, so it has no surroundings to judge.
   *
   * <p>Not reachable for a placed block. It answers "not a still" rather than throwing, so that no
   * caller has to null-check a level before asking a question.
   */
  NOT_IN_A_WORLD;

  /**
   * The value a caller uses when there is no problem to report.
   *
   * <p>Negative so that it cannot be confused with a constant, and defined here rather than at the
   * one call site that produces it so that the reader and the writer of an ordinal agree.
   */
  public static final int NONE_ORDINAL = -1;

  /**
   * @param ordinal an ordinal as it was carried across a data slot
   * @return the problem, or empty if the ordinal names none
   */
  public static Optional<StillProblem> ofOrdinal(int ordinal) {
    StillProblem[] values = values();
    if (ordinal < 0 || ordinal >= values.length) {
      return Optional.empty();
    }
    return Optional.of(values[ordinal]);
  }
}
