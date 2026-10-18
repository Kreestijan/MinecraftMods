package io.github.kreestijan.alchemizer.core.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The shop rules, driven directly with no world anywhere near them.
 *
 * <p>These are the cases the design calls out and the ones it implies: that a first placement makes
 * a shop, that breaking and re-placing is a move rather than a new shop, and above all that a
 * <em>copy</em> of a Counter's item cannot reach into a shop that is already trading. That last one
 * is why this lives in {@code core}: it is a rule about a map, and testing it through a block
 * entity would mean building a world for every case.
 */
class ShopLedgerTest {

  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID OTHER_FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

  private static final ShopLimits LIMITS = ShopLimits.defaults();

  /** A minter the ledger can be handed, wrapping a predictable source of draws. */
  private static Supplier<ShopId> minter() {
    LongSupplier draws = counting();
    return () -> ShopId.mint(draws);
  }

  /** Draws 0, 1, 2, … so every minted id in a test is predictable and distinct. */
  private static LongSupplier counting() {
    AtomicLong next = new AtomicLong();
    return next::getAndIncrement;
  }

  // ------------------------------------------------------------ placing

  @Test
  void aFirstPlacementMintsAShopItsPlacerOwns() {
    ShopLedger ledger = new ShopLedger();

    PlacementOutcome outcome = ledger.claimOnPlace(null, OWNER, LIMITS, minter());

    assertEquals(PlacementOutcome.Kind.FRESH, outcome.kind());
    assertEquals(ShopRole.OWNER, ledger.roleOf(outcome.id(), OWNER));
    ShopRecord record = ledger.record(outcome.id()).orElseThrow();
    assertTrue(record.placed(), "the Counter is standing");
    assertFalse(
        record.open(),
        "a shop is closed when it is built; its owner opens it from the Counter's own screen,"
            + " which is the Open Shop control the addendum gives them");
    assertTrue(record.coOwners().isEmpty());
  }

  @Test
  void theItemCarryingNoIdIsTheOrdinaryCaseNotAnError() {
    ShopLedger ledger = new ShopLedger();

    PlacementOutcome first = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    PlacementOutcome second = ledger.claimOnPlace(null, OWNER, LIMITS, minter());

    assertNotEquals(first.id(), second.id(), "two counters are two shops");
    assertEquals(2, ledger.size());
  }

