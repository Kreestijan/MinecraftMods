package io.github.kreestijan.alchemizer.core.tuning;

import io.github.kreestijan.alchemizer.core.flowfield.FieldBounds;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.tank.TankMath;
import io.github.kreestijan.alchemizer.core.value.ValueFactors;
import java.util.ArrayList;
import java.util.List;

/**
 * The shipping values, and the bounds the config file is allowed to move them within.
 *
 * <p>The bounds live here rather than in the config definition so that a test can assert the
 * defaults sit inside them. A default outside its own declared range is the kind of bug that only
 * shows up on a player's machine, and it is cheap to rule out.
 */
public final class TuningDefaults {

  public static final int MACHINE_TIER_CAP = 4;
  public static final int MACHINE_TIER_CAP_MIN = 1;
  public static final int MACHINE_TIER_CAP_MAX = 4;

  public static final boolean FUEL_TIER_GATING_ENABLED = true;

  /**
   * How many potions share one stack.
   *
   * <p>Vanilla says one. This is the mod overriding that, and the ceiling is sixty-four rather than
   * anything larger because sixty-four is the number a player already reads as "a full stack".
   */
  public static final int POTION_STACK_SIZE = 64;

  public static final int POTION_STACK_SIZE_MIN = 1;
  public static final int POTION_STACK_SIZE_MAX = 64;

  public static final int TANK_CAPACITY_MB = TankMath.BASIC_TANK_CAPACITY_MB;
  public static final int TANK_CAPACITY_MB_MIN = TankMath.BUCKET_MB;
  public static final int TANK_CAPACITY_MB_MAX = 64 * TankMath.BUCKET_MB;

  public static final int DOSE_MB = TankMath.DOSE_MB;
  public static final int DOSE_MB_MIN = 1;
  public static final int DOSE_MB_MAX = TankMath.BUCKET_MB;

  public static final int SCAN_RADIUS = 4;
  public static final int SCAN_RADIUS_MIN = 1;
  public static final int SCAN_RADIUS_MAX = 8;

  /**
   * How long a run of alchemical tubing may be, counted in tubing blocks.
   *
   * <p>{@code DESIGN.md} §3.1 asks for a configurable maximum and gives no number. Eight is
   * generous next to the scan radius it is measured against: it allows a run that goes round a
   * corner or two and still leaves the far end in view, which is the point at which a longer limit
   * stops meaning anything. Zero is a legal setting and refuses tubing altogether.
   */
  public static final int MAX_TUBING_LENGTH = 8;

  public static final int MAX_TUBING_LENGTH_MIN = 0;
  public static final int MAX_TUBING_LENGTH_MAX = 16;

  public static final double BELLOWS_FACTOR = 0.9;
  public static final double BELLOWS_FACTOR_MIN = 0.5;
  public static final double BELLOWS_FACTOR_MAX = 1.0;

  public static final int MIN_BREW_TICKS = 20;
  public static final int MIN_BREW_TICKS_MIN = 1;
  public static final int MIN_BREW_TICKS_MAX = 20 * 60;

  public static final int FALLBACK_EFFECT_BASE_VALUE = 1;
  public static final int FALLBACK_EFFECT_BASE_VALUE_MIN = 0;
  public static final int FALLBACK_EFFECT_BASE_VALUE_MAX = 100;

  public static final double POTENCY_STEP = 0.25;
  public static final double POTENCY_STEP_MIN = 0.0;
  public static final double POTENCY_STEP_MAX = 2.0;

  public static final int DURATION_BASELINE_TICKS = 3600;
  public static final int DURATION_BASELINE_TICKS_MIN = 20;
  public static final int DURATION_BASELINE_TICKS_MAX = 20 * 60 * 10;

  public static final double DURATION_EXPONENT = 0.5;
  public static final double DURATION_EXPONENT_MIN = 0.1;
  public static final double DURATION_EXPONENT_MAX = 2.0;

  public static final boolean DEBUG_LOGGING = false;

