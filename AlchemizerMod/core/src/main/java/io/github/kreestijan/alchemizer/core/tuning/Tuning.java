package io.github.kreestijan.alchemizer.core.tuning;

import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.value.ValueFactors;
import java.util.List;
import java.util.Objects;

/**
 * Every number the rules depend on, in one immutable bundle.
 *
 * <p>The mod's config file is the user-facing surface, but config is read in one place — the
 * platform module — and passed in as this. That keeps the rules testable without a config file, and
 * keeps the "where does this number come from" question answerable by looking at a single
 * declaration.
 *
 * @param machineTierCap the highest still tier the mod will recognise; a machine built past it
 *     counts as this tier rather than failing to validate
 * @param fuelTierGatingEnabled whether a recipe's tier must be met by both the fuel and the machine
 * @param potionStackSize how many potions sharing every trait may occupy one stack; this overrides
 *     vanilla's own limit of one rather than sitting beside it, so it is a whole-game rule and not
 *     only a rule about this mod's machines
 * @param tankCapacityMb how much water one Alchemical Tank holds
 * @param doseMb how much water one potion consumes
 * @param scanRadius how far from the pressure chamber the still is searched for parts, in blocks
 * @param maxTubingLength how many alchemical tubing blocks a run from the pressure chamber to a
 *     tank or condenser may be; zero refuses tubing altogether, so every tank has to touch the
 *     chamber
 * @param hourglassBaseSeconds the unmodified brew time at each hourglass setting, by preset ordinal
 * @param bellowsFactor the multiplier applied per bellows or valve, below one to shorten the brew
 * @param minBrewTicks the floor under a brew time, so that no combination reaches zero
 * @param fallbackEffectBaseValue the worth given to an effect with no authored entry
 * @param valueFactors the shape of the value curve
 * @param debugLogging whether the mod logs still validation in detail
 * @param customersPerShop how many customers one shop may have waiting at once, which is both the
 *     shop's cap and the length of its line
 * @param customerArrivalTicks how long a shop waits between one customer and the next, in ticks
 * @param customerGraceTicks how long a customer with no usable route waits before leaving, in ticks
 * @param shopFieldRadius how far a shop's navigation field reaches horizontally from the Counter,
 *     in blocks
 * @param shopFieldYBelow how far below the Counter that field may reach, in blocks
 * @param shopFieldYAbove how far above the Counter that field may reach, in blocks
 * @param pathWearThreshold how many passes over one cell wear it into a path
 * @param pathWearEnabled whether walking wears the ground into a path at all
 * @param preferredPathCost what the navigation field charges for a step onto a preferred path block
 * @param plainPathCost what it charges for a step onto ordinary ground
 * @param climbSurcharge what a step that changes height costs on top of the ground it lands on
 */
