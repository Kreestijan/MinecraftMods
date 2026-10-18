package io.github.kreestijan.alchemizer.customer;

import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.flowfield.FlowField;
import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import io.github.kreestijan.alchemizer.shop.CustomerQueue;
import io.github.kreestijan.alchemizer.shop.pathing.ShopPathField;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Someone who has come to the shop and is walking to the Counter.
 *
 * <p>{@code DESIGN.md} §5.2: customers arrive along a route proved to exist and walk it. This class
 * is the walking half of that, and the whole of its job is to turn the answer the shop's own
 * navigation field already holds into one steering order per block travelled.
 *
 * <p><b>It does not pathfind, and the navigation is left alone on purpose.</b> Every other mob in
 * the game is steered by a goal selector asking a {@code PathNavigation} for a route, and every one
 * of those routes is an A* search over vanilla's own idea of the world. This mod's design says the
 * opposite: one field is computed per shop, it is shared by every customer that shop ever has, and
 * the answer to "which way do I go" is a lookup in it. So {@link #registerGoals()} is empty, no
 * goal is ever added, and nothing here ever calls {@code moveTo}. Because {@code
 * PathNavigation.isDone()} is true whenever no path was ever set, the navigation ticks as a no-op:
 * no search, no {@code Path} and nothing allocated. That is not a detail — it is the feature's cost
 * model, and it is asserted by a game test.
 *
 * <p><b>The whole of its thinking is one block.</b> A customer's route only ever changes when it
 * arrives somewhere, and arriving somewhere means changing the cell it is standing in. So the field
 * is read once per cell entered and not once per tick: on the four ticks in five where a walking
 * customer has not crossed a cell boundary, that half of the tick is three floors and a comparison.
 * It reads the field once per cell, takes one step from it, and works out one steering order. No
 * searches, no entity scans, no allocation beyond the handful of small values the field's own API
 * hands back.
 *
 * <p><b>The steering order is repeated every tick, and it has to be.</b> The other half of the tick
 * is one call, and it is there because {@code MoveControl} is not a heading but a destination that
 * is consumed: it turns the order it is given into a single tick's worth of movement and then drops
 * back to {@code WAIT}, whose branch zeroes the entity's forward input. A customer told once where
 * to walk therefore walks for one tick — five thousandths of a block, measured — and then stands
 * still inside a cell it will never leave, so it is never told again. The order is re-issued from
 * the cell the customer is already walking to, which is what a vanilla goal does by asking again on
 * every tick it runs, and costs one comparison and one call rather than another field lookup.
 *
 * <p><b>It walks to its seat and stands in it, in two legs.</b> {@code DESIGN.md} §5.2's queue is a
 * line of customers one block apart, and the Counter hands each arrival the first free cell of its
 * shop's own line. The journey therefore ends at a <em>cell</em> rather than at the Counter, and it
 * is walked as two things. Off the line, the customer follows the field's gradient, which leads to
 * the cell in front of the Counter — the head of the line — and has to route around the Counter to
 * get there, because the Counter's own cell is not a place anybody may stand. On the line, it walks
 * along the line to its own place, one cell at a time, which is the only direction that reaches a
 * cell whose whole meaning is its number along that line. It then stops the moment it is standing
 * in it: arrival is one {@code equals} against the cell the shop named, so "has this customer
 * arrived?" has one answer and a customer cannot stop early somewhere that merely happens to be as
 * close to the shop as its seat is.
 *
 * <p><b>Its seat moves, and it walks again when it does.</b> The shop moves the line up whenever a
 * seat falls empty — see {@code CustomerQueue} — and tells each customer whose seat changed. That
 * is the event this customer's tick cannot see for itself, because a waiting customer never crosses
 * a cell boundary; {@link #setSeat(CustomerQueue, int)} is how it hears, and the tick that follows
 * finds it standing outside a seat that is no longer the one the shop named, so it walks into the
 * cell that was vacated.
 *
 * <p><b>A customer that cannot take up the place it was given leaves.</b> A field that has been
 * thrown away, a Counter that has been broken, a cell the field does not reach, or a place in the
 * line the shop no longer has all mean the same thing: there is nothing left for it to walk to.
 * Rather than standing in the road forever — and rather than being left standing wherever it
 * happens to be, holding a place in a line it is not in — the customer counts those ticks and
 * vanishes once the count passes the grace the config names. The grace exists for one reason: a
 * field is rebuilt from scratch when something near the shop changes, and a rebuild takes a few
 * ticks during which there is no current field at all. Without the grace, every rebuild would empty
 * the street.
 *
 * <p><b>It poses no danger</b> (§5.3). It has no goals, no attack damage and no equipment, it
 * cannot be reasoned with, and it never fights back. Its whole interaction with the world is
 * walking, looking at the Counter, and leaving.
 */
public class Customer extends PathfinderMob {

  /**
   * What the steering order is scaled by.
   *
   * <p>One, so that the speed actually walked is the movement speed the entity's attributes declare
   * — see {@link #createAttributes()}. The attribute is the tunable; this is only the multiplier a
   * drive-by decision would use, and the mod makes none.
   */
  private static final double WALK_SPEED_MODIFIER = 1.0;

  /** How high up the Counter a customer looks, which is the middle of the block. */
  private static final double COUNTER_LOOK_HEIGHT = 1.0;

  /**
   * How high off the ground a departing customer's puff appears, which is about its chest.
   *
   * <p>The same number as the look height above, and a different decision: one is where the
   * Counter's middle is and one is where a person is. Two constants rather than one, so that
   * changing either is a change to the thing it is about.
   */
  private static final double PUFF_HEIGHT = 1.0;

  /** PLACEHOLDER: how many puff particles a departing customer leaves. */
  private static final int PUFF_COUNT = 8;

  /** PLACEHOLDER: how far a departing customer's puff is spread. */
  private static final double PUFF_SPREAD = 0.4;

  /**
   * A cell no position can ever be in, so that the first tick always counts as a change of cell.
   *
   * <p>Cheaper than a null check and impossible to get wrong: the customer's cell is the floor of
   * its coordinates, and no coordinate floors to the smallest int.
   */
  private static final int NO_CELL = Integer.MIN_VALUE;

  /**
   * A place in the queue no shop has given out, so that a customer always has a useless default.
   */
  private static final int NO_SEAT = -1;

  /** The shop's field, or null before a route has been given. */
  private @Nullable ShopPathField route;

  /** Where the Counter stands, or null before a route has been given. */
  private @Nullable BlockPos counter;

  /** The shop's queue, or null before a route has been given. */
  private @Nullable CustomerQueue queue;

  /**
   * Which place in that queue is this customer's.
   *
   * <p>{@link #NO_SEAT} until the shop gives it one, which is a state a customer spawned into the
   * world by something other than the shop's own spawner can be in. Such a customer has nothing to
   * walk to and leaves the way a customer with no route does.
   */
  private int seat = NO_SEAT;

  /** The cell the customer was last seen in, as three numbers so that reading it costs nothing. */
  private int lastX = NO_CELL;

  private int lastY = NO_CELL;
  private int lastZ = NO_CELL;

  /** How many ticks running the route has been missing or useless. */
  private int lostTicks;

  /**
   * The world position the customer was last told to walk to: the middle of the next cell along.
   *
   * <p>Held so that the order can be repeated without asking the field again. {@code MoveControl}
   * consumes the order it is given — its tick reads the wanted position, turns it into one tick's
   * worth of speed, and drops back to {@code WAIT}, whose branch zeroes the mob's forward input —
   * so an entity told where to walk once walks for exactly one tick and then stands still. Every
   * goal in the game re-issues its order on the ticks it runs, which is what keeps its mob moving;
   * these three numbers are the same order, repeated, and repeating it from here rather than from
   * the field is what keeps the cost of a walking customer at one call per tick.
   */
  private double stepX;

  private double stepY;
  private double stepZ;

  /**
   * Whether the last decision was to walk to {@link #stepX} at all.
   *
   * <p>False for a customer standing in its place in the line and for one that has lost its route:
   * neither has an order worth repeating. A customer standing in the cell it was given is where it
   * meant to be and has nothing left to walk to; a customer that repeats the order that took it
   * there would walk out of its own place and back in again, which is exactly the shuffle the whole
   * of this class exists to avoid.
   */
  private boolean walking;

  /**
   * @param type the entity type, which the loader supplies
   * @param level the level the customer is in
   */
  public Customer(EntityType<? extends PathfinderMob> type, Level level) {
    super(type, level);
  }

  /**
   * PLACEHOLDER: a customer's numbers.
   *
   * <p>Every value here is a placeholder standing in for a decision the design has not made yet,
   * and they are grouped in one place so that the day it does, one method changes. Health and the
   * four values around it describe a creature that cannot fight, cannot be pushed, and takes
   * nothing from falling, which is the "poses no danger" rule expressed as numbers.
   *
   * @return the attribute builder for {@code EntityAttributeCreationEvent}
   */
  public static AttributeSupplier.Builder createAttributes() {
    return createMobAttributes()
        .add(Attributes.MAX_HEALTH, 20.0)
        .add(Attributes.MOVEMENT_SPEED, 0.28)
        .add(Attributes.STEP_HEIGHT, 1.0)
        .add(Attributes.FOLLOW_RANGE, 0.0)
        .add(Attributes.ATTACK_DAMAGE, 0.0)
        .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
        .add(Attributes.SAFE_FALL_DISTANCE, 4.0);
  }

  /**
   * Gives the customer the shop it is walking to.
   *
   * <p>Called once, by whoever put the customer in the world. The field is held directly rather
   * than looked up per tick, because looking it up means finding the Counter's block entity, and
   * that is a chunk lookup on the walking path of every customer in the world.
   *
   * @param route the shop's field
   * @param counter where the Counter stands, which is where the field leads
   */
  public void setRoute(ShopPathField route, BlockPos counter) {
    this.route = Objects.requireNonNull(route, "route");
    // Snapshotted: the caller is usually a spawner holding a mutable cursor of its own.
    this.counter = Objects.requireNonNull(counter, "counter").immutable();
  }

  /**
   * Tells the customer which place in the shop's line is its.
   *
   * <p>Called twice in a customer's life, and the two calls are the same statement. The first is
   * whoever put it in the world, immediately after building it and before it is added: a customer
   * that walked one tick without knowing where it was going would take a step toward the Counter,
   * which is exactly the step the queue exists to replace. The second is the shop itself, every
   * time the line moves up and this customer's place becomes the empty one in front of it — see
   * {@code CustomerQueue}, whose whole job at the head of the line is to say so.
   *
   * <p><b>The cell the customer is standing in is forgotten here, and that is not bookkeeping.</b>
   * A customer's tick reads the field only when it has crossed into a different cell, so that a
   * customer walking a block spends most of its ticks on three floors and a comparison — and a
   * customer <em>waiting</em> in its place never crosses a cell at all. Its place changing is
   * therefore exactly the event its tick cannot see: without this, a customer told to move up a
   * place would stand in the old one for ever, because as far as its own tick was concerned nothing
   * about the world had changed. Clearing the memory is what makes the next tick look again.
   *
   * @param queue the shop's line of waiting people
   * @param seat which seat of it is this customer's
   * @throws IllegalArgumentException when the seat is not a seat number at all
   */
  public void setSeat(CustomerQueue queue, int seat) {
    this.queue = Objects.requireNonNull(queue, "queue");
    if (seat < 0) {
      throw new IllegalArgumentException("a seat number must not be negative, was " + seat);
    }
    this.seat = seat;
    lastX = NO_CELL;
    lastY = NO_CELL;
    lastZ = NO_CELL;
  }

  /**
   * @return where the Counter of the shop this customer came for stands, or empty when it has not
   *     been told which shop it is walking to
   */
  public Optional<BlockPos> counter() {
    return Optional.ofNullable(counter);
  }

  /**
   * @param where a Counter stands
   * @return whether this customer came for the shop whose Counter stands there
   */
  public boolean isHeadingFor(BlockPos where) {
    // An equals-and-null check rather than an Optional of the destination: this is asked once per
    // arrival interval for every customer near a shop, and a box of them is not worth allocating
    // one wrapper each.
    return where.equals(counter);
  }

  /**
   * Does nothing, deliberately. See the class javadoc: a customer is steered by the shop's field
   * and never by a goal.
   */
  @Override
  protected void registerGoals() {}

  @Override
  protected void customServerAiStep(ServerLevel level) {
    ShopPathField field = route;
    BlockPos goal = counter;
    CustomerQueue shop = queue;
    if (field == null || goal == null || shop == null || seat < 0) {
      loseRoute(level);
      return;
    }

    int x = Mth.floor(getX());
    int y = Mth.floor(getY());
    int z = Mth.floor(getZ());
    if (lostTicks == 0 && x == lastX && y == lastY && z == lastZ) {
      // The customer has not crossed a cell boundary since the last tick, so the field would give
      // the same answer it gave then. This is the common case and the whole of the field's cost
      // model: a customer spends most of its ticks inside a cell it has already been told about.
      //
      // The steering order is repeated anyway, and that is a correctness rule rather than the
      // optimisation it looks like it should be. MoveControl consumes the order it is handed: it
      // sets one tick's worth of speed and drops back to WAIT, whose branch zeroes the entity's
      // forward input. So a customer told where to walk once and then left alone walks for one tick
      // — measured at five thousandths of a block — and then stands still inside a cell it is never
      // going to leave, which means the field is never asked again either. Repeating the order from
      // the cell the customer is already walking into is what keeps it walking, and it costs one
      // boolean and one call where asking the field again would cost a lookup and an allocation.
      //
      // Only when the last answer was a good one, though. A customer that has lost its route is not
      // moving, so its cell is not changing either, and skipping the work here would be skipping
      // the countdown that takes it out of the world.
      if (walking) {
        getMoveControl().setWantedPosition(stepX, stepY, stepZ, WALK_SPEED_MODIFIER);
      }
      return;
    }
    lastX = x;
    lastY = y;
    lastZ = z;

    Optional<FlowField> current = field.current();
    if (current.isEmpty()) {
      // A field is rebuilt a slice at a time, so there are ticks during which a shop has none.
      loseRoute(level);
      return;
    }
    FlowField built = current.get();

    Optional<RelativePos> place = shop.seatCell(seat);
    if (place.isEmpty() || !built.reaches(place.get())) {
      // The shop's line has no such place, or the field cannot lead to it: the ground near the shop
      // changed under the queue, or the shop is holding fewer places than it has taken people in.
      // The shop's own line is the only place a customer may wait, so it does not wait here at all
      // —
      // it gives the place up and leaves, which is the same refusal a lost route gets.
      loseRoute(level);
      return;
    }
    RelativePos mine = place.get();

    RelativePos here = new RelativePos(x - goal.getX(), y - goal.getY(), z - goal.getZ());
    if (!built.reaches(here)) {
      // This cell is not on the field at all — a customer standing in a wall, or in a chunk that
      // was loaded when the field was built and is not now.
      loseRoute(level);
      return;
    }

    lostTicks = 0;
    getLookControl()
        .setLookAt(goal.getX() + 0.5, goal.getY() + COUNTER_LOOK_HEIGHT, goal.getZ() + 0.5);

    if (here.equals(mine)) {
      // Standing in the cell the shop gave it, which is where this customer's journey ends: seat 0
      // is the customer being served, and everybody else is waiting for the seat in front of them.
      // It walks again the moment its seat changes, because the shop says so and this tick then
      // finds itself somewhere other than its seat.
      walking = false;
      getMoveControl().setWait();
      return;
    }

    OptionalInt at = shop.laneIndex(here);
    Optional<RelativePos> step;
    if (at.isPresent()) {
      // On the shop's line, so the rest of the journey is along it: one place at a time, in the
      // direction of this customer's own place. That is the whole of walking the queue — the cells
      // are one block apart by construction, and the place it is walking to is the cell its number
      // now stands for.
      int standingAt = at.getAsInt();
      step = shop.seatCell(standingAt < seat ? standingAt + 1 : standingAt - 1);
    } else {
      // Not on the line yet. The field leads to the shop's front, which is the head of the line,
      // and
      // the Counter is an obstacle it routes around rather than the hole at its centre it used to
      // be: a customer that came at the shop from behind walks round it and joins the line at the
      // front rather than stopping wherever its descent first met ground as near the Counter as its
      // place is.
      step = built.nextStepFrom(here);
    }
    if (step.isEmpty()) {
      // Nowhere to go: a cell the field cannot walk out of, which is a customer boxed in by
      // something the field did not know about when it was built.
      loseRoute(level);
      return;
    }

    RelativePos nextStep = step.get();
    stepX = goal.getX() + nextStep.x() + 0.5;
    stepY = goal.getY() + nextStep.y();
    stepZ = goal.getZ() + nextStep.z() + 0.5;
    walking = true;
    getMoveControl().setWantedPosition(stepX, stepY, stepZ, WALK_SPEED_MODIFIER);
  }

  /**
   * Puffs once and takes the customer out of the world, leaving nothing behind.
   *
   * <p>The design's clean departure, in one place, so that the night skip and a lost route look the
   * same from outside. There are no drops to suppress: the entity type has no loot table at all —
   * see {@code ModEntities}.
   *
   * @param level the level the customer is in
   */
  public void despawn(ServerLevel level) {
    // First, because this is the one method every way out of the world goes through: the shop's
    // place in its line is given up here rather than at each of the callers, so a departure, a lost
    // route, a night skip and a shop that has closed all leave the queue as they found it.
    CustomerQueue shop = queue;
    if (shop != null) {
      shop.release(this);
    }

    level.sendParticles(
        ParticleTypes.POOF,
        getX(),
        getY() + PUFF_HEIGHT,
        getZ(),
        PUFF_COUNT,
        PUFF_SPREAD,
        PUFF_SPREAD,
        PUFF_SPREAD,
        0.0);
    discard();
  }

  /**
   * @param distanceToClosestPlayer how far away the nearest player is
   * @return false, always: how long a customer lives is this mod's decision and nobody else's
   */
  @Override
  public boolean removeWhenFarAway(double distanceToClosestPlayer) {
    return false;
  }

  /**
   * Nobody shoves a customer, and that is the queue's rule rather than a detail of the entity.
   *
   * <p>A customer's place in the line is a cell, and a customer standing in its cell is standing
   * where the shop put it: another customer walking past on its way to a place further out must not
   * be able to move it, or the line would be a scrum rather than a queue, and the one being shunted
   * would be shoved toward the Counter and up its side. Vanilla's pushing is also what made a
   * customer end up standing on the Counter: a step of one block is ordinary in this game, so a
   * customer pressed against the Counter's side climbed it, and a customer standing on top of the
   * shop is on no cell of the field at all.
   *
   * <p>The design says the same thing from the other side: {@code KNOCKBACK_RESISTANCE} is set to
   * one so that a player cannot knock a customer out of its place, and this is the other half of it
   * — a player who walks into a customer is stopped by it rather than able to shove it aside.
   *
   * @return false, always: a customer is not moved by anything that pushes
   */
  @Override
  public boolean isPushable() {
    return false;
  }

  /**
   * @return true, always: a customer is not a wild spawn and is not cleaned up like one
   */
  @Override
  public boolean requiresCustomPersistence() {
    return true;
  }

  /**
   * Does nothing, deliberately. Vanilla's own despawning is switched off here rather than argued
   * with: a customer leaves when the shop is done with it, when the night is skipped, or when its
   * route is gone.
   */
  @Override
  public void checkDespawn() {}

  /**
   * Counts one tick without a usable route, and leaves when the count runs out.
   *
   * @param level the level the customer is in
   */
  private void loseRoute(ServerLevel level) {
    // Nothing to repeat while there is no route: the order the customer holds was worked out from
    // a field that is no longer the one it would be given, and repeating it would be repeating a
    // guess. The next tick that has a field sets a fresh one.
    walking = false;
    // The grace is a config value rather than a field of this entity, and it is read here because
    // this is the one tick that asks for it: a customer that is walking never reads it at all.
    if (++lostTicks >= ConfigCache.current().customerGraceTicks()) {
      despawn(level);
    }
  }
}