  /**
   * How many customers one shop may have waiting at once.
   *
   * <p>{@code DESIGN.md} §5.2 has the queue capped by config and gives no number. Four is a line
   * short enough that the shop can serve it before the night skip takes it away.
   */
  public static final int CUSTOMERS_PER_SHOP = 4;

  public static final int CUSTOMERS_PER_SHOP_MIN = 1;
  public static final int CUSTOMERS_PER_SHOP_MAX = 16;

  /**
   * How long a shop waits between customers, in ticks.
   *
   * <p>Ten seconds. The design leaves the number open — it is one of the things reputation is meant
   * to move later — so the shipping value is a placeholder with a shape rather than a number that
   * means anything.
   */
  public static final int CUSTOMER_ARRIVAL_TICKS = 200;

  public static final int CUSTOMER_ARRIVAL_TICKS_MIN = 20;
  public static final int CUSTOMER_ARRIVAL_TICKS_MAX = 12000;

  /**
   * How long a customer with no usable route waits before leaving, in ticks.
   *
   * <p>Ten seconds, and it has two bounds to sit between: it has to outlast a navigation field
   * being rebuilt, which takes a few ticks, and it has to be short enough that a customer stranded
   * by a broken Counter is gone before the player walks back round to look at it. Zero is legal and
   * means a customer leaves the tick its route goes away.
   */
  public static final int CUSTOMER_GRACE_TICKS = 200;

  public static final int CUSTOMER_GRACE_TICKS_MIN = 0;
  public static final int CUSTOMER_GRACE_TICKS_MAX = 12000;

  /**
   * How far a shop's navigation field reaches horizontally from the Counter, in blocks.
   *
   * <p>Sixteen is the number the feature was built and measured at, and it is what {@code
   * PathInvalidation} searches: a field reaching further than the invalidation listener searched
   * would be a field that missed the wall going up at its edge. The ceiling is set where the
   * largest legal field — this radius with the tallest legal vertical reach — still fits inside
   * {@link FieldBounds}' own cell limit, which {@link #sanityCheck()} holds it to.
   */
  public static final int SHOP_FIELD_RADIUS = 16;

  public static final int SHOP_FIELD_RADIUS_MIN = FieldBounds.MIN_RADIUS;
  public static final int SHOP_FIELD_RADIUS_MAX = 64;

  /** How far below the Counter the field may reach, in blocks. */
  public static final int SHOP_FIELD_Y_BELOW = 8;

  /** How far above the Counter the field may reach, in blocks. */
  public static final int SHOP_FIELD_Y_ABOVE = 8;

  /**
   * The floor under both vertical reaches, which is a field that only covers the Counter's level.
   */
  public static final int SHOP_FIELD_VERTICAL_MIN = 0;

  public static final int SHOP_FIELD_VERTICAL_MAX = 16;

  /**
   * How many passes over one cell wear it into a path.
   *
   * <p>{@code DESIGN.md} §5.2 asks for a configurable threshold and gives no number; twenty passes
   * is a shop that has been busy for a while rather than a customer walking past it once, which is
   * the difference between a footpath and a divot.
   */
  public static final int PATH_WEAR_THRESHOLD = 20;

  public static final int PATH_WEAR_THRESHOLD_MIN = 1;
  public static final int PATH_WEAR_THRESHOLD_MAX = 200;

  /** Whether walking wears the ground into a path at all. */
  public static final boolean PATH_WEAR_ENABLED = true;

  /**
   * What walking onto a preferred path block costs the navigation field.
   *
   * <p>Weighted rather than free: cost is what the field's gradient is made of, and a zero-cost
   * cell is one the builder refuses outright. {@code DESIGN.md} §5.2 says the preferred blocks are
   * cheaper than the ordinary ones, so the ratio between this and {@link #PLAIN_PATH_COST} is the
   * number that means anything: at one to three, a detour along a path of up to three times the
   * straight-line distance is still the cheaper way round.
   */
  public static final int PREFERRED_PATH_COST = 1;

