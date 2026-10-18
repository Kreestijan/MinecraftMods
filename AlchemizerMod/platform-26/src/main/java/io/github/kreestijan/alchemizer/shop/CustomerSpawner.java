package io.github.kreestijan.alchemizer.shop;

import io.github.kreestijan.alchemizer.core.flowfield.FlowField;
import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.customer.Customer;
import io.github.kreestijan.alchemizer.registry.ModEntities;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jetbrains.annotations.Nullable;

/**
 * Decides when a shop gets its next customer, and where they appear.
 *
 * <p>One of these belongs to one Counter and is driven by that Counter's own block entity tick,
 * which is what makes the cadence per shop rather than per world: two shops in the same village
 * arrive independently, and a shop that is loaded long after its neighbour does not inherit the
 * other's clock.
 *
 * <p><b>Every gate is a reason the design gives.</b> The shop has to be open (addendum A6's Open
 * Shop control). An owner or co-owner has to be online (A6 again: no shopkeeper, no trade). A field
 * has to exist with at least one reachable spawn cell, which is §5.2's rule that a customer only
 * ever appears where a route to the Counter has been <em>proved</em> to exist — and proved by
 * construction, because a spawn cell is one the field itself reached. And the shop's queue has to
 * have a seat free, because customers are not counted against the mob cap and the queue is the only
 * cap there is.
 *
 * <p><b>A shop that is not trading loses the customers it has.</b> The open gate is also the rule
 * for the people already waiting: a shop its owner has just closed has nobody to serve them, and
 * A6's assumption is that they leave the way they do at daybreak. It is asked on every tick rather
 * than once per arrival interval — see {@link #tick} — because a closed shop has no event of its
 * own and ten seconds of customers standing outside one is ten seconds of the mod looking broken.
 *
 * <p><b>The player-distance rule is a filter, not a gate.</b> A customer appearing in a player's
 * face is a jump scare rather than a shop; a cell closer than {@link #MIN_PLAYER_DISTANCE} to
 * anybody online is dropped from the list of places a customer may appear, and if that empties the
 * list nobody arrives this interval. It is not a refusal of the whole attempt: with a field of
 * sixteen blocks there is almost always somewhere further away.
 *
 * <p><b>Nothing here searches.</b> The spawn cells come out of a field that was built, a slice at a
 * time, by the Counter's own tick, and whether the shop is full is a look at the queue's own seats.
 * What is left is a set membership test per candidate, once per arrival interval rather than once
 * per tick.
 *
 * <p>Not thread-safe and not meant to be. It is touched from the Counter's server tick.
 */
public final class CustomerSpawner {

  /** PLACEHOLDER: how far from any player a customer has to appear, in blocks. */
  private static final double MIN_PLAYER_DISTANCE = 8.0;

  /** The same distance squared, because the comparison is made against squared distances. */
  private static final double MIN_PLAYER_DISTANCE_SQR = MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE;

  /** How long this shop waits between one customer and the next, in ticks. */
  private final int arrivalTicks;

  /** Ticks left before the next arrival may be considered. */
  private int countdown;

  /**
   * Builds the clock of a shop that arrives as often as the config says.
   *
   * <p>The interval is taken here rather than looked up on every tick, because the countdown is
   * reset to it on every arrival attempt: this is the one number in the class that is read more
   * than once per interval, and reading it once is what keeps the tick a subtraction.
   *
   * @param tuning the numbers the rules run on
   */
  public CustomerSpawner(Tuning tuning) {
    this.arrivalTicks = tuning.customerArrivalTicks();
    this.countdown = this.arrivalTicks;
  }

  /**
   * Spends this shop's share of its Counter's tick.
   *
   * <p><b>Whether the shop is trading is asked every tick; when the next customer arrives is a
   * countdown.</b> The two are separate because they answer to different clocks. A shop that its
   * owner has just closed — or one whose Counter has no shop behind it at all — has to lose the
   * people standing outside it <em>now</em>: the arrival interval is ten seconds, and asking the
   * same question on that clock meant a street of customers waiting outside a shut shop for up to
   * that long. The question is one lookup in the shop's own ledger, so asking it every tick costs a
   * subtraction and a map read rather than anything worth budgeting.
   *
   * <p>The other gate — whether anybody who could serve is online — stays on the interval, and that
   * is a decision rather than an oversight: it is a walk over the server's player list, which is
   * more expensive and answers to an event that already exists. Somebody signing off is what {@code
   * StakeholderLogout} is for, and it dismisses the affected shops the moment it happens; this is
   * the backstop for everything else.
   *
   * <p>The countdown is not reset when the attempt is refused, which is deliberate: a closed shop
   * and a shop with nowhere to appear both cost one attempt per interval rather than one per tick,
   * and the cost of a shut shop is a subtraction.
   *
   * @param level the level the Counter stands in
   * @param counter the Counter that owns this spawner
   */
  public void tick(ServerLevel level, CounterBlockEntity counter) {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(counter, "counter");

    if (!counter.trading()) {
      // A Counter with no shop, or a shop its owner has closed. Either way there is nothing here to
      // wait for, and A6's assumption is that the people already waiting leave the way they do at
      // daybreak rather than standing outside a shut shop until they lose their patience — which is
      // the same sentence as saying they leave when the shop closes, so they leave on this tick.
      counter.dismissCustomers(level);
      return;
    }

    if (--countdown > 0) {
      return;
    }
    countdown = arrivalTicks;
    arrive(level, counter);
  }

