package io.github.kreestijan.alchemizer.shop.pathing;

import io.github.kreestijan.alchemizer.core.flowfield.FieldBounds;
import io.github.kreestijan.alchemizer.core.flowfield.FlowField;
import io.github.kreestijan.alchemizer.core.flowfield.FlowFieldBuilder;
import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The navigation field of one Counter: the last good one, and the build of the next.
 *
 * <p>One of these belongs to one Counter and lives as long as its block entity does. It is the only
 * thing in the mod that owns a {@link FlowFieldBuilder}, and it exists so that nothing else has to:
 * whoever wants to know which way to walk asks {@link #current()} and gets the answer for free, and
 * the slow work of working that answer out happens here, a slice at a time.
 *
 * <p><b>The field leads to the shop's front, not to the Counter.</b> Its goal is the cell in front
 * of the Counter — the head of the shop's line — because the Counter's own cell is a block that
 * nobody stands in, and a field built from a cell no customer may enter has a hole at its centre. A
 * gradient cannot walk around a hole: a customer coming at the shop from behind descends to
 * whichever cell of the Counter's own ring its descent happens to touch, and stops there, which is
 * the opposite face as often as the front. Built from the front cell instead, the Counter is an
 * ordinary obstacle the field routes around, so a customer from any direction walks round the shop
 * and arrives at the head of the line.
 *
 * <p><b>A step budget, not a clock.</b> The build is advanced by a fixed number of cells per tick.
 * {@code AGENTS.md} §3 asks for work like this to be time-sliced, and the honest way to do it is to
 * bound the thing that is actually fixed: a cell costs the same on every machine, whereas a
 * millisecond budget makes the field's build time depend on the hardware and on whatever else the
 * tick is doing. A budget in cells means the same field takes the same number of ticks everywhere,
 * which is a property a test can assert.
 *
 * <p><b>The stale field is served while the next one is built.</b> Marking a field dirty does not
 * throw it away. A block changing near a shop is a wall going up or a path being laid, and the
 * customers already walking know a route that was good a moment ago; replacing it with nothing
 * would freeze every one of them until the build finished. So {@link #current()} keeps handing out
 * the old field until the new one is complete, and the change is picked up a few ticks later.
 *
 * <p><b>Dirty flag plus a cooldown.</b> Building a field is the most expensive thing this feature
 * does, and the event that dirties one fires on every block change in the level. A cooldown means a
 * player placing forty path blocks costs one rebuild rather than forty, and the builds are spaced
 * out rather than landing on consecutive ticks.
 *
 * <p>Not thread-safe and not meant to be. It is touched from the Counter's own server tick and from
 * the block-change listener, both of which run on the server thread.
 */
public final class ShopPathField {

  /**
   * PLACEHOLDER: how many cells one tick of building may settle.
   *
   * <p>A field over open ground is a little over a thousand cells, so this is a handful of ticks
   * for the ordinary case and a bound on the worst one. It is one field's share of a tick and there
   * may be one build per shop at once.
   */
  private static final int STEPS_PER_TICK = 512;

  /**
   * PLACEHOLDER: how long a dirtied field waits before it is rebuilt, in ticks.
   *
   * <p>Two seconds, which is longer than a player takes to place the next block of a path and
   * shorter than anyone will notice.
   */
  private static final int REBUILD_COOLDOWN_TICKS = 40;

  /**
   * The box every field of this shop covers, measured from the Counter at its centre.
   *
   * <p>Built from the config when the Counter is created rather than shared as a constant, because
   * the reach is one of the numbers the design leaves to the server owner. {@code PathInvalidation}
   * looks for Counters within the same radius, so a field reaching further than the listener
   * searched would be a field that missed the wall being built at its edge.
   */
  private final FieldBounds bounds;

  /** What a step that changes height costs, which is the field's own share of the config. */
  private final int climbSurcharge;

  /** What the field charges for a preferred path block, and for everything else. */
  private final int preferredPathCost;

  private final int plainPathCost;

  /** The last field that finished building, or null when none ever has. */
  private @Nullable FlowField field;

  /** The build in progress, or null when there is not one. */
  private @Nullable FlowFieldBuilder building;

  /**
   * Which way the Counter faced when the field was built, or null before it ever was.
   *
   * <p>Held because the field leads to the shop's front, and which cell that is follows the
   * Counter's facing. A Counter that has been turned has a different front, so the field that led
   * to the old one is not merely stale — it is a route to the wrong side of the shop.
   */
  private @Nullable Direction builtFacing;

  /**
   * Whether something near the Counter has changed since the last build started.
   *
   * <p>True to begin with, and that is not a default so much as a statement: a field that has never
   * been built is out of date by definition, so a Counter placed in a fresh chunk starts building
   * on its first tick rather than waiting for a block to change near it.
   */
  private boolean dirty = true;

  /** Ticks left before a dirtied field may start rebuilding; zero when it may. */
  private int cooldown;

  /**
   * @param tuning the numbers the rules run on
   */
  public ShopPathField(Tuning tuning) {
    this.bounds =
        new FieldBounds(
            tuning.shopFieldRadius(), tuning.shopFieldYBelow(), tuning.shopFieldYAbove());
    this.climbSurcharge = tuning.climbSurcharge();
    this.preferredPathCost = tuning.preferredPathCost();
    this.plainPathCost = tuning.plainPathCost();
  }

  /**
   * @return the most recent finished field, which may be out of date while a new one is built
   */
  public Optional<FlowField> current() {
    return Optional.ofNullable(field);
  }

  /**
   * Says that the world around the Counter is no longer what the field was built from.
   *
   * <p>Deliberately does nothing but set a flag and start a clock. What changed is not known here —
   * it could be one block or a hundred — and a rebuild per change is exactly what the cooldown
   * exists to prevent.
   */
  public void markDirty() {
    dirty = true;
    if (cooldown == 0) {
      cooldown = REBUILD_COOLDOWN_TICKS;
    }
  }

  /**
   * Spends this Counter's share of the tick on the field.
   *
   * <p>Called once per tick from the Counter's own block entity tick. It either starts a build, or
   * advances the one already running, or does nothing at all — and the nothing case is the one that
   * has to be cheap, because it is the case on almost every tick.
   *
   * @param level the level the Counter stands in
   * @param origin the Counter's own position, which the field's cells are measured from
   * @param facing which way the Counter faces, and therefore which cell the field leads to
   */
  public void tick(ServerLevel level, BlockPos origin, Direction facing) {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(facing, "facing");

    if (facing != builtFacing) {
      // A Counter that has been turned leads somewhere else. The old field is not kept to serve in
      // the meantime, which the staleness rule above does everywhere else: a field whose goal is
      // now
      // the back of the shop is not an out-of-date answer to the right question, it is an answer to
      // a question nobody asked. The rebuild starts at once rather than after the cooldown, because
      // the cooldown is there for a player placing forty blocks and this is one block changing.
      builtFacing = facing;
      field = null;
      building = null;
      dirty = true;
      cooldown = 0;
    }

    if (cooldown > 0) {
      cooldown--;
    }

    if (building == null) {
      if (!dirty || cooldown > 0) {
        return;
      }
      // The view captures the level and the origin and is thrown away with the build, so a Counter
      // that is broken and put back does not leave this field reading through a stale origin.
      LevelNavView view = new LevelNavView(level, origin, preferredPathCost, plainPathCost);
      RelativePos front = frontOf(facing);
      if (view.entryCostAt(front).isEmpty()) {
        // The shop's front is inside something — a block built against the Counter's face. There is
        // nothing to lead anybody to and no cell in front of the shop for a line to start in, so
        // there is no field at all: the shop has no line, takes nobody in, and anybody already
        // walking here loses their route and leaves. The field that was standing is thrown away
        // rather than served on, because this is not the ordinary staleness — the ground near the
        // shop changing leaves a route that is out of date, while a front that has become a wall
        // leaves one that leads into it. Not permanent either: taking the block away is a change
        // near the Counter like any other, and it dirties this again.
        field = null;
        dirty = false;
        return;
      }
      building = new FlowFieldBuilder(front, bounds, view, climbSurcharge);
      dirty = false;
      return;
    }

    if (building.advance(STEPS_PER_TICK)) {
      field = building.result();
      building = null;
    }
  }

  /**
   * @param facing which way the Counter faces
   * @return the cell in front of it, as an offset from the Counter
   */
  private static RelativePos frontOf(Direction facing) {
    return new RelativePos(facing.getStepX(), 0, facing.getStepZ());
  }
}
