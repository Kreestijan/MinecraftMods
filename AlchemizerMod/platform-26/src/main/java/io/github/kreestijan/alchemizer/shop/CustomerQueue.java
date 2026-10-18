package io.github.kreestijan.alchemizer.shop;

import io.github.kreestijan.alchemizer.core.flowfield.FlowField;
import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import io.github.kreestijan.alchemizer.core.pathing.PathWear;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.customer.Customer;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import io.github.kreestijan.alchemizer.shop.pathing.PathFlattening;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The line of people waiting at one Counter: the seats they stand in, and what their feet do.
 *
 * <p>{@code DESIGN.md} §5.2 asks customers to form a neat line along the route from the counter,
 * one block apart, each taking the next free slot, capped by config. This class is that line: it
 * holds the shop's seats, hands the first free one to whoever arrives, keeps the people behind a
 * gap moving up into it, and knows which cell each seat is — the first of those cells being the one
 * the shop's navigation field leads to, and the rest drawn straight out from it along the Counter's
 * face by {@link FlowField#laneFacing(int, int, int)}.
 *
 * <p><b>Seats are numbered from the face outward, and seat nought is the one being served.</b> Seat
 * 0 is the cell in front of the Counter, seat 1 the cell beyond it, and so on to the shop's cap.
 * That ordering is the whole of what makes a queue a queue: a customer knows its seat by its
 * number, the shop knows every seat's cell, and a customer may only ever stand in the cell the shop
 * gave it.
 *
 * <p><b>The line moves up.</b> When a seat falls empty, everybody behind it takes one step forward
 * into the gap and is told so, which is the tick that sets them walking into the cell that was
 * vacated. Nothing else moves them: a customer waiting its turn waits in its own cell, and the shop
 * decides when that cell becomes a different one. Today nothing but a departure takes the customer
 * in seat 0 away — there is no serving yet — so the line moves only when somebody leaves it.
 * <b>When serving lands, the served customer leaving is what will pull the whole line forward</b>
 * one seat, and that is the moment this pass exists for.
 *
 * <p><b>One of these belongs to one Counter, and only that Counter writes to it.</b> There is no
 * locking and no queue of changes, because a shop's arrivals and departures all happen on the
 * server thread inside the Counter's own tick: a seat is taken in the tick that spawns the customer
 * and given up in the tick that takes one away, and the step forward is taken in a tick of the same
 * thread, so two customers cannot be handed the same cell. That is the whole of the design's "the
 * Counter is the single writer" note, and it is the reason this is a plain array rather than
 * anything concurrent.
 *
 * <p><b>The line is as long as the ground in front of the shop.</b> A seat is a cell of the shop's
 * line, so a shop whose walkable ground offers only two cells in front of the Counter has two seats
 * rather than the four the config allows: the cap is the most that may wait, and somewhere to stand
 * is what makes one of them real. A field that has not been built yet — the first ticks after a
 * Counter is placed — has no line and therefore no seats, which is the same state as a wall built
 * across the shop's front.
 *
 * <p><b>Wear is counted here, for the people this class seats.</b> A customer's cell is read once
 * per cell it enters rather than every tick — the same saving the customer's own walking makes —
 * and the ground beneath it is handed to {@link PathWear}, which says when a cell has been walked
 * over enough to become {@link PathFlattening a path}. The wear itself lives on the Counter and is
 * forgotten with it.
 *
 * <p><b>Nothing here is saved.</b> The seats, the line and the wear are all in memory and are lost
 * when the Counter unloads or is broken, which is deliberate: a queue is a picture of the people
 * standing in it, and those people are gone too.
 */
public final class CustomerQueue {

  /** Who is standing in each seat, by its number along the line. Null means nobody. */
  private final Customer[] seats;

  /**
   * The cell the ground under each seat's customer was last seen at.
   *
   * <p>The ground rather than the customer's own cell, because that is the block wear is counted
   * against, and one determines the other: a customer standing in a cell is standing on the block
   * below it. Held so that a customer who has not moved counts once rather than once per tick —
   * which is most ticks, and the whole of why this is cheap.
   */
  private final RelativePos[] lastFootstep;

  /** The cells of the line, from the one in front of the counter outward. */
  private List<RelativePos> line = List.of();

  /**
   * Which finished field the line was drawn from.
   *
   * <p>Compared by identity rather than by value: a field is rebuilt wholesale when the ground near
   * a shop changes, so a different object is a different world and the line may have moved with it.
   */
  private @Nullable FlowField lineFrom;

  /**
   * Which way the Counter faced when the line was drawn.
   *
   * <p>Held as well as the field, because a redraw is two facts about the world rather than one:
   * the ground can be rebuilt and the Counter can be turned, and a line drawn for the old direction
   * would run across the face of a shop that now looks somewhere else. A field read again produces
   * the same object, so without this a turn would keep the line it had.
   */
  private @Nullable Direction lineFacing;

  /**
   * Builds the line of a shop that may hold as many people as the config allows.
   *
   * <p>The size is taken here rather than looked up per tick, because it is the length of the two
   * arrays below. A shop that is created before the config has been read holds the shipping number
   * and keeps it, which is the same rule the potion stack size follows: a change to it is a change
   * to the next Counter that is loaded rather than to the shops already standing.
   *
   * @param tuning the numbers the rules run on
   */
  public CustomerQueue(Tuning tuning) {
    this.seats = new Customer[tuning.customersPerShop()];
    this.lastFootstep = new RelativePos[tuning.customersPerShop()];
  }

  /**
   * Spends this shop's share of its Counter's tick.
   *
   * <p>Four jobs, all of them bounded by the shop's own number of seats and none of them doing any
   * work on the ticks where nothing has changed: redraw the line if the field has been rebuilt or
   * the Counter has been turned, let go of the seats of customers that have gone, move everybody
   * behind an empty seat one step up into it, and count one footstep for each customer that has
   * crossed into a cell it was not in a tick ago.
   *
   * @param level the level the Counter stands in
   * @param counter the Counter that owns this queue
   */
  public void tick(ServerLevel level, CounterBlockEntity counter) {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(counter, "counter");

    redraw(counter.pathField().current(), counter.facing());
    // In this order, and it matters: a seat whose customer has left the world is given up before
    // the line moves up, so the tick a customer leaves is the tick the person behind it starts
    // walking into the cell it left rather than the tick after.
    releaseTheSeatsOfTheGone();
    advanceTheLine();
    recordFootsteps(level, counter.getBlockPos(), counter.wear());
  }

  /**
   * The first free seat of the line, or empty when the shop is full.
   *
   * <p>Asked before a customer is put in the world rather than after, so that a shop with no room
   * left costs nothing: see {@code CustomerSpawner}. The seat is not held by the asking — {@link
   * #occupy(int, Customer)} is what takes it — because a caller that asks and then fails to place
   * its customer would otherwise have to remember to give the seat back.
   *
   * @return the number of the first seat along the line that nobody is in
   */
  public OptionalInt freeSeat() {
    for (int seat = 0; seat < line.size(); seat++) {
      if (seats[seat] == null) {
        return OptionalInt.of(seat);
      }
    }
    return OptionalInt.empty();
  }

  /**
   * Puts a customer in a seat.
   *
   * @param seat the seat, which {@link #freeSeat()} has just said is free
   * @param customer who is to stand there
   * @throws IllegalArgumentException when the seat number is not one this queue has
   * @throws IllegalStateException when that seat is already taken, which is a caller that has
   *     skipped {@link #freeSeat()} rather than a state the game can reach
   */
  public void occupy(int seat, Customer customer) {
    Objects.requireNonNull(customer, "customer");
    if (seat < 0 || seat >= seats.length) {
      throw new IllegalArgumentException(
          "seat must be between 0 and " + (seats.length - 1) + ", was " + seat);
    }
    if (seats[seat] != null) {
      throw new IllegalStateException("seat " + seat + " is already taken");
    }
    seats[seat] = customer;
  }

  /**
   * Gives up whatever seat a customer was standing in.
   *
   * <p>By the customer rather than by the seat number, so that every way out of the world — a
   * departure, a lost route, a night skip — frees the place without any of them having to remember
   * where they were standing. A customer this queue never seated is not an error: a customer that
   * was refused a seat has one to give up as much as any other.
   *
   * @param customer who is leaving
   */
  public void release(Customer customer) {
    Objects.requireNonNull(customer, "customer");
    for (int seat = 0; seat < seats.length; seat++) {
      if (seats[seat] == customer) {
        clearSeat(seat);
        return;
      }
    }
  }

  /**
   * @param seat a seat along the line
   * @return the cell that seat is, or empty when the line is shorter than that — which is a
   *     customer whose place in the queue the shop no longer has
   */
  public Optional<RelativePos> seatCell(int seat) {
    if (seat < 0 || seat >= line.size()) {
      return Optional.empty();
    }
    return Optional.of(line.get(seat));
  }

  /**
   * @return how many cells the line has, which is how many people may wait here: as many as the
   *     config's cap allows, or as many as the ground in front of the shop offers, whichever is
   *     fewer
   */
  public int lineLength() {
    return line.size();
  }

  /**
   * @param cell a cell of the level, as an offset from the Counter
   * @return which place in the line that cell is, or empty when it is not one of them
   */
  public OptionalInt laneIndex(RelativePos cell) {
    Objects.requireNonNull(cell, "cell");
    for (int seat = 0; seat < line.size(); seat++) {
      if (line.get(seat).equals(cell)) {
        return OptionalInt.of(seat);
      }
    }
    return OptionalInt.empty();
  }

  /**
   * @return how many seats are taken
   */
  public int occupiedCount() {
    int taken = 0;
    for (Customer seated : seats) {
      if (seated != null) {
        taken++;
      }
    }
    return taken;
  }

  /**
   * Sends everybody waiting here away, puffing.
   *
   * <p>The shop's own exit, used when there is nothing left to wait for: a Counter that has been
   * broken, a shop its owner has closed, or a shop whose last owner or co-owner has just left the
   * server. The seats are emptied before the customers are taken out of the world, so that a
   * departure which itself asks to give up a seat finds nothing to give up and nothing to confuse.
   *
   * @param level the level the customers are in
   */
  public void dismissAll(ServerLevel level) {
    Objects.requireNonNull(level, "level");
    for (int seat = 0; seat < seats.length; seat++) {
      Customer leaving = seats[seat];
      if (leaving == null) {
        continue;
      }
      clearSeat(seat);
      leaving.despawn(level);
    }
  }

  /**
   * Draws the line from the field and the Counter's facing as they now stand.
   *
   * <p>Seats survive a redraw: a customer keeps its number in the queue even when the ground under
   * the queue changes or the Counter is turned, and simply walks to wherever that number now is. A
   * line that loses a seat altogether leaves the customer who held it with nowhere to stand, which
   * that customer notices on its next tick and acts on by leaving.
   *
   * @param built the shop's current field, empty before one has ever been finished
   * @param facing which way the Counter faces, which is the direction the line runs in
   */
  private void redraw(Optional<FlowField> built, Direction facing) {
    if (built.isEmpty()) {
      lineFrom = null;
      lineFacing = null;
      line = List.of();
      return;
    }
    FlowField current = built.get();
    if (current == lineFrom && facing == lineFacing) {
      return;
    }
    lineFrom = current;
    lineFacing = facing;
    line = theLineOf(current, facing);
  }

  /**
   * The line of the shop whose field this is: its front, and the cells straight out from it.
   *
   * <p>The field's goal <em>is</em> the cell in front of the Counter — see {@code ShopPathField} —
   * so the first place in the queue is the goal itself and the rest are the cells {@link
   * FlowField#laneFacing(int, int, int)} walks beyond it. Drawing the line this way rather than
   * from the Counter is what keeps the customers and the field pointing at the same cell: a
   * customer whose place is the head of the line is standing where the field leads, so it has
   * arrived, and one whose place is further out has only to walk out along the line to find it.
   *
   * @param field the shop's current field
   * @param facing which way the Counter faces
   * @return the cells of the line, the first of them in front of the Counter
   */
  private List<RelativePos> theLineOf(FlowField field, Direction facing) {
    List<RelativePos> lane = new ArrayList<>(seats.length);
    lane.add(field.goal());
    lane.addAll(field.laneFacing(facing.getStepX(), facing.getStepZ(), seats.length - 1));
    return List.copyOf(lane);
  }

  /**
   * Moves everybody behind an empty seat one step up into it, and tells them.
   *
   * <p>This is the queue behaving like a queue rather than like a row of reserved places: the seat
   * in front of the Counter is the queue's head, and when it falls empty the person behind it walks
   * into it. The walk is the customer's own — the shop moves the <em>seat</em> and the customer
   * walks to where the seat now is, which is the same thing it does on the way in and the reason a
   * customer is never teleported into a cell it did not walk to.
   *
   * <p>A single ascending sweep, so a whole line shifts in one tick: the person moved into the
   * empty seat frees the one behind them as the sweep reaches it, which is why nobody has to be
   * moved twice and no gap is leapfrogged. Told, not asked — {@link Customer#setSeat(CustomerQueue,
   * int)} clears the customer's memory of the cell it is standing in, without which a waiting
   * customer would never notice its seat had changed and would stand where it was for ever.
   *
   * <p>Nothing at all happens on the ordinary tick, which is every tick of a shop whose queue is
   * standing still: one comparison per seat, and no work.
   */
  private void advanceTheLine() {
    for (int seat = 1; seat < seats.length; seat++) {
      Customer waiting = seats[seat];
      if (waiting == null) {
        continue;
      }
      int ahead = seat - 1;
      // A seat is only a seat if the line has a cell there: a line shortened by a wall going up in
      // front of the shop leaves its far numbers with nowhere to stand, and their customers leave
      // rather than being walked into a cell the shop no longer has.
      if (seats[ahead] != null || ahead >= line.size()) {
        continue;
      }
      seats[ahead] = waiting;
      lastFootstep[ahead] = lastFootstep[seat];
      clearSeat(seat);
      waiting.setSeat(this, ahead);
    }
  }

  /**
   * Frees the seats of customers that are no longer in the world.
   *
   * <p>A belt to the braces of {@link Customer#despawn}: a customer taken out by something this mod
   * does not control — a kill command, another mod, a chunk that unloaded and took it with it —
   * would otherwise hold its place for ever and leave the shop one seat short with nobody in it.
   */
  private void releaseTheSeatsOfTheGone() {
    for (int seat = 0; seat < seats.length; seat++) {
      Customer seated = seats[seat];
      if (seated != null && seated.isRemoved()) {
        clearSeat(seat);
      }
    }
  }

  /**
   * Counts one footstep for each customer that has entered a cell it was not in last tick.
   *
   * @param level the level the Counter stands in
   * @param counter where the Counter stands, which is what offsets are measured from
   * @param wear what this shop's customers have walked over
   */
  private void recordFootsteps(ServerLevel level, BlockPos counter, PathWear wear) {
    for (int seat = 0; seat < seats.length; seat++) {
      Customer walking = seats[seat];
      if (walking == null) {
        continue;
      }

      int x = Mth.floor(walking.getX());
      int y = Mth.floor(walking.getY());
      int z = Mth.floor(walking.getZ());
      RelativePos ground =
          new RelativePos(x - counter.getX(), y - counter.getY() - 1, z - counter.getZ());
      if (ground.equals(lastFootstep[seat])) {
        continue;
      }
      lastFootstep[seat] = ground;

      if (wear.recordPass(ground)) {
        PathFlattening.flatten(level, counter.offset(ground.x(), ground.y(), ground.z()));
      }
    }
  }

  /**
   * @param seat the seat to empty
   */
  private void clearSeat(int seat) {
    seats[seat] = null;
    lastFootstep[seat] = null;
  }
}
