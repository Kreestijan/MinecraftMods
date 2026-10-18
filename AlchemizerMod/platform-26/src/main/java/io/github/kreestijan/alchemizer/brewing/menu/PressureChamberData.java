package io.github.kreestijan.alchemizer.brewing.menu;

import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.core.machine.StillProblem;
import java.util.Objects;
import net.minecraft.world.inventory.ContainerData;

/**
 * The numbers a brewing screen needs, read straight from the chamber.
 *
 * <p>Everything the screen draws comes through here, including the two that are not quantities: the
 * hourglass the player chose and why a brew would be refused. Sending those as data rather than
 * keeping a copy on the client is what makes the screen a view of the machine instead of a second
 * opinion about it — the client cannot show a still as ready when the server would refuse it.
 *
 * <p>Only the server ever holds one of these. The client's menu gets a plain mutable store of the
 * same length, filled by the container synchroniser, which is why {@link #set} is unreachable here
 * rather than implemented.
 */
public final class PressureChamberData implements ContainerData {

  /** Ticks the running brew has taken so far. */
  public static final int BREW_ELAPSED = 0;

  /** Ticks the running brew will take in total, or zero when nothing is running. */
  public static final int BREW_TOTAL = 1;

  /** Millibuckets of water in the tanks feeding this still. */
  public static final int WATER_STORED = 2;

  /** Millibuckets those tanks hold between them. */
  public static final int WATER_CAPACITY = 3;

  /** One when the chamber's fire is burning, zero when it is cold. */
  public static final int LIT = 4;

  /** One when the blocks around the chamber form a still. */
  public static final int FORMED = 5;

  /** The ordinal of the hourglass setting the player last chose. */
  public static final int PRESET = 6;

  /** The ordinal of what a brew started now would come to. */
  public static final int READINESS = 7;

  /** The tier of the still around this chamber, or zero when the parts are not a still. */
  public static final int TIER = 8;

  /**
   * The ordinal of the first thing wrong with the machine, or {@code -1} when nothing is.
   *
   * <p>Sent as an ordinal rather than as a phrase because the wording belongs to the client's
   * language file, and because an ordinal is what a data slot can carry. The client turns it back
   * into a translation key; see {@code PressureChamberScreen}. The value is the ordinal of a {@code
   * StillProblem}, which only ever grows by appending, so an ordinal means the same thing across
   * versions.
   */
  public static final int PROBLEM = 9;

  /**
   * How many potions the next brew would make, which is one unless the player has asked for more
   * and the machine can supply it.
   *
   * <p>The screen needs this one and not the dial: a slot is highlighted when it holds fewer than
   * this, and a control showing anything else would be a control lying about what it will do.
   */
  public static final int BATCH_MULTIPLIER = 10;

  /** The largest batch the still's condensers allow, which is one when there are none. */
  public static final int BATCH_MAXIMUM = 11;

  /** One when batch brewing is switched on, zero when it is off. */
  public static final int BATCH_ENABLED = 12;

  /** Ticks of burn left on the fuel alight. */
  public static final int BURN_REMAINING = 13;

  /** How long that fuel was going to burn for, so the flame can be a fraction of something. */
  public static final int BURN_TOTAL = 14;

  /** How many numbers this view has; also the size of the client's store. */
  public static final int COUNT = 15;

  private final PressureChamberBlockEntity chamber;

  /**
   * @param chamber the chamber whose brew this view reports
   */
  public PressureChamberData(PressureChamberBlockEntity chamber) {
    this.chamber = Objects.requireNonNull(chamber, "chamber");
  }

  @Override
  public int get(int dataId) {
    return switch (dataId) {
      case BREW_ELAPSED -> chamber.brewSession().elapsedTicks();
      case BREW_TOTAL -> chamber.brewSession().totalTicks();
      // Resolved per read rather than cached across the two: the tank positions behind it are
      // already cached and a tier-1 still has one tank, so a second lookup is cheaper than a field
      // that could go stale between reads.
      case WATER_STORED -> chamber.clusterWater().storedMb();
      case WATER_CAPACITY -> chamber.clusterWater().capacityMb();
      case LIT -> chamber.isLit() ? 1 : 0;
      case FORMED -> chamber.still().isFormed() ? 1 : 0;
      case PRESET -> chamber.selectedPreset().ordinal();
      case READINESS -> chamber.readiness().ordinal();
      // One scan serves both, and the screen needs both together: the tier to say the machine is
      // sound, and the problem to say what is wrong when it is not.
      case TIER -> chamber.still().tier().tier();
      case PROBLEM ->
          chamber
              .still()
              .firstProblem()
              .map(StillProblem::ordinal)
              .orElse(StillProblem.NONE_ORDINAL);
      case BATCH_MULTIPLIER -> chamber.effectiveBatch();
      case BATCH_MAXIMUM -> chamber.maximumBatch();
      case BATCH_ENABLED -> chamber.batchEnabled() ? 1 : 0;
      case BURN_REMAINING -> chamber.burnRemainingTicks();
      case BURN_TOTAL -> chamber.burnTotalTicks();
      default ->
          throw new IndexOutOfBoundsException(
              "Pressure chamber data has " + COUNT + " entries, got " + dataId);
    };
  }

  /**
   * Unreachable on purpose: every number above is derived from the chamber, and none of them is
   * something a client may say.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void set(int dataId, int value) {
    throw new UnsupportedOperationException(
        "Pressure chamber data is read-only; the client is sent data id " + dataId);
  }

  @Override
  public int getCount() {
    return COUNT;
  }
}