  /**
   * Runs the remaining gates and, if they all pass, puts one customer in the world.
   *
   * @param level the level the Counter stands in
   * @param counter the Counter that owns this spawner
   */
  private void arrive(ServerLevel level, CounterBlockEntity counter) {
    // The shop itself rather than the flag the tick above asked about, because these gates are
    // about its people. Read here rather than carried down from that call: this is one read per
    // arrival, and a shop that has gone away between the two statements is answered for by the next
    // tick, whose gate dismisses whatever it finds.
    ShopRecord shop = counter.record().orElse(null);
    if (shop == null) {
      return;
    }

    // Read once and used for two of the gates below: the players who may run the shop, and the
    // distance a customer has to keep from all of them. The list is the server's own rather than
    // anything a client had a hand in.
    List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
    if (!hasStakeholderOnline(players, shop)) {
      // Nobody who may trade at this shop is online, so nobody could serve anybody who arrived. The
      // logout event is the prompt version of this rule and this is the one that catches everything
      // else — a shop whose co-owners were all removed, say.
      counter.dismissCustomers(level);
      return;
    }

    Optional<FlowField> built = counter.pathField().current();
    if (built.isEmpty()) {
      return;
    }
    List<RelativePos> candidates = built.get().spawnCandidates();
    if (candidates.isEmpty()) {
      return;
    }

    // The shop's cap, asked of its queue rather than counted from the entities standing around it.
    // How many people may wait is the same number as how many seats the line has, and asking is the
    // only way for those two to be unable to disagree — a shop whose queue is full has refused one
    // arrival, and one whose line is shorter than the cap has, too.
    CustomerQueue queue = counter.queue();
    OptionalInt seat = queue.freeSeat();
    if (seat.isEmpty()) {
      return;
    }

    BlockPos counterPos = counter.getBlockPos();
    RelativePos cell = pick(level, counterPos, candidates, players);
    if (cell == null) {
      return;
    }

    // Placed at the centre of the cell, at the height the field says the customer's feet belong.
    // create() rather than spawn(), because the route and the seat have to be attached before the
    // customer is added: one tick of not knowing where it is going is a tick of walking nowhere.
    Customer customer =
        ModEntities.CUSTOMER
            .get()
            .create(
                level,
                spawned -> {
                  spawned.setRoute(counter.pathField(), counterPos);
                  spawned.setSeat(queue, seat.getAsInt());
                },
                counterPos.offset(cell.x(), cell.y(), cell.z()),
                EntitySpawnReason.EVENT,
                true,
                false);
    if (customer == null) {
      return;
    }
    queue.occupy(seat.getAsInt(), customer);
    level.addFreshEntity(customer);
  }

  /**
   * @param players who is online
   * @param shop the shop
   * @return whether any of them owns or co-owns it, which is addendum A6's condition for trading
   */
  private static boolean hasStakeholderOnline(List<ServerPlayer> players, ShopRecord shop) {
    for (int index = 0; index < players.size(); index++) {
      if (shop.isStakeholder(players.get(index).getUUID())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Picks one of the cells a customer may appear in.
   *
   * <p>Uniformly among the cells that are far enough from every player, which is a deliberate
   * choice: a customer is meant to come from somewhere out in the world rather than to materialise
   * at the nearest edge, and picking the furthest cell instead would put every arrival at the same
   * place, which looks like a spawner rather than like a village.
   *
   * @param level the level the Counter stands in
   * @param counterPos where the Counter stands
   * @param candidates the field's reachable edge cells
   * @param players who is online, none of whom the customer may appear next to
   * @return a cell, or null when every candidate is too close to somebody
   */
  private static @Nullable RelativePos pick(
      ServerLevel level,
      BlockPos counterPos,
      List<RelativePos> candidates,
      List<ServerPlayer> players) {
    List<RelativePos> acceptable = new ArrayList<>(candidates.size());
    for (int index = 0; index < candidates.size(); index++) {
      RelativePos cell = candidates.get(index);
      if (isClearOfEveryone(counterPos, cell, players)) {
        acceptable.add(cell);
      }
    }
    if (acceptable.isEmpty()) {
      return null;
    }
    return acceptable.get(level.getRandom().nextInt(acceptable.size()));
  }

  /**
   * @param counterPos where the Counter stands
   * @param cell a cell a customer might appear in
   * @param players who is online
   * @return whether that cell is at least {@link #MIN_PLAYER_DISTANCE} from every one of them
   */
  private static boolean isClearOfEveryone(
      BlockPos counterPos, RelativePos cell, List<ServerPlayer> players) {
    double x = counterPos.getX() + cell.x() + 0.5;
    double y = counterPos.getY() + cell.y();
    double z = counterPos.getZ() + cell.z() + 0.5;
    for (int index = 0; index < players.size(); index++) {
      if (players.get(index).distanceToSqr(x, y, z) < MIN_PLAYER_DISTANCE_SQR) {
        return false;
      }
    }
    return true;
  }
}
