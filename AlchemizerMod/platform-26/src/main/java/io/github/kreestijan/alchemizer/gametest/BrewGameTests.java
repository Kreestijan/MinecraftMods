package io.github.kreestijan.alchemizer.gametest;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.blockentity.AlchemicalTankBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.IgnitionChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity.StartResult;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberData;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.machine.StillProblem;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.tank.TankMath;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The in-game tests for brewing, run by {@code runGameTestServer}.
 *
 * <p>{@link StillGameTests} proves the machine knows it is assembled. These prove it then does
 * something: that the definitions in the datapack reach {@code core}, that a brew matches a recipe,
 * spends its ingredients, its bottle and its water, and fences a real {@code minecraft:potion} with
 * the right effects in it.
 *
 * <p><b>Why a game test and not a unit test.</b> Every rule a brew follows is already unit-tested
 * in {@code core}, against plain records. What cannot be unit-tested is the wiring between those
 * rules and the game: whether the datapack registries actually load, whether a {@code
 * Holder<MobEffect>} resolves from an id, whether the block entity's ticker is reached at all. This
 * is the only place those are exercised together.
 *
 * <p><b>Why the "paused" test matters most.</b> A brew that freezes when the fire goes out, and
 * resumes when it is lit again, is a claim about a timer that keeps running across ticks where
 * nothing should happen. Asserting it from outside the game would mean simulating the level;
 * asserting it here means leaving the firebox empty, watching the counter not move, and then
 * feeding it.
 *
 * <p>The registration arrangement — a test function registry, a {@code FunctionGameTestInstance},
 * and vanilla's empty structure — is explained in {@link StillGameTests} and is not repeated here.
 */
public final class BrewGameTests {

  private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
      DeferredRegister.create(Registries.TEST_FUNCTION, Alchemizer.MOD_ID);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      TEST_POTION_BREW = function("brew_makes_the_test_potion", BrewGameTests::brewsTheTestPotion);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      SWIFTNESS_BREW = function("brew_carries_the_recipes_effects", BrewGameTests::brewsSwiftness);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      FROZEN_WITHOUT_FIRE =
          function("brew_freezes_without_fire", BrewGameTests::freezesWithoutFire);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      MISMATCHED_LAYOUT =
          function("brew_without_a_recipe_is_murky_mess", BrewGameTests::brewsMurkyMess);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BOTTLE_STACK =
          function("brew_spends_bottles_from_a_stack", BrewGameTests::spendsFromBottleStack);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      OUTPUT_STACKS =
          function("brew_results_stack_in_the_output_slot", BrewGameTests::outputsStack);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      OUTPUT_BLOCKS =
          function("a_full_output_slot_refuses_a_start", BrewGameTests::refusesWhenOutputIsBlocked);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      FUEL_WAITS = function("fuel_does_not_burn_before_a_brew", BrewGameTests::fuelWaitsForABrew);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BREW_NOT_SAVED = function("a_saved_brew_does_not_come_back", BrewGameTests::aSavedBrewIsGone);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      PART_REMOVED =
          function("breaking_a_part_loses_the_brew", BrewGameTests::removingAPartLosesTheBrew);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      SPARE_TANK_REMOVED =
          function(
              "breaking_an_unconnected_tank_keeps_the_brew",
              BrewGameTests::removingAnUnconnectedTankKeepsTheBrew);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      CHAMBER_SPILLS =
          function(
              "breaking_the_chamber_drops_what_was_in_it", BrewGameTests::chamberSpillsItsItems);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      UNFORMED_SAYS_WHY =
          function(
              "an_unformed_machine_names_the_fault", BrewGameTests::anUnformedMachineNamesTheFault);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BATCH_BREW = function("a_batch_brews_and_spends_three_at_a_time", BrewGameTests::brewsABatch);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BATCH_CLAMPED =
          function(
              "a_batch_is_clamped_by_the_condensers_present",
              BrewGameTests::clampsTheBatchToTheCondensers);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      FUEL_GATE =
          function("a_tier_three_recipe_waits_for_tier_three_fire", BrewGameTests::waitsForItsFuel);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      PART_ADDED =
          function(
              "a_part_added_to_a_finished_still_is_noticed", BrewGameTests::noticesAPartAddedLater);

  /** Every body above, so that the loop in {@link #onRegisterGameTests} cannot miss one. */
  private static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>>
      TESTS =
          List.of(
              TEST_POTION_BREW,
              SWIFTNESS_BREW,
              FROZEN_WITHOUT_FIRE,
              MISMATCHED_LAYOUT,
              BOTTLE_STACK,
              OUTPUT_STACKS,
              OUTPUT_BLOCKS,
              FUEL_WAITS,
              BREW_NOT_SAVED,
              PART_REMOVED,
              SPARE_TANK_REMOVED,
              CHAMBER_SPILLS,
              UNFORMED_SAYS_WHY,
              BATCH_BREW,
              BATCH_CLAMPED,
              FUEL_GATE,
              PART_ADDED);

  /** Vanilla's blank template — {@code data/minecraft/structure/empty.nbt}. */
  private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

  /**
   * Headroom for the longest brew under test, not a budget.
   *
   * <p>One number for every body rather than one each: a test that finishes early stops there, so a
   * generous ceiling costs nothing, and a per-test timeout would be a second place to keep in step
   * with the recipe files.
   */
  private static final int MAX_TICKS = 800;

  /** How long the framework waits after placing the template before calling the body. */
  private static final int SETUP_TICKS = 1;

  /** The five-second hourglass, in ticks. Fixed by {@code BrewTime.TICKS_PER_SECOND}. */
  private static final int FIVE_SECOND_BREW_TICKS = 100;

  /** The fifteen-second hourglass, in ticks. */
  private static final int FIFTEEN_SECOND_BREW_TICKS = 300;

