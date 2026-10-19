package io.github.kreestijan.alchemizer.gametest;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.block.BellowsBlock;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-game tests for the still parts' shapes and the bellows' facing.
 *
 * <p>{@link StillGameTests} tests whether the parts form a still; this class tests what a player
 * sees and touches. The shapes only exist in a world, and the facing only happens on the item's
 * placement path, so neither can be driven from the unit tests in {@code core}. The two live
 * together because they are one concern — the parts' appearance behaviour — and apart from the
 * machine tests so neither class grows past the size the project allows.
 */
public final class StillPartGameTests {

  private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
      DeferredRegister.create(Registries.TEST_FUNCTION, Alchemizer.MOD_ID);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      PART_SHAPES =
          TEST_FUNCTIONS.register(
              "part_shapes_are_partial_and_collidable",
              () -> StillPartGameTests::partShapesArePartialAndCollidable);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BELLOWS_FACES =
          TEST_FUNCTIONS.register(
              "bellows_faces_the_block_it_is_placed_against",
              () -> StillPartGameTests::bellowsFacesTheBlockItIsPlacedAgainst);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BELLOWS_ANIMATION =
          TEST_FUNCTIONS.register(
              "bellows_animation_follows_the_chamber",
              () -> StillPartGameTests::bellowsAnimationFollowsTheChamber);

  /** Vanilla's blank template — {@code data/minecraft/structure/empty.nbt}. */
  private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

  /** Headroom; both bodies finish in their first tick. */
  private static final int MAX_TICKS = 100;

  /** How long the framework waits after placing the template before calling the body. */
  private static final int SETUP_TICKS = 1;

  private StillPartGameTests() {}

  /**
   * @param modEventBus the bus this mod's registration events arrive on
   */
  public static void register(IEventBus modEventBus) {
    TEST_FUNCTIONS.register(modEventBus);
  }

  /**
   * @param event the game test registries, open for this mod to add to
   */
  public static void onRegisterGameTests(RegisterGameTestsEvent event) {
    Holder<TestEnvironmentDefinition<?>> environment =
        event.registerEnvironment(Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "still_part"));

