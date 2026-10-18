package io.github.kreestijan.alchemizer.core.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Every shop in one world, and the rules about who may do what to them.
 *
 * <p><b>Why the rules live in {@code core}.</b> None of them needs a world: a shop is a name, an
 * owner, a list of players and two flags, and the interesting cases are about what happens when an
 * item carrying an id meets a shop that already has it. That is arithmetic on a map, and it is the
 * part of the shop most worth testing exhaustively — so it lives here where a test can drive it
 * directly, rather than inside a block entity where every case would need a world built first.
 *
 * <p><b>Mutable, unlike most of {@code core}.</b> This is a store, not a value: it is read and
 * written over a world's lifetime and handed to the save system as-is. The <em>records</em> in it
 * are immutable and replaced rather than edited, which is what keeps "the original is never
 * touched" true by construction — a duplicate's new record is a different object, so there is no
 * path from editing one to reaching the other.
 *
 * <p>Not thread-safe, and not meant to be. It is the server's, touched on the server thread, and
 * the save system's own read happens under the level's lock.
 */
public final class ShopLedger {

  /**
   * How many times to redraw an id before giving up.
   *
   * <p>A collision is a one-in-2^128 event, so this is not a real bound on anything — it exists so
   * that minting cannot spin forever if a caller passes a {@link Supplier} that is broken, which is
   * a much more likely bug than a collision and would otherwise present as a hung server.
   */
  private static final int MINT_ATTEMPTS = 8;

  private final Map<ShopId, ShopRecord> shops;

  /** A ledger with nothing in it, which is what a world that has never had a Counter sees. */
  public ShopLedger() {
    this.shops = new HashMap<>();
  }

  /**
   * @param records what to start from, as read back from a save
   */
  public ShopLedger(Iterable<ShopRecord> records) {
    this();
    Objects.requireNonNull(records, "records");
    for (ShopRecord record : records) {
      shops.put(record.id(), record);
    }
  }

  /**
   * Works out which shop a Counter being placed belongs to, and records it.
   *
   * <p>This is the design's placement rule in one place. Three things can arrive on the item:
   * nothing, an id this world knows, or an id that is in use right now — and the third is the one
   * that matters, because it is what a player gets by copying the item. A copy must not be able to
   * reach into a shop that is already trading, so it is given a shop of its own and the original is
   * left exactly as it was.
   *
   * <p>An id this world has no record of is treated as fresh <em>but keeps its id</em>. That is the
   * item-brought-through-a-portal case: shop state is stored per dimension, so an id from elsewhere
   * is simply unknown here, and discarding the id would silently rename a player's shop for no
   * reason.
   *
   * @param offered the id the item carried, or null when it carried none
   * @param placer who put the block down, and who therefore owns the shop if it is a new one
   * @param limits the bounds to work within
   * @param minter where a fresh id comes from
   * @return which shop the Counter now belongs to, and why
   */
  public PlacementOutcome claimOnPlace(
      ShopId offered, UUID placer, ShopLimits limits, Supplier<ShopId> minter) {
    Objects.requireNonNull(placer, "placer");
    Objects.requireNonNull(limits, "limits");
    Objects.requireNonNull(minter, "minter");

    if (offered == null) {
      return new PlacementOutcome(mintFresh(placer, minter), PlacementOutcome.Kind.FRESH);
    }

    ShopRecord existing = shops.get(offered);
    if (existing == null) {
      shops.put(offered, ShopRecord.placedBy(offered, placer));
      return new PlacementOutcome(offered, PlacementOutcome.Kind.FRESH);
    }
    if (!existing.placed()) {
      shops.put(offered, existing.withPlaced(true));
      return new PlacementOutcome(offered, PlacementOutcome.Kind.RESTORED);
    }
    return new PlacementOutcome(mintFresh(placer, minter), PlacementOutcome.Kind.DUPLICATE);
  }

  /**
   * @param id the shop
   * @return whether that shop is trading, which is false for one this world has never heard of
   */
  public boolean isOpen(ShopId id) {
    Objects.requireNonNull(id, "id");
    ShopRecord record = shops.get(id);
    return record != null && record.open();
  }

  /**
   * Marks a Counter as gone without forgetting anything about its shop.
   *
   * <p>The record stays. That is the whole of "breaking and re-placing restores everything": the
   * shop is not deleted when its block is, only set down.
   *
   * @param id the shop whose Counter was broken
   * @return the record as it now stands, or empty if nothing was known under that id
   */
  public Optional<ShopRecord> release(ShopId id) {
    Objects.requireNonNull(id, "id");
    ShopRecord existing = shops.get(id);
    if (existing == null) {
      return Optional.empty();
    }
    ShopRecord released = existing.withPlaced(false);
    shops.put(id, released);
    return Optional.of(released);
  }

