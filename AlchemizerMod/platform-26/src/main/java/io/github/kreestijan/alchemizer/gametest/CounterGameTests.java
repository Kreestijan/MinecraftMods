package io.github.kreestijan.alchemizer.gametest;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import io.github.kreestijan.alchemizer.core.shop.ShopRole;
import io.github.kreestijan.alchemizer.event.CounterProtection;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModDataComponents;
import io.github.kreestijan.alchemizer.registry.ModItems;
import io.github.kreestijan.alchemizer.shop.CounterView;
import io.github.kreestijan.alchemizer.shop.ShopData;
import io.github.kreestijan.alchemizer.shop.block.CounterBlock;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import io.github.kreestijan.alchemizer.shop.menu.CounterMenu;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-game tests for the Counter: its shop, its id, and who may take it down.
 *
 * <p>The rules themselves are unit-tested in {@code core}, where they run against a map in a
 * millisecond. What only a world can show is the seam: that placing a Counter actually reaches the
 * ledger, that the id written on the item is the id the block ends up holding, that the block
 * exposes it where the loot table looks for it, and that a break by the wrong player is refused.
 * Those are the four things here.
 *
 * <p><b>Placement is driven through {@code setPlacedBy} rather than by simulating a click.</b> That
 * is not a shortcut past the real path — it <em>is</em> the real path: vanilla's {@code
 * BlockItem.place} does its component work and then calls exactly this method, so calling it is
 * calling the hook that carries the rule. What it lets a test do that a click cannot is hand over a
 * stack carrying an arbitrary id, which is how the duplicate case is reached.
 */
public final class CounterGameTests {

  private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
      DeferredRegister.create(Registries.TEST_FUNCTION, Alchemizer.MOD_ID);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      PLACEMENT_MINTS =
          function("counter_placement_mints_a_shop", CounterGameTests::placementMints);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      REPLACING_RESTORES =
          function("counter_moves_with_its_shop", CounterGameTests::replacingRestores);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      COPY_IS_ITS_OWN = function("counter_copy_is_its_own_shop", CounterGameTests::copyIsItsOwn);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      ID_TRAVELS =
          function("counter_drop_carries_the_id", CounterGameTests::theIdTravelsWithTheBlock);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      STRANGER_REFUSED =
          function("counter_refuses_a_stranger", CounterGameTests::aStrangerIsRefused);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      ADDS_A_CO_OWNER =
          function("counter_owner_adds_a_co_owner", CounterGameTests::ownerAddsACoOwner);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      STRANGER_CANNOT_ADD =
          function("counter_refuses_a_strangers_add", CounterGameTests::strangerCannotAdd);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      STALE_INDEX_REFUSED =
          function("counter_refuses_a_stale_index", CounterGameTests::aStaleIndexIsRefused);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      TOGGLE_OBEYS_THE_SHOP =
          function("counter_toggles_for_its_own_people", CounterGameTests::theToggleObeysTheShop);

  /** Every body, so the registration loop below cannot miss one. */
  private static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>>
      TESTS =
          List.of(
              PLACEMENT_MINTS,
              REPLACING_RESTORES,
              COPY_IS_ITS_OWN,
              ID_TRAVELS,
              STRANGER_REFUSED,
              ADDS_A_CO_OWNER,
              STRANGER_CANNOT_ADD,
              STALE_INDEX_REFUSED,
              TOGGLE_OBEYS_THE_SHOP);

  private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
  private static final int MAX_TICKS = 100;
  private static final int SETUP_TICKS = 1;

  /** The window id the server would have chosen. Any number will do; nothing here reads it. */
  private static final int CONTAINER_ID = 0;

  /** The Counter under test, one block above the template floor. */
  private static final BlockPos HERE = new BlockPos(0, 1, 0);