    event.registerTest(
        Identifier.fromNamespaceAndPath(
            Alchemizer.MOD_ID, "part_shapes_are_partial_and_collidable"),
        new FunctionGameTestInstance(
            PART_SHAPES.getKey(),
            new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, SETUP_TICKS, true)));
    event.registerTest(
        Identifier.fromNamespaceAndPath(
            Alchemizer.MOD_ID, "bellows_faces_the_block_it_is_placed_against"),
        new FunctionGameTestInstance(
            BELLOWS_FACES.getKey(),
            new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, SETUP_TICKS, true)));
    event.registerTest(
        Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "bellows_animation_follows_the_chamber"),
        new FunctionGameTestInstance(
            BELLOWS_ANIMATION.getKey(),
            new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, SETUP_TICKS, true)));
  }

  /**
   * Places every still part and checks that its outline is its own shape rather than a full cube,
   * and that it still collides.
   *
   * <p>The bellows is then checked in all four facings. Its collision footprint is symmetric in
   * plan, so every facing is expected to give the same shape; the facing turns the model and its
   * nozzle, not the collision box. What this catches is a facing whose shape lookup fails.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void partShapesArePartialAndCollidable(GameTestHelper helper) {
    Block[] parts = {
      ModBlocks.AIR_PRESSURE_VALVE.get(),
      ModBlocks.BELLOWS.get(),
      ModBlocks.FUNNEL.get(),
      ModBlocks.FLUE_STACK.get(),
      ModBlocks.RESONANCE_CRYSTAL.get(),
      ModBlocks.CONDENSER.get(),
      ModBlocks.ADVANCED_CONDENSER.get()
    };

    for (int i = 0; i < parts.length; i++) {
      BlockPos pos = new BlockPos(i, 1, 0);
      helper.setBlock(pos, parts[i]);
      assertPartialAndCollidable(helper, pos, parts[i].getName().getString());
    }

    BlockPos bellows = new BlockPos(0, 3, 0);
    helper.setBlock(bellows, ModBlocks.BELLOWS.get());
    for (Direction facing : Direction.Plane.HORIZONTAL) {
      helper.setBlock(
          bellows,
          ModBlocks.BELLOWS.get().defaultBlockState().setValue(BellowsBlock.FACING, facing));
      VoxelShape shape = shapeAt(helper, bellows);
      helper.assertTrue(
          !shape.isEmpty() && !Block.isShapeFullBlock(shape),
          Component.literal("the bellows must stay shaped when facing " + facing + ": " + shape));
      helper.assertTrue(
          !collisionAt(helper, bellows).isEmpty(),
          Component.literal("the bellows must collide when facing " + facing));
    }

    helper.succeed();
  }

  /**
   * Clicks a stone block's east face and its top face and checks where the bellows' mount points.
   *
   * <p>The context is built with a null player, which is why the top-face case proves the fallback:
   * {@code getHorizontalDirection()} answers north when there is no player, so the mount faces
   * south. The east-face case is the one that matters and does not involve the player at all.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void bellowsFacesTheBlockItIsPlacedAgainst(GameTestHelper helper) {
    BlockPos clicked = new BlockPos(1, 1, 1);
    helper.setBlock(clicked, Blocks.STONE);
    BlockPos clickedAbs = helper.absolutePos(clicked);

    BlockState sideState =
        ModBlocks.BELLOWS
            .get()
            .getStateForPlacement(
                new BlockPlaceContext(
                    helper.getLevel(),
                    null,
                    InteractionHand.MAIN_HAND,
                    ItemStack.EMPTY,
                    new BlockHitResult(
                        Vec3.atCenterOf(clickedAbs), Direction.EAST, clickedAbs, false)));
    helper.assertTrue(
        sideState.getValue(BellowsBlock.FACING) == Direction.WEST,
        Component.literal(
            "clicking a block's east face should mount the bellows facing west: " + sideState));

    BlockState topState =
        ModBlocks.BELLOWS
            .get()
            .getStateForPlacement(
                new BlockPlaceContext(
                    helper.getLevel(),
                    null,
                    InteractionHand.MAIN_HAND,
                    ItemStack.EMPTY,
                    new BlockHitResult(
                        Vec3.atCenterOf(clickedAbs), Direction.UP, clickedAbs, false)));
    helper.assertTrue(
        topState.getValue(BellowsBlock.FACING) == Direction.SOUTH,
        Component.literal(
            "a click on a top face with no player should fall back to south: " + topState));

    helper.succeed();
  }

  /**
   * Checks that the while-brewing mode follows the chamber and that the other modes do not.
   *
   * <p>The motion itself is client-side rendering, so there is no frame to assert here; what the
   * server contract has to get right is the {@code active} bit the renderer reads.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void bellowsAnimationFollowsTheChamber(GameTestHelper helper) {
    BlockPos pos = new BlockPos(0, 1, 0);
    BellowsBlock bellows = ModBlocks.BELLOWS.get();
    helper.setBlock(
        pos,
        bellows
            .defaultBlockState()
            .setValue(BellowsBlock.MODE, BellowsBlock.Mode.WHILE_BREWING)
            .setValue(BellowsBlock.ACTIVE, false));

    bellows.setAnimating(
        helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), true);
    BlockState running = helper.getBlockState(pos);
    helper.assertTrue(
        running.getValue(BellowsBlock.ACTIVE),
        Component.literal("a while-brewing bellows should start with the chamber: " + running));

    bellows.setAnimating(helper.getLevel(), helper.absolutePos(pos), running, false);
    BlockState stopped = helper.getBlockState(pos);
    helper.assertTrue(
        !stopped.getValue(BellowsBlock.ACTIVE),
        Component.literal("a while-brewing bellows should stop with the chamber: " + stopped));

    // The default mode is always-on, and the chamber must not be able to park it.
    helper.setBlock(pos, bellows.defaultBlockState());
    bellows.setAnimating(
        helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), false);
    BlockState always = helper.getBlockState(pos);
    helper.assertTrue(
        always.getValue(BellowsBlock.ACTIVE),
        Component.literal("an always-on bellows should ignore the chamber: " + always));
    helper.succeed();
  }

  /**
   * @param helper the running test
   * @param pos a relative position
   * @return the outline of the block there
   */
  private static VoxelShape shapeAt(GameTestHelper helper, BlockPos pos) {
    return helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos));
  }

  /**
   * @param helper the running test
   * @param pos a relative position
   * @return the collision shape of the block there
   */
  private static VoxelShape collisionAt(GameTestHelper helper, BlockPos pos) {
    return helper.getBlockState(pos).getCollisionShape(helper.getLevel(), helper.absolutePos(pos));
  }

  /**
   * @param helper the running test
   * @param pos a relative position holding a part
   * @param name the part's display name, for the failure message
   */
  private static void assertPartialAndCollidable(GameTestHelper helper, BlockPos pos, String name) {
    VoxelShape shape = shapeAt(helper, pos);
    helper.assertTrue(
        !shape.isEmpty() && !Block.isShapeFullBlock(shape),
        Component.literal(name + " should have a shaped outline, not a full cube: " + shape));
    helper.assertTrue(
        !collisionAt(helper, pos).isEmpty(), Component.literal(name + " should still collide"));
  }
}