  /** The thirty-second hourglass, in ticks, which is what the tier-three recipe asks for. */
  private static final int THIRTY_SECOND_BREW_TICKS = 600;

  /** Extra ticks allowed on top of a brew's own time before its result is asserted. */
  private static final int BREW_GRACE_TICKS = 40;

  /** Enough water for several doses, and well inside the shipping tank capacity. */
  private static final int STARTING_WATER_MB = 4000;

  /** The potion id the Test Potion's file defines, and the recipe that produces it. */
  private static final String TEST_POTION_ID = "alchemizer:test_potion";

  /** The effect the swiftness recipe's file names, and the numbers it gives it. */
  private static final Identifier SPEED_EFFECT = Identifier.withDefaultNamespace("speed");

  private static final int SWIFTNESS_DURATION_TICKS = 3600;

  /** The effect the tier three recipe's potion carries, which is what proves it brewed at all. */
  private static final Identifier FIRE_RESISTANCE_EFFECT =
      Identifier.withDefaultNamespace("fire_resistance");

  /**
   * The name key {@code PotionContents.getName} builds: {@code item.minecraft.potion.effect.<id>}.
   */
  private static final String POTION_NAME_PREFIX = "item.minecraft.potion.effect.";

  /** Where {@link #buildStill} puts the three parts. */
  private static final BlockPos FIREBOX_POS = new BlockPos(0, 0, 0);

  private static final BlockPos CHAMBER_POS = new BlockPos(0, 1, 0);

  private static final BlockPos TANK_POS = new BlockPos(1, 1, 0);

  /**
   * Where a test puts a tank that is near the still without being part of it.
   *
   * <p>Far enough that it never touches the chamber, so no chain of tanks can reach it, but inside
   * the scan cube so that it is a thing the validator saw and chose not to count. That is the case
   * worth testing: it is the one where a chamber that threw its brew away on any nearby removal
   * would be wrong.
   */
  private static final BlockPos SPARE_TANK_POS = new BlockPos(3, 1, 0);

  /**
   * Two cells that each touch the pressure chamber, which is what makes a condenser connected.
   *
   * <p>Neither touches the other, and that is on purpose: a condenser is an endpoint of the
   * validator's walk and not a way through it, so a second condenser behind the first would not be
   * counted at all. Two cells side by side on the chamber are two condensers; two in a row are one.
   */
  private static final BlockPos CONDENSER_WEST_POS = new BlockPos(-1, 1, 0);

  private static final BlockPos CONDENSER_NORTH_POS = new BlockPos(0, 1, 1);

  /**
   * The batch the batch test brews, which is deliberately not one a condenser count can produce.
   */
  private static final int BATCH = 3;

  /**
   * Where a tier-three still puts the parts the tiers above one add.
   *
   * <p>Read off the design's own placement rules rather than invented: the valve goes on a side
   * face of the ignition chamber, the bellows on one of the valve's four side faces, the funnel on
   * top of the pressure chamber and the flue on the valve's top face. They are the same cells
   * {@code StillValidatorTest.tierFour} uses, moved onto the firebox's position.
   */
  private static final BlockPos VALVE_POS = new BlockPos(1, 0, 0);

  private static final BlockPos BELLOWS_POS = new BlockPos(1, 0, 1);

  private static final BlockPos FUNNEL_POS = new BlockPos(0, 2, 0);

  private static final BlockPos FLUE_POS = new BlockPos(1, 1, 0);

  /**
   * Where a tier-three still puts its tank.
   *
   * <p>Not {@link #TANK_POS}: the flue stack wants the top face of the valve, and that is the cell
   * the tier-one layout gives the tank. Moving the tank one cell round the chamber is the whole of
   * the difference between the two layouts.
   */
  private static final BlockPos TIER_THREE_TANK_POS = new BlockPos(0, 1, 1);

  /**
   * Where a crystal goes on the tier-three layout: against the flue stack, and against the funnel.
   *
   * <p>Deliberately touching neither chamber nor tank. A cell touching one of those would be
   * announced to the chamber by the tank it stands beside, which is the case that always worked;
   * this is the case that did not.
   */
  private static final BlockPos CRYSTAL_POS = new BlockPos(1, 2, 0);

  /**
   * A run of tubing off the chamber's free side face, and a condenser at the far end of it.
   *
   * <p>The condenser touches the tubing and nothing else, which is what §3.1 asks of it and also
   * what makes it invisible to a chamber that only listens for the three structural blocks.
   */
  private static final BlockPos BATCH_TUBING_POS = new BlockPos(-1, 1, 0);

  private static final BlockPos BATCH_CONDENSER_POS = new BlockPos(-2, 1, 0);

  private BrewGameTests() {}

  /**
   * @param modEventBus the bus this mod's registration events arrive on
   */
  public static void register(IEventBus modEventBus) {
    TEST_FUNCTIONS.register(modEventBus);
  }

  /**
   * Gives the bodies above something to be run as, one test per body.
   *
   * @param event the game test registries, open for this mod to add to
   */
  public static void onRegisterGameTests(RegisterGameTestsEvent event) {
    // A separate environment from the still test's, because an environment is a registry entry and
    // two handlers claiming the same id would be a duplicate. The contents are the same empty one:
    // a brew cares about the weather and the clock no more than a still does.
    Holder<TestEnvironmentDefinition<?>> environment =
        event.registerEnvironment(Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "brew"));