  /**
   * @param id the shop
   * @param player who is asking
   * @return their role, which is {@link ShopRole#STRANGER} for an unknown shop as well as an
   *     unknown player
   */
  public ShopRole roleOf(ShopId id, UUID player) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(player, "player");
    ShopRecord record = shops.get(id);
    return record == null ? ShopRole.STRANGER : record.roleOf(player);
  }

  /**
   * @param id the shop
   * @return its record, if this world has one
   */
  public Optional<ShopRecord> record(ShopId id) {
    Objects.requireNonNull(id, "id");
    return Optional.ofNullable(shops.get(id));
  }

  /**
   * @return every shop, in no particular order
   */
  public List<ShopRecord> records() {
    return List.copyOf(shops.values());
  }

  /**
   * @return how many shops this world has ever known, placed or not
   */
  public int size() {
    return shops.size();
  }

  /**
   * Adds a player to a shop's co-owners.
   *
   * <p>The owner alone may do this — the design is explicit that a co-owner can do everything
   * except change who else may. Adding someone already on the list, or the owner themselves, is not
   * an error worth reporting: it leaves the shop as it was and answers false, which is what a
   * caller pressing a button wants to hear.
   *
   * @param id the shop
   * @param actor who is asking
   * @param target who is to be added
   * @param limits the bounds to work within
   * @return whether the shop changed
   */
  public boolean addCoOwner(ShopId id, UUID actor, UUID target, ShopLimits limits) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(limits, "limits");

    ShopRecord record = shops.get(id);
    if (record == null || !record.roleOf(actor).mayManageOwners()) {
      return false;
    }
    if (record.coOwners().contains(target) || record.owner().equals(target)) {
      return false;
    }
    if (record.coOwners().size() >= limits.maxCoOwners()) {
      return false;
    }
    shops.put(id, record.withCoOwnerAdded(target));
    return true;
  }

  /**
   * Removes a player from a shop's co-owners.
   *
   * <p>The owner alone may do this, for the reason {@link #addCoOwner} gives. The owner is not in
   * the co-owner list and so cannot be removed by this route — the design's "fire the owner" case
   * cannot arise, rather than being guarded against.
   *
   * @param id the shop
   * @param actor who is asking
   * @param target who is to be removed
   * @return whether the shop changed
   */
  public boolean removeCoOwner(ShopId id, UUID actor, UUID target) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(target, "target");

    ShopRecord record = shops.get(id);
    if (record == null || !record.roleOf(actor).mayManageOwners()) {
      return false;
    }
    if (!record.coOwners().contains(target)) {
      return false;
    }
    shops.put(id, record.withCoOwnerRemoved(target));
    return true;
  }

  /**
   * Opens or closes a shop.
   *
   * <p>Any stakeholder may do this, unlike the two above: the design gives co-owners everything the
   * owner has except changing who else may act, and whether the shop is trading is part of running
   * it.
   *
   * @param id the shop
   * @param actor who is asking
   * @param open whether it should be trading
   * @return whether the shop changed
   */
  public boolean setOpen(ShopId id, UUID actor, boolean open) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(actor, "actor");

    ShopRecord record = shops.get(id);
    if (record == null || !record.roleOf(actor).isStakeholder() || record.open() == open) {
      return false;
    }
    shops.put(id, record.withOpen(open));
    return true;
  }

  /**
   * @param placer who the new shop belongs to
   * @param minter where ids come from
   * @return an id no shop is using, registered to a new shop
   */
  private ShopId mintFresh(UUID placer, Supplier<ShopId> minter) {
    for (int attempt = 0; attempt < MINT_ATTEMPTS; attempt++) {
      ShopId candidate = Objects.requireNonNull(minter.get(), "the minter returned null");
      if (!shops.containsKey(candidate)) {
        shops.put(candidate, ShopRecord.placedBy(candidate, placer));
        return candidate;
      }
    }
    throw new IllegalStateException(
        "the minter produced an id already in use " + MINT_ATTEMPTS + " times running");
  }

  /**
   * @return the records as a list, for a codec to write out
   */
  public List<ShopRecord> toList() {
    return new ArrayList<>(shops.values());
  }
}
