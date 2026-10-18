package io.github.kreestijan.alchemizer.core.shop;

import java.util.Objects;

/**
 * What happened when a Counter was put down, and which shop it now belongs to.
 *
 * <p>The id is the point of returning anything at all. The item that was placed may have carried no
 * id, or an id this world has never seen, or the id of a shop that is standing somewhere else right
 * now — and in two of those three cases the block entity must end up holding an id that is not the
 * one it was given. Handing back the id that actually took effect is what keeps the caller from
 * having to reconstruct the rule that produced it.
 *
 * @param id the shop the Counter now belongs to
 * @param kind how that shop came to exist
 */
public record PlacementOutcome(ShopId id, Kind kind) {

  public PlacementOutcome {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(kind, "kind");
  }

  /** The three ways a Counter can come to belong to a shop. */
  public enum Kind {

    /**
     * A shop that did not exist until now: either the item carried no id, or it carried one this
     * world has no record of — which is what an item brought from another world looks like.
     */
    FRESH,

    /**
     * A shop that was standing before, in this world, and whose Counter was broken. Breaking and
     * re-placing is meant to be a move, not a new shop, so the record — its owner, its co-owners,
     * whether it is open — comes back exactly as it was.
     */
    RESTORED,

    /**
     * The id was already in use by a Counter that is standing right now. A copied item must not be
     * able to reach into a shop that is already trading, so the copy is given a shop of its own and
     * the original is left alone. This is the case the design calls out by name.
     */
    DUPLICATE
  }
}
