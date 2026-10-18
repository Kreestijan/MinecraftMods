package io.github.kreestijan.alchemizer.shop.menu;

import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;

/**
 * The handful of numbers a Counter screen needs, read straight from the shop.
 *
 * <p>This exists instead of the block entity implementing {@link ContainerData} itself, for the
 * reason given in {@code IgnitionChamberData}: the indices below are the screen's business, and a
 * block entity that had to carry them would change whenever a gauge moved.
 *
 * <p><b>Everything here is a number, and the names are not.</b> The lists a Counter draws are names
 * of players, and there is no arrangement of integers that is a name — so the names travel in their
 * own payload (see {@code CounterNetwork}) and this carries only what an integer can hold. The two
 * halves are read together by the screen and mean different things: these are live, and the names
 * are as current as the last time the server sent them.
 *
 * <p><b>Why the viewer is held here.</b> The role is the one number in this view that is about the
 * person reading it rather than about the shop, and a data slot belongs to one menu, which belongs
 * to one player. Holding the viewer's id is what keeps the role honest when two people have the
 * same Counter open: each is told the part they play, and neither is told the other's.
 *
 * <p>Only the server ever holds one of these. The client's menu is handed a plain mutable store of
 * the same length and the server's values are written into it by the container synchroniser, which
 * is why {@link #set} is unreachable here rather than implemented.
 */
public final class CounterData implements ContainerData {

  /** One when a shop stands at this Counter, zero when it has not been claimed. */
  public static final int HAS_SHOP = 0;

  /** One while the shop is trading, zero while it is closed. */
  public static final int OPEN = 1;

  /** How many players the owner has added. */
  public static final int CO_OWNER_COUNT = 2;

  /**
   * How many players are online and could be added, which is the size of the list the owner's add
   * buttons index.
   *
   * <p>The names behind this number arrive in their own payload, and that payload is sent when the
   * menu opens and when the shop changes — not every tick. A player who logs in while a Counter is
   * open therefore moves this number before the screen has a row to draw for them, which is the
   * honest reading: the count is live and the list is as old as the last send.
   */
  public static final int CANDIDATE_COUNT = 3;

  /** The ordinal of the viewer's {@code ShopRole}, which is what the screen draws controls from. */
  public static final int ROLE = 4;

  /** How many numbers this view has; also the size of the client's store. */
  public static final int COUNT = 5;

  private final CounterBlockEntity counter;

  private final UUID viewer;

  /**
   * @param counter the Counter whose shop this view reports
   * @param viewer who is looking at it, which is what makes the role below their own
   */
  public CounterData(CounterBlockEntity counter, UUID viewer) {
    this.counter = Objects.requireNonNull(counter, "counter");
    this.viewer = Objects.requireNonNull(viewer, "viewer");
  }

  @Override
  public int get(int dataId) {
    return switch (dataId) {
      case HAS_SHOP -> counter.shopId().isPresent() ? 1 : 0;
      case OPEN -> counter.record().map(ShopRecord::open).orElse(false) ? 1 : 0;
      case CO_OWNER_COUNT -> counter.record().map(shop -> shop.coOwners().size()).orElse(0);
      case CANDIDATE_COUNT -> candidateCount();
      case ROLE -> counter.roleOf(viewer).ordinal();
      default ->
          throw new IndexOutOfBoundsException(
              "Counter data has " + COUNT + " entries, got " + dataId);
    };
  }

  /**
   * Unreachable on purpose: every number above is derived from the shop, and none of them is
   * something a client may say.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void set(int dataId, int value) {
    throw new UnsupportedOperationException(
        "Counter data is read-only; the client is sent data id " + dataId);
  }

  @Override
  public int getCount() {
    return COUNT;
  }

  /**
   * Counts the players the owner could add, without building the list they would be added from.
   *
   * <p>A share of {@link CounterMenu#candidates} rather than a call to it, because this is read
   * once per data slot per tick and the list is built once per menu open: counting is a walk over
   * the online players with nothing allocated, where building is a sorted copy of them. The two
   * have to agree — {@link CounterMenu#isCandidate} is the one place the rule itself is written —
   * and a number that disagreed with the list would put a header above rows that never appear.
   *
   * @return how many online players hold nothing in this shop yet
   */
  private int candidateCount() {
    if (!(counter.getLevel() instanceof ServerLevel server)) {
      return 0;
    }
    ShopRecord shop = counter.record().orElse(null);
    if (shop == null) {
      return 0;
    }

    int count = 0;
    for (ServerPlayer player : server.getServer().getPlayerList().getPlayers()) {
      if (CounterMenu.isCandidate(shop, player.getUUID())) {
        count++;
      }
    }
    return count;
  }
}