  public static final int PREFERRED_PATH_COST_MIN = 1;
  public static final int PREFERRED_PATH_COST_MAX = 100;

  /** What walking onto ordinary ground costs the navigation field. */
  public static final int PLAIN_PATH_COST = 3;

  public static final int PLAIN_PATH_COST_MIN = 1;
  public static final int PLAIN_PATH_COST_MAX = 100;

  /**
   * What a step that changes height costs on top of the ground it lands on.
   *
   * <p>Without it a staircase and a hillside are the same distance and customers zig-zag up a cliff
   * face. Deliberately small: a route that climbs one block is still worth taking to avoid a wall
   * twice as long. Zero is legal and makes a climb free.
   */
  public static final int CLIMB_SURCHARGE = 2;

  public static final int CLIMB_SURCHARGE_MIN = 0;
  public static final int CLIMB_SURCHARGE_MAX = 100;

  private TuningDefaults() {}

  /**
   * @return the hourglass base times, in the order of {@link HourglassPreset}
   */
  public static List<Integer> hourglassBaseSeconds() {
    List<Integer> seconds = new ArrayList<>(HourglassPreset.values().length);
    for (HourglassPreset preset : HourglassPreset.values()) {
      seconds.add(preset.baseSeconds());
    }
    return List.copyOf(seconds);
  }

  /**
   * @return the tuning the mod ships with
   */
  public static Tuning defaultTuning() {
    return new Tuning(
        MACHINE_TIER_CAP,
        FUEL_TIER_GATING_ENABLED,
        POTION_STACK_SIZE,
        TANK_CAPACITY_MB,
        DOSE_MB,
        SCAN_RADIUS,
        MAX_TUBING_LENGTH,
        hourglassBaseSeconds(),
        BELLOWS_FACTOR,
        MIN_BREW_TICKS,
        FALLBACK_EFFECT_BASE_VALUE,
        new ValueFactors(POTENCY_STEP, DURATION_BASELINE_TICKS, DURATION_EXPONENT),
        DEBUG_LOGGING,
        CUSTOMERS_PER_SHOP,
        CUSTOMER_ARRIVAL_TICKS,
        CUSTOMER_GRACE_TICKS,
        SHOP_FIELD_RADIUS,
        SHOP_FIELD_Y_BELOW,
        SHOP_FIELD_Y_ABOVE,
        PATH_WEAR_THRESHOLD,
        PATH_WEAR_ENABLED,
        PREFERRED_PATH_COST,
        PLAIN_PATH_COST,
        CLIMB_SURCHARGE);
  }

  /**
   * Builds the default tuning and fails loudly if it is not internally consistent.
   *
   * <p>Called once at mod construction. It costs nothing and it turns a broken default into a
   * startup error rather than a strange number in a player's game.
   *
   * @throws IllegalStateException if the shipping defaults are inconsistent
   */
  public static void sanityCheck() {
    Tuning tuning = defaultTuning();
    if (tuning.hourglassBaseSeconds().size() != HourglassPreset.values().length) {
      throw new IllegalStateException("hourglass defaults do not cover every preset");
    }
    if (tuning.doseMb() > tuning.tankCapacityMb()) {
      throw new IllegalStateException("a single dose does not fit in a single tank");
    }
    if (tuning.preferredPathCost() > tuning.plainPathCost()) {
      throw new IllegalStateException(
          "ordinary ground must not be cheaper than a preferred path, or the preference the design"
              + " asks for is a penalty");
    }
    // The largest field the config's own ranges allow has to be one the field type will build: a
    // radius and a vertical reach that are each legal but do not fit together would be a shop whose
    // Counter throws on every rebuild instead of admitting a customer. Constructed rather than
    // calculated, because the check belongs to the type that enforces it.
    new FieldBounds(SHOP_FIELD_RADIUS_MAX, SHOP_FIELD_VERTICAL_MAX, SHOP_FIELD_VERTICAL_MAX);
  }
}
