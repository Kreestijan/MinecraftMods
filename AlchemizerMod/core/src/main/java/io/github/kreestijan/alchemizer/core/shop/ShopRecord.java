package io.github.kreestijan.alchemizer.core.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What is durably known about one shop, keyed by its {@link ShopId}.
 *
 * <p>Deliberately the whole of a shop's state and nothing else: no position, no tick counters, no
 * cached path. A record outlives the Counter it belongs to — the design requires that breaking and
 * re-placing restores everything — so anything stored here has to still mean something while the
 * block is an item in someone's inventory, and a block position does not.
 *
 * <p><b>Immutable, with {@code with*} methods.</b> The ledger replaces one record with the next
 * rather than mutating in place, which is what makes "the original is never touched" a property of
 * the code rather than a promise about it: a duplicate's record is a different object, so there is
 * no path by which editing one could reach the other.
 *
 * @param id the shop this describes
 * @param owner who placed it first; fixed, and never removed
 * @param coOwners the players the owner has added, in the order they were added
 * @param open whether the shop is trading; a closed shop gets no customers (addendum A6)
 * @param placed whether a Counter holding this shop currently exists in the world
 */
public record ShopRecord(ShopId id, UUID owner, List<UUID> coOwners, boolean open, boolean placed) {

  public ShopRecord {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(coOwners, "coOwners");
    coOwners = List.copyOf(coOwners);
  }

  /**
   * @param id the shop
   * @param owner who placed it first
   * @return a record for a shop whose Counter has just been put down, closed and with nobody added
   */
  public static ShopRecord placedBy(ShopId id, UUID owner) {
    // Closed, and opened by its owner from the Counter's own screen (addendum A6's Open Shop
    // control). A shop that opened itself would be trading from the moment the block landed, before
    // its owner had said what they are selling or who may sell it, and a shop that has just been
    // put down is one whose owner is still building the room around it.
    return new ShopRecord(id, owner, List.of(), false, true);
  }

  /**
   * What a player is to this shop.
   *
   * <p>The owner is asked about first, so that a player who is somehow both — a state the ledger's
   * own rules cannot produce, but a corrupted or hand-edited save could — reads as the owner rather
   * than as the weaker of the two.
   *
   * @param player who is asking
   * @return the role they hold here
   */
  public ShopRole roleOf(UUID player) {
    Objects.requireNonNull(player, "player");
    if (owner.equals(player)) {
      return ShopRole.OWNER;
    }
    return coOwners.contains(player) ? ShopRole.CO_OWNER : ShopRole.STRANGER;
  }

  /**
   * @param player who is asking
   * @return whether they may add or remove co-owners, which the owner alone may do
   */
  public boolean isOwner(UUID player) {
    return owner.equals(player);
  }

  /**
   * @param player who is asking
   * @return whether they hold any role here at all
   */
  public boolean isStakeholder(UUID player) {
    return roleOf(player) != ShopRole.STRANGER;
  }

  /**
   * @param player the player to add
   * @return a record with them added; the owner already counts, so adding them changes nothing
   */
  public ShopRecord withCoOwnerAdded(UUID player) {
    Objects.requireNonNull(player, "player");
    if (coOwners.contains(player) || owner.equals(player)) {
      return this;
    }
    List<UUID> added = new ArrayList<>(coOwners);
    added.add(player);
    return new ShopRecord(id, owner, added, open, placed);
  }

  /**
   * @param player the player to remove
   * @return a record with them removed; the owner is not a co-owner and so cannot be removed
   */
  public ShopRecord withCoOwnerRemoved(UUID player) {
    Objects.requireNonNull(player, "player");
    if (!coOwners.contains(player)) {
      return this;
    }
    List<UUID> kept = new ArrayList<>(coOwners);
    kept.remove(player);
    return new ShopRecord(id, owner, kept, open, placed);
  }

  /**
   * @param placed whether a Counter for this shop now exists
   * @return a record saying so
   */
  public ShopRecord withPlaced(boolean placed) {
    return new ShopRecord(id, owner, coOwners, open, placed);
  }

  /**
   * @param open whether the shop is trading
   * @return a record saying so
   */
  public ShopRecord withOpen(boolean open) {
    return new ShopRecord(id, owner, coOwners, open, placed);
  }
}