  @Test
  void breakingAndReplacingIsAMoveRatherThanANewShop() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);
    ledger.setOpen(placed.id(), OWNER, true);

    ledger.release(placed.id());
    assertFalse(ledger.record(placed.id()).orElseThrow().placed(), "the block is gone");

    PlacementOutcome again = ledger.claimOnPlace(placed.id(), OWNER, LIMITS, minter());

    assertEquals(PlacementOutcome.Kind.RESTORED, again.kind());
    assertEquals(placed.id(), again.id(), "the same shop, with the same name");
    assertEquals(1, ledger.size(), "and no second shop left behind");
    ShopRecord record = ledger.record(placed.id()).orElseThrow();
    assertEquals(List.of(FRIEND), record.coOwners(), "the co-owner came back");
    assertTrue(record.open(), "so did being open");
  }

  @Test
  void aShopThatWasNeverOpenedComesBackClosed() {
    // The state a shop spends its first moments in, and the one a move has to preserve: a Counter
    // that has been broken and put back is the same shop, so a shop nobody has opened is still
    // closed when it comes back rather than trading from the moment the block lands.
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());

    assertFalse(ledger.record(placed.id()).orElseThrow().open(), "a new shop is closed");
    ledger.release(placed.id());
    PlacementOutcome again = ledger.claimOnPlace(placed.id(), OWNER, LIMITS, minter());

    assertEquals(PlacementOutcome.Kind.RESTORED, again.kind());
    assertFalse(
        ledger.record(placed.id()).orElseThrow().open(),
        "and a shop nobody opened comes back closed");
  }

  @Test
  void aRestoredShopKeepsItsOriginalOwnerWhoeverPutsTheBlockBack() {
    // The owner is fixed. A co-owner rebuilding a broken counter moves it; it does not take it.
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);
    ledger.release(placed.id());

    ledger.claimOnPlace(placed.id(), FRIEND, LIMITS, minter());

    assertEquals(ShopRole.OWNER, ledger.roleOf(placed.id(), OWNER));
    assertEquals(ShopRole.CO_OWNER, ledger.roleOf(placed.id(), FRIEND));
  }

  // ------------------------------------------------------------ the duplicate rule

  @Test
  void aCopyOfALiveCountersItemGetsAShopOfItsOwn() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome original = ledger.claimOnPlace(null, OWNER, LIMITS, minter());

    PlacementOutcome copy = ledger.claimOnPlace(original.id(), STRANGER, LIMITS, minter());

    assertEquals(PlacementOutcome.Kind.DUPLICATE, copy.kind());
    assertNotEquals(original.id(), copy.id(), "a copy must not join a shop already trading");
    assertEquals(ShopRole.OWNER, ledger.roleOf(copy.id(), STRANGER), "the copier owns the copy");
    assertEquals(2, ledger.size());
  }

  @Test
  void theOriginalIsLeftExactlyAsItWas() {
    // The design's own words: the original is never touched. Asserted by taking a copy of the
    // record
    // before and comparing after, so any field drifting is caught rather than only the obvious
    // ones.
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome original = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.addCoOwner(original.id(), OWNER, FRIEND, LIMITS);
    ShopRecord before = ledger.record(original.id()).orElseThrow();

    ledger.claimOnPlace(original.id(), STRANGER, LIMITS, minter());

    assertEquals(before, ledger.record(original.id()).orElseThrow());
    assertEquals(ShopRole.OWNER, ledger.roleOf(original.id(), OWNER));
    assertEquals(ShopRole.CO_OWNER, ledger.roleOf(original.id(), FRIEND));
    assertEquals(
        ShopRole.STRANGER, ledger.roleOf(original.id(), STRANGER), "the copier holds nothing");
  }

  @Test
  void aBrokenCountersItemIsMovedRatherThanCopied() {
    // The same stack, but the original is no longer standing: this is the legitimate move, and the
    // difference between this test and the one above is the whole of the placement rule.
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome original = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.release(original.id());

    PlacementOutcome moved = ledger.claimOnPlace(original.id(), STRANGER, LIMITS, minter());

    assertEquals(PlacementOutcome.Kind.RESTORED, moved.kind());
    assertEquals(original.id(), moved.id());
    assertEquals(1, ledger.size());
  }

  @Test
  void anIdThisWorldHasNeverSeenIsKeptAndBelongsToWhoeverBroughtIt() {
    // A Counter item carried through a portal: shop state is stored per dimension, so the id is
    // real
    // but unknown here. Renaming it would silently detach the item from the shop it names.
    ShopLedger ledger = new ShopLedger();
    ShopId fromElsewhere = ShopId.mint(counting());

    PlacementOutcome outcome = ledger.claimOnPlace(fromElsewhere, OWNER, LIMITS, minter());

    assertEquals(PlacementOutcome.Kind.FRESH, outcome.kind());
    assertEquals(fromElsewhere, outcome.id());
    assertEquals(ShopRole.OWNER, ledger.roleOf(fromElsewhere, OWNER));
  }

  @Test
  void mintingStepsOverAnIdThatIsSomehowTaken() {
    // A collision should be impossible at 128 bits, so this is not really about collisions: it is
    // about a minter that hands back an id already in use. Two shops under one name would be worse
    // than a failed placement, so the taken id must be stepped over rather than re-registered.
    ShopLedger ledger = new ShopLedger();
    ShopId taken = ShopId.mint(counting()); // draws 0 then 1
    ledger.claimOnPlace(taken, OWNER, LIMITS, minter());

    // Offers the taken id first — the same two draws — and a free one afterwards.
    LongSupplier collidesOnce =
        new LongSupplier() {
          private final long[] draws = {0L, 1L, 9L, 9L};
          private int at;

          @Override
          public long getAsLong() {
            return draws[at++];
          }
        };

    PlacementOutcome second =
        ledger.claimOnPlace(null, FRIEND, LIMITS, () -> ShopId.mint(collidesOnce));

    assertNotEquals(taken, second.id(), "the taken id must not be handed out again");
    assertEquals(ShopRole.OWNER, ledger.roleOf(second.id(), FRIEND));
    assertEquals(2, ledger.size(), "and the original shop must still be the only one under its id");
  }

  @Test
  void aMinterThatNeverSettlesIsRefusedRatherThanLoopingForever() {
    ShopLedger ledger = new ShopLedger();
    ShopId fixed = ShopId.mint(counting());
    ledger.claimOnPlace(fixed, OWNER, LIMITS, () -> fixed);

    assertThrows(
        IllegalStateException.class, () -> ledger.claimOnPlace(fixed, OWNER, LIMITS, () -> fixed));
  }

  // ------------------------------------------------------------ roles

  @Test
  void aShopKnowsItsOwnerItsCoOwnersAndEveryoneElse() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);

    assertEquals(ShopRole.OWNER, ledger.roleOf(placed.id(), OWNER));
    assertEquals(ShopRole.CO_OWNER, ledger.roleOf(placed.id(), FRIEND));
    assertEquals(ShopRole.STRANGER, ledger.roleOf(placed.id(), STRANGER));
  }

  @Test
  void anUnknownShopHasNoStakeholders() {
    ShopLedger ledger = new ShopLedger();

    assertEquals(ShopRole.STRANGER, ledger.roleOf(ShopId.mint(counting()), OWNER));
    assertTrue(ledger.record(ShopId.mint(counting())).isEmpty());
  }

  @Test
  void releasingAShopNobodyKnowsIsNotAnError() {
    ShopLedger ledger = new ShopLedger();

    assertTrue(ledger.release(ShopId.mint(counting())).isEmpty());
  }

  // ------------------------------------------------------------ managing co-owners

  @Test
  void onlyTheOwnerMayAddACoOwner() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);

    assertFalse(ledger.addCoOwner(placed.id(), FRIEND, OTHER_FRIEND, LIMITS), "a co-owner may not");
    assertFalse(ledger.addCoOwner(placed.id(), STRANGER, OTHER_FRIEND, LIMITS), "nor a stranger");

    assertEquals(List.of(FRIEND), ledger.record(placed.id()).orElseThrow().coOwners());
  }

  @Test
  void onlyTheOwnerMayRemoveACoOwner() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);

    assertFalse(ledger.removeCoOwner(placed.id(), FRIEND, FRIEND), "a co-owner may not remove");
    assertTrue(ledger.removeCoOwner(placed.id(), OWNER, FRIEND));
    assertTrue(ledger.record(placed.id()).orElseThrow().coOwners().isEmpty());
  }

  @Test
  void addingSomeoneWhoIsAlreadyThereChangesNothing() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);

    assertFalse(ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS));
    assertEquals(List.of(FRIEND), ledger.record(placed.id()).orElseThrow().coOwners());
  }

  @Test
  void theOwnerIsNotACoOwnerAndCannotBeAddedAsOne() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());

    assertFalse(ledger.addCoOwner(placed.id(), OWNER, OWNER, LIMITS));
    assertEquals(ShopRole.OWNER, ledger.roleOf(placed.id(), OWNER));
    assertTrue(ledger.record(placed.id()).orElseThrow().coOwners().isEmpty());
  }

  @Test
  void aShopCannotBeGivenMoreCoOwnersThanItIsAllowed() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ShopLimits tight = new ShopLimits(2);

    for (int i = 0; i < 6; i++) {
      ledger.addCoOwner(
          placed.id(),
          OWNER,
          UUID.fromString("00000000-0000-0000-0000-0000000000" + (10 + i)),
          tight);
    }

    assertEquals(2, ledger.record(placed.id()).orElseThrow().coOwners().size());
  }

  @Test
  void coOwnersComeBackInTheOrderTheyWereAdded() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());

    ledger.addCoOwner(placed.id(), OWNER, OTHER_FRIEND, LIMITS);
    ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);

    assertEquals(
        List.of(OTHER_FRIEND, FRIEND), ledger.record(placed.id()).orElseThrow().coOwners());
  }

  // ------------------------------------------------------------ opening and closing

  @Test
  void aStakeholderMayOpenAndCloseTheShop() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);

    assertTrue(ledger.setOpen(placed.id(), FRIEND, true), "a co-owner may open the shop");
    assertTrue(ledger.record(placed.id()).orElseThrow().open());
    assertTrue(ledger.setOpen(placed.id(), OWNER, false), "and the owner may close it");
    assertFalse(ledger.record(placed.id()).orElseThrow().open());
  }

  @Test
  void aStrangerMayNotOpenOrCloseTheShop() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());
    ledger.setOpen(placed.id(), OWNER, true);

    assertFalse(ledger.setOpen(placed.id(), STRANGER, false), "a stranger may not close it");
    assertTrue(ledger.record(placed.id()).orElseThrow().open(), "and it is still open");
  }

  @Test
  void settingTheStateItIsAlreadyInChangesNothing() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());

    assertFalse(ledger.setOpen(placed.id(), OWNER, false), "a shop just placed is already closed");
    assertTrue(ledger.setOpen(placed.id(), OWNER, true), "and opening it is a change");
    assertFalse(ledger.setOpen(placed.id(), OWNER, true), "opening it again is not");
  }

  /**
   * The one-question form of opening, which the Counter's own tick asks every tick.
   *
   * <p>Its answer has to be the same one {@code record().orElseThrow().open()} gives, including for
   * a shop this world has never heard of — a Counter whose id came from another dimension has no
   * record here, and a shop that does not exist is not trading.
   */
  @Test
  void anOpenFlagCanBeAskedWithoutAskingForTheWholeShop() {
    ShopLedger ledger = new ShopLedger();
    PlacementOutcome placed = ledger.claimOnPlace(null, OWNER, LIMITS, minter());

    assertFalse(ledger.isOpen(placed.id()), "a shop just placed is closed");
    ledger.setOpen(placed.id(), OWNER, true);
    assertTrue(ledger.isOpen(placed.id()), "and it is open once its owner has opened it");
    assertFalse(
        ledger.isOpen(ShopId.mint(() -> 12345L)),
        "a shop this world has never heard of is not trading");
  }

  // ------------------------------------------------------------ bounds and rebuilding

  @Test
  void nonsenseLimitsAreRefusedByName() {
    assertThrows(IllegalArgumentException.class, () -> new ShopLimits(-1));
    assertThrows(
        IllegalArgumentException.class, () -> new ShopLimits(ShopLimits.MAX_CO_OWNERS_CEILING + 1));
  }

  @Test
  void aLedgerRebuiltFromItsRecordsBehavesLikeTheOneItCameFrom() {
    // What the save system does: take the records out, put them back, and the rules still hold.
    ShopLedger original = new ShopLedger();
    PlacementOutcome placed = original.claimOnPlace(null, OWNER, LIMITS, minter());
    original.addCoOwner(placed.id(), OWNER, FRIEND, LIMITS);
    original.setOpen(placed.id(), OWNER, false);

    ShopLedger restored = new ShopLedger(original.toList());

    assertEquals(original.records(), restored.records());
    assertEquals(ShopRole.CO_OWNER, restored.roleOf(placed.id(), FRIEND));
    assertEquals(
        PlacementOutcome.Kind.DUPLICATE,
        restored.claimOnPlace(placed.id(), STRANGER, LIMITS, minter()).kind(),
        "a rebuilt ledger still refuses to let a copy join a live shop");
  }
}
