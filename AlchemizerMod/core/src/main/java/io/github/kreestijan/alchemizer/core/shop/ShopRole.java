package io.github.kreestijan.alchemizer.core.shop;

/**
 * What a player is to one shop.
 *
 * <p>Three states rather than a pair of booleans, because the design's rules are written in terms
 * of "owner or co-owner" and "owner alone" — serving a customer, and breaking the Counter, are the
 * first; adding a co-owner is the second. Naming the three positions lets each rule say which it
 * means instead of testing two flags in an order that has to be got right every time.
 *
 * <p>{@link #STRANGER} is the answer for a player the shop has never heard of <em>and</em> for a
 * shop that does not exist. Both are "you have no business here", and separating them would only
 * invite a caller to decide, wrongly, that an unknown shop is a permission error worth telling a
 * player about.
 */
public enum ShopRole {

  /** Placed the Counter first. Fixed, and the only role that may add or remove co-owners. */
  OWNER,

  /** Added by the owner. Everything the owner may do, except change who else may. */
  CO_OWNER,

  /** Anyone else, and everyone at all when the shop is unknown. */
  STRANGER;

  /**
   * @return whether this role may serve customers, open and close the shop, and break the Counter
   */
  public boolean isStakeholder() {
    return this != STRANGER;
  }

  /**
   * @return whether this role may add and remove co-owners, which is the owner alone
   */
  public boolean mayManageOwners() {
    return this == OWNER;
  }
}
