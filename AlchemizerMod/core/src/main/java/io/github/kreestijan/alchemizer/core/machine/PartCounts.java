package io.github.kreestijan.alchemizer.core.machine;

import java.util.Optional;

/**
 * How many of each part of a still were counted, and what tier that adds up to.
 *
 * <p>A count here is not a tally of blocks. A part appears only once it satisfies the placement
 * rule its tier asks of it: a valve standing on the ground four blocks from the fire is not a
 * valve, and a bellows hanging off that valve is not a bellows. {@link StillValidator} reads the
 * world; this is the answer to "what does the machine actually have", and {@link #tier(int)} is the
 * only place that decides what that is worth.
 *
 * <p>One record rather than a field per part on {@link StillTier}, because the ladder reads ten
 * numbers and a ten-argument constructor silently accepts them in the wrong order. The counts are
 * cumulative by kind, not by tier: {@link #valves()} is every valve the rules accepted, whatever
 * tier the machine reaches.
 *
 * @param ignition how many ignition chambers were counted
 * @param pressure how many pressure chambers were counted
 * @param tanks how many tanks are connected to the pressure chamber
 * @param valves how many air pressure valves are attached to the ignition chamber
 * @param bellowsValves how many of those valves carry a bellows; the design counts valves, not
 *     bellows ("v = valves with bellows"), so a valve with four bellows is still worth one
 * @param funnels how many funnels sit on top of the pressure chamber
 * @param flues how many flue blocks make up the stacks standing on the valves
 * @param crystals how many resonance crystals touch another part
 * @param condensers how many condensers are connected to the pressure chamber
 * @param advancedCondensers how many advanced condensers are connected to the pressure chamber
 */
public record PartCounts(
    int ignition,
    int pressure,
    int tanks,
    int valves,
    int bellowsValves,
    int funnels,
    int flues,
    int crystals,
    int condensers,
    int advancedCondensers) {

  /** A machine with no parts in it at all. */
  public static final PartCounts NONE = new PartCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

  public PartCounts {
    if (ignition < 0
        || pressure < 0
        || tanks < 0
        || valves < 0
        || bellowsValves < 0
        || funnels < 0
        || flues < 0
        || crystals < 0
        || condensers < 0
        || advancedCondensers < 0) {
      throw new IllegalArgumentException("no count of a still part may be negative");
    }
  }

  /**
   * How many potions one brew run may produce.
   *
   * <p>{@code DESIGN.md} §3.4: each condenser doubles the run and each advanced condenser
   * quadruples it, and they add together rather than compounding — three condensers and one
   * advanced condenser is ten times, not twenty-four. The first condenser is worth its full value,
   * so one condenser is two and none is one. There is no ceiling here: the clamp is the stack size
   * a slot can hold, which is the game's own limit rather than one this mod invents.
   *
   * @return the largest batch this machine can brew
   */
  public int maxBatch() {
    long batch = 2L * condensers + 4L * advancedCondensers;
    return (int) Math.max(1, Math.min(batch, Integer.MAX_VALUE));
  }

  /**
   * The tier these counts describe, clamped to the cap.
   *
   * <p>The tiers are cumulative, so this climbs as far as the machine allows and stops: a still
   * with a funnel and a flue stack but no bellows is a tier 1 still that has grown a funnel, not a
   * broken tier 3 one. Every requirement is read off the table in {@code DESIGN.md} §3.1.
   *
   * <p>The clamp is applied here rather than by the caller so that a capped machine reads as its
   * capped tier everywhere — the status line, the fuel gate and the recipe gate all ask this one
   * question — instead of validating as a tier 4 still and then refusing tier 3 recipes.
   *
   * @param cap the highest tier the mod recognises
   * @return the tier reached, or empty when the counts describe no still at all
   * @throws IllegalArgumentException if {@code cap} is less than one
   */
  public Optional<StillTier> tier(int cap) {
    if (cap < 1) {
      throw new IllegalArgumentException("cap must be at least 1, got " + cap);
    }
    // Tier 1: one ignition chamber, one pressure chamber sitting on it, and water within reach. The
    // validator refuses every other way of failing this before it gets here, so an empty answer
    // means the caller skipped a check rather than that the player built something odd.
    if (ignition != 1 || pressure != 1 || tanks < 1) {
      return Optional.empty();
    }

    int reached = 1;
    if (valves >= 1 && bellowsValves >= 1) {
      reached = 2;
    }
    if (reached >= 2 && funnels >= 1 && flues >= 1) {
      reached = 3;
    }
    if (reached >= 3 && crystals == 1) {
      reached = 4;
    }

    return Optional.of(new StillTier(Math.min(reached, cap), this));
  }
}
