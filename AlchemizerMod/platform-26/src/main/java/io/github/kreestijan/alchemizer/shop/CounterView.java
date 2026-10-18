package io.github.kreestijan.alchemizer.shop;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the Counter screen draws, as the server tells it to.
 *
 * <p><b>Names, and therefore a payload.</b> Every other screen in this mod draws numbers, which fit
 * in the container synchroniser's int slots and need nothing else. This one draws a list of
 * players, and there is no arrangement of integers that is a name. So this is the mod's first
 * packet — and deliberately its only one in this direction: the client answers with button ids, as
 * it does everywhere else, so no serverbound payload was needed.
 *
 * <p><b>Facts only, no per-viewer flags.</b> One Counter can have two menus open on it at once, and
 * a synced value is one value. So this carries what is true — who owns the shop, who else may act,
 * who is online to be added, whether it is trading — and each screen works out from its own player
 * name which controls to draw. Putting "may I remove this one" in here would be a field that is
 * correct for whoever happened to receive it last, which is the kind of bug that shows up only when
 * two people are looking at the same block.
 *
 * <p>Both name lists are bounded by the codec rather than by hope. Everything here crosses a
 * network boundary, so the size is a hard cap on what a malformed or hostile sender can make a
 * client allocate — the same rule the recipe book's view follows.
 *
 * @param shopId the shop these names belong to
 * @param ownerName who placed the Counter
 * @param coOwnerNames the players the owner has added, in the order they were added
 * @param candidateNames players online now who are not already part of the shop
 * @param open whether the shop is trading
 */
public record CounterView(
    String shopId,
    String ownerName,
    List<String> coOwnerNames,
    List<String> candidateNames,
    boolean open) {

  /** The most names either list may carry, and so the most a client will allocate for one. */
  public static final int MAX_NAMES = 64;

  /** The longest a player name may be, which is Minecraft's own ceiling. */
  public static final int MAX_NAME_LENGTH = 16;

  public static final StreamCodec<RegistryFriendlyByteBuf, CounterView> STREAM_CODEC =
      StreamCodec.composite(
          ByteBufCodecs.stringUtf8(64),
          CounterView::shopId,
          ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH),
          CounterView::ownerName,
          ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).apply(ByteBufCodecs.list(MAX_NAMES)),
          CounterView::coOwnerNames,
          ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).apply(ByteBufCodecs.list(MAX_NAMES)),
          CounterView::candidateNames,
          ByteBufCodecs.BOOL,
          CounterView::open,
          CounterView::new);

  public CounterView {
    if (coOwnerNames.size() > MAX_NAMES || candidateNames.size() > MAX_NAMES) {
      throw new IllegalArgumentException(
          "a counter view carries at most " + MAX_NAMES + " names per list");
    }
    coOwnerNames = List.copyOf(coOwnerNames);
    candidateNames = List.copyOf(candidateNames);
  }

  /**
   * @return an empty view, for a Counter that has no shop yet
   */
  public static CounterView empty() {
    return new CounterView("", "", List.of(), List.of(), false);
  }
}