    for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : TESTS) {
      event.registerTest(
          test.getKey().identifier(),
          new FunctionGameTestInstance(
              test.getKey(),
              new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, SETUP_TICKS, true)));
    }
  }

  /**
   * Builds the smallest working still and puts water in its tank.
   *
   * <p>The same three blocks {@link StillGameTests} uses, and the same layout: the chamber sits on
   * the firebox because that is where the validator looks for it, and the tank touches the chamber
   * because a tank that does not is not connected.
   *
   * <p>The positions are constants because a test that breaks a part has to name the cell it is
   * breaking, and a second copy of the layout written out in that test is a second thing to keep in
   * step with this one.
   *
   * @param helper the running test's handle on its own copy of the world
   * @return the three block entities, so a test can drive them directly
   */
  private static TestStill buildStill(GameTestHelper helper) {
    helper.setBlock(FIREBOX_POS, ModBlocks.IGNITION_CHAMBER.get());
    helper.setBlock(CHAMBER_POS, ModBlocks.PRESSURE_CHAMBER.get());
    helper.setBlock(TANK_POS, ModBlocks.ALCHEMICAL_TANK.get());

    AlchemicalTankBlockEntity tankEntity =
        helper.getBlockEntity(TANK_POS, AlchemicalTankBlockEntity.class);
    tankEntity.setStoredMb(STARTING_WATER_MB);

    return new TestStill(
        helper.getBlockEntity(CHAMBER_POS, PressureChamberBlockEntity.class),
        helper.getBlockEntity(FIREBOX_POS, IgnitionChamberBlockEntity.class),
        tankEntity);
  }

  /**
   * Builds the same still with the parts up to tier three on it, and puts water in its tank.
   *
   * <p>Everything {@link #buildStill} does, plus the four blocks the tiers above one add. It is a
   * second builder rather than a flag on the first because the two layouts genuinely differ — one
   * cell of it is a tank in one and a flue stack in the other — and a parameter that moved a block
   * around would be harder to read than the six lines it saved.
   *
   * @param helper the running test's handle on its own copy of the world
   * @return the three block entities, the firebox being the one this layout builds
   */
  private static TestStill buildTierThreeStill(GameTestHelper helper) {
    helper.setBlock(FIREBOX_POS, ModBlocks.IGNITION_CHAMBER.get());
    helper.setBlock(CHAMBER_POS, ModBlocks.PRESSURE_CHAMBER.get());
    helper.setBlock(TIER_THREE_TANK_POS, ModBlocks.ALCHEMICAL_TANK.get());
    helper.setBlock(VALVE_POS, ModBlocks.AIR_PRESSURE_VALVE.get());
    helper.setBlock(BELLOWS_POS, ModBlocks.BELLOWS.get());
    helper.setBlock(FUNNEL_POS, ModBlocks.FUNNEL.get());
    helper.setBlock(FLUE_POS, ModBlocks.FLUE_STACK.get());

    AlchemicalTankBlockEntity tankEntity =
        helper.getBlockEntity(TIER_THREE_TANK_POS, AlchemicalTankBlockEntity.class);
    tankEntity.setStoredMb(STARTING_WATER_MB);

    return new TestStill(
        helper.getBlockEntity(CHAMBER_POS, PressureChamberBlockEntity.class),
        helper.getBlockEntity(FIREBOX_POS, IgnitionChamberBlockEntity.class),
        tankEntity);
  }

  /**
   * Fills the first ingredient slots in order and puts one glass bottle in the bottle slot.
   *
   * <p>Order matters: a recipe lists its ingredients from the first slot onwards, so an item in the
   * wrong slot is a different recipe or none at all.
   *
   * @param chamber the still's chamber
   * @param ingredients the ingredients, first slot first
   */
  private static void loadIngredients(PressureChamberBlockEntity chamber, Item... ingredients) {
    loadIngredients(chamber, 1, ingredients);
  }

  /**
   * The same, with a chosen number of bottles.
   *
   * @param chamber the still's chamber
   * @param bottles how many glass bottles to put in the bottle slot
   * @param ingredients the ingredients, first slot first
   */
  private static void loadIngredients(
      PressureChamberBlockEntity chamber, int bottles, Item... ingredients) {
    for (int i = 0; i < ingredients.length; i++) {
      chamber.setItem(
          PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST + i, new ItemStack(ingredients[i]));
    }
    chamber.setItem(
        PressureChamberBlockEntity.SLOT_BOTTLE, new ItemStack(Items.GLASS_BOTTLE, bottles));
  }

  /**
   * @param helper the running test
   * @param chamber the still's chamber
   * @param preset the hourglass setting to start at
   */
  private static void assertStarted(
      GameTestHelper helper, PressureChamberBlockEntity chamber, HourglassPreset preset) {
    StartResult result = chamber.startBrew(preset);
    helper.assertTrue(
        result == StartResult.STARTED,
        Component.literal(
            "a formed still with ingredients, a bottle and water should start a brew, but it said "
                + result));
  }

  /**
   * Brews the Test Potion and checks what came out.
   *
   * <p>The Test Potion is the one recipe whose potion has no effects, so everything asserted here
   * is about the machinery rather than about a mob effect: the stack is a real {@code
   * minecraft:potion}, it carries the recipe's potion id, and that id drives its name. The name
   * assertion is the point of the test — it is the one claim in the plan that could not be checked
   * from the sources alone.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void brewsTheTestPotion(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    still.firebox().setItem(0, new ItemStack(Items.COAL));

    // Read before starting, because starting is what spends the water.
    int waterBefore = still.tank().level().storedMb();
    assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

    helper.runAfterDelay(
        FIVE_SECOND_BREW_TICKS + BREW_GRACE_TICKS,
        () -> {
          ItemStack brewed = still.chamber().getItem(PressureChamberBlockEntity.SLOT_OUTPUT);
          helper.assertTrue(
              brewed.getItem() == Items.POTION,
              Component.literal("the output slot should hold a potion, but holds " + brewed));

          PotionContents contents = brewed.get(DataComponents.POTION_CONTENTS);
          if (contents == null) {
            helper.fail("the brewed potion carries no potion contents");
            return;
          }

          helper.assertTrue(
              contents.customEffects().isEmpty(),
              Component.literal(
                  "the test potion has no effects, but came out with " + contents.customEffects()));
          helper.assertTrue(
              contents.customName().equals(Optional.of(TEST_POTION_ID)),
              Component.literal(
                  "the potion should name itself "
                      + TEST_POTION_ID
                      + ", not "
                      + contents.customName()));
          helper.assertTrue(
              brewed
                  .getHoverName()
                  .equals(Component.translatable(POTION_NAME_PREFIX + TEST_POTION_ID)),
              Component.literal(
                  "the potion's displayed name should come from "
                      + POTION_NAME_PREFIX
                      + TEST_POTION_ID
                      + ", but was "
                      + brewed.getHoverName()));

          helper.assertTrue(
              still.chamber().getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).isEmpty(),
              Component.literal("a brew spends its ingredients when it starts"));
          helper.assertValueEqual(
              waterBefore - still.tank().level().storedMb(),
              TankMath.cost(1, ConfigCache.current().doseMb()),
              "millibuckets spent on one potion");

          helper.succeed();
        });
  }

  /**
   * Brews the swiftness recipe and checks the effect it carries.
   *
   * <p>This is the only test that exercises the outbound seam end to end: an effect id written as
   * text in a datapack file becomes an {@code EffectId} in {@code core}, becomes a {@code
   * Holder<MobEffect>} through the adapter, and becomes a {@code MobEffectInstance} on the stack.
   * If the adapter stopped resolving, the potion would still brew and simply carry nothing, which
   * is why the effect is asserted rather than assumed.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void brewsSwiftness(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.NETHER_WART, Items.SUGAR);
    still.firebox().setItem(0, new ItemStack(Items.COAL));
    assertStarted(helper, still.chamber(), HourglassPreset.FIFTEEN);

    helper.runAfterDelay(
        FIFTEEN_SECOND_BREW_TICKS + BREW_GRACE_TICKS,
        () -> {
          ItemStack brewed = still.chamber().getItem(PressureChamberBlockEntity.SLOT_OUTPUT);
          PotionContents contents = brewed.get(DataComponents.POTION_CONTENTS);
          if (contents == null) {
            helper.fail("the brewed potion carries no potion contents");
            return;
          }

          List<MobEffectInstance> effects = contents.customEffects();
          helper.assertValueEqual(effects.size(), 1, "number of effects on the swiftness potion");

          MobEffectInstance effect = effects.getFirst();
          helper.assertTrue(
              SPEED_EFFECT.equals(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value())),
              Component.literal(
                  "the swiftness potion should carry speed, not " + effect.getEffect()));
          helper.assertValueEqual(
              effect.getDuration(), SWIFTNESS_DURATION_TICKS, "effect duration");
          helper.assertValueEqual(effect.getAmplifier(), 0, "effect amplifier");

          helper.succeed();
        });
  }

  /**
   * Checks that a brew with no fire does not advance, and that it resumes when one is lit.
   *
   * <p>The brew is started with an empty firebox on purpose. Starting does not require fire — a
   * player may load a still and only then light it — so the counter should sit at zero for as long
   * as the chamber is cold. Feeding it afterwards is the half that matters: a still that reset
   * instead of freezing would pass the first assertion and fail the second.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void freezesWithoutFire(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

    // Deliberately more ticks than the whole five-second brew takes, so an unlit still that made
    // any progress at all would be caught rather than merely suspected.
    helper.runAfterDelay(
        FIVE_SECOND_BREW_TICKS + BREW_GRACE_TICKS,
        () -> {
          helper.assertValueEqual(
              still.chamber().brewSession().elapsedTicks(),
              0,
              "ticks elapsed on a brew with no fire");

          still.firebox().setItem(0, new ItemStack(Items.COAL));

          helper.runAfterDelay(
              BREW_GRACE_TICKS,
              () -> {
                helper.assertTrue(
                    still.chamber().brewSession().elapsedTicks() > 0,
                    Component.literal(
                        "lighting the firebox should resume the brew it was holding"));
                helper.succeed();
              });
        });
  }

  /**
   * Checks that a layout no recipe describes brews Murky Mess, and still costs what a brew costs.
   *
   * <p>One spider eye where the Test Potion's recipe wants two ingredients. Nothing matches, so the
   * brew fails — and the failure has to be paid for, or experimenting would be free. The water
   * assertion is what makes that concrete.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void brewsMurkyMess(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.SPIDER_EYE);
    still.firebox().setItem(0, new ItemStack(Items.COAL));

    // Read before starting, because starting is what spends the water — a failed brew is paid for
    // at the same moment a successful one is.
    int waterBefore = still.tank().level().storedMb();
    assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

    helper.runAfterDelay(
        FIVE_SECOND_BREW_TICKS + BREW_GRACE_TICKS,
        () -> {
          ItemStack brewed = still.chamber().getItem(PressureChamberBlockEntity.SLOT_OUTPUT);
          helper.assertTrue(
              brewed.getItem() == ModItems.MURKY_MESS.get(),
              Component.literal(
                  "a brew no recipe describes should yield murky mess, but yielded " + brewed));
          helper.assertValueEqual(
              waterBefore - still.tank().level().storedMb(),
              TankMath.cost(1, ConfigCache.current().doseMb()),
              "millibuckets spent on a failed brew");
          helper.succeed();
        });
  }

  /**
   * Checks that a brew takes the bottles it needs out of a stack and leaves the rest.
   *
   * <p>The rule this replaces was "exactly one bottle", which existed only because the bottle slot
   * doubled as the output. Now that the output has its own slot, a stack of bottles is a stack the
   * player loads once, and the count has to fall by the recipe's share rather than to zero.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void spendsFromBottleStack(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), 3, Items.SPIDER_EYE, Items.DANDELION);
    still.firebox().setItem(0, new ItemStack(Items.COAL));
    assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

    ItemStack bottles = still.chamber().getItem(PressureChamberBlockEntity.SLOT_BOTTLE);
    helper.assertTrue(
        bottles.getItem() == Items.GLASS_BOTTLE,
        Component.literal("the bottle slot should still hold bottles, but holds " + bottles));
    helper.assertValueEqual(bottles.getCount(), 2, "glass bottles left after starting one brew");

    helper.succeed();
  }

  /**
   * Checks that two brews of the same recipe leave one stack of two potions.
   *
   * <p>This is the pair of claims that only make sense together: the output slot accepts a second
   * brew's potion, which means the potion item itself has to stack, and it only does so because the
   * mod raises the item's stack limit. A potion that did not stack would leave the second brew
   * refused as OUTPUT_FULL rather than merged.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void outputsStack(GameTestHelper helper) {
    TestStill still = buildStill(helper);

    helper.assertValueEqual(
        new ItemStack(Items.POTION).getMaxStackSize(),
        ConfigCache.current().potionStackSize(),
        "the stack size the potion item reports");

    loadIngredients(still.chamber(), 2, Items.SPIDER_EYE, Items.DANDELION);
    still.firebox().setItem(0, new ItemStack(Items.COAL));
    assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

    helper.runAfterDelay(
        FIVE_SECOND_BREW_TICKS + BREW_GRACE_TICKS,
        () -> {
          loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
          assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

          helper.runAfterDelay(
              FIVE_SECOND_BREW_TICKS + BREW_GRACE_TICKS,
              () -> {
                ItemStack brewed = still.chamber().getItem(PressureChamberBlockEntity.SLOT_OUTPUT);
                helper.assertTrue(
                    brewed.getItem() == Items.POTION,
                    Component.literal("the output slot should hold potions, but holds " + brewed));
                helper.assertValueEqual(
                    brewed.getCount(), 2, "potions stacked in the output slot after two brews");
                helper.succeed();
              });
        });
  }

  /**
   * Checks that an output slot which cannot take the next potion refuses the start.
   *
   * <p>The blocking stack is a plain potion, which is a different potion from anything this still
   * brews and therefore cannot be merged into. The water assertion is the half that matters most: a
   * refusal that had already charged the player for ingredients and water would be worse than no
   * check at all.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void refusesWhenOutputIsBlocked(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    still.firebox().setItem(0, new ItemStack(Items.COAL));
    still
        .chamber()
        .setItem(PressureChamberBlockEntity.SLOT_OUTPUT, new ItemStack(Items.POTION, 64));

    helper.assertValueEqual(
        still.chamber().readiness(),
        StartResult.OUTPUT_FULL,
        "what a brew started with a blocked output slot comes to");

    int waterBefore = still.tank().level().storedMb();
    StartResult result = still.chamber().startBrew(HourglassPreset.FIVE);
    helper.assertTrue(
        result == StartResult.OUTPUT_FULL,
        Component.literal("a blocked output slot should refuse the start, but it said " + result));
    helper.assertValueEqual(
        still.tank().level().storedMb(), waterBefore, "millibuckets spent on a refused start");
    helper.assertTrue(
        still.chamber().getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).getCount() == 1,
        Component.literal("a refused start must not spend its ingredients"));
    helper.succeed();
  }

  /**
   * Checks that a loaded coal is not alight until a brew wants the fire.
   *
   * <p>The assertion is made twice on purpose: once immediately, and once after enough ticks that a
   * chamber which lit its fuel the way it used to would certainly have caught. An idle firebox used
   * to burn a loaded coal down to nothing while the player was still assembling the machine.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void fuelWaitsForABrew(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    still.firebox().setItem(0, new ItemStack(Items.COAL));

    helper.assertTrue(
        !still.firebox().isLit(),
        Component.literal("a firebox with no brew above it should not light its fuel"));

    helper.runAfterDelay(
        BREW_GRACE_TICKS,
        () -> {
          helper.assertTrue(
              !still.firebox().isLit(),
              Component.literal("a firebox with no brew above it should stay cold"));
          helper.assertValueEqual(
              still.firebox().getItem(0).getCount(), 1, "coal left in an idle firebox");

          assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

          helper.runAfterDelay(
              BREW_GRACE_TICKS,
              () -> {
                helper.assertTrue(
                    still.firebox().isLit(),
                    Component.literal("a firebox under a running brew should light its fuel"));
                helper.succeed();
              });
        });
  }

  /**
   * Checks that a chamber saved mid-brew comes back idle, with its items intact.
   *
   * <p>This is the rule that makes a still's ingredients cost something: the brew is spent at the
   * start and is not written down, so a reload loses it. The counterweight matters as much as the
   * claim — the <em>slots</em> are the player's and do persist, so the assertion that the bottles
   * are still there is what shows the save was not simply dropped whole.
   *
   * <p>The save is taken after forty ticks rather than at the start, so the brew has real progress
   * on the clock when it is written. Without that, a chamber that saved and restored a zero-length
   * brew would pass.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aSavedBrewIsGone(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), 3, Items.SPIDER_EYE, Items.DANDELION);
    still.firebox().setItem(0, new ItemStack(Items.COAL));
    assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

    helper.runAfterDelay(
        BREW_GRACE_TICKS,
        () -> {
          helper.assertTrue(
              still.chamber().brewSession().elapsedTicks() > 0,
              Component.literal("the brew should have made progress before it is saved"));

          CompoundTag saved = still.chamber().saveCustomOnly(helper.getLevel().registryAccess());
          PressureChamberBlockEntity reloaded =
              new PressureChamberBlockEntity(
                  CHAMBER_POS, ModBlocks.PRESSURE_CHAMBER.get().defaultBlockState());
          reloaded.loadCustomOnly(
              TagValueInput.create(
                  ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));

          helper.assertTrue(
              !reloaded.hasBrew(),
              Component.literal(
                  "a brew is not saved, so a reloaded chamber must not still be brewing"));
          helper.assertValueEqual(
              reloaded.brewSession().elapsedTicks(), 0, "ticks elapsed on a reloaded chamber");
          helper.assertValueEqual(
              reloaded.brewSession().totalTicks(),
              0,
              "ticks the reloaded chamber's missing brew takes");
          // What the player loaded is theirs, and stays.
          helper.assertValueEqual(
              reloaded.getItem(PressureChamberBlockEntity.SLOT_BOTTLE).getCount(),
              2,
              "bottles left in the reloaded chamber's bottle slot");
          helper.succeed();
        });
  }

  /**
   * Checks that taking a part away mid-brew costs the player the brew.
   *
   * <p>The tank goes, the still is no longer whole, and the ingredients and water that were spent
   * at the start are gone for good. The assertion on the elapsed count is the half that separates
   * this from a brew that merely paused: a chamber that held the timer would report the same
   * non-zero count it had before.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void removingAPartLosesTheBrew(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    still.firebox().setItem(0, new ItemStack(Items.COAL));
    assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

    helper.runAfterDelay(
        BREW_GRACE_TICKS,
        () -> {
          helper.assertTrue(
              still.chamber().brewSession().elapsedTicks() > 0,
              Component.literal("the brew should be running before the part is broken"));

          helper.setBlock(TANK_POS, Blocks.AIR);

          helper.assertTrue(
              !still.chamber().hasBrew(),
              Component.literal("breaking a part of the still should end the brew"));
          helper.assertValueEqual(
              still.chamber().brewSession().elapsedTicks(),
              0,
              "ticks left on a brew whose machine was broken");
          helper.succeed();
        });
  }

  /**
   * Checks that breaking a tank the still was not drawing on leaves the brew alone.
   *
   * <p>This is the precision the removal hook buys, and it is worth a test of its own. A tank can
   * stand near a still without being connected to it — a spare, or one feeding a different machine
   * — and a chamber that gave up its brew on any nearby removal would punish a player for tidying
   * up their base. The chamber revalidates before it believes itself broken, and this is what says
   * so.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void removingAnUnconnectedTankKeepsTheBrew(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    helper.setBlock(SPARE_TANK_POS, ModBlocks.ALCHEMICAL_TANK.get());
    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    still.firebox().setItem(0, new ItemStack(Items.COAL));
    assertStarted(helper, still.chamber(), HourglassPreset.FIVE);

    helper.runAfterDelay(
        BREW_GRACE_TICKS,
        () -> {
          helper.setBlock(SPARE_TANK_POS, Blocks.AIR);

          helper.assertTrue(
              still.chamber().hasBrew(),
              Component.literal("a tank the still was not drawing on is not a part of it"));
          helper.assertTrue(
              still.chamber().brewSession().elapsedTicks() > 0,
              Component.literal("the brew should still be running"));
          helper.succeed();
        });
  }

  /**
   * Checks that breaking the chamber drops what the player had put in it.
   *
   * <p>Nothing in this mod arranges that: the chamber is a {@code Container}, and vanilla drops a
   * container's contents when the block holding it is removed. The test is here because the rule is
   * a promise the design makes, and a later change that moved the items out of {@code Container} —
   * onto a capability, say — would quietly break it with nothing else to notice.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void chamberSpillsItsItems(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);

    helper.setBlock(CHAMBER_POS, Blocks.AIR);

    // A tick, so that the item entities the removal spawned have been added to the level before the
    // level is asked what is in it.
    helper.runAfterDelay(
        1,
        () -> {
          helper.assertItemEntityPresent(Items.SPIDER_EYE, CHAMBER_POS, 2.0);
          helper.assertItemEntityPresent(Items.DANDELION, CHAMBER_POS, 2.0);
          helper.succeed();
        });
  }

  /**
   * Checks that an unformed machine says which piece is missing, and at what tier it is.
   *
   * <p>A chamber alone is the simplest wrong machine there is, and the screen has one line to
   * explain it with. What it must not say is a bare "not assembled": the whole point of carrying
   * the fault to the client is that a player can be told which block to place.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void anUnformedMachineNamesTheFault(GameTestHelper helper) {
    helper.setBlock(CHAMBER_POS, ModBlocks.PRESSURE_CHAMBER.get());
    PressureChamberBlockEntity chamber =
        helper.getBlockEntity(CHAMBER_POS, PressureChamberBlockEntity.class);

    helper.assertValueEqual(
        chamber.still().firstProblem(),
        Optional.of(StillProblem.NO_IGNITION),
        "the fault a lone pressure chamber reports");

    PressureChamberData data = new PressureChamberData(chamber);
    helper.assertValueEqual(
        data.get(PressureChamberData.TIER), 0, "the tier of a machine that is not a still");
    helper.assertTrue(
        data.get(PressureChamberData.PROBLEM) >= 0,
        Component.literal("an unformed machine should name a fault, but named none"));
    helper.succeed();
  }

  /**
   * Brews a batch of three and checks what it spent and what came out.
   *
   * <p>Three is chosen because no arrangement of condensers produces it: one condenser allows two
   * and two allow four. So a batch of three is only reachable by a dial wound below the machine's
   * ceiling, which is the case where a clamp that rounded up, or a spend that used the ceiling
   * rather than the dial, would show up as a wrong count.
   *
   * <p>Everything is asserted against the difference across the brew rather than against a total,
   * because the tank starts with several doses in it and the point is what one run costs, not what
   * the tank holds.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void brewsABatch(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    helper.setBlock(CONDENSER_WEST_POS, ModBlocks.CONDENSER.get());
    helper.setBlock(CONDENSER_NORTH_POS, ModBlocks.CONDENSER.get());

    PressureChamberBlockEntity chamber = still.chamber();
    helper.assertValueEqual(chamber.maximumBatch(), 4, "the ceiling two condensers allow");

    // Empty wells throughout: a batch of three has to come out of the same three wells every time,
    // and leaving the fourth and fifth stocked would hide a spend that walked the whole row.
    loadBatch(chamber, BATCH, BATCH, Items.SPIDER_EYE, Items.DANDELION);
    setBatch(chamber, BATCH);
    helper.assertValueEqual(chamber.effectiveBatch(), BATCH, "the batch the dial asks for");
    still.firebox().setItem(0, new ItemStack(Items.COAL));

    int waterBefore = still.tank().level().storedMb();
    assertStarted(helper, chamber, HourglassPreset.FIVE);

    helper.runAfterDelay(
        FIVE_SECOND_BREW_TICKS + BREW_GRACE_TICKS,
        () -> {
          ItemStack brewed = chamber.getItem(PressureChamberBlockEntity.SLOT_OUTPUT);
          helper.assertTrue(
              brewed.getItem() == Items.POTION,
              Component.literal("a batch should brew the recipe's potion, but got " + brewed));
          helper.assertValueEqual(brewed.getCount(), BATCH, "potions in the output slot");

          for (int slot = PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST;
              slot < PressureChamberBlockEntity.SLOT_BOTTLE;
              slot++) {
            helper.assertTrue(
                chamber.getItem(slot).isEmpty(),
                Component.literal(
                    "a batch of "
                        + BATCH
                        + " should empty a well holding "
                        + BATCH
                        + ", but slot "
                        + slot
                        + " holds "
                        + chamber.getItem(slot)));
          }
          helper.assertTrue(
              chamber.getItem(PressureChamberBlockEntity.SLOT_BOTTLE).isEmpty(),
              Component.literal(
                  "a batch of "
                      + BATCH
                      + " should spend all "
                      + BATCH
                      + " bottles, but the bottle slot holds "
                      + chamber.getItem(PressureChamberBlockEntity.SLOT_BOTTLE)));

          helper.assertValueEqual(
              waterBefore - still.tank().level().storedMb(),
              TankMath.cost(BATCH, ConfigCache.current().doseMb()),
              "millibuckets spent on a batch");
          helper.succeed();
        });
  }

  /**
   * Checks that the batch is whatever the condensers allow, and never what the dial says.
   *
   * <p>The dial is deliberately wound far past the machine in both halves. A stale setting is the
   * failure this guards: a player who has chosen ten, then loses a condenser, must brew two and not
   * ten — and must go back to one when the last condenser goes, without the dial having to be
   * touched.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void clampsTheBatchToTheCondensers(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    PressureChamberBlockEntity chamber = still.chamber();
    helper.setBlock(CONDENSER_WEST_POS, ModBlocks.CONDENSER.get());

    loadBatch(chamber, 2, 2, Items.SPIDER_EYE, Items.DANDELION);
    setBatch(chamber, 10);

    helper.assertValueEqual(chamber.maximumBatch(), 2, "the ceiling one condenser allows");
    helper.assertValueEqual(chamber.effectiveBatch(), 2, "the batch one condenser allows");
    // The dial itself is left where the player put it, which is what lets the condenser be put back
    // without the setting being re-chosen.
    helper.assertValueEqual(chamber.batchMultiplier(), 10, "the dial, which is not the batch");

    helper.setBlock(CONDENSER_WEST_POS, Blocks.AIR);
    helper.assertValueEqual(chamber.maximumBatch(), 1, "the ceiling with no condensers");
    helper.assertValueEqual(chamber.effectiveBatch(), 1, "the batch with no condensers");

    // Two of each in the wells and two bottles, which a machine brewing one at a time accepts and
    // spends one of — the clamp is what makes this a start rather than a refusal either way.
    still.firebox().setItem(0, new ItemStack(Items.COAL));
    assertStarted(helper, chamber, HourglassPreset.FIVE);
    helper.assertValueEqual(
        chamber.getItem(PressureChamberBlockEntity.SLOT_OUTPUT).getCount(),
        0,
        "potions in the output slot while the brew is still running");
    helper.assertValueEqual(
        chamber.getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).getCount(),
        1,
        "ingredients left in the first well after a single brew was paid for");
    helper.succeed();
  }

  /**
   * Checks that a still built up further is told about it, rather than read off a stale scan.
   *
   * <p>The chamber remembers what it last found and looks again only when a block near it changes,
   * so the question here is not "does a crystal make a tier four still" — that is a unit test in
   * {@code core} — but "does the chamber hear about the crystal at all". It hears about a block
   * placed against itself or a tank by the same notification that reports the change; it has to
   * hear about one placed against a flue stack, a valve, a funnel or a condenser too, because those
   * are parts as well. Reading the tier before each placement is what makes that the question at
   * all: with nothing cached there is nothing to go stale.
   *
   * <p>The condenser half is the same failure one tier down, and is the one a player met first: one
   * condenser against the chamber doubles a run, and every one after it that touches only tubing
   * does nothing at all.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void noticesAPartAddedLater(GameTestHelper helper) {
    TestStill still = buildTierThreeStill(helper);
    PressureChamberBlockEntity chamber = still.chamber();

    // The read that turns the next assertion into a question about staleness rather than about the
    // ladder's rules.
    helper.assertValueEqual(chamber.still().tier().tier(), 3, "the tier before the crystal");

    helper.setBlock(CRYSTAL_POS, ModBlocks.RESONANCE_CRYSTAL.get());

    helper.assertValueEqual(chamber.still().tier().tier(), 4, "the tier the crystal should raise");

    helper.setBlock(BATCH_TUBING_POS, ModBlocks.ALCHEMICAL_TUBING.get());
    helper.assertValueEqual(chamber.maximumBatch(), 1, "the ceiling before the condenser");

    helper.setBlock(BATCH_CONDENSER_POS, ModBlocks.CONDENSER.get());

    helper.assertValueEqual(chamber.maximumBatch(), 2, "the ceiling the condenser should raise");
    helper.succeed();
  }

  /**
   * Brews a tier-three recipe on tier-two fuel, and then on the fuel it actually asks for.
   *
   * <p>This is the whole fuel ladder made observable, which is the reason the tier recipes exist at
   * all. Every rule in it is unit-tested in {@code core} against plain records; what cannot be
   * checked there is that the datapack files name the tiers they claim to, that a fuel item
   * resolves to the rung its tag puts it on, and that the two gates agree about a machine and a
   * recipe that are both real. A recipe whose tier was written one too low would pass every unit
   * test in the project and make the ladder decorative.
   *
   * <p>The first half asserts the brew did not move at all rather than that it was slow: a chamber
   * that advanced even one tick would fail, so the assertion does not depend on how long the test
   * waits, and the wait can be short.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void waitsForItsFuel(GameTestHelper helper) {
    TestStill still = buildTierThreeStill(helper);
    helper.assertValueEqual(
        still.chamber().still().tier().tier(),
        3,
        "the tier of a still with a valve, a bellows, a funnel and a flue");

    loadIngredients(still.chamber(), Items.MAGMA_CREAM, Items.BLAZE_POWDER);
    // Alchemical Coal is the second rung and this recipe is on the third, so the brew is accepted
    // and then never moves. Starting is not where the fuel is checked — a player may load a still
    // and light it afterwards — so this is a running brew held still, not a refused one.
    still.firebox().setItem(0, new ItemStack(ModItems.ALCHEMICAL_COAL.get()));
    assertStarted(helper, still.chamber(), HourglassPreset.THIRTY);

    helper.runAfterDelay(
        BREW_GRACE_TICKS,
        () -> {
          helper.assertValueEqual(
              still.chamber().brewSession().elapsedTicks(),
              0,
              "ticks elapsed on a tier-three brew burning tier-two fuel");

          // The same recipe on the same machine with one rung more fire under it. The firebox is
          // replaced rather than refuelled, because a chamber that is already alight will not look
          // at its slot until the fuel it lit has burned down, and the coal has most of two minutes
          // left on it.
          helper.setBlock(FIREBOX_POS, Blocks.AIR);
          helper.setBlock(FIREBOX_POS, ModBlocks.IGNITION_CHAMBER.get());
          helper
              .getBlockEntity(FIREBOX_POS, IgnitionChamberBlockEntity.class)
              .setItem(0, new ItemStack(Items.BLAZE_ROD));

          loadIngredients(still.chamber(), Items.MAGMA_CREAM, Items.BLAZE_POWDER);
          assertStarted(helper, still.chamber(), HourglassPreset.THIRTY);

          helper.runAfterDelay(
              THIRTY_SECOND_BREW_TICKS + BREW_GRACE_TICKS,
              () -> {
                ItemStack brewed = still.chamber().getItem(PressureChamberBlockEntity.SLOT_OUTPUT);
                helper.assertTrue(
                    brewed.getItem() == Items.POTION,
                    Component.literal(
                        "a tier-three recipe should brew on a blaze rod, but the output holds "
                            + brewed));

                PotionContents contents = brewed.get(DataComponents.POTION_CONTENTS);
                if (contents == null) {
                  helper.fail("the brewed potion carries no potion contents");
                  return;
                }
                List<MobEffectInstance> effects = contents.customEffects();
                helper.assertValueEqual(
                    effects.size(), 1, "number of effects on the fire resistance potion");
                helper.assertTrue(
                    FIRE_RESISTANCE_EFFECT.equals(
                        BuiltInRegistries.MOB_EFFECT.getKey(
                            effects.getFirst().getEffect().value())),
                    Component.literal(
                        "the tier-three potion should carry fire resistance, not "
                            + effects.getFirst().getEffect()));
                helper.succeed();
              });
        });
  }

  /**
   * Fills the wells with a chosen number in each and the bottle slot with a chosen number.
   *
   * <p>Distinct from {@link #loadIngredients}, which puts exactly one in each well. A batch test
   * needs the count to be the thing under test, so it is an argument here rather than a constant.
   *
   * @param chamber the still's chamber
   * @param perWell how many to put in each ingredient well
   * @param bottles how many glass bottles to put in the bottle slot
   * @param ingredients the ingredients, first well first
   */
  private static void loadBatch(
      PressureChamberBlockEntity chamber, int perWell, int bottles, Item... ingredients) {
    for (int i = 0; i < ingredients.length; i++) {
      chamber.setItem(
          PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST + i,
          new ItemStack(ingredients[i], perWell));
    }
    chamber.setItem(
        PressureChamberBlockEntity.SLOT_BOTTLE, new ItemStack(Items.GLASS_BOTTLE, bottles));
  }

  /**
   * Turns batching on at a chosen size, in the two steps a player takes.
   *
   * @param chamber the still's chamber
   * @param size the batch to dial in, which the machine may allow less of
   */
  private static void setBatch(PressureChamberBlockEntity chamber, int size) {
    chamber.setBatchMultiplier(size);
    chamber.toggleBatch();
  }

  /**
   * @param body the function to run
   * @param path the test's name, which is also its registry path
   * @return the registered function
   */
  private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function(
      String path, Consumer<GameTestHelper> body) {
    return TEST_FUNCTIONS.register(path, () -> body);
  }

  /**
   * @param chamber the still's pressure chamber, which owns the brew
   * @param firebox the ignition chamber below it
   * @param tank the tank feeding it
   */
  private record TestStill(
      PressureChamberBlockEntity chamber,
      IgnitionChamberBlockEntity firebox,
      AlchemicalTankBlockEntity tank) {}
}