  private CounterGameTests() {}

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
        event.registerEnvironment(Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "counter"));

    for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : TESTS) {
      event.registerTest(
          test.getKey().identifier(),
          new FunctionGameTestInstance(
              test.getKey(),
              new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, SETUP_TICKS, true)));
    }
  }

  // ------------------------------------------------------------------ the bodies

  private static void placementMints(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);

    ShopId id = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));

    ShopData shops = ShopData.of(helper.getLevel());
    helper.assertTrue(
        shops.ledger().roleOf(id, owner.getUUID()) == ShopRole.OWNER,
        Component.literal("whoever placed the Counter should own the shop"));
    helper.assertTrue(
        shops.ledger().record(id).orElseThrow().placed(),
        Component.literal("a Counter that is standing is a shop that is standing"));
    helper.succeed();
  }

  private static void replacingRestores(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    ShopId first = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));

    // Put the shop down and stand the same one back up: an item carrying the id, placed while
    // nothing is standing on it.
    ShopData.of(helper.getLevel()).ledger().release(first);
    ShopId second = place(helper, owner, stackCarrying(first));

    helper.assertTrue(
        first.equals(second),
        Component.literal("moving a Counter must not rename its shop, got " + second));
    ShopData.of(helper.getLevel()).ledger().release(first);
    helper.succeed();
  }

  private static void copyIsItsOwn(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    Player copier = helper.makeMockPlayer(GameType.CREATIVE);

    ShopId original = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));

    // A second Counter in the same test carries the first one's id while the first is still
    // standing. That is exactly what a copied item looks like, and it must not join the shop.
    helper.setBlock(new BlockPos(2, 1, 0), ModBlocks.COUNTER.get());
    BlockPos copyPos = new BlockPos(2, 1, 0);
    BlockState copyState = helper.getLevel().getBlockState(helper.absolutePos(copyPos));
    ((CounterBlock) copyState.getBlock())
        .setPlacedBy(
            helper.getLevel(),
            helper.absolutePos(copyPos),
            copyState,
            copier,
            stackCarrying(original));

    ShopData shops = ShopData.of(helper.getLevel());
    ShopId copyId = helper.getBlockEntity(copyPos, CounterBlockEntity.class).shopId().orElseThrow();

    helper.assertTrue(
        !copyId.equals(original),
        Component.literal("a copy must be given a shop of its own, but both are " + original));
    helper.assertTrue(
        shops.ledger().roleOf(original, copier.getUUID()) == ShopRole.STRANGER,
        Component.literal("the copier must hold nothing in the original shop"));
    helper.assertTrue(
        shops.ledger().roleOf(original, owner.getUUID()) == ShopRole.OWNER,
        Component.literal("and the original must still belong to whoever built it"));

    shops.ledger().release(original);
    shops.ledger().release(copyId);
    helper.succeed();
  }

  private static void theIdTravelsWithTheBlock(GameTestHelper helper) {
    // What the loot table reads. The generated table copies alchemizer:shop_id from a block-entity
    // source, and a block entity source means collectComponents() — so this is the assertion that
    // the drop will carry the id, made without needing to break the block and chase the item.
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    ShopId id = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));

    CounterBlockEntity counter = helper.getBlockEntity(HERE, CounterBlockEntity.class);
    ShopId exposed = counter.collectComponents().get(ModDataComponents.SHOP_ID.get());

    helper.assertTrue(
        id.equals(exposed),
        Component.literal("the block should expose its shop id to the loot table, saw " + exposed));

    ShopData.of(helper.getLevel()).ledger().release(id);
    helper.succeed();
  }

  private static void aStrangerIsRefused(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    Player stranger = helper.makeMockPlayer(GameType.CREATIVE);
    ShopId id = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));

    BreakBlockEvent byStranger =
        new BreakBlockEvent(
            helper.getLevel(),
            helper.absolutePos(HERE),
            helper.getLevel().getBlockState(helper.absolutePos(HERE)),
            stranger);
    CounterProtection.onBreakBlock(byStranger);
    helper.assertTrue(
        byStranger.isCanceled(), Component.literal("a stranger must not be able to break it"));

    BreakBlockEvent byOwner =
        new BreakBlockEvent(
            helper.getLevel(),
            helper.absolutePos(HERE),
            helper.getLevel().getBlockState(helper.absolutePos(HERE)),
            owner);
    CounterProtection.onBreakBlock(byOwner);
    helper.assertFalse(
        byOwner.isCanceled(), Component.literal("the owner must be able to move their own shop"));

    ShopData.of(helper.getLevel()).ledger().release(id);
    helper.succeed();
  }

  /**
   * Adds the player the server offered, by index, and checks the shop gained them.
   *
   * <p><b>What this drives and what it cannot.</b> The menu is built the way the server builds it
   * and the button is pressed the way a client presses it, so everything between the two — the
   * candidate list the index is derived from, the ledger's own consent, and the view that goes back
   * to the screen — is the real path. What no game test can reach is the screen itself: a dedicated
   * server has no {@code Screen}, so what is checked of the view is what it says rather than how it
   * is drawn.
   *
   * <p>The index is read from {@code CounterMenu.candidates} rather than assumed to be nought,
   * because the candidates are every player online who holds nothing in this shop and a game test
   * server accumulates the mock players its neighbours made. Asking the same list the server would
   * ask is what makes this a test of the press rather than of the other tests' leftovers.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void ownerAddsACoOwner(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    ShopId id = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));
    CounterBlockEntity counter = helper.getBlockEntity(HERE, CounterBlockEntity.class);
    ServerPlayer candidate = joinAMockServerPlayer(helper);
    CounterMenu menu = counterMenu(owner, counter);

    int index = candidateIndex(helper.getLevel(), id, candidate);
    helper.assertTrue(
        index >= 0, "a player online with no part in this shop should be a candidate");

    helper.assertTrue(
        menu.clickMenuButton(owner, CounterMenu.BUTTON_ADD_CANDIDATE_BASE + index),
        "the add button on the candidate the server itself listed");
    helper.assertTrue(
        ShopData.of(helper.getLevel()).ledger().roleOf(id, candidate.getUUID())
            == ShopRole.CO_OWNER,
        "the player an add button named must be a co-owner");

    // And the change has to have reached the view, not only the ledger: the screen draws the
    // co-owner list from what the server last sent, so a shop that gained somebody the panel never
    // hears about is a shop whose owner cannot take them back out.
    CounterView view = menu.view();
    helper.assertTrue(
        view.coOwnerNames().contains(candidate.getGameProfile().name()),
        Component.literal(
            "the view should name the new co-owner, but holds " + view.coOwnerNames()));
    helper.assertValueEqual(
        menu.coOwnerCount(), 1, "the co-owner count the screen is told after the add");

    ShopData.of(helper.getLevel()).ledger().release(id);
    helper.succeed();
  }

  /**
   * Checks that the same press from someone with no part in the shop is refused.
   *
   * <p>The candidate is real and the index is good, so the only thing standing between the press
   * and a changed shop is the rule: the owner alone may add. That is the ledger's answer rather
   * than the menu's, which is what makes this a test of where the rule lives as well as of what it
   * says.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void strangerCannotAdd(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    Player stranger = helper.makeMockPlayer(GameType.CREATIVE);
    ShopId id = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));
    CounterBlockEntity counter = helper.getBlockEntity(HERE, CounterBlockEntity.class);
    ServerPlayer candidate = joinAMockServerPlayer(helper);
    CounterMenu menu = counterMenu(stranger, counter);

    int index = candidateIndex(helper.getLevel(), id, candidate);
    helper.assertTrue(
        index >= 0, "a player online with no part in this shop should be a candidate");

    helper.assertFalse(
        menu.clickMenuButton(stranger, CounterMenu.BUTTON_ADD_CANDIDATE_BASE + index),
        "the add button pressed by someone with no part in the shop");
    helper.assertTrue(
        ShopData.of(helper.getLevel()).ledger().record(id).orElseThrow().coOwners().isEmpty(),
        "a refused add must not have added anybody");

    ShopData.of(helper.getLevel()).ledger().release(id);
    helper.succeed();
  }

  /**
   * Checks that an index pointing past the list is refused, and that the shop is left alone.
   *
   * <p>Two ways to be past the end, and both are what a screen a moment out of date sends: an add
   * index one beyond the candidates, and a remove index on a shop with nobody to remove. The pair
   * of bounds at the end are the ranges themselves — a client that made an id up has to be told it
   * was not understood rather than have it read as the first id of the other range, which is the
   * next one along.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aStaleIndexIsRefused(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    ShopId id = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));
    CounterBlockEntity counter = helper.getBlockEntity(HERE, CounterBlockEntity.class);
    CounterMenu menu = counterMenu(owner, counter);
    ShopRecord shop = ShopData.of(helper.getLevel()).ledger().record(id).orElseThrow();

    int beyond = CounterMenu.candidates(helper.getLevel(), shop).size();
    helper.assertFalse(
        menu.clickMenuButton(owner, CounterMenu.BUTTON_ADD_CANDIDATE_BASE + beyond),
        "an add index one past the last candidate");
    helper.assertFalse(
        menu.clickMenuButton(owner, CounterMenu.BUTTON_REMOVE_CO_OWNER_BASE),
        "a remove index on a shop with no co-owners");
    helper.assertFalse(
        menu.clickMenuButton(
            owner, CounterMenu.BUTTON_REMOVE_CO_OWNER_BASE + CounterView.MAX_NAMES),
        "one past the last button id of all");
    helper.assertFalse(menu.clickMenuButton(owner, -1), "a negative button id");

    helper.assertTrue(
        ShopData.of(helper.getLevel()).ledger().record(id).orElseThrow().coOwners().isEmpty(),
        "a refused index must change nothing");

    ShopData.of(helper.getLevel()).ledger().release(id);
    helper.succeed();
  }

  /**
   * Checks who the Open/Closed control opens the shop for.
   *
   * <p>The answer to each press is the shop's own record rather than what any menu reported back:
   * the owner opens it and closes it again, and a stranger is refused both ways. It starts closed,
   * which is the state a Counter is placed in — opening it is the owner's decision and theirs
   * alone, and the press is the only thing that makes it, which is why the first press here is an
   * open.
   *
   * <p>The co-owner's half of the rule — that a co-owner may do everything the owner may except
   * change who else may — is the ledger's, and shows up here as the role the add test put them in
   * rather than as a third press, because a press made by a player whose connection was never
   * negotiated cannot be sent the view that follows it and would fail on the send rather than on
   * the rule.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void theToggleObeysTheShop(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    Player stranger = helper.makeMockPlayer(GameType.CREATIVE);
    ShopId id = place(helper, owner, new ItemStack(ModItems.COUNTER.get()));
    CounterBlockEntity counter = helper.getBlockEntity(HERE, CounterBlockEntity.class);
    CounterMenu ownerMenu = counterMenu(owner, counter);

    helper.assertFalse(
        ownerMenu.open(), "a Counter just placed belongs to a shop that is closed until opened");
    helper.assertTrue(
        ownerMenu.role().orElseThrow().isStakeholder(), "the owner should be a stakeholder");

    helper.assertTrue(
        ownerMenu.clickMenuButton(owner, CounterMenu.BUTTON_TOGGLE_OPEN), "the owner's toggle");
    helper.assertTrue(recordOf(helper, id).open(), "the shop an owner has opened");
    helper.assertTrue(ownerMenu.open(), "the toggle the screen is told after the owner opened it");

    // The stranger's press goes against an open shop, so a refusal is a shop left exactly as it was
    // rather than a press that happened to agree with the state it was already in.
    CounterMenu strangerMenu = counterMenu(stranger, counter);
    helper.assertFalse(
        strangerMenu.clickMenuButton(stranger, CounterMenu.BUTTON_TOGGLE_OPEN),
        "the toggle pressed by someone with no part in the shop");
    helper.assertTrue(
        recordOf(helper, id).open(), "a refused toggle must not have closed the shop");

    helper.assertTrue(
        ownerMenu.clickMenuButton(owner, CounterMenu.BUTTON_TOGGLE_OPEN),
        "the owner's toggle pressed a second time");
    helper.assertFalse(recordOf(helper, id).open(), "the shop an owner has closed");
    helper.assertFalse(ownerMenu.open(), "the toggle the screen is told after the owner closed it");

    ShopData.of(helper.getLevel()).ledger().release(id);
    helper.succeed();
  }

  // ------------------------------------------------------------------ helpers

  /**
   * @param path the registry path the test is reported under
   * @param body what the test does
   * @return the holder, for the list above
   */
  private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function(
      String path, Consumer<GameTestHelper> body) {
    return TEST_FUNCTIONS.register(path, () -> body);
  }

  /**
   * @param id the shop the item is to name
   * @return a Counter item carrying that id
   */
  private static ItemStack stackCarrying(ShopId id) {
    ItemStack stack = new ItemStack(ModItems.COUNTER.get());
    stack.set(ModDataComponents.SHOP_ID.get(), id);
    return stack;
  }

  /**
   * Puts a Counter at {@link #HERE} and runs the placement hook vanilla would run.
   *
   * @param helper the running test's handle
   * @param placer who is putting it down
   * @param stack what they are putting down, which may or may not name a shop
   * @return the shop the Counter ended up belonging to
   */
  private static ShopId place(GameTestHelper helper, Player placer, ItemStack stack) {
    helper.setBlock(HERE, ModBlocks.COUNTER.get());
    BlockState state = helper.getLevel().getBlockState(helper.absolutePos(HERE));
    ((CounterBlock) state.getBlock())
        .setPlacedBy(helper.getLevel(), helper.absolutePos(HERE), state, placer, stack);
    return helper
        .getBlockEntity(HERE, CounterBlockEntity.class)
        .shopId()
        .orElseThrow(() -> new AssertionError("the Counter was placed with no shop"));
  }

  /**
   * @param viewer who is looking at the Counter
   * @param counter the Counter being opened
   * @return the server's menu for it, which is the one a button press arrives at
   */
  private static CounterMenu counterMenu(Player viewer, CounterBlockEntity counter) {
    return new CounterMenu(CONTAINER_ID, viewer.getInventory(), counter, viewer);
  }

  /**
   * Finds where the server's candidate list puts a player.
   *
   * <p>Asked rather than assumed, because the list is every player online who holds nothing in the
   * shop and a game test server accumulates the mock players its neighbours made: an index written
   * down here would be right in isolation and wrong the moment two tests ran in the same world.
   *
   * @param level the level the Counter stands in
   * @param id the shop
   * @param player whose position is wanted
   * @return their index in the list the add buttons index, or -1 when they are not on it
   */
  private static int candidateIndex(ServerLevel level, ShopId id, ServerPlayer player) {
    List<ServerPlayer> candidates = CounterMenu.candidates(level, recordOf(level, id));
    for (int index = 0; index < candidates.size(); index++) {
      if (candidates.get(index).getUUID().equals(player.getUUID())) {
        return index;
      }
    }
    return -1;
  }

  /**
   * @param helper the running test's handle on its own copy of the world
   * @param id the shop
   * @return the shop's durable record, which is where every assertion here is made
   */
  private static ShopRecord recordOf(GameTestHelper helper, ShopId id) {
    return recordOf(helper.getLevel(), id);
  }

  /**
   * @param level the level the Counter stands in
   * @param id the shop
   * @return the shop's durable record
   */
  private static ShopRecord recordOf(ServerLevel level, ShopId id) {
    return ShopData.of(level).ledger().record(id).orElseThrow();
  }

  /**
   * Signs a player on to the test server, which is the only way to have a candidate at all.
   *
   * <p>The candidate list is the players currently online — a shop knows its co-owners by id, and a
   * screen draws names — so a test of an add needs somebody the server's player list actually
   * holds. {@code makeMockPlayer}, which the rest of these tests use, is a fake player and is on no
   * list; this is the one that is.
   *
   * <p><b>Why the suppression.</b> Vanilla's own {@code makeMockServerPlayerInLevel} is the only
   * supported way to do this, and it is deprecated for removal with no replacement — what it
   * reaches for is engine bootstrap a mod has no business assembling by hand. Using it is what
   * keeps these tests tests of the Counter rather than of a hand-rolled connection, and this note
   * is here so that whoever meets the compile error when it finally goes knows what it stood for.
   *
   * @param helper the running test's handle on its own copy of the world
   * @return the player, now online, and therefore a candidate for any shop they hold no part in
   */
  @SuppressWarnings("removal")
  private static ServerPlayer joinAMockServerPlayer(GameTestHelper helper) {
    return helper.makeMockServerPlayerInLevel();
  }
}
