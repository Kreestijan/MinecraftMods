package io.github.kreestijan.alchemizer.gametest;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.flowfield.FieldBounds;
import io.github.kreestijan.alchemizer.core.flowfield.FlowField;
import io.github.kreestijan.alchemizer.core.flowfield.FlowFieldBuilder;
import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;
import io.github.kreestijan.alchemizer.customer.Customer;
import io.github.kreestijan.alchemizer.customer.NightSkip;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModEntities;
import io.github.kreestijan.alchemizer.registry.ModItems;
import io.github.kreestijan.alchemizer.shop.CustomerQueue;
import io.github.kreestijan.alchemizer.shop.ShopData;
import io.github.kreestijan.alchemizer.shop.block.CounterBlock;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import io.github.kreestijan.alchemizer.shop.pathing.LevelNavView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.ClockAdjustment;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-game tests for the customer: the walking, the field it walks on, and the fact that it is not
 * pathfinding at all.
 *
 * <p>The field's own arithmetic is unit-tested in {@code core}, against a map of costs, in a
 * millisecond. What only a world can show is the seam: that reading real blocks produces a field
 * that reaches across real ground, that a customer steered by one actually moves toward the Counter
 * and does so without a vanilla path, and that a Counter with no way to reach it has nowhere for a
 * customer to appear. Those are the three things this class began with, and the fourth is the
 * ground rule itself.
 *
 * <p>The rest of it is the other half of §5.2: the queue that customers form, and the path their
 * feet wear into the ground. That a shop hands out its places one at a time and turns the next
 * customer away once they are all taken; that the people it has seated stand in a line one block
 * apart; and that walking over one cell often enough turns it into a dirt path — unless something
 * is standing on it, which is left where the player put it.
 *
 * <p>And the two things that make that line a queue rather than a row of reserved places: that it
 * leaves the Counter the way the Counter faces — which only a world can show, because the block
 * state, the block entity's reading of it and the line drawn from it are three links of one chain —
 * and that a customer whose neighbour walks away moves up into the cell that neighbour left.
 *
 * <p><b>Why the tests build their own floor, and build it where they do.</b> A test body runs in a
 * copy of the world with nothing in it but the template and a shell of barriers one block thick
 * around the plot — see {@link #FLOOR_Y} for what that shell costs a feature whose whole business
 * is deciding where something can stand. Laying the pavement from code, one layer above the shell,
 * means the field's answers are assertions about this test's blocks and not about the world's, and
 * it means the shape being measured is written next to the assertions about it.
 *
 * <p><b>Why nothing here is asserted on the tick it was set up.</b> A field is built a slice at a
 * time by the Counter's own tick, so on the tick a Counter is placed there is no field at all.
 * Every assertion that depends on one is made from {@code runAfterDelay}, which is the same
 * structure the feature itself has to live with.
 */
public final class CustomerGameTests {

  /**
   * Test bodies, keyed by name.
   *
   * <p>Held here rather than in a constants class for the reason {@code StillGameTests} gives:
   * these are not game content, and nothing reads them except the game test runner.
   */
  private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
      DeferredRegister.create(Registries.TEST_FUNCTION, Alchemizer.MOD_ID);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> WALKS =
      function("customer_walks_to_the_counter", CustomerGameTests::walksToTheCounter);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      NO_PATHFINDING = function("customer_does_not_pathfind", CustomerGameTests::doesNotPathfind);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      PATH_IS_GROUND = function("customer_walks_a_preferred_path", CustomerGameTests::prefersAPath);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      WALLED_IN =
          function("walled_in_shop_spawns_nobody", CustomerGameTests::aWalledInShopSpawnsNobody);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      QUEUE_IS_CAPPED = function("the_queue_is_capped", CustomerGameTests::theQueueIsCapped);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      QUEUE_IS_A_LINE =
          function(
              "queued_customers_are_one_block_apart",
              CustomerGameTests::queuedCustomersAreOneApart);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      WEARS_INTO_A_PATH =
          function(
              "walked_over_ground_becomes_a_path", CustomerGameTests::walkedOverGroundBecomesAPath);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      A_TORCH_STOPS_THE_WEAR =
          function("a_torch_stops_the_wear", CustomerGameTests::aTorchStopsTheWear);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      FACES_THE_PLAYER =
          function("counter_faces_the_player", CustomerGameTests::counterFacesThePlayer);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      QUEUES_THE_WAY_IT_FACES =
          function("counter_queues_along_its_facing", CustomerGameTests::queuesAlongItsFacing);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      THE_LINE_MOVES_UP =
          function("a_place_freed_ahead_pulls_the_line_up", CustomerGameTests::theLineMovesUp);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      THE_LINE_IS_WALKED_INTO =
          function(
              "every_queued_customer_stands_on_the_line",
              CustomerGameTests::everyQueuedCustomerStandsOnTheLine);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      A_SHORT_LINE_SEATS_NOBODY_OFF_IT =
          function(
              "a_line_shorter_than_the_cap_seats_nobody_off_it",
              CustomerGameTests::aLineShorterThanTheCapSeatsNobodyOffIt);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      CLOSING_CLEARS_THE_STREET =
          function(
              "closing_the_shop_clears_the_street_at_once",
              CustomerGameTests::closingTheShopClearsTheStreetAtOnce);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      THE_NIGHT_CLOSES_THE_SLEEPERS_SHOPS =
          function(
              "a_nights_sleep_closes_the_shops_of_whoever_slept",
              CustomerGameTests::aNightsSleepClosesTheShopsOfWhoeverSlept);

  /** Vanilla's blank template — {@code data/minecraft/structure/empty.nbt}. */
  private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

  /** How long the framework waits after placing the template before calling the body. */
  private static final int SETUP_TICKS = 1;

  /**
   * Enough ticks for the shorter tests to finish and say so. The body finishes when its own delayed
   * check does, so this is headroom rather than a budget.
   */
  private static final int MAX_TICKS = 200;

  /** How long the shorter tests wait before asking whether the customer has walked anywhere. */
  private static final int WALK_TICKS = 100;

  /**
   * How many ticks a customer is given to cross the street and stand in its place.
   *
   * <p>A budget rather than a target: the walk is two blocks, a customer covers a block in a dozen
   * ticks or so, and what this is for is a slow machine rather than the ordinary case. A test that
   * runs out of it reports where the customer got to, which is the only way to tell a customer that
   * walked slowly from one that never set off.
   */
  private static final int ARRIVE_TICKS = 150;

  /**
   * How long a customer that has arrived somewhere is given to come to a stop there.
   *
   * <p>Stopping is not instant when the steering is right: a customer told to wait carries on
   * sliding for a few ticks while the ground friction takes its speed away, which is exactly the
   * behaviour that makes this test fail if the customer is still walking for any other reason.
   */
  private static final int SETTLE_TICKS = 10;

  /**
   * How fast a customer that is standing still may still be moving, as a distance squared per tick.
   *
   * <p>Not zero, because a customer standing on the ground is moved a little by it, and a test that
   * asserted exactly zero would be a test that failed on the tick the entity was nudged.
   */
  private static final double STILL_SPEED_SQR = 1.0E-4;

  /**
   * Longer, for the test that has to wait out an arrival interval: a shop's first attempt is at
   * {@code CustomerSpawner}'s own interval, which is hundreds of ticks rather than dozens.
   */
  private static final int ARRIVAL_MAX_TICKS = 400;

  /** When the walled-in test looks, which is after the first arrival attempt has been and gone. */
  private static final int ARRIVAL_TICKS = 260;

  /**
   * Where the pavement goes, which is two blocks above the template's own layer.
   *
   * <p>Not one, and the reason is worth writing down. The framework does not hand a test an empty
   * world: it seals the plot in a shell of barrier blocks one block thick on every side of the
   * template, so with vanilla's one-block {@code empty} template the entire neighbourhood is
   * barrier and the template's own block is the only air for a block around. That is invisible to a
   * test that only ever places blocks it cares about — which is why every other test in this mod is
   * written that way — and it is fatal to one that wants a customer to walk, because a barrier is
   * solid, and the field refuses to route through it. Building the street one layer higher puts it
   * in the open air above the shell, which has a second benefit: the world there is empty rather
   * than superflat, so the field covers exactly the pavement this test laid and nothing else.
   */
  private static final int FLOOR_Y = 2;

  /**
   * The Counter stands on the pavement, with the cells a customer walks through at its own level.
   */
  private static final BlockPos COUNTER = new BlockPos(0, FLOOR_Y + 1, 0);

  /**
   * Where a customer is put down in the tests that watch one walk: three blocks short of the shop.
   */
  private static final BlockPos ARRIVAL_CELL = new BlockPos(3, FLOOR_Y + 1, 0);

  /**
   * How often a test asks whether the shop has built its field yet, in ticks.
   *
   * <p>Asked rather than waited out, because how long a field takes depends on how much walkable
   * world is around the shop: the world a game test runs in is a whole world, and a field reaches
   * sixteen blocks into it. What matters to these tests is the queue, not the build, so they ask
   * and carry on the moment the answer is yes.
   */
  private static final int FIELD_POLL_TICKS = 2;

  /** How many times a test asks before it gives up on the field ever arriving. */
  private static final int FIELD_TRIES = 150;

  /**
   * The budget for a test that has to wait for a field: the field, and then its own work.
   *
   * <p>Generous next to {@link #FIELD_TRIES}, which is what actually decides when waiting has gone
   * on too long — a test that runs out of ticks is a test with a budget problem, and one that gives
   * up on the field has a message saying so.
   */
  private static final int FIELD_MAX_TICKS = 600;

  /**
   * Where the street ends, in blocks east of the Counter.
   *
   * <p>Six: four cells for a whole four-seat line and, past the far end of it, the four cells the
   * queue tests put their customers down in — one each, the last of them on the street's last cell.
   */
  private static final int STREET_END = 6;

  /** How long the queue tests leave between sending one customer in and the next. */
  private static final int QUEUE_SPACING_TICKS = 10;

  /**
   * The queue tests' budget: a field, four arrivals with a gap between them, and the walk in.
   *
   * <p>The longest of them, because it is the only one that waits for four customers to arrive and
   * settle rather than for one to move.
   */
  private static final int QUEUE_MAX_TICKS = 900;

  /**
   * How many ticks a shop that has just been closed is given to be empty of customers.
   *
   * <p>Far less than one arrival interval — which is the point of the number — and more than the
   * single tick the shop's own clock needs, so that a server busy with something else on that tick
   * is not the reason a correct shop looks broken.
   */
  private static final int CLOSING_TICKS = 5;

  /**
   * The budget for the test that walks a whole queue in from around the shop: a field, and then
   * four arrivals, each of which is waited out before the next is put down.
   */
  private static final int WALK_IN_MAX_TICKS = 1500;

  /**
   * Where the queue-walking test puts its customers down: one per arrival, none of them in front of
   * the shop.
   *
   * <p>Around rather than in front, because that is the case the queue has to get right: a customer
   * that arrives along the street walks straight down the line to its place, and a customer that
   * arrives at the side or the back has to walk round the Counter and join the line at its head.
   * Cells rather than a walk, because a customer's arrival is the shop's business and the walk is
   * this test's.
   */
  private static final List<BlockPos> AROUND_THE_SHOP =
      List.of(
          new BlockPos(-1, FLOOR_Y + 1, 0),
          new BlockPos(0, FLOOR_Y + 1, -1),
          new BlockPos(0, FLOOR_Y + 1, 1),
          new BlockPos(-1, FLOOR_Y + 1, 1));

  /** How many passes a wear test walks a customer over each of its two cells. */
  private static final int WEAR_PASSES = TuningDefaults.PATH_WEAR_THRESHOLD;

  private CustomerGameTests() {}

  /**
   * @param modEventBus the bus this mod's registration events arrive on
   */
  public static void register(IEventBus modEventBus) {
    TEST_FUNCTIONS.register(modEventBus);
  }

  /**
   * Gives the bodies above something to be run as.
   *
   * <p>Each test is registered with its own tick budget rather than sharing one, because the
   * walled-in test has to wait for an arrival attempt that the others have no reason to.
   *
   * @param event the game test registries, open for this mod to add to
   */
  public static void onRegisterGameTests(RegisterGameTestsEvent event) {
    Holder<TestEnvironmentDefinition<?>> environment =
        event.registerEnvironment(Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "customer"));

    register(event, environment, WALKS, FIELD_MAX_TICKS);
    register(event, environment, NO_PATHFINDING, FIELD_MAX_TICKS);
    register(event, environment, PATH_IS_GROUND, MAX_TICKS);
    register(event, environment, WALLED_IN, ARRIVAL_MAX_TICKS);
    register(event, environment, QUEUE_IS_CAPPED, QUEUE_MAX_TICKS);
    register(event, environment, QUEUE_IS_A_LINE, QUEUE_MAX_TICKS);
    register(event, environment, WEARS_INTO_A_PATH, FIELD_MAX_TICKS);
    register(event, environment, A_TORCH_STOPS_THE_WEAR, FIELD_MAX_TICKS);
    register(event, environment, FACES_THE_PLAYER, MAX_TICKS);
    register(event, environment, QUEUES_THE_WAY_IT_FACES, FIELD_MAX_TICKS);
    register(event, environment, THE_LINE_MOVES_UP, QUEUE_MAX_TICKS);
    register(event, environment, THE_LINE_IS_WALKED_INTO, WALK_IN_MAX_TICKS);
    register(event, environment, A_SHORT_LINE_SEATS_NOBODY_OFF_IT, QUEUE_MAX_TICKS);
    register(event, environment, CLOSING_CLEARS_THE_STREET, FIELD_MAX_TICKS);
    register(event, environment, THE_NIGHT_CLOSES_THE_SLEEPERS_SHOPS, MAX_TICKS);
  }

  // ------------------------------------------------------------------ the bodies

  /**
   * A customer on reachable ground walks to its place in the shop's line and stops there.
   *
   * <p><b>The assertion is arrival, and it used to be drift.</b> This test once asked only that the
   * distance to the Counter had fallen, which a customer's own steering satisfied by moving a few
   * thousandths of a block and stopping — the ordering it is given is consumed by {@code
   * MoveControl} and never repeated, so it walked for one tick and then stood still forever. The
   * distance fell, the test passed, and the feature it names did not work. What is asserted now is
   * the thing §5.2 actually asks for: the customer is standing in the cell the shop gave it, and it
   * has come to a stop there rather than walking on toward the Counter or sliding past its place.
   *
   * <p>Arrival is asked for rather than waited out at a fixed tick, so a machine that walks the
   * customer slowly is a slower pass rather than a failure, and the cell is read from the shop's
   * own line rather than written down here, so a customer standing in the wrong cell fails this
   * test even when it has stopped in it.
   *
   * <p>The customer is put down once the shop has a field, because a customer is given a place in
   * the shop's line when it arrives and a shop that has not built a field yet has no line to be
   * given a place in.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void walksToTheCounter(GameTestHelper helper) {
    paveTheFloor(helper);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          Customer customer = putACustomerDown(helper, counter, ARRIVAL_CELL);

          assertTheCustomerReaches(helper, customer, seatInTheWorld(counter, 0), ARRIVE_TICKS);
        });
  }

  /**
   * Waits for a customer to stand in a cell, and then that it has stopped moving.
   *
   * <p>One tick at a time rather than in a lump, because what is being tested is that the customer
   * arrives at all: a test that looked once after a fixed wait would be asking whether the walk is
   * faster than the wait, and would call a customer that is still on its way a customer that never
   * comes.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param customer the customer to watch
   * @param where the cell it has to end up in
   * @param ticksLeft how many more ticks it may take
   */
  private static void assertTheCustomerReaches(
      GameTestHelper helper, Customer customer, BlockPos where, int ticksLeft) {
    assertTheCustomerReaches(helper, customer, where, ticksLeft, helper::succeed);
  }

  /**
   * Waits for a customer to stand in a cell and stop, and then carries on with the test.
   *
   * <p>The continuation is what lets a test send one customer in and wait for it before sending the
   * next: a queue is built one arrival at a time, and a customer that is still walking is a
   * customer that would collide with the one behind it.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param customer the customer to watch
   * @param where the cell it has to end up in
   * @param ticksLeft how many more ticks it may take
   * @param then what to do once it is standing there, still
   */
  private static void assertTheCustomerReaches(
      GameTestHelper helper, Customer customer, BlockPos where, int ticksLeft, Runnable then) {
    helper.assertTrue(
        !customer.isRemoved(),
        Component.literal("a customer walking to its place at " + where + " left the world"));
    if (customer.blockPosition().equals(where)) {
      // Arrived. It is given a few ticks to come to a stop, because a customer told to wait this
      // tick is still moving at the speed it had, and the whole point of the place in the line is
      // that it stops in it rather than walking through it or on past it.
      helper.runAfterDelay(
          SETTLE_TICKS,
          () -> {
            helper.assertTrue(
                customer.blockPosition().equals(where),
                Component.literal(
                    "a customer that has reached its place at "
                        + where
                        + " must stop in it, but it is now at "
                        + customer.blockPosition()));
            helper.assertTrue(
                customer.getDeltaMovement().horizontalDistanceSqr() < STILL_SPEED_SQR,
                Component.literal(
                    "a customer in its place must be standing still, but it is moving at "
                        + customer.getDeltaMovement()));
            then.run();
          });
      return;
    }

    helper.assertTrue(
        ticksLeft > 0,
        Component.literal(
            "a customer given a place at "
                + where
                + " must walk into it, but "
                + (ARRIVE_TICKS - ticksLeft)
                + " ticks after it was put down it is still at "
                + customer.blockPosition()
                + ", "
                + Math.sqrt(
                    customer.distanceToSqr(where.getX() + 0.5, where.getY(), where.getZ() + 0.5))
                + " blocks away from it"));
    helper.runAfterDelay(
        1, () -> assertTheCustomerReaches(helper, customer, where, ticksLeft - 1, then));
  }

  /**
   * @param counter the shop's Counter
   * @param seat a place in the shop's line
   * @return that place as a block position in the world
   */
  private static BlockPos seatInTheWorld(CounterBlockEntity counter, int seat) {
    RelativePos cell = theCellOfTheShop(counter, seat);
    return counter.getBlockPos().offset(cell.x(), cell.y(), cell.z());
  }

  /**
   * @param counter the shop's Counter
   * @param seat a place in the shop's line
   * @return that place as a cell of the template, which is how a test addresses a block it puts
   *     down or measures
   */
  private static BlockPos seatInTheTemplate(CounterBlockEntity counter, int seat) {
    RelativePos cell = theCellOfTheShop(counter, seat);
    return new BlockPos(cell.x(), FLOOR_Y + 1 + cell.y(), cell.z());
  }

  /**
   * Reads a place in the shop's line, rather than writing its cell down in a test.
   *
   * <p>The cell a place is is the shop's answer and the thing these tests are about, so a test that
   * wrote one down would be asserting its own arithmetic back at itself — and would keep passing
   * after the line moved.
   *
   * @param counter the shop's Counter
   * @param seat a place in the shop's line
   * @return that place's cell, as an offset from the Counter
   */
  private static RelativePos theCellOfTheShop(CounterBlockEntity counter, int seat) {
    return counter
        .queue()
        .seatCell(seat)
        .orElseThrow(
            () ->
                new AssertionError(
                    "the shop's line has no place "
                        + seat
                        + ", so no customer could have been given one"));
  }

  /**
   * A walking customer has no vanilla path at all.
   *
   * <p>This is the mod's central claim about cost, made checkable: §5.2 says pathing is the mod's
   * own field and <em>not</em> per-spawn vanilla pathfinding, and the way that shows up in the game
   * is that nothing ever asks the navigation for a route. {@code PathNavigation.isDone} is true
   * exactly when no path was ever set, so a customer that has demonstrably walked — which the
   * distance assertion below establishes — while its navigation reports itself done is a customer
   * being steered by something else. An A* search per customer would have it false within a tick of
   * the customer setting off.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void doesNotPathfind(GameTestHelper helper) {
    paveTheFloor(helper);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          Customer customer = putACustomerDown(helper, counter, ARRIVAL_CELL);
          helper.assertTrue(
              customer.getNavigation().isDone(), "a customer that has just arrived has no path");

          double before = distanceToCounter(helper, customer);
          helper.runAfterDelay(
              WALK_TICKS,
              () -> {
                helper.assertTrue(
                    distanceToCounter(helper, customer) < before,
                    Component.literal(
                        "the customer has to have walked for this test to mean anything"));
                helper.assertTrue(
                    customer.getNavigation().isDone(),
                    Component.literal(
                        "a customer must be steered by the shop's field and not by a vanilla path,"
                            + " but its navigation holds one"));
                helper.succeed();
              });
        });
  }

  /**
   * A preferred path is ground, and cheaper ground than what is beside it.
   *
   * <p>Two cells, the same distance from the Counter, one over a dirt path and one over stone. Both
   * have to be reachable — which is the assertion that matters most, because the obvious ways to
   * write the standability rule (vanilla's {@code isPathfindable} above all, and the three-argument
   * {@code isFaceSturdy}) both refuse a dirt path, and a mod that refused to walk on the blocks it
   * prefers would be a mod whose preference never applied. The second assertion is the preference
   * itself: the path side has to come out cheaper than the stone side, or laying a path buys a
   * shopkeeper nothing.
   *
   * <p>The field here is built by hand rather than by a Counter, because what is under test is
   * {@code LevelNavView}'s reading of blocks and not the block entity's clock: a bounds of one
   * block, one advance to completion, and no ticks at all. The two costs are the shipping ones
   * rather than whatever a config file happens to hold, because what is asserted is the preference
   * the mod ships with.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void prefersAPath(GameTestHelper helper) {
    // The two cells the field will be asked about, either side of the Counter along the x axis.
    BlockPos stoneGround = new BlockPos(1, FLOOR_Y, 0);
    BlockPos pathGround = new BlockPos(-1, FLOOR_Y, 0);
    helper.setBlock(COUNTER, ModBlocks.COUNTER.get());
    helper.setBlock(stoneGround, Blocks.STONE);
    helper.setBlock(pathGround, Blocks.DIRT_PATH);

    ServerLevel level = helper.getLevel();
    BlockPos origin = helper.absolutePos(COUNTER);
    FlowFieldBuilder builder =
        new FlowFieldBuilder(
            RelativePos.ORIGIN,
            new FieldBounds(1, 1, 1),
            new LevelNavView(
                level, origin, TuningDefaults.PREFERRED_PATH_COST, TuningDefaults.PLAIN_PATH_COST),
            // No surcharge: both cells are on the Counter's own level, so a climb cost would only
            // blur the comparison this test is making.
            0);
    builder.advance(Integer.MAX_VALUE);
    FlowField field = builder.result();

    RelativePos overPath = new RelativePos(-1, 0, 0);
    RelativePos overStone = new RelativePos(1, 0, 0);
    helper.assertTrue(
        field.reaches(overPath), Component.literal("a dirt path is ground a customer can walk on"));
    helper.assertTrue(
        field.reaches(overStone),
        Component.literal("and so is stone, or this comparison means nothing"));
    helper.assertTrue(
        field.costAt(overPath).orElseThrow() < field.costAt(overStone).orElseThrow(),
        Component.literal(
            "a preferred path is meant to be cheaper to walk than ordinary ground, but the costs"
                + " were "
                + field.costAt(overPath).orElseThrow()
                + " and "
                + field.costAt(overStone).orElseThrow()));
    helper.succeed();
  }

  /**
   * A Counter sealed into a box has nowhere for anybody to appear.
   *
   * <p>The Counter stands in a one-block hole in a solid three-by-three-by-three box, so the cell
   * in front of it — the shop's front, and the cell its field is built from — has a block in it.
   * The design's rule is that a customer only appears where a route has been proved to exist, and
   * the proof is the field; a shop with no front has no field to build at all, so it has no line,
   * no seats and nowhere to spawn anybody.
   *
   * <p>The gates before that one are satisfied on purpose, and that is what makes this a test of
   * the field rather than of a shut door: the shop is open, and its owner is a real player on the
   * server's own list, so the arrival attempt is made and is refused for the reason under test. The
   * assertion is therefore made after a full arrival interval has gone by.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aWalledInShopSpawnsNobody(GameTestHelper helper) {
    sealTheCounterIn(helper);
    ServerPlayer owner = joinAMockServerPlayer(helper);
    CounterBlockEntity counter = counterPlacedBy(helper, owner);

    helper.assertTrue(
        ShopData.of(helper.getLevel())
            .ledger()
            .record(counter.shopId().orElseThrow())
            .orElseThrow()
            .isStakeholder(owner.getUUID()),
        "the Counter was placed by a player, so they own the shop and it is open by default");

    helper.runAfterDelay(
        ARRIVAL_TICKS,
        () -> {
          helper.assertTrue(
              counter.pathField().current().isEmpty(),
              Component.literal(
                  "a Counter walled in on every side has nowhere to stand in front of it, so its"
                      + " shop has no field to build and nowhere to put a line"));
          // Absent from the whole test region, which is the claim: no customer may appear at a shop
          // with no route to it. The framework's own message names the type it looked for.
          helper.assertEntityNotPresent(ModEntities.CUSTOMER.get());
          helper.succeed();
        });
  }

  /**
   * A shop with every place in its line taken has no place for one more.
   *
   * <p>§5.2's cap, and the overflow that goes with it: the line is finite, the shop hands out its
   * places one at a time to the customers that arrive, and the shop's answer to an arrival it
   * cannot seat is a refusal rather than a fifth person standing in somebody's way. The assertion
   * is made of the queue itself — the next arrival asks for a seat and is told there is none —
   * because that question is the whole of the gate a real arrival goes through.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void theQueueIsCapped(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.STONE);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          sendInTheQueue(
              helper,
              counter,
              TuningDefaults.CUSTOMERS_PER_SHOP,
              () -> {
                CustomerQueue queue = counter.queue();
                helper.assertTrue(
                    queue.occupiedCount() == TuningDefaults.CUSTOMERS_PER_SHOP,
                    Component.literal(
                        "the shop should be holding all "
                            + TuningDefaults.CUSTOMERS_PER_SHOP
                            + " of its places, but it holds "
                            + queue.occupiedCount()));
                helper.assertTrue(
                    queue.freeSeat().isEmpty(),
                    Component.literal(
                        "every place in the line is taken, so the next arrival has to be refused"));
                helper.assertTrue(
                    customersAtTheShop(helper, counter).size() == TuningDefaults.CUSTOMERS_PER_SHOP,
                    Component.literal(
                        "a refused customer must not be left standing in the street, but there are "
                            + customersAtTheShop(helper, counter).size()
                            + " of them"));
                helper.succeed();
              });
        });
  }

  /**
   * The customers waiting at a shop stand in a line, one block apart.
   *
   * <p>The other half of §5.2's queue sentence, and the half about where people stand: each
   * customer is given a place in the shop's line when it arrives, and the four of them are standing
   * in four different cells, in the order they arrived, each one block from the next. Two customers
   * in one cell would be a queue that is not a queue, and a gap would be one they have walked
   * through.
   *
   * <p>Three claims, and they are separable: that the cells are the shop's own line rather than
   * four cells it happens to have picked, that the places are handed out in order — the one nearest
   * the Counter first — and that consecutive places are one block apart. The last is asserted of
   * the customers' own positions rather than of the line, so a customer that wandered off would
   * fail this test even if the shop's seats were right.
   *
   * <p>It also settles the other half of what a customer does with its place. A customer standing
   * in the cell the shop gave it waits there rather than walking on to the Counter, which is why
   * the assertion is made after a wait rather than at once: a queue that had marched forward to the
   * shopfront would be standing somewhere else by then, and the cells would not be the line's.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void queuedCustomersAreOneApart(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.STONE);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          sendInTheQueue(
              helper,
              counter,
              TuningDefaults.CUSTOMERS_PER_SHOP,
              () ->
                  helper.runAfterDelay(
                      WALK_TICKS,
                      () -> {
                        List<Customer> queued =
                            new ArrayList<>(customersAtTheShop(helper, counter));
                        helper.assertTrue(
                            queued.size() == TuningDefaults.CUSTOMERS_PER_SHOP,
                            Component.literal(
                                "four customers were sent in, but "
                                    + queued.size()
                                    + " are standing in the street"));

                        List<BlockPos> standing = new ArrayList<>(queued.size());
                        for (Customer customer : queued) {
                          standing.add(customer.blockPosition());
                        }
                        Set<BlockPos> placesStood = new HashSet<>(standing);
                        helper.assertTrue(
                            placesStood.size() == standing.size(),
                            Component.literal(
                                "no two customers may stand in the same cell, but they are in "
                                    + placesStood.size()
                                    + " cells between "
                                    + standing.size()
                                    + " of them"));

                        List<BlockPos> line = new ArrayList<>(TuningDefaults.CUSTOMERS_PER_SHOP);
                        for (int seat = 0; seat < TuningDefaults.CUSTOMERS_PER_SHOP; seat++) {
                          RelativePos cell =
                              counter
                                  .queue()
                                  .seatCell(seat)
                                  .orElseThrow(
                                      () ->
                                          new AssertionError(
                                              "the shop's line has a seat with no cell in it, so"
                                                  + " its places and its line have come apart"));
                          line.add(counter.getBlockPos().offset(cell.x(), cell.y(), cell.z()));
                        }
                        helper.assertTrue(
                            new HashSet<>(line).equals(placesStood),
                            Component.literal(
                                "the customers should be standing in the shop's own line, "
                                    + line
                                    + ", but they are in "
                                    + standing));

                        BlockPos counterPos = counter.getBlockPos();
                        double centreX = counterPos.getX() + 0.5;
                        double centreY = counterPos.getY() + 0.5;
                        double centreZ = counterPos.getZ() + 0.5;
                        queued.sort(
                            Comparator.comparingDouble(
                                customer -> customer.distanceToSqr(centreX, centreY, centreZ)));

                        for (int index = 0; index < queued.size(); index++) {
                          BlockPos standingAt = queued.get(index).blockPosition();
                          BlockPos shouldStandAt = line.get(index);
                          helper.assertTrue(
                              standingAt.equals(shouldStandAt),
                              Component.literal(
                                  "the customer "
                                      + (index + 1)
                                      + " of the queue should be standing in the shop's place "
                                      + index
                                      + ", "
                                      + shouldStandAt
                                      + ", but it is in "
                                      + standingAt));
                          if (index == 0) {
                            continue;
                          }
                          BlockPos ahead = line.get(index - 1);
                          helper.assertTrue(
                              stepsApart(ahead, shouldStandAt) == 1,
                              Component.literal(
                                  "queued customers have to be one block apart, but "
                                      + shouldStandAt
                                      + " is "
                                      + stepsApart(ahead, shouldStandAt)
                                      + " blocks from "
                                      + ahead));
                        }
                        helper.succeed();
                      }));
        });
  }

  /**
   * Walking over the same cell enough times turns it into a path.
   *
   * <p>§5.2's path wear. A customer is moved back and forth between two cells of a grass street
   * until it has stood on each of them as many times as the threshold asks for, and the assertion
   * is about the blocks underneath: both have become dirt paths, which is the block a player's own
   * shovel would have made of them. The threshold is read from the tuning rather than written out
   * here, so a test that passes passes for the number the mod actually wears paths at.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void walkedOverGroundBecomesAPath(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.GRASS_BLOCK);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    BlockPos firstGround = new BlockPos(ARRIVAL_CELL.getX(), FLOOR_Y, 0);
    BlockPos secondGround = new BlockPos(ARRIVAL_CELL.getX() - 1, FLOOR_Y, 0);

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          Customer customer = putACustomerDown(helper, counter, ARRIVAL_CELL);
          walkBackAndForth(
              helper,
              customer,
              ARRIVAL_CELL,
              ARRIVAL_CELL.offset(-1, 0, 0),
              WEAR_PASSES * 2 + 2,
              () -> {
                helper.assertBlockPresent(Blocks.DIRT_PATH, firstGround);
                helper.assertBlockPresent(Blocks.DIRT_PATH, secondGround);
                helper.succeed();
              });
        });
  }

  /**
   * A cell with something standing on it never becomes a path.
   *
   * <p>The rule that keeps a player's torch where they put it: a block that changes shape under
   * something destroys it, so a worn cell with anything above it is left alone. Both cells here are
   * walked over exactly as often as in the test above — one of them converts, which is what makes
   * this a test of the rule and not of the wear having failed — and the one with a torch in it
   * stays grass.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aTorchStopsTheWear(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.GRASS_BLOCK);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    BlockPos torchGround = new BlockPos(ARRIVAL_CELL.getX(), FLOOR_Y, 0);
    BlockPos bareGround = new BlockPos(ARRIVAL_CELL.getX() - 1, FLOOR_Y, 0);
    helper.setBlock(ARRIVAL_CELL, Blocks.TORCH);

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          Customer customer = putACustomerDown(helper, counter, ARRIVAL_CELL.offset(-1, 0, 0));
          walkBackAndForth(
              helper,
              customer,
              ARRIVAL_CELL,
              ARRIVAL_CELL.offset(-1, 0, 0),
              WEAR_PASSES * 2 + 2,
              () -> {
                helper.assertBlockPresent(Blocks.DIRT_PATH, bareGround);
                helper.assertBlockPresent(Blocks.GRASS_BLOCK, torchGround);
                helper.succeed();
              });
        });
  }

  /**
   * A Counter comes out facing the player who put it down.
   *
   * <p>Vanilla's convention for a block with a front, and the reason a shop's door ends up where
   * its owner was standing: the direction a player is looking is the direction of the face they are
   * looking at, so a block that turns to its placer puts its front on their side of it. Asserted of
   * the state a placement context produces rather than of a state written out here, because that
   * method is the whole of the convention — which way a Counter faces is its answer, and a test
   * that wrote the answer down would be testing itself.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void counterFacesThePlayer(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    // Looking north, so the face it is looking at is the south one, and the face that turns to it.
    owner.setYRot(180.0F);

    BlockPos where = helper.absolutePos(COUNTER);
    BlockPlaceContext placing =
        new BlockPlaceContext(
            owner,
            InteractionHand.MAIN_HAND,
            new ItemStack(ModItems.COUNTER.get()),
            new BlockHitResult(Vec3.atCenterOf(where), Direction.UP, where, false));

    BlockState placed = ModBlocks.COUNTER.get().getStateForPlacement(placing);
    helper.assertTrue(placed != null, "a Counter has to be placed facing something");
    helper.assertValueEqual(
        placed.getValue(CounterBlock.FACING),
        Direction.SOUTH,
        "the way a Counter faces when the player placing it is looking north");
    helper.succeed();
  }

  /**
   * A Counter facing north queues to the north.
   *
   * <p>The facing as the whole shop sees it: the block's own face, the block entity's reading of
   * it, and the line the queue is drawn from that reading. The arithmetic of a straight line is
   * {@code core}'s to assert and is asserted there; what only a world can show is this seam, and a
   * line that is straight and deterministic but starts in the wrong direction is a queue down the
   * side of a shop.
   *
   * <p>The ground is a crossroads, so the choice is a real one rather than a coincidence: the
   * pavement runs east of the Counter as well as north of it, which is exactly the ground the field
   * itself cannot choose on — two ways out that cost the same, settled by whatever order the
   * field's own directions happen to be tried in rather than by the shop's design.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void queuesAlongItsFacing(GameTestHelper helper) {
    paveTheCrossroads(helper);
    CounterBlockEntity counter =
        counterPlacedBy(helper, joinAMockServerPlayer(helper), Direction.NORTH);

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          helper.assertValueEqual(
              counter.facing(), Direction.NORTH, "the way the Counter was placed facing");

          BlockPos counterPos = counter.getBlockPos();
          for (int seat = 0; seat < TuningDefaults.CUSTOMERS_PER_SHOP; seat++) {
            helper.assertValueEqual(
                seatInTheWorld(counter, seat),
                counterPos.offset(0, 0, -(seat + 1)),
                "the cell of place " + seat + ", which has to be out along the face");
          }
          helper.succeed();
        });
  }

  /**
   * A customer moves up a place when the place in front of it is given up.
   *
   * <p>The heart of a queue rather than of a row of reserved places, and the assertion is the
   * customer's own block position: it stands waiting in the second place, the customer in the first
   * is taken out of the world, and the one behind has to end up standing in the cell the first one
   * was standing in. A test that asked the shop which place it had handed out would pass on a
   * customer that never moved a block, because the bookkeeping is the shop's answer about itself
   * and §5.2 asks for a line that shuffles forward.
   *
   * <p>The head customer leaves the way every departure in this mod leaves, which is also how the
   * served customer will leave once serving exists: {@code Customer#despawn} gives up its place and
   * puffs. Everything after that is the shop's own tick finding the place empty and the customer's
   * own walk into a cell nobody has to be told about twice.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void theLineMovesUp(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.STONE);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          // The head of the queue, standing in the cell in front of the Counter: the customer the
          // shop serves once serving exists, and the one whose leaving moves everybody up.
          Customer served = putACustomerDown(helper, counter, seatInTheTemplate(counter, 0));
          // And the customer behind it in the second place. Put down where it will stand rather
          // than made to walk in, because walking in is the subject of the test above and this one
          // is about what happens to a queue that is already standing in its places.
          Customer waiting = putACustomerDown(helper, counter, seatInTheTemplate(counter, 1));

          helper.runAfterDelay(
              QUEUE_SPACING_TICKS,
              () -> {
                // Waiting, and in the second place, before the first is given up: the state this
                // test is about to change, asserted rather than assumed, so a customer that walked
                // off somewhere and left this test to prove nothing is its own failure.
                helper.assertTrue(
                    waiting.blockPosition().equals(seatInTheWorld(counter, 1)),
                    Component.literal(
                        "the customer behind the head should be waiting in the second place at "
                            + seatInTheWorld(counter, 1)
                            + ", but it is at "
                            + waiting.blockPosition()));
                served.despawn(helper.getLevel());
                assertTheCustomerReaches(helper, waiting, seatInTheWorld(counter, 0), ARRIVE_TICKS);
              });
        });
  }

  /**
   * Every customer a shop takes in walks into its own place in the line and stands in it.
   *
   * <p>The complaint this test exists for is a bug and not a nuisance: the shop's line runs out
   * from the face of the Counter, and a customer that came at the shop from the side or the back
   * used to stop wherever its walk toward the Counter first met ground as near the Counter as its
   * place is — which is a cell on the <em>ring</em> around the shop at the right distance and the
   * wrong side, the opposite face as often as not. It stopped there for good, holding a place in a
   * line it was not standing in. So the claim here is about where each customer ends up, not about
   * what the shop wrote down: every customer is standing in one of the cells of the shop's line,
   * and the customers in order of how far they are from the Counter are standing in places 0, 1, 2
   * and 3.
   *
   * <p>The customers are put down around the shop rather than in front of it — behind it, and
   * either side of it — and each is waited out before the next is put down, so what is being
   * measured is four journeys and not four customers shoving each other. Nothing here asserts
   * <em>how</em> they got there: the walk round the Counter is the field's business, and this is
   * the claim that it arrives.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void everyQueuedCustomerStandsOnTheLine(GameTestHelper helper) {
    paveThePlaza(helper);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    onceTheShopHasAField(
        helper,
        counter,
        () ->
            walkInOneAtATime(
                helper,
                counter,
                0,
                () -> assertTheQueueIsOnTheLine(helper, counter, WALK_TICKS, helper::succeed)));
  }

  /**
   * A shop whose line is shorter than its cap takes in as many as it has places for, and nobody
   * stands anywhere else.
   *
   * <p>§5.2's cap is the most that may wait, and the ground decides how many of those places are
   * real: a wall across the shop's street two cells out leaves a line of two places, and the shop
   * then has nowhere to put a third customer. The answer to an arrival it cannot seat is a refusal
   * — {@code freeSeat} has nothing to hand out — and never a customer standing outside the line,
   * because a customer's place in the queue <em>is</em> a cell of that line and there is no second
   * place for one to stand.
   *
   * <p>The assertion is made of the customers' own block positions rather than of the shop's
   * bookkeeping, and of the third arrival's gate rather than of a third customer: the shop is the
   * only thing that seats anybody, so asking it is asking the question a real arrival asks.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aLineShorterThanTheCapSeatsNobodyOffIt(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.STONE);
    // A wall across the street two cells out from the Counter, so that the line is the cell in
    // front of it and the one beyond that, and the four the config allows are not there to be used.
    helper.setBlock(new BlockPos(3, FLOOR_Y + 1, 0), Blocks.STONE);
    CounterBlockEntity counter = counterPlacedBy(helper, joinAMockServerPlayer(helper));

    onceTheShopHasAField(
        helper,
        counter,
        () ->
            helper.runAfterDelay(
                FIELD_POLL_TICKS * 2,
                () -> {
                  CustomerQueue queue = counter.queue();
                  helper.assertValueEqual(
                      queue.lineLength(),
                      2,
                      "the cells of a line that runs up against a wall two blocks out");
                  helper.assertTrue(
                      queue.seatCell(2).isEmpty(),
                      Component.literal(
                          "a line of two has no third place for a customer to be seated in"));

                  putACustomerDown(helper, counter, seatInTheTemplate(counter, 0));
                  putACustomerDown(helper, counter, seatInTheTemplate(counter, 1));

                  helper.assertTrue(
                      queue.freeSeat().isEmpty(),
                      Component.literal(
                          "a shop holding every place its line has must refuse the next arrival, but"
                              + " its fourth place is still being offered"));
                  assertTheQueueIsOnTheLine(helper, counter, WALK_TICKS, helper::succeed);
                }));
  }

  // ------------------------------------------------------------------ helpers

  /**
   * Sleeping a night away closes the shops of the people who slept, and nobody else's.
   *
   * <p>Two shops and two players: one goes to bed, one stays up, and the night is skipped. On a
   * server a single player sleeping passes the night for everyone, so the rule has to be about
   * whose shop it is rather than about the night having happened — a mod that shut every shop in
   * the level would be shutting shops whose owners were standing in them, which is the failure this
   * test exists for.
   *
   * <p>The event is the real one and the handler is the real handler, and the sleeper is put into
   * the state that handler finds a sleeper in — still in bed — because that is what makes its
   * answer to "who slept" testable without a bed, a clock and a level of players agreeing that it
   * is night.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aNightsSleepClosesTheShopsOfWhoeverSlept(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.STONE);
    ServerPlayer sleeper = joinAMockServerPlayer(helper);
    ServerPlayer staysUp = joinAMockServerPlayer(helper);
    CounterBlockEntity theirs = counterPlacedBy(helper, sleeper);
    CounterBlockEntity others =
        counterAt(helper, new BlockPos(3, FLOOR_Y + 1, 0), staysUp, Direction.EAST);

    ShopId theirShop = theirs.shopId().orElseThrow();
    ShopId otherShop = others.shopId().orElseThrow();
    helper.assertTrue(
        shopAt(helper, theirShop).open() && shopAt(helper, otherShop).open(),
        "both Counters were placed by a player, so both shops are open to begin with");

    sleeper.startSleeping(helper.absolutePos(COUNTER));
    NightSkip.onSleepFinished(
        new SleepFinishedTimeEvent(
            helper.getLevel(), new ClockAdjustment.Marker(ClockTimeMarkers.WAKE_UP_FROM_SLEEP)));

    helper.assertFalse(
        shopAt(helper, theirShop).open(),
        "the shop of the player who slept through the night is shut");
    helper.assertTrue(
        shopAt(helper, otherShop).open(),
        "the shop of the player who stayed up is still trading: one player sleeping may not shut"
            + " every shop on the server");

    ShopData.of(helper.getLevel()).ledger().release(theirShop);
    ShopData.of(helper.getLevel()).ledger().release(otherShop);
    helper.succeed();
  }

  /**
   * A shop that is closed loses the customers standing outside it on the spot.
   *
   * <p>Addendum A6's assumption, and the timing is the whole of what this asserts. A closed shop
   * has no event of its own, so the shop's own clock is the only thing that notices — and the clock
   * has two speeds in it: the arrival interval of several hundred ticks, and the tick itself.
   * Asking whether the shop is trading on the <em>interval</em> was the fault: closing a shop left
   * its customers standing in the street for up to ten seconds, and the fix is that the question is
   * asked every tick while the arrivals stay on their clock. So the assertion is made a handful of
   * ticks after the close, well inside the interval, which is what makes it a test of the once-per-
   * tick gate rather than of the shop being emptied eventually by something else.
   *
   * <p>The shop is closed the way its owner closes it — through the ledger, which is what the
   * Counter's own Open/Closed control reaches — rather than by pressing the button, because a mock
   * player has no connection for the view that follows a press.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void closingTheShopClearsTheStreetAtOnce(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.STONE);
    ServerPlayer owner = joinAMockServerPlayer(helper);
    CounterBlockEntity counter = counterPlacedBy(helper, owner);
    ShopId shop = counter.shopId().orElseThrow();

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          putACustomerDown(helper, counter, seatInTheTemplate(counter, 0));
          helper.runAfterDelay(
              QUEUE_SPACING_TICKS,
              () -> {
                putACustomerDown(helper, counter, seatInTheTemplate(counter, 1));
                helper.assertTrue(
                    counter.queue().occupiedCount() == 2,
                    Component.literal(
                        "the shop should be holding the two customers it was given, but it holds "
                            + counter.queue().occupiedCount()));

                helper.assertTrue(
                    ShopData.of(helper.getLevel()).ledger().setOpen(shop, owner.getUUID(), false),
                    "the owner's own Open/Closed control closes the shop");

                helper.runAfterDelay(
                    CLOSING_TICKS,
                    () -> {
                      helper.assertTrue(
                          counter.queue().occupiedCount() == 0,
                          Component.literal(
                              "a closed shop has nobody waiting at it, but it is still holding "
                                  + counter.queue().occupiedCount()
                                  + " places "
                                  + CLOSING_TICKS
                                  + " ticks after it was closed"));
                      helper.assertTrue(
                          customersAtTheShop(helper, counter).isEmpty(),
                          Component.literal(
                              "the customers of a shop that has just been closed have to be gone at"
                                  + " once, but "
                                  + customersAtTheShop(helper, counter).size()
                                  + " of them are still standing in the street"));
                      helper.succeed();
                    });
              });
        });
  }

  /**
   * Walks a shop's queue in one customer at a time, each from where {@link #AROUND_THE_SHOP} puts
   * it, and waits for each to reach its place before the next is put down.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @param index which customer of the queue is being put down
   * @param then what to do once they are all standing in their places
   */
  private static void walkInOneAtATime(
      GameTestHelper helper, CounterBlockEntity counter, int index, Runnable then) {
    if (index >= AROUND_THE_SHOP.size()) {
      then.run();
      return;
    }
    Customer customer = putACustomerDown(helper, counter, AROUND_THE_SHOP.get(index));
    assertTheCustomerReaches(
        helper,
        customer,
        seatInTheWorld(counter, index),
        ARRIVE_TICKS,
        () -> walkInOneAtATime(helper, counter, index + 1, then));
  }

  /**
   * Asks every customer at the shop to be standing in its own place in the line, or gives up.
   *
   * <p>Asked once a tick rather than looked at after a fixed wait, for the reason {@code
   * assertTheCustomerReaches} is: a customer that is still walking is a customer this test has a
   * question about, not an answer. The exception is an empty queue, which is what a shop whose
   * customers all left looks like, and which this reports rather than waiting out.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @param triesLeft how many more times to ask before giving up
   * @param then what to do once every customer is in its place
   */
  private static void assertTheQueueIsOnTheLine(
      GameTestHelper helper, CounterBlockEntity counter, int triesLeft, Runnable then) {
    CustomerQueue queue = counter.queue();
    List<Customer> queued = new ArrayList<>(customersAtTheShop(helper, counter));
    String wrong = wrongPlace(queued, counter);
    if (queued.size() == queue.occupiedCount() && wrong.isEmpty()) {
      then.run();
      return;
    }
    helper.assertTrue(
        triesLeft > 0,
        Component.literal(
            "the shop is holding "
                + queue.occupiedCount()
                + " places at a line of "
                + queue.lineLength()
                + " cells ("
                + lineInTheWorld(counter)
                + ") with "
                + queued.size()
                + " customers standing at it, and every one of them has to end up standing in its"
                + " own place: "
                + (wrong.isEmpty() ? "the shop and the street disagree about how many came" : wrong)
                + "; the Counter is at "
                + counter.getBlockPos()
                + " facing "
                + counter.facing()
                + " and its field leads to "
                + counter
                    .pathField()
                    .current()
                    .map(field -> field.goal().toString())
                    .orElse("nowhere")
                + ", and the customers are at "
                + queued.stream().map(customer -> customer.position().toString()).toList()));
    helper.runAfterDelay(1, () -> assertTheQueueIsOnTheLine(helper, counter, triesLeft - 1, then));
  }

  /**
   * @param queued the customers standing at the shop
   * @param counter the shop's Counter
   * @return one line per customer that is not in the place the line gives it, or an empty string
   *     when every one of them is exactly where it belongs
   */
  private static String wrongPlace(List<Customer> queued, CounterBlockEntity counter) {
    List<Customer> ordered = new ArrayList<>(queued);
    BlockPos counterPos = counter.getBlockPos();
    ordered.sort(
        Comparator.comparingDouble(
            customer ->
                customer.distanceToSqr(
                    counterPos.getX() + 0.5, counterPos.getY() + 0.5, counterPos.getZ() + 0.5)));

    StringBuilder wrong = new StringBuilder();
    for (int index = 0; index < ordered.size(); index++) {
      Optional<RelativePos> cell = counter.queue().seatCell(index);
      if (cell.isEmpty()) {
        return "there are " + ordered.size() + " customers and no place " + index + " for one";
      }
      BlockPos shouldBeStandingAt =
          counterPos.offset(cell.get().x(), cell.get().y(), cell.get().z());
      BlockPos standingAt = ordered.get(index).blockPosition();
      if (!standingAt.equals(shouldBeStandingAt)) {
        if (wrong.length() > 0) {
          wrong.append("; ");
        }
        wrong
            .append("the customer ")
            .append(index + 1)
            .append(" of the queue is at ")
            .append(standingAt)
            .append(" and its place is ")
            .append(shouldBeStandingAt);
      }
    }
    return wrong.toString();
  }

  /**
   * @param counter the shop's Counter
   * @return the cells of the shop's line, as positions in the world
   */
  private static Set<BlockPos> lineInTheWorld(CounterBlockEntity counter) {
    Set<BlockPos> line = new HashSet<>();
    for (int seat = 0; seat < TuningDefaults.CUSTOMERS_PER_SHOP; seat++) {
      Optional<RelativePos> cell = counter.queue().seatCell(seat);
      if (cell.isEmpty()) {
        break;
      }
      line.add(counter.getBlockPos().offset(cell.get().x(), cell.get().y(), cell.get().z()));
    }
    return line;
  }

  /**
   * @param path the registry path the test is reported under
   * @param body what the test does
   * @return the holder, for the registration above
   */
  private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function(
      String path, Consumer<GameTestHelper> body) {
    return TEST_FUNCTIONS.register(path, () -> body);
  }

  /**
   * @param event the game test registries
   * @param environment the environment the test runs in
   * @param test the body, which is also the name the test is reported under
   * @param maxTicks how long the framework may take over it
   */
  private static void register(
      RegisterGameTestsEvent event,
      Holder<TestEnvironmentDefinition<?>> environment,
      DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test,
      int maxTicks) {
    event.registerTest(
        test.getKey().identifier(),
        new FunctionGameTestInstance(
            test.getKey(),
            new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, SETUP_TICKS, true)));
  }

  /**
   * Lays the pavement a customer walks on: a stone floor at the Counter's feet, running out to the
   * cell the tests put a customer down in.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void paveTheFloor(GameTestHelper helper) {
    for (int x = 0; x <= ARRIVAL_CELL.getX(); x++) {
      for (int z = -1; z <= 1; z++) {
        helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
      }
    }
  }

  /**
   * Lays pavement north and south of the Counter as well as east of it: a plaza it can be walked
   * round.
   *
   * <p>The walking test sends its customers in from behind the shop and from either side of it, and
   * a customer that comes at the shop from there has to walk round the Counter to reach the line in
   * front of it — which needs ground on the far side, beside it, and in the two corners, because
   * the field steps orthogonally and the Counter's own cell is a block.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void paveThePlaza(GameTestHelper helper) {
    for (int x = -1; x <= STREET_END; x++) {
      for (int z = -1; z <= 1; z++) {
        helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
      }
    }
  }

  /**
   * Walls the Counter off from every direction, floor and ceiling included.
   *
   * <p>Diagonals are filled as well as the six neighbours, which the field does not need — it steps
   * orthogonally — but a customer does: the design says they pose no danger and are not to be
   * trapped inside a wall by a stray block update, and a box with holes in the corners looks like
   * one that was meant to be sealed.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void sealTheCounterIn(GameTestHelper helper) {
    for (int x = -1; x <= 1; x++) {
      for (int y = -1; y <= 1; y++) {
        for (int z = -1; z <= 1; z++) {
          BlockPos pos = COUNTER.offset(x, y, z);
          if (!pos.equals(COUNTER)) {
            helper.setBlock(pos, Blocks.STONE);
          }
        }
      }
    }
  }

  /**
   * Puts a customer in the world, in the shop's next free place in its line.
   *
   * <p>Built rather than spawned through {@code CustomerSpawner}, because what these tests are
   * about is the walking: the spawner's own gates are the subject of the walled-in test, and a test
   * of both at once would fail for either reason. What is taken from the spawner is exactly what
   * the spawner does — ask for a seat, attach the route and the seat, then put the customer in the
   * world — so that a customer these tests place is seated the way a real one is, and its place in
   * the line is the real shop's.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @param cell the cell to put the customer down in, as an offset from the template's corner
   * @return the customer, already in the world
   */
  private static Customer putACustomerDown(
      GameTestHelper helper, CounterBlockEntity counter, BlockPos cell) {
    CustomerQueue queue = counter.queue();
    OptionalInt seat = queue.freeSeat();
    if (seat.isEmpty()) {
      throw new AssertionError(
          "the shop's line is full, so there is no place to put a customer; field="
              + counter.pathField().current().isPresent()
              + " seat0="
              + queue.seatCell(0)
              + " held="
              + queue.occupiedCount()
              + " tick="
              + helper.getTick()
              + " at="
              + counter.getBlockPos());
    }

    Customer customer =
        ModEntities.CUSTOMER
            .get()
            .create(
                helper.getLevel(),
                spawned -> {
                  spawned.setRoute(counter.pathField(), counter.getBlockPos());
                  spawned.setSeat(queue, seat.getAsInt());
                },
                helper.absolutePos(cell),
                EntitySpawnReason.EVENT,
                true,
                false);
    if (customer == null) {
      throw new AssertionError("the customer entity type could not build one");
    }
    queue.occupy(seat.getAsInt(), customer);
    helper.getLevel().addFreshEntity(customer);
    return customer;
  }

  /**
   * Runs a body once the shop has a navigation field.
   *
   * <p>A shop with no field has no line, and so has no place to put a customer: the queue is drawn
   * from the field, which is why these tests ask for one before they ask the shop for anything. A
   * field is built a slice at a time by the Counter's own tick, and how long it takes depends on
   * how much walkable world is around the shop rather than on anything this test controls, so it is
   * asked about rather than waited out.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @param then what to do with a shop that has a field
   */
  private static void onceTheShopHasAField(
      GameTestHelper helper, CounterBlockEntity counter, Runnable then) {
    onceTheShopHasAField(helper, counter, FIELD_TRIES, then);
  }

  /**
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @param tries how many more times to ask before giving up
   * @param then what to do with a shop that has a field
   */
  private static void onceTheShopHasAField(
      GameTestHelper helper, CounterBlockEntity counter, int tries, Runnable then) {
    if (counter.pathField().current().isPresent()) {
      then.run();
      return;
    }
    helper.assertTrue(
        tries > 0,
        Component.literal(
            "the Counter at "
                + counter.getBlockPos()
                + " never finished building a navigation field in "
                + (FIELD_POLL_TICKS * FIELD_TRIES)
                + " ticks, so no customer could have been given a place to wait in"));
    helper.runAfterDelay(
        FIELD_POLL_TICKS, () -> onceTheShopHasAField(helper, counter, tries - 1, then));
  }

  /**
   * Every customer that came for this shop, wherever on the street it is standing.
   *
   * <p>Asked of the level rather than of the helper's own entity methods, which search the plot the
   * framework sealed the test into: a street is longer than that plot, and a customer just outside
   * it is still a customer the shop is dealing with.
   *
   * <p><b>Filtered by the Counter it came for, and not only by where it is standing.</b> The tests
   * of one environment run side by side, so the street being tested has a neighbour's street beside
   * it and a box drawn around one of them reaches into the other. What decides which shop a
   * customer belongs to is the Counter it was spawned for, which every customer remembers.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @return those customers, in no particular order
   */
  private static List<Customer> customersAtTheShop(
      GameTestHelper helper, CounterBlockEntity counter) {
    BlockPos near = helper.absolutePos(new BlockPos(0, FLOOR_Y, 0));
    AABB street =
        new AABB(
            near.getX() - 1.0,
            near.getY() - 1.0,
            near.getZ() - 1.0,
            near.getX() + STREET_END + 1.0,
            near.getY() + 3.0,
            near.getZ() + 2.0);

    List<Customer> theirs = helper.getLevel().getEntitiesOfClass(Customer.class, street);
    List<Customer> mine = new ArrayList<>(theirs.size());
    for (Customer customer : theirs) {
      if (customer.isHeadingFor(counter.getBlockPos())) {
        mine.add(customer);
      }
    }
    return mine;
  }

  /**
   * @param from one cell
   * @param to another
   * @return how many orthogonal steps apart they are
   */
  private static int stepsApart(BlockPos from, BlockPos to) {
    return Math.abs(from.getX() - to.getX())
        + Math.abs(from.getY() - to.getY())
        + Math.abs(from.getZ() - to.getZ());
  }

  /**
   * Lays a street out from the Counter: three blocks wide and long enough for a queue.
   *
   * <p>Three wide rather than one, because customers are solid and a queue is a crowd: a customer
   * nudged sideways on a one-block street is a customer pushed into the ground beside it, and one
   * that has fallen into the pavement is no longer standing in a queue. Three blocks of pavement
   * also happen to leave the shop's own line straight, because a step east is always the cheapest
   * step and the cells either side of it are never cheaper.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param ground what the street is paved with
   */
  private static void paveTheStreet(GameTestHelper helper, Block ground) {
    for (int x = 0; x <= STREET_END; x++) {
      for (int z = -1; z <= 1; z++) {
        helper.setBlock(new BlockPos(x, FLOOR_Y, z), ground);
      }
    }
  }

  /**
   * Lays pavement north of the Counter as well as east of it, so that its queue has a choice.
   *
   * <p>Two arms of a crossroads, three blocks wide each, meeting at the Counter. The point is not
   * to give the shop a nicer street but to take a tie out of the field's hands: over ground like
   * this there are two ways out of the Counter that cost the same, and a line drawn by walking down
   * the field's own gradient would be settled by the order its directions happen to be tried in.
   * What decides it, when the Counter faces, is the face.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void paveTheCrossroads(GameTestHelper helper) {
    paveTheStreet(helper, Blocks.STONE);
    for (int z = -1; z >= -STREET_END; z--) {
      for (int x = -1; x <= 1; x++) {
        helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
      }
    }
  }

  /**
   * Sends customers in one after another, a few ticks apart, in the order they arrive.
   *
   * <p>Spaced rather than put down all together, and in different cells rather than the same one,
   * because customers are solid: four of them in one cell shove each other off their places and out
   * of the street, and a queue that has been shoved around is not the queue being tested. Each one
   * is put down in the place the shop gives it, in the order the places are handed out.
   *
   * <p><b>Why they are put down in their places rather than made to walk into them.</b> What these
   * tests are about is the shop's line: which cell an arrival is given, that the cells are the
   * shop's own, and that they come out one block apart. Making each customer walk in would test all
   * of that and one more thing, at the price of a test that measures two features at once and fails
   * for either of them — and the walk is a subject of its own, with a place in the queue as its own
   * destination, in {@code customer_walks_to_the_counter}. What is asserted here instead is the
   * shop's decision, and what a customer does standing in the place it was given.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @param remaining how many customers are still to arrive
   * @param then what to do once they all have
   */
  private static void sendInTheQueue(
      GameTestHelper helper, CounterBlockEntity counter, int remaining, Runnable then) {
    if (remaining <= 0) {
      then.run();
      return;
    }
    int arrival = TuningDefaults.CUSTOMERS_PER_SHOP - remaining;
    putACustomerDown(helper, counter, seatInTheTemplate(counter, arrival));
    helper.runAfterDelay(
        QUEUE_SPACING_TICKS, () -> sendInTheQueue(helper, counter, remaining - 1, then));
  }

  /**
   * Walks a customer over two cells, one tick per step, without it going anywhere.
   *
   * <p>Wear is counted per cell entered, so the honest way to test a threshold is to put a customer
   * on the same cell the number of times the threshold asks for — and the way to do that in a world
   * is to move it back and forth, which is also the shortest walk a queue ever makes. Moved to the
   * middle of the cell because the customer keeps steering while it is there: the middle is as far
   * from the cell's edge as a position can be, so the step its own tick takes between one move and
   * the next cannot carry it over the edge into a third cell and count a footstep nobody asked for.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param customer the customer to move
   * @param first the cell to stand in on the first tick
   * @param second the cell to stand in on the next
   * @param steps how many times to move it, alternating between the two
   * @param then what to do once it has been moved that many times
   */
  private static void walkBackAndForth(
      GameTestHelper helper,
      Customer customer,
      BlockPos first,
      BlockPos second,
      int steps,
      Runnable then) {
    if (steps <= 0) {
      then.run();
      return;
    }
    BlockPos absolute = helper.absolutePos(first);
    customer.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
    helper.runAfterDelay(
        1, () -> walkBackAndForth(helper, customer, second, first, steps - 1, then));
  }

  /**
   * @param helper the running test's handle on its own copy of the world
   * @param customer the customer to measure
   * @return how far it is from the Counter's centre, squared
   */
  private static double distanceToCounter(GameTestHelper helper, Customer customer) {
    BlockPos counter = helper.absolutePos(COUNTER);
    return customer.distanceToSqr(counter.getX() + 0.5, counter.getY() + 0.5, counter.getZ() + 0.5);
  }

  /**
   * Puts a Counter down with a real player behind it, facing east, and opens its shop.
   *
   * <p>East, because that is the way the streets these tests build run: the Counter stands at the
   * west end of one and its queue leaves along it. A Counter faces whoever placed it, so this is
   * the Counter a player standing west of it would have made.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param owner who is putting the Counter down, who will own the shop
   * @return the Counter's block entity, now holding an open shop
   */
  private static CounterBlockEntity counterPlacedBy(GameTestHelper helper, Player owner) {
    return counterPlacedBy(helper, owner, Direction.EAST);
  }

  /**
   * Runs the placement hook vanilla runs, with a real player as the placer.
   *
   * <p>Called rather than simulated for the reason {@code CounterGameTests} gives: {@code
   * BlockItem.place} does its component work and then calls exactly this method, so calling it is
   * calling the hook that carries the ownership rule. A Counter with a shop behind it, an owner and
   * that owner online is a shop that keeps its customers; one with no shop at all turns them away
   * on its own clock.
   *
   * <p><b>The shop is opened, and that is one of the things a Counter does not do for itself any
   * more.</b> A Counter is placed closed and its owner opens it from its own screen, and a closed
   * shop sends the customers it has away on every arrival interval — so every test here that wants
   * a queue has to open its shop the way its owner would, or it is testing the doors rather than
   * the line. The facing is written into the block state rather than left to a placement context
   * for the same reason it is written anywhere: this test's street runs east, and the Counter it
   * wants is one facing east.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param owner who is putting the Counter down, who will own the shop
   * @param facing which way the Counter is to face, which is the way its queue will run
   * @return the Counter's block entity, now holding a shop
   */
  private static CounterBlockEntity counterPlacedBy(
      GameTestHelper helper, Player owner, Direction facing) {
    return counterAt(helper, COUNTER, owner, facing);
  }

  /**
   * Puts a Counter down somewhere and opens its shop.
   *
   * <p>The two-argument form above is the shop most of these tests use; this one is for the tests
   * that need a second shop, and it is the same work: the state a real placement ends in, then the
   * owner opening the shop from its screen.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param where the cell to put the Counter in, as a cell of the template
   * @param owner who is putting it down, who will own the shop
   * @param facing which way the Counter is to face
   * @return the Counter's block entity, now holding an open shop
   */
  private static CounterBlockEntity counterAt(
      GameTestHelper helper, BlockPos where, Player owner, Direction facing) {
    helper.setBlock(
        where, ModBlocks.COUNTER.get().defaultBlockState().setValue(CounterBlock.FACING, facing));
    BlockState state = helper.getLevel().getBlockState(helper.absolutePos(where));
    ((CounterBlock) state.getBlock())
        .setPlacedBy(
            helper.getLevel(),
            helper.absolutePos(where),
            state,
            owner,
            new ItemStack(ModItems.COUNTER.get()));

    CounterBlockEntity counter = helper.getBlockEntity(where, CounterBlockEntity.class);
    openTheShop(helper, counter, owner);
    return counter;
  }

  /**
   * @param helper the running test's handle on its own copy of the world
   * @param id the shop
   * @return the shop's durable record, which is where the questions about it are answered
   */
  private static ShopRecord shopAt(GameTestHelper helper, ShopId id) {
    return ShopData.of(helper.getLevel()).ledger().record(id).orElseThrow();
  }

  /**
   * Opens the shop standing at the test's Counter, the way its owner does from the Counter's
   * screen.
   *
   * <p>Asked of the ledger rather than by pressing the Open Shop button, because that button
   * answers the player who pressed it with the screen's view and a mock player has no connection to
   * send it down — {@code CounterGameTests} says the same thing from the other side. What these
   * tests need is the state the shop is in, and the press is where that state comes from rather
   * than the state itself.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @param owner who is opening it, who owns the shop
   */
  private static void openTheShop(GameTestHelper helper, CounterBlockEntity counter, Player owner) {
    ShopId id =
        counter
            .shopId()
            .orElseThrow(
                () -> new AssertionError("the Counter was placed without a shop behind it"));
    helper.assertTrue(
        ShopData.of(helper.getLevel()).ledger().setOpen(id, owner.getUUID(), true),
        "the owner of a shop that has just been placed should be able to open it");
  }

  /**
   * Signs a player on to the test server.
   *
   * <p>The shop's gate is that an owner or co-owner is <em>online</em>, and a fake player is on no
   * list, so the walled-in test needs one the server's player list actually holds. See {@code
   * CounterGameTests} for why this is the only supported way to do it, and why it is deprecated.
   *
   * @param helper the running test's handle on its own copy of the world
   * @return the player, now online
   */
  @SuppressWarnings("removal")
  private static ServerPlayer joinAMockServerPlayer(GameTestHelper helper) {
    return helper.makeMockServerPlayerInLevel();
  }
}
