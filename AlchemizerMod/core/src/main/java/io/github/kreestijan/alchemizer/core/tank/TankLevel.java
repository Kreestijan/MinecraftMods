package io.github.kreestijan.alchemizer.core.tank;

/**
 * How much water a tank cluster holds, and how much it could hold.
 *
 * <p>Immutable: every change hands back a new level rather than mutating one. A tank that is read
 * by a menu on one thread and drained by a brew on another is the kind of thing that produces water
 * out of nowhere, and an immutable value rules that out by construction.
 *
 * <p>Both mutations clamp rather than throw. A player draining a tank that just ran dry should lose
 * the water they did not get, not crash the server, and the caller can compare before and after if
 * it needs to know exactly how much moved.
 *
 * @param storedMb the water currently held
 * @param capacityMb the most this cluster can hold; must be positive
 */
public record TankLevel(int storedMb, int capacityMb) {

  public TankLevel {
    if (capacityMb <= 0) {
      throw new IllegalArgumentException("capacityMb must be positive, got " + capacityMb);
    }
    if (storedMb < 0) {
      throw new IllegalArgumentException("storedMb must not be negative, got " + storedMb);
    }
    if (storedMb > capacityMb) {
      throw new IllegalArgumentException(
          "storedMb (" + storedMb + ") cannot exceed capacityMb (" + capacityMb + ")");
    }
  }

  /**
   * @param capacityMb the capacity of the cluster
   * @return an empty tank of that capacity
   */
  public static TankLevel empty(int capacityMb) {
    return new TankLevel(0, capacityMb);
  }

  /**
   * @return whether there is no water at all
   */
  public boolean isEmpty() {
    return storedMb == 0;
  }

  /**
   * @return whether the cluster is full
   */
  public boolean isFull() {
    return storedMb >= capacityMb;
  }

  /**
   * @return how much more water this cluster will accept
   */
  public int room() {
    return capacityMb - storedMb;
  }

  /**
   * @return how much of the cluster is filled, from zero to one
   */
  public double fraction() {
    return storedMb / (double) capacityMb;
  }

  /**
   * Takes water out, taking less if there is not enough.
   *
   * @param amountMb how much to take; negative counts as nothing
   * @return the level afterwards
   */
  public TankLevel withdraw(int amountMb) {
    if (amountMb <= 0) {
      return this;
    }
    return new TankLevel(Math.max(0, storedMb - amountMb), capacityMb);
  }

  /**
   * Puts water in, accepting less if it will not all fit.
   *
   * @param amountMb how much to add; negative counts as nothing
   * @return the level afterwards
   */
  public TankLevel fill(int amountMb) {
    if (amountMb <= 0) {
      return this;
    }
    long filled = (long) storedMb + amountMb;
    return new TankLevel((int) Math.min(filled, capacityMb), capacityMb);
  }

  /**
   * @param amountMb how much is wanted
   * @return whether that much can be taken without running dry
   */
  public boolean canWithdraw(int amountMb) {
    return amountMb >= 0 && storedMb >= amountMb;
  }

  /**
   * @return how many potions this much water will brew
   */
  public int doses() {
    return TankMath.dosesIn(storedMb);
  }
}
