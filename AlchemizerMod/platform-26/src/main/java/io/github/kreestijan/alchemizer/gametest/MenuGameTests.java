package io.github.kreestijan.alchemizer.gametest;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.brewing.blockentity.AlchemicalTankBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity;
import io.github.kreestijan.alchemizer.brewing.blockentity.PressureChamberBlockEntity.StartResult;
import io.github.kreestijan.alchemizer.brewing.menu.AlchemicalTankMenu;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberData;
import io.github.kreestijan.alchemizer.brewing.menu.PressureChamberMenu;
import io.github.kreestijan.alchemizer.core.recipe.BrewRecipe;
import io.github.kreestijan.alchemizer.core.recipe.HourglassPreset;
import io.github.kreestijan.alchemizer.core.tank.TankMath;
import io.github.kreestijan.alchemizer.defs.ModDefinitions;
import io.github.kreestijan.alchemizer.defs.RecipeBookView;
import io.github.kreestijan.alchemizer.registry.ModAttachments;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The in-game tests for the three menus, and for the tank's own behaviour, run by {@code
 * runGameTestServer}.
 *
 * <p>{@link BrewGameTests} drives the chamber by calling {@code startBrew} directly, which is the
 * server-side API and nothing to do with a screen. These tests drive it the way a player does —
 * through {@link PressureChamberMenu#clickMenuButton} — and assert what the screen is allowed to
 * learn back. That distinction is the whole point of M6: a screen is only a view if the machine's
 * state reaches it from the server, and the only way to test a view is to press its buttons.
 *
 * <p><b>What can be tested here and what cannot.</b> A game test runs on a dedicated server, so no
 * {@code Screen} is ever constructed: {@code PressureChamberScreen} is not mentioned here at all,
 * and its drawing is unchecked by this class. What is checked is everything behind it — the menu
 * the screen is handed, the numbers it reads, and the server's response to each button id. Opening
 * a window and looking at it stays a manual step.
 *
 * <p><b>Why the buttons are pressed through the menu rather than through the chamber.</b> {@code
 * clickMenuButton} is the single entry point a client's button press arrives at, and it is where
 * the design's promise lives: the request carries an id and the server re-derives everything else.
 * A test that called {@code startBrew} instead would pass whether or not the menu re-validated
 * anything.
 *
 * <p>The registration arrangement — a test function registry, a {@code FunctionGameTestInstance},
 * and vanilla's empty structure — is explained in {@link StillGameTests} and is not repeated here.
 */
public final class MenuGameTests {

  private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
      DeferredRegister.create(Registries.TEST_FUNCTION, Alchemizer.MOD_ID);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      PRESSURE_REFUSALS =
          function(
              "menu_refuses_a_start_the_still_cannot_pay_for",
              MenuGameTests::refusesUnpayableStart);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      PRESSURE_BUTTONS =
          function(
              "menu_start_uses_the_servers_own_preset", MenuGameTests::startsAtTheServersPreset);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      PRESSURE_SLOTS =
          function("menu_slots_answer_to_the_chamber", MenuGameTests::slotsAnswerToChamber);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      IGNITION_SLOTS = function("fuel_menu_takes_fuel_and_nothing_else", MenuGameTests::fuelOnly);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      FUEL_COUNTDOWN =
          function("the_fuel_menu_counts_the_burn_down", MenuGameTests::countsTheBurnDown);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      FILL_BUTTON =
          function(
              "menu_fills_the_tanks_from_the_cursor", MenuGameTests::fillsTheTankFromTheCursor);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      POUR_IN_WORLD =
          function("a_tank_takes_water_from_the_hand", MenuGameTests::poursFromTheWorld);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      TANK_KEEPS_WATER =
          function("a_broken_tank_keeps_its_water", MenuGameTests::keepsItsWaterWhenBroken);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      TANK_MENU_LEVEL =
          function("the_tank_menu_reads_the_tanks_level", MenuGameTests::tankMenuReadsTheLevel);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BOOK_LOAD =
          function(
              "the_book_loads_what_the_inventory_has", MenuGameTests::bookLoadsWhatTheInventoryHas);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BOOK_FUNNEL = function("the_book_needs_a_funnel", MenuGameTests::bookNeedsAFunnel);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BOOK_STARS =
          function("the_book_keeps_the_stars_it_was_given", MenuGameTests::bookKeepsItsStars);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BOOK_RETURNS =
          function("a_load_returns_what_the_machine_was_holding", MenuGameTests::bookReturnsFirst);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      BOOK_FULL_INVENTORY =
          function("a_load_that_cannot_be_returned_is_refused", MenuGameTests::bookRefusesWhenFull);

  /** Every body above, so that the loop in {@link #onRegisterGameTests} cannot miss one. */
  private static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>>
      TESTS =
          List.of(
              PRESSURE_REFUSALS,
              PRESSURE_BUTTONS,
              PRESSURE_SLOTS,
              IGNITION_SLOTS,
              FUEL_COUNTDOWN,
              FILL_BUTTON,
              POUR_IN_WORLD,
              TANK_KEEPS_WATER,
              TANK_MENU_LEVEL,
              BOOK_LOAD,
              BOOK_FUNNEL,
              BOOK_STARS,
              BOOK_RETURNS,
              BOOK_FULL_INVENTORY);

  /** Vanilla's blank template — {@code data/minecraft/structure/empty.nbt}. */
  private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

  /**
   * Headroom for a body that only presses buttons, not a budget.
   *
   * <p>Almost every body here finishes on the tick it is called, so this is only the framework's
   * ceiling. The brews these tests start are never waited on: what a brew does with its time is
   * {@link BrewGameTests}' subject, and repeating it here would cost ticks to re-prove something
   * already proven. The one exception is the fuel countdown, which has to watch a fire catch and
   * then burn for a while — tens of ticks, not hundreds.
   */
  private static final int MAX_TICKS = 100;

  /** How long the framework waits after placing the template before calling the body. */
  private static final int SETUP_TICKS = 1;

  /** Enough water for several doses, and well inside the shipping tank capacity. */
  private static final int STARTING_WATER_MB = 4000;

  /** The window id the server would have chosen. Any number will do; nothing here reads it. */
  private static final int CONTAINER_ID = 0;

  /**
   * The first slot of the player's main inventory as the menus number it.
   *
   * <p>Not zero: the machine's own slots come first, and the pressure chamber now has one more of
   * them than it used to. Read from the chamber rather than written as a number for exactly that
   * reason — this is the boundary the menu's shift-click routing turns on, and a stale copy of it
   * here would make these tests pass against a menu that routes the wrong way.
   *
   * <p>{@code addStandardInventorySlots} adds the main inventory before the hotbar, and starts it
   * at the player's index nine, so menu slot {@code SLOT_COUNT} is player inventory slot nine.
   */
  private static final int MENU_INV_SLOT_START = PressureChamberBlockEntity.SLOT_COUNT;

  /** The player inventory index that menu slot {@link #MENU_INV_SLOT_START} holds. */
  private static final int PLAYER_INV_SLOT_START = 9;

  /** The fifteen-second hourglass, in ticks. Fixed by {@code BrewTime.TICKS_PER_SECOND}. */
  private static final int FIFTEEN_SECOND_BREW_TICKS = 300;

  /** Ticks to wait for a loaded fuel to catch, which is one tick's work plus one for the order. */
  private static final int LIT_TICKS = 2;

  /**
   * Ticks to leave a fire burning before the flame's fraction is read again.
   *
   * <p>More than twenty, so that a fraction which never moved would be caught rather than a reading
   * merely landing inside the same piece of the piece of coal.
   */
  private static final int COUNTDOWN_TICKS = 25;

  /**
   * The recipes the book tests reach for.
   *
   * <p>All are the mod's own. The first pair sit either side of a tier-three still's ceiling: one
   * at tier three, which such a still may load, and one at tier four, which it may not. That pair
   * is what makes the tier check a test of the boundary rather than of the feature being present.
   * Swiftness is the third because it shares no ingredient with fire resistance, which is what lets
   * a second load be shown to have returned the first one's rather than quietly kept it.
   */
  private static final String SWIFTNESS_RECIPE = "alchemizer:swiftness";

  private static final String FIRE_RESISTANCE_RECIPE = "alchemizer:fire_resistance";

  private static final String REGENERATION_RECIPE = "alchemizer:regeneration";

  /** Where {@link #buildTierThreeStill} puts the parts the tiers above one add. */
  private static final BlockPos VALVE_POS = new BlockPos(1, 0, 0);

  private static final BlockPos BELLOWS_POS = new BlockPos(1, 0, 1);

  private static final BlockPos FUNNEL_POS = new BlockPos(0, 2, 0);

  private static final BlockPos FLUE_POS = new BlockPos(1, 1, 0);

  /**
   * Where a tier-three still puts its tank: one cell round the chamber from the tier-one layout,
   * because the flue stack wants the valve's top face and that is the cell tier one gives the tank.
   */
  private static final BlockPos TIER_THREE_TANK_POS = new BlockPos(0, 1, 1);

  private MenuGameTests() {}

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
    // A third environment, for the reason BrewGameTests gives: an environment is a registry entry,
    // and two handlers claiming one id would be a duplicate.
    Holder<TestEnvironmentDefinition<?>> environment =
        event.registerEnvironment(Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "menu"));

    for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : TESTS) {
      event.registerTest(
          test.getKey().identifier(),
          new FunctionGameTestInstance(
              test.getKey(),
              new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, SETUP_TICKS, true)));
    }
  }

  /**
   * Presses Start on a still that cannot pay for a brew, one missing thing at a time.
   *
   * <p>The ladder is the test. Each step supplies exactly the thing the previous refusal named, and
   * the refusal has to move on — an implementation that always answered the same way, or that only
   * ever asked whether the still was assembled, would fail here rather than at the first press.
   *
   * <p>Fire is deliberately absent throughout. A brew that may not start for want of ingredients is
   * refused for that reason and not for a cold firebox, which is the same rule {@link
   * BrewGameTests} proves from the other end when it starts a brew with nothing burning.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void refusesUnpayableStart(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());

    helper.assertValueEqual(
        menu.readiness(), Optional.of(StartResult.NOTHING_TO_BREW), "a still with nothing loaded");
    helper.assertFalse(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_START),
        "the start button on a still with nothing to brew");
    helper.assertFalse(
        still.chamber().brewSession().isRunning(), "a refused start must not have started a brew");

    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    still.chamber().setItem(PressureChamberBlockEntity.SLOT_BOTTLE, ItemStack.EMPTY);
    helper.assertValueEqual(
        menu.readiness(), Optional.of(StartResult.NEEDS_BOTTLES), "a still with no bottle");
    helper.assertFalse(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_START),
        "the start button on a still with no bottle");

    still
        .chamber()
        .setItem(PressureChamberBlockEntity.SLOT_BOTTLE, new ItemStack(Items.GLASS_BOTTLE));
    helper.assertValueEqual(
        menu.readiness(), Optional.of(StartResult.STARTED), "a still that can pay for a brew");
    helper.assertTrue(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_START),
        "the start button on a still that can pay for a brew");
    helper.assertTrue(
        still.chamber().brewSession().isRunning(), "an accepted start must have started a brew");
    helper.succeed();
  }

  /**
   * Checks the buttons the screen actually has: four hourglasses and a start.
   *
   * <p>Two claims. The first is that a button press changes what the still will brew at, and that
   * the change is visible through the menu's data slots rather than only inside the chamber — that
   * round trip is what the screen's bracketed hourglass reads. The second is that the start button
   * brews at the preset <em>the server remembers</em> and not at one named in the request, which is
   * what makes a modified client unable to pick its own brew time.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void startsAtTheServersPreset(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    still.chamber().setItem(PressureChamberBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());

    helper.assertValueEqual(
        menu.selectedPreset(), Optional.of(HourglassPreset.FIVE), "the hourglass before any press");

    int fifteen = HourglassPreset.FIFTEEN.ordinal();
    helper.assertTrue(
        menu.clickMenuButton(player, fifteen), "the button for the fifteen-second hourglass");
    helper.assertValueEqual(
        menu.selectedPreset(),
        Optional.of(HourglassPreset.FIFTEEN),
        "the hourglass the menu reports after its button was pressed");

    helper.assertValueEqual(
        menu.readiness(),
        Optional.of(StartResult.STARTED),
        "a loaded still before the start button");
    helper.assertTrue(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_START), "the start button");
    helper.assertValueEqual(
        menu.readiness(),
        Optional.of(StartResult.ALREADY_BREWING),
        "readiness once the start button has been accepted");
    helper.assertValueEqual(
        still.chamber().brewSession().totalTicks(),
        FIFTEEN_SECOND_BREW_TICKS,
        "the brew time the still dialled in");

    // One past the last button. A screen never sends this; a client that made one up must be told
    // it was not understood rather than have it read as the fill button, which is the next id
    // along.
    helper.assertFalse(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_FILL_TANK + 1),
        "a button id that is not one");
    helper.assertFalse(menu.clickMenuButton(player, -1), "a negative button id");
    helper.succeed();
  }

  /**
   * Checks that the slots the menu shows behave as the chamber's own slots.
   *
   * <p>Most of the claims are about the seam between a menu and a container. A vanilla slot does
   * not consult {@code Container.canPlaceItem}, so each refusal below is a place where a slot that
   * forgot to ask would quietly disagree with the machine. The enhancer pair adds the other
   * direction: slots that are drawn and hoverable while the chamber still says no to everything
   * offered to them, which is a state the two halves have to agree on rather than one that either
   * can hold alone.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void slotsAnswerToChamber(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());

    // Both halves are asserted for each enhancer slot, because either alone passes for the wrong
    // reason: a slot parked off the panel is not shown, and a slot that takes anything is not
    // refusing. Nothing supplies an enhancer yet, so visible-and-refusing is the whole of the
    // intended behaviour until one exists.
    for (int i = 0; i < PressureChamberBlockEntity.SLOT_ENHANCER_COUNT; i++) {
      Slot enhancer = menu.slots.get(PressureChamberBlockEntity.SLOT_ENHANCER_FIRST + i);
      helper.assertTrue(enhancer.isActive(), "an enhancer slot should be shown");
      helper.assertTrue(
          enhancer.x >= 0 && enhancer.y >= 0, "an enhancer slot should sit on the panel");
      helper.assertFalse(
          enhancer.mayPlace(new ItemStack(Items.DIAMOND)),
          "an enhancer slot should refuse an item, since nothing supplies an enhancer yet");
    }
    helper.assertFalse(
        menu.slots
            .get(PressureChamberBlockEntity.SLOT_BOTTLE)
            .mayPlace(new ItemStack(Items.DIAMOND)),
        "the bottle slot should refuse an item that is not a bottle");
    helper.assertFalse(
        menu.slots
            .get(PressureChamberBlockEntity.SLOT_OUTPUT)
            .mayPlace(new ItemStack(Items.POTION)),
        "the output slot should refuse a potion, since the machine is what fills it");

    // And a potion already in the output travels the other way: shift-clicked, it goes to the
    // player rather than being routed back into the machine's own slots. This is the assertion that
    // catches the shift-click boundary being left one slot short.
    still.chamber().setItem(PressureChamberBlockEntity.SLOT_OUTPUT, new ItemStack(Items.POTION));
    menu.quickMoveStack(player, PressureChamberBlockEntity.SLOT_OUTPUT);
    helper.assertTrue(
        still.chamber().getItem(PressureChamberBlockEntity.SLOT_OUTPUT).isEmpty(),
        "a shift-clicked potion should leave the output slot");
    helper.assertTrue(
        player.getInventory().contains(new ItemStack(Items.POTION)),
        "a shift-clicked potion should reach the player's inventory");
    helper.assertTrue(
        still.chamber().getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).isEmpty(),
        "a shift-clicked potion should not be routed into an ingredient slot");

    // A bottle shift-clicked from the player's inventory reaches the bottle slot, not the first
    // ingredient slot. Both would accept it, which is exactly why the routing is by item.
    player.getInventory().setItem(PLAYER_INV_SLOT_START, new ItemStack(Items.GLASS_BOTTLE));
    menu.quickMoveStack(player, MENU_INV_SLOT_START);
    ItemStack bottled = menu.slots.get(PressureChamberBlockEntity.SLOT_BOTTLE).getItem();
    helper.assertTrue(
        bottled.getItem() == Items.GLASS_BOTTLE,
        "a shift-clicked bottle should land in the bottle slot");
    helper.assertTrue(
        menu.slots.get(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).getItem().isEmpty(),
        "a shift-clicked bottle should not be taken by an ingredient slot");

    // Anything else goes to an ingredient slot, which takes whatever it is given.
    player.getInventory().setItem(PLAYER_INV_SLOT_START + 1, new ItemStack(Items.DIAMOND));
    menu.quickMoveStack(player, MENU_INV_SLOT_START + 1);
    ItemStack ingredient =
        menu.slots.get(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).getItem();
    helper.assertTrue(
        ingredient.getItem() == Items.DIAMOND,
        "a shift-clicked diamond should land in an ingredient slot");
    helper.succeed();
  }

  /**
   * Checks that pressing the fill button pours the cursor's water into the tanks.
   *
   * <p>The button is the whole of what a client may send, so what is asserted is the round trip: an
   * empty press changes nothing, a press with a bucket on the cursor fills the cluster and leaves
   * the empty bucket behind, and a press when there is no room is refused rather than half-taken.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void fillsTheTankFromTheCursor(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());

    int before = still.tank().level().storedMb();
    helper.assertFalse(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_FILL_TANK),
        "the fill button with nothing on the cursor");
    helper.assertValueEqual(
        still.tank().level().storedMb(), before, "millibuckets added by an empty press");

    menu.setCarried(new ItemStack(Items.WATER_BUCKET));
    helper.assertTrue(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_FILL_TANK),
        "the fill button with a water bucket on the cursor");
    helper.assertValueEqual(
        still.tank().level().storedMb(), before + TankMath.BUCKET_MB, "millibuckets added");
    helper.assertTrue(
        menu.getCarried().getItem() == Items.BUCKET,
        Component.literal(
            "the cursor should hold an empty bucket afterwards, but holds " + menu.getCarried()));

    // Full to the brim, so there is nowhere for the next bucket to go.
    still.tank().setStoredMb(still.tank().capacityMb());
    menu.setCarried(new ItemStack(Items.WATER_BUCKET));
    helper.assertFalse(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_FILL_TANK),
        "the fill button on a full tank");
    helper.assertTrue(
        menu.getCarried().getItem() == Items.WATER_BUCKET,
        Component.literal("a refused fill must not empty the cursor's bucket"));
    helper.succeed();
  }

  /**
   * Checks that right-clicking a tank with a water bucket fills it instead of spilling.
   *
   * <p>This drives the block's own interaction rather than calling the chamber, because that is
   * where the reported fault was: the tank had no handler, so the click reached the bucket's own
   * use and placed a water source in the world beside it. Both halves are asserted — the water went
   * in, and no water block appeared where the bucket would have put one.
   *
   * <p>The click is aimed at the tank's top face so that a spill would land on the block above it,
   * which is inside this structure and therefore a place the test can look.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void poursFromTheWorld(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);

    BlockPos tank = new BlockPos(1, 1, 0);
    BlockPos above = new BlockPos(1, 2, 0);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));

    int before = still.tank().level().storedMb();
    helper.useBlock(
        tank,
        player,
        new BlockHitResult(
            Vec3.atCenterOf(helper.absolutePos(tank)),
            Direction.UP,
            helper.absolutePos(tank),
            false));

    helper.assertValueEqual(
        still.tank().level().storedMb(), before + TankMath.BUCKET_MB, "millibuckets poured in");
    helper.assertTrue(
        player.getMainHandItem().getItem() == Items.BUCKET,
        Component.literal(
            "the hand should hold an empty bucket afterwards, but holds "
                + player.getMainHandItem()));
    helper.assertBlockNotPresent(Blocks.WATER, above);
    helper.succeed();
  }

  /**
   * Checks that a broken tank drops itself still carrying its water.
   *
   * <p>The reported fault was the opposite: breaking a tank threw its level away. {@code DESIGN.md}
   * §3.5 has always said a broken tank keeps its water in the item, so what is asserted is the
   * whole of that claim — one tank item comes out, and the water comes out with it.
   *
   * <p><b>Why the block is broken through the level and not through the helper.</b> {@code
   * GameTestHelper.destroyBlock} destroys with drops switched off, so it would leave nothing to
   * look at and this test would pass against a tank that dropped nothing at all. {@code
   * ServerLevel.destroyBlock(pos, true, breaker)} is the call that runs the drop path, and it is
   * the one a player's break reaches. The block entity is still in place while that runs, which is
   * what lets the block find the level it is carrying.
   *
   * <p>The other half — placing the item back and getting the water again — is deliberately not
   * re-tested here. That is vanilla's {@code BlockItem.updateCustomBlockEntityTag}, the same path
   * every chest and furnace in the game already uses.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void keepsItsWaterWhenBroken(GameTestHelper helper) {
    BlockPos tank = new BlockPos(1, 1, 0);
    helper.setBlock(tank, ModBlocks.ALCHEMICAL_TANK.get());
    AlchemicalTankBlockEntity tankEntity =
        helper.getBlockEntity(tank, AlchemicalTankBlockEntity.class);
    tankEntity.setStoredMb(STARTING_WATER_MB);

    helper.getLevel().destroyBlock(helper.absolutePos(tank), true, null);

    List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, tank, 1.0);
    helper.assertValueEqual(drops.size(), 1, "the number of items a broken tank drops");
    ItemStack dropped = drops.get(0).getItem();
    helper.assertTrue(
        dropped.is(ModBlocks.ALCHEMICAL_TANK.get().asItem()),
        Component.literal("a broken tank should drop a tank, but dropped " + dropped));
    helper.assertValueEqual(
        AlchemicalTankBlockEntity.storedMbOf(dropped),
        STARTING_WATER_MB,
        "millibuckets carried by the dropped tank");
    helper.succeed();
  }

  /**
   * Checks that a tank's screen is told what the tank holds.
   *
   * <p>Two claims, and the second is the one that matters. The first is that both numbers the gauge
   * needs reach the screen at all: a gauge is a fraction of something, so the level is useless
   * without the capacity beside it. The second is that nothing is a stale copy — a menu is a view,
   * so water put into the tank after the screen was opened has to show up without reopening it.
   *
   * <p>The server's menu is the one built here, not the client's. A game test runs on a dedicated
   * server, where no screen exists, so the client's plain store and the synchroniser that fills it
   * are out of reach; what is checked is the server half the numbers come from. That is the same
   * limit {@link #fillsTheTankFromTheCursor} works inside.
   *
   * <p>Pouring is asserted here as well as in the brewing screen's test because it is a different
   * button on a different menu, and a tank's is the only machine half that menu has at all: the
   * water goes in by clicking the gauge, there being no slot to put a bucket in.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void tankMenuReadsTheLevel(GameTestHelper helper) {
    BlockPos tank = new BlockPos(1, 1, 0);
    helper.setBlock(tank, ModBlocks.ALCHEMICAL_TANK.get());
    AlchemicalTankBlockEntity tankEntity =
        helper.getBlockEntity(tank, AlchemicalTankBlockEntity.class);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    AlchemicalTankMenu menu =
        new AlchemicalTankMenu(CONTAINER_ID, player.getInventory(), tankEntity);

    helper.assertValueEqual(menu.waterStoredMb(), 0, "a new tank's level");
    helper.assertValueEqual(
        menu.waterCapacityMb(), tankEntity.level().capacityMb(), "the capacity the screen is told");

    tankEntity.setStoredMb(STARTING_WATER_MB);
    helper.assertValueEqual(
        menu.waterStoredMb(), STARTING_WATER_MB, "the level after the tank was filled");

    menu.setCarried(new ItemStack(Items.WATER_BUCKET));
    helper.assertTrue(
        menu.clickMenuButton(player, AlchemicalTankMenu.BUTTON_FILL_TANK),
        "the fill button with a water bucket on the cursor");
    helper.assertValueEqual(
        menu.waterStoredMb(),
        STARTING_WATER_MB + TankMath.BUCKET_MB,
        "the level after a bucket was poured in");
    helper.succeed();
  }

  /**
   * Checks that the fuel menu takes fuel and refuses everything else.
   *
   * <p>Coal is fuel tier 1 and a stick is fuel tier 0, so both may go in: wood burns and gives a
   * lit chamber, which is a rule about brewing rather than about the slot. Redstone is not a fuel
   * at all, and the point of the test is that the refusal survives a shift-click — the path a
   * player actually uses, and the one a plain {@code Slot} would let through.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void fuelOnly(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());
    int fuel = PressureChamberBlockEntity.SLOT_FUEL;

    helper.assertTrue(menu.slots.get(fuel).mayPlace(new ItemStack(Items.COAL)), "coal is a fuel");
    helper.assertTrue(
        menu.slots.get(fuel).mayPlace(new ItemStack(Items.STICK)), "a stick is a fuel");
    helper.assertFalse(
        menu.slots.get(fuel).mayPlace(new ItemStack(Items.REDSTONE)), "redstone is not a fuel");

    // The other machine slots are filled, so a non-fuel shift-clicked from the player has nowhere
    // to go: the fuel slot refuses it and every well is taken, which is what keeps it put.
    for (int slot = PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST;
        slot < PressureChamberBlockEntity.SLOT_BOTTLE;
        slot++) {
      still.chamber().setItem(slot, new ItemStack(Items.SPIDER_EYE));
    }

    player.getInventory().setItem(PLAYER_INV_SLOT_START, new ItemStack(Items.REDSTONE));
    menu.quickMoveStack(player, MENU_INV_SLOT_START);
    helper.assertTrue(
        menu.slots.get(fuel).getItem().isEmpty(),
        "a shift-clicked non-fuel should not reach the fuel slot");
    helper.assertTrue(
        menu.slots.get(MENU_INV_SLOT_START).getItem().getItem() == Items.REDSTONE,
        "a non-fuel should be left where it was");

    player.getInventory().setItem(PLAYER_INV_SLOT_START + 1, new ItemStack(Items.COAL));
    menu.quickMoveStack(player, MENU_INV_SLOT_START + 1);
    helper.assertTrue(
        menu.slots.get(fuel).getItem().getItem() == Items.COAL,
        "a shift-clicked fuel should reach the fuel slot");
    helper.succeed();
  }

  /**
   * Checks the flame's fraction: nothing while the chamber is cold, then nearly a whole piece of
   * coal, then less.
   *
   * <p>The flame is the one picture of the burn on the brewing screen, so what is under test is the
   * conversion — that the ticks the chamber reports become the fraction the flame is cut from, and
   * that the fraction falls as the coal burns.
   *
   * <p>A brew has to be started to get there, because the fire only lights for one. It is started
   * and then left alone: what the brew does with its time is {@link BrewGameTests}' subject, and
   * this body only waits for the fuel to catch.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void countsTheBurnDown(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu fuel = pressureMenu(player, still.chamber());

    helper.assertFalse(fuel.isLit(), "a chamber nobody has lit");
    helper.assertValueEqual(fuel.burnProgress(), 0.0F, "the flame of a cold chamber");

    loadIngredients(still.chamber(), Items.SPIDER_EYE, Items.DANDELION);
    still.chamber().setItem(PressureChamberBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
    helper.assertTrue(
        fuel.clickMenuButton(player, PressureChamberMenu.BUTTON_START),
        "the start button, which is what the fire lights for");

    helper.runAfterDelay(
        LIT_TICKS,
        () -> {
          helper.assertTrue(fuel.isLit(), "a fire under a running brew");
          float fresh = fuel.burnProgress();
          helper.assertTrue(
              fresh > 0.9F,
              Component.literal(
                  "a freshly lit piece of coal should read nearly full, but read " + fresh));

          helper.runAfterDelay(
              COUNTDOWN_TICKS,
              () -> {
                float later = fuel.burnProgress();
                helper.assertTrue(
                    later < fresh,
                    Component.literal(
                        "the flame should shrink as the coal burns, but it still reads " + later));
                helper.succeed();
              });
        });
  }

  /**
   * Presses the load button on a recipe and checks the wells it filled.
   *
   * <p>Three claims in one press, because they are one behaviour: what the inventory could supply
   * moved into the well, what it could not was left empty rather than refused, and the hourglass
   * came with it. The fourth is the tier boundary — the recipe above the still's own tier is
   * refused on the same screen, so the check is not merely that loading works.
   *
   * <p>Nothing is spent, which is asserted last: the load moves items into the wells, and the
   * machine does not consume them until the player presses start. A load that brewed would be a
   * button a player could not press to see what it did.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void bookLoadsWhatTheInventoryHas(GameTestHelper helper) {
    TestStill still = buildTierThreeStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());

    // The tier above this still's. Refused, and with nothing moved: a refusal that half-filled the
    // wells would be the worst of both answers.
    int tooHigh = bookIndex(REGENERATION_RECIPE);
    helper.assertFalse(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_BOOK_BASE + tooHigh * 2),
        "a tier-four recipe on a tier-three still");
    helper.assertTrue(
        still.chamber().getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).isEmpty(),
        "a refused load must not fill the wells");

    int index = bookIndex(FIRE_RESISTANCE_RECIPE);
    // One of the recipe's two ingredients, and deliberately not the other.
    player.getInventory().setItem(PLAYER_INV_SLOT_START, new ItemStack(Items.MAGMA_CREAM));

    helper.assertTrue(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_BOOK_BASE + index * 2),
        "the load button on a tier-three still");

    helper.assertValueEqual(
        still.chamber().getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).getItem(),
        Items.MAGMA_CREAM,
        "the ingredient the inventory could supply");
    helper.assertTrue(
        still.chamber().getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST + 1).isEmpty(),
        "the ingredient the inventory could not supply, which is left empty rather than refused");
    helper.assertTrue(
        player.getInventory().getItem(PLAYER_INV_SLOT_START).isEmpty(),
        "an ingredient the load took should have left the inventory");
    helper.assertValueEqual(
        menu.selectedPreset(), Optional.of(HourglassPreset.THIRTY), "the hourglass the load set");
    helper.assertFalse(
        still.chamber().brewSession().isRunning(), "loading a recipe must not start a brew");
    helper.succeed();
  }

  /**
   * Presses two loads and checks the first one's items are given back on the way in.
   *
   * <p>What this pins down is what a second load means. Without the return, wells holding the first
   * recipe's ingredients are only refilled where the second recipe's matchers happen to accept
   * them, so a player who changes their mind gets a machine stocked with both recipes and neither
   * one brewable. The second recipe here — swiftness — shares no ingredient with the first, which
   * is what makes the check a check: there is nothing the load could legitimately do with a magma
   * cream or a finished potion except hand them back.
   *
   * <p>The output slot is filled by hand rather than by brewing. A brew costs fuel, water and time
   * to reach the same state, and none of those is what is being tested; a stack put there directly
   * is indistinguishable to the load, which is the point.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void bookReturnsFirst(GameTestHelper helper) {
    TestStill still = buildTierThreeStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());
    PressureChamberBlockEntity chamber = still.chamber();

    // The first load, which is what the second one has to undo. Its other ingredient is not in the
    // inventory, so exactly one well is stocked and the other is left for the second load to show
    // as empty.
    player.getInventory().setItem(PLAYER_INV_SLOT_START, new ItemStack(Items.MAGMA_CREAM));
    helper.assertTrue(
        menu.clickMenuButton(
            player, PressureChamberMenu.BUTTON_BOOK_BASE + bookIndex(FIRE_RESISTANCE_RECIPE) * 2),
        "the first load");
    helper.assertValueEqual(
        chamber.getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).getItem(),
        Items.MAGMA_CREAM,
        "the ingredient the first load stocked");
    helper.assertTrue(
        player.getInventory().getItem(PLAYER_INV_SLOT_START).isEmpty(),
        "the ingredient the first load took out of the inventory");

    // A player's finished brew, which a second load has no more business keeping than it has
    // keeping the first recipe's ingredients.
    chamber.setItem(PressureChamberBlockEntity.SLOT_OUTPUT, new ItemStack(Items.POTION));

    helper.assertTrue(
        menu.clickMenuButton(
            player, PressureChamberMenu.BUTTON_BOOK_BASE + bookIndex(SWIFTNESS_RECIPE) * 2),
        "the second load");

    helper.assertTrue(
        chamber.getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).isEmpty(),
        "the well holding an ingredient the second recipe does not want");
    helper.assertTrue(
        chamber.getItem(PressureChamberBlockEntity.SLOT_OUTPUT).isEmpty(),
        "the output slot the second load should have emptied");
    helper.assertTrue(
        player.getInventory().contains(new ItemStack(Items.MAGMA_CREAM)),
        "the returned ingredient, which should be back in the inventory");
    helper.assertTrue(
        player.getInventory().contains(new ItemStack(Items.POTION)),
        "the returned potion, which should be back in the inventory");
    helper.assertValueEqual(
        menu.selectedPreset(),
        Optional.of(HourglassPreset.FIFTEEN),
        "the hourglass the second load set, which is swiftness's");
    helper.succeed();
  }

  /**
   * Checks that a load which cannot give its items back is refused outright.
   *
   * <p>The failure this guards against is the half-done one: an inventory with room for some of
   * what the machine is holding takes that much, the rest is gone, and the player is left with a
   * machine they did not empty and an inventory they did not fill. So the assertion is not merely
   * that the press was refused — it is that the well still holds what it held, which is only true
   * if the refusal undid itself.
   *
   * <p>The inventory is filled with stone, which no recipe here accepts, so the stone is untouched
   * by the return and doubles as the proof that the return did not quietly shuffle something else
   * out of the way to make room.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void bookRefusesWhenFull(GameTestHelper helper) {
    TestStill still = buildTierThreeStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());
    PressureChamberBlockEntity chamber = still.chamber();

    chamber.setItem(
        PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST, new ItemStack(Items.MAGMA_CREAM));
    for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
      player.getInventory().setItem(slot, new ItemStack(Items.STONE));
    }

    helper.assertFalse(
        menu.clickMenuButton(
            player, PressureChamberMenu.BUTTON_BOOK_BASE + bookIndex(SWIFTNESS_RECIPE) * 2),
        "a load whose items have nowhere to go");

    helper.assertValueEqual(
        chamber.getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).getItem(),
        Items.MAGMA_CREAM,
        "the ingredient a refused load must put back where it found it");
    for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
      helper.assertValueEqual(
          player.getInventory().getItem(slot).getItem(),
          Items.STONE,
          "the inventory slot a refused load must leave alone, at index " + slot);
    }
    helper.succeed();
  }

  /**
   * Checks that the book is shut without a funnel, stars and all.
   *
   * <p>The gate is the tier, not the button: a screen on a tier-one still draws no book at all, so
   * the only way to press these is from a client that made the id up. Both intents are refused,
   * because the check is on the way in — a star is the player's own preference and still has no
   * business being recorded against a machine that has no book to show it in.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void bookNeedsAFunnel(GameTestHelper helper) {
    TestStill still = buildStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());
    int index = bookIndex(FIRE_RESISTANCE_RECIPE);

    helper.assertFalse(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_BOOK_BASE + index * 2),
        "a load on a still with no funnel");
    helper.assertFalse(
        menu.clickMenuButton(player, PressureChamberMenu.BUTTON_BOOK_BASE + index * 2 + 1),
        "a star on a still with no funnel");
    helper.assertTrue(
        still.chamber().getItem(PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST).isEmpty(),
        "a refused load must not fill the wells");
    helper.assertFalse(
        player.getData(ModAttachments.RECIPE_BOOK.get()).isFavourite(FIRE_RESISTANCE_RECIPE),
        "a refused star must not have been recorded");
    helper.succeed();
  }

  /**
   * Checks that starring a recipe puts it first, and that the stars survive leaving the player.
   *
   * <p>The second half is the attachment's own round trip. NeoForge's copy for a death or a portal
   * is a serialise and a deserialise through the type's handler, and that handler is where this
   * mod's decision lives: the favourites are written, the entries are not, and the entries come
   * back from whatever book is loaded at the far end. The copy is driven through {@code
   * AttachmentInternals} rather than through a real death because it is the same call the clone
   * event makes, with the same filter — {@code copyOnDeath} — applied to it.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void bookKeepsItsStars(GameTestHelper helper) {
    TestStill still = buildTierThreeStill(helper);
    Player player = helper.makeMockPlayer(GameType.CREATIVE);
    PressureChamberMenu menu = pressureMenu(player, still.chamber());
    AttachmentType<RecipeBookView> type = ModAttachments.RECIPE_BOOK.get();
    int star = PressureChamberMenu.BUTTON_BOOK_BASE + bookIndex(FIRE_RESISTANCE_RECIPE) * 2 + 1;

    helper.assertTrue(menu.clickMenuButton(player, star), "the star button");
    RecipeBookView starred = player.getData(type);
    helper.assertTrue(
        starred.isFavourite(FIRE_RESISTANCE_RECIPE), "the recipe the star was pressed on");
    helper.assertValueEqual(
        starred.displayOrder().getFirst().recipeId(),
        FIRE_RESISTANCE_RECIPE,
        "a starred recipe should be drawn first");

    Player heir = helper.makeMockPlayer(GameType.CREATIVE);
    AttachmentInternals.copyEntityAttachments(player, heir, true);
    RecipeBookView inherited = heir.getData(type);
    helper.assertTrue(
        inherited.isFavourite(FIRE_RESISTANCE_RECIPE), "a favourite should survive the copy");
    helper.assertValueEqual(
        inherited.entries().size(),
        starred.entries().size(),
        "the entries a copy should have rebuilt from the loaded book");

    helper.assertTrue(menu.clickMenuButton(player, star), "the star button pressed a second time");
    helper.assertFalse(
        player.getData(type).isFavourite(FIRE_RESISTANCE_RECIPE),
        "a star pressed twice should take the recipe back out");
    helper.succeed();
  }

  /**
   * @param recipeId the recipe to find
   * @return its position in the book the server sent, which is the index an intent is built from
   */
  private static int bookIndex(String recipeId) {
    List<BrewRecipe> all = ModDefinitions.current().recipes().all();
    for (int i = 0; i < all.size(); i++) {
      if (all.get(i).id().equals(recipeId)) {
        return i;
      }
    }
    throw new IllegalStateException("no brew recipe " + recipeId + " in the loaded book");
  }

  /**
   * Builds the smallest working still and puts water in its tank.
   *
   * <p>The same layout {@link BrewGameTests} uses, for the same reason: the chamber sits on the
   * firebox because that is where the validator looks for it, and the tank touches the chamber
   * because a tank that does not is not connected.
   *
   * @param helper the running test's handle on its own copy of the world
   * @return the three block entities, so a test can drive them directly
   */
  private static TestStill buildStill(GameTestHelper helper) {
    BlockPos firebox = new BlockPos(0, 0, 0);
    BlockPos chamber = new BlockPos(0, 1, 0);
    BlockPos tank = new BlockPos(1, 1, 0);

    helper.setBlock(firebox, ModBlocks.IGNITION_CHAMBER.get());
    helper.setBlock(chamber, ModBlocks.PRESSURE_CHAMBER.get());
    helper.setBlock(tank, ModBlocks.ALCHEMICAL_TANK.get());

    AlchemicalTankBlockEntity tankEntity =
        helper.getBlockEntity(tank, AlchemicalTankBlockEntity.class);
    tankEntity.setStoredMb(STARTING_WATER_MB);

    return new TestStill(
        helper.getBlockEntity(chamber, PressureChamberBlockEntity.class), firebox, tankEntity);
  }

  /**
   * Builds the same still with the parts up to tier three on it, which is what a book needs.
   *
   * <p>A second builder rather than a flag on the first, for the reason {@link BrewGameTests} gives
   * for its own pair: the two layouts genuinely differ — one cell is a tank in one and a flue stack
   * in the other — and the six lines are clearer than a parameter that moves a block.
   *
   * @param helper the running test's handle on its own copy of the world
   * @return the three block entities, so a test can drive them directly
   */
  private static TestStill buildTierThreeStill(GameTestHelper helper) {
    BlockPos firebox = new BlockPos(0, 0, 0);
    BlockPos chamber = new BlockPos(0, 1, 0);

    helper.setBlock(firebox, ModBlocks.IGNITION_CHAMBER.get());
    helper.setBlock(chamber, ModBlocks.PRESSURE_CHAMBER.get());
    helper.setBlock(TIER_THREE_TANK_POS, ModBlocks.ALCHEMICAL_TANK.get());
    helper.setBlock(VALVE_POS, ModBlocks.AIR_PRESSURE_VALVE.get());
    helper.setBlock(BELLOWS_POS, ModBlocks.BELLOWS.get());
    helper.setBlock(FUNNEL_POS, ModBlocks.FUNNEL.get());
    helper.setBlock(FLUE_POS, ModBlocks.FLUE_STACK.get());

    AlchemicalTankBlockEntity tankEntity =
        helper.getBlockEntity(TIER_THREE_TANK_POS, AlchemicalTankBlockEntity.class);
    tankEntity.setStoredMb(STARTING_WATER_MB);

    return new TestStill(
        helper.getBlockEntity(chamber, PressureChamberBlockEntity.class), firebox, tankEntity);
  }

  /**
   * @param player the player opening the chamber
   * @param chamber the chamber being opened
   * @return the server's menu for it, which is the one a button press arrives at
   */
  private static PressureChamberMenu pressureMenu(
      Player player, PressureChamberBlockEntity chamber) {
    return new PressureChamberMenu(
        CONTAINER_ID, player.getInventory(), chamber, new PressureChamberData(chamber));
  }

  /**
   * Fills the first ingredient slots in order.
   *
   * <p>Order matters: a recipe lists its ingredients from the first slot onwards, so an item in the
   * wrong slot is a different recipe or none at all.
   *
   * @param chamber the still's chamber
   * @param ingredients the ingredients, first slot first
   */
  private static void loadIngredients(PressureChamberBlockEntity chamber, Item... ingredients) {
    for (int i = 0; i < ingredients.length; i++) {
      chamber.setItem(
          PressureChamberBlockEntity.SLOT_INGREDIENT_FIRST + i, new ItemStack(ingredients[i]));
    }
    chamber.setItem(PressureChamberBlockEntity.SLOT_BOTTLE, new ItemStack(Items.GLASS_BOTTLE));
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
   * @param chamber the still's pressure chamber, which owns the brew and the fire
   * @param firebox the ignition chamber's position below it
   * @param tank the tank feeding it
   */
  private record TestStill(
      PressureChamberBlockEntity chamber, BlockPos firebox, AlchemicalTankBlockEntity tank) {}
}