public record Tuning(
    int machineTierCap,
    boolean fuelTierGatingEnabled,
    int potionStackSize,
    int tankCapacityMb,
    int doseMb,
    int scanRadius,
    int maxTubingLength,
    List<Integer> hourglassBaseSeconds,
    double bellowsFactor,
    int minBrewTicks,
    int fallbackEffectBaseValue,
    ValueFactors valueFactors,
    boolean debugLogging,
    int customersPerShop,
    int customerArrivalTicks,
    int customerGraceTicks,
    int shopFieldRadius,
    int shopFieldYBelow,
    int shopFieldYAbove,
    int pathWearThreshold,
    boolean pathWearEnabled,
    int preferredPathCost,
    int plainPathCost,
    int climbSurcharge) {

  public Tuning {
    if (machineTierCap < 1) {
      throw new IllegalArgumentException(
          "machineTierCap must be at least 1, got " + machineTierCap);
    }
    if (potionStackSize < 1) {
      throw new IllegalArgumentException(
          "potionStackSize must be at least 1, got " + potionStackSize);
    }
    if (tankCapacityMb < 0) {
      throw new IllegalArgumentException(
          "tankCapacityMb must not be negative, got " + tankCapacityMb);
    }
    if (doseMb < 1) {
      throw new IllegalArgumentException("doseMb must be at least 1, got " + doseMb);
    }
    if (scanRadius < 1) {
      throw new IllegalArgumentException("scanRadius must be at least 1, got " + scanRadius);
    }
    if (maxTubingLength < 0) {
      throw new IllegalArgumentException(
          "maxTubingLength must not be negative, got " + maxTubingLength);
    }
    Objects.requireNonNull(hourglassBaseSeconds, "hourglassBaseSeconds");
    hourglassBaseSeconds = List.copyOf(hourglassBaseSeconds);
    if (hourglassBaseSeconds.size() != HourglassPreset.values().length) {
      throw new IllegalArgumentException(
          "hourglassBaseSeconds must hold one entry per preset ("
              + HourglassPreset.values().length
              + "), got "
              + hourglassBaseSeconds.size());
    }
    for (int i = 0; i < hourglassBaseSeconds.size(); i++) {
      Integer seconds = hourglassBaseSeconds.get(i);
      if (seconds == null || seconds <= 0) {
        throw new IllegalArgumentException(
            "hourglassBaseSeconds[" + i + "] must be positive, got " + seconds);
      }
    }
    if (!Double.isFinite(bellowsFactor) || bellowsFactor <= 0 || bellowsFactor > 1) {
      throw new IllegalArgumentException(
          "bellowsFactor must be finite and within (0, 1], got " + bellowsFactor);
    }
    if (minBrewTicks < 1) {
      throw new IllegalArgumentException("minBrewTicks must be at least 1, got " + minBrewTicks);
    }
    if (fallbackEffectBaseValue < 0) {
      throw new IllegalArgumentException(
          "fallbackEffectBaseValue must not be negative, got " + fallbackEffectBaseValue);
    }
    Objects.requireNonNull(valueFactors, "valueFactors");
    if (customersPerShop < 1) {
      throw new IllegalArgumentException(
          "customersPerShop must be at least 1, got " + customersPerShop);
    }
    if (customerArrivalTicks < 1) {
      throw new IllegalArgumentException(
          "customerArrivalTicks must be at least 1, got " + customerArrivalTicks);
    }
    if (customerGraceTicks < 0) {
      throw new IllegalArgumentException(
          "customerGraceTicks must not be negative, got " + customerGraceTicks);
    }
    if (shopFieldRadius < 1) {
      throw new IllegalArgumentException(
          "shopFieldRadius must be at least 1, got " + shopFieldRadius);
    }
    if (shopFieldYBelow < 0) {
      throw new IllegalArgumentException(
          "shopFieldYBelow must not be negative, got " + shopFieldYBelow);
    }
    if (shopFieldYAbove < 0) {
      throw new IllegalArgumentException(
          "shopFieldYAbove must not be negative, got " + shopFieldYAbove);
    }
    if (pathWearThreshold < 1) {
      throw new IllegalArgumentException(
          "pathWearThreshold must be at least 1, got " + pathWearThreshold);
    }
    // A step has to cost something for the field to be a gradient at all: the builder refuses a
    // cost that is not positive as a caller that has not answered its question, so a zero here
    // would silently make every cell of a kind unwalkable.
    if (preferredPathCost < 1) {
      throw new IllegalArgumentException(
          "preferredPathCost must be at least 1, got " + preferredPathCost);
    }
    if (plainPathCost < 1) {
      throw new IllegalArgumentException("plainPathCost must be at least 1, got " + plainPathCost);
    }
    if (climbSurcharge < 0) {
      throw new IllegalArgumentException(
          "climbSurcharge must not be negative, got " + climbSurcharge);
    }
  }

  /**
   * @param preset the hourglass setting
   * @return the unmodified brew time at that setting, in seconds
   */
  public int baseSecondsFor(HourglassPreset preset) {
    Objects.requireNonNull(preset, "preset");
    return hourglassBaseSeconds.get(preset.ordinal());
  }

  /**
   * The same tuning at a different ceiling.
   *
   * <p>A wither rather than a constructor call at the test that wants a capped machine: the
   * constructor takes twelve arguments and half of them are ints, so a test that spells one out is
   * one transposition away from asserting something other than what it says.
   *
   * @param cap the highest tier the mod recognises
   * @return this tuning with that cap
   */
  public Tuning withMachineTierCap(int cap) {
    return new Tuning(
        cap,
        fuelTierGatingEnabled,
        potionStackSize,
        tankCapacityMb,
        doseMb,
        scanRadius,
        maxTubingLength,
        hourglassBaseSeconds,
        bellowsFactor,
        minBrewTicks,
        fallbackEffectBaseValue,
        valueFactors,
        debugLogging,
        customersPerShop,
        customerArrivalTicks,
        customerGraceTicks,
        shopFieldRadius,
        shopFieldYBelow,
        shopFieldYAbove,
        pathWearThreshold,
        pathWearEnabled,
        preferredPathCost,
        plainPathCost,
        climbSurcharge);
  }

  /**
   * @param length the longest run of tubing a tank or condenser may be behind
   * @return this tuning with that limit
   */
  public Tuning withMaxTubingLength(int length) {
    return new Tuning(
        machineTierCap,
        fuelTierGatingEnabled,
        potionStackSize,
        tankCapacityMb,
        doseMb,
        scanRadius,
        length,
        hourglassBaseSeconds,
        bellowsFactor,
        minBrewTicks,
        fallbackEffectBaseValue,
        valueFactors,
        debugLogging,
        customersPerShop,
        customerArrivalTicks,
        customerGraceTicks,
        shopFieldRadius,
        shopFieldYBelow,
        shopFieldYAbove,
        pathWearThreshold,
        pathWearEnabled,
        preferredPathCost,
        plainPathCost,
        climbSurcharge);
  }

  /**
   * @return this tuning with logging switched on
   */
  public Tuning withDebugLogging() {
    return new Tuning(
        machineTierCap,
        fuelTierGatingEnabled,
        potionStackSize,
        tankCapacityMb,
        doseMb,
        scanRadius,
        maxTubingLength,
        hourglassBaseSeconds,
        bellowsFactor,
        minBrewTicks,
        fallbackEffectBaseValue,
        valueFactors,
        true,
        customersPerShop,
        customerArrivalTicks,
        customerGraceTicks,
        shopFieldRadius,
        shopFieldYBelow,
        shopFieldYAbove,
        pathWearThreshold,
        pathWearEnabled,
        preferredPathCost,
        plainPathCost,
        climbSurcharge);
  }
}
