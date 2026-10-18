package io.github.kreestijan.alchemizer.gametest;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.flowfield.FlowField;
import io.github.kreestijan.alchemizer.customer.Customer;
import io.github.kreestijan.alchemizer.customer.CustomerDialogue;
import io.github.kreestijan.alchemizer.customer.menu.CustomerDialogueMenu;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModEntities;
import io.github.kreestijan.alchemizer.registry.ModItems;
import io.github.kreestijan.alchemizer.shop.CustomerQueue;
import io.github.kreestijan.alchemizer.shop.ShopData;
import io.github.kreestijan.alchemizer.shop.block.CounterBlock;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import java.util.List;
import java.util.OptionalInt;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-game tests for the Serve/Refuse dialogue: who may open it, and what pressing a button does.
 *
 * <p>What can only be shown in a world is the seam this feature is made of. The dialogue is a menu
 * with a customer behind it and a shop behind that, and every claim worth testing is about the
 * chain — that a customer is told which shop it belongs to, that the shop is found again from the
 * customer rather than from anything the client said, and that the right to act is asked of the
 * ledger at the moment of the press. A unit test could not reach any of it: the whole of the
 * feature's logic is what it does with a real block entity and a real customer.
 *
 * <p>The screens themselves are out of reach here — a dedicated server has no {@code Screen} — so
 * every press below goes to the menu the server would have put behind one, which is the same menu a
 * client's button id arrives at. No window is opened: a mock player has no connection for the
 * open-screen packet, and opening one is not what these tests are about.
 */
public final class CustomerDialogueGameTests {

  private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
      DeferredRegister.create(Registries.TEST_FUNCTION, Alchemizer.MOD_ID);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      SERVING_DISMISSES =
          function("serving_a_customer_dismisses_it", CustomerDialogueGameTests::servingDismisses);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      A_STRANGERS_SERVE_IS_REFUSED =
          function(
              "a_strangers_serve_is_refused", CustomerDialogueGameTests::aStrangersServeIsRefused);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      A_CLICK_WITH_NO_SHOP_IS_REFUSED =
          function(
              "a_right_click_with_no_shop_behind_it_is_refused",
              CustomerDialogueGameTests::aClickWithNoShopIsRefused);

  private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
      A_STAKEHOLDERS_CLICK_OPENS_IT =
          function(
              "a_stakeholders_right_click_opens_the_dialogue",
              CustomerDialogueGameTests::aStakeholdersClickOpensIt);

  private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

  private static final int MAX_TICKS = 600;

  private static final int SETUP_TICKS = 1;

  /** The window id the server would have chosen. Any number will do; nothing here reads it. */
  private static final int CONTAINER_ID = 0;

  /** Where the Counter stands: on the pavement, one block above the template's own floor. */
  private static final BlockPos COUNTER = new BlockPos(0, 2, 0);

  /** The pavement the shop's front needs, which is what gives it a line to seat anybody in. */
  private static final int PAVEMENT = 3;

  /**
   * How often a test asks whether the shop has built its field yet, and how many times it asks.
   *
   * <p>The same shape {@code CustomerGameTests} uses for the same reason: a field is built a slice
   * at a time and how long it takes depends on how much walkable world is around the shop.
   */
  private static final int FIELD_POLL_TICKS = 2;

  private static final int FIELD_TRIES = 150;

  private CustomerDialogueGameTests() {}

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
        event.registerEnvironment(Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "dialogue"));

    for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test :
        List.of(
            SERVING_DISMISSES,
            A_STRANGERS_SERVE_IS_REFUSED,
            A_CLICK_WITH_NO_SHOP_IS_REFUSED,
            A_STAKEHOLDERS_CLICK_OPENS_IT)) {
      event.registerTest(
          test.getKey().identifier(),
          new FunctionGameTestInstance(
              test.getKey(),
              new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, SETUP_TICKS, true)));
    }
  }

  // ------------------------------------------------------------------ the bodies

  /**
   * A stakeholder serving a customer takes it out of the world and gives its place in the line up.
   *
   * <p>Both halves of that sentence matter and both are asserted. The customer leaving is what the
   * dialogue is <em>for</em> today, and the place being given up is what makes a queue a queue: the
   * line moves up into the gap on the shop's next tick, and a place that were still held by
   * somebody who is gone would leave the shop one short with nobody in it.
   *
   * <p>The press is made against the server's own menu with the window id the server would have
   * chosen, which is where a client's button id arrives — see {@code CounterGameTests} for the same
   * arrangement on the Counter's screen.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void servingDismisses(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    CounterBlockEntity counter = aShopOwnedBy(helper, owner);

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          Customer customer = putACustomerInItsPlace(helper, counter);
          CustomerDialogueMenu menu =
              new CustomerDialogueMenu(CONTAINER_ID, owner.getInventory(), customer);

          helper.assertTrue(
              menu.clickMenuButton(owner, CustomerDialogueMenu.BUTTON_SERVE),
              "the shop's own owner served a customer at their shop");
          helper.assertTrue(
              customer.isRemoved(),
              "a served customer leaves the world the way any departure does");
          helper.assertTrue(
              counter.queue().occupiedCount() == 0,
              Component.literal(
                  "a served customer gives its place up, but the shop is still holding "
                      + counter.queue().occupiedCount()));
          helper.succeed();
        });
  }

  /**
   * A player who holds nothing in the customer's shop cannot serve it.
   *
   * <p>The press is real and the menu is the server's, so the only thing between it and a departure
   * is the rule: serving is an owner's or a co-owner's, asked of the ledger at the moment of the
   * press rather than trusted from when the dialogue opened. The refusal is asserted of the
   * ledger's answer and of the customer still standing there with its place still held, because a
   * refusal that quietly dismissed somebody anyway is the failure this test is for.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aStrangersServeIsRefused(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    Player stranger = helper.makeMockPlayer(GameType.CREATIVE);
    CounterBlockEntity counter = aShopOwnedBy(helper, owner);

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          Customer customer = putACustomerInItsPlace(helper, counter);
          CustomerDialogueMenu menu =
              new CustomerDialogueMenu(CONTAINER_ID, stranger.getInventory(), customer);

          helper.assertFalse(
              menu.clickMenuButton(stranger, CustomerDialogueMenu.BUTTON_SERVE),
              "a player with no part in this shop served a customer at it");
          helper.assertFalse(
              menu.clickMenuButton(stranger, CustomerDialogueMenu.BUTTON_REFUSE),
              "and turning a customer away is no more theirs to do");
          helper.assertFalse(
              customer.isRemoved(), "a refused press must not have dismissed the customer");
          helper.assertTrue(
              counter.queue().occupiedCount() == 1,
              "and it must not have given up the customer's place in the line");
          helper.succeed();
        });
  }

  /**
   * A right-click that has no shop behind it is refused, and the click stays vanilla's.
   *
   * <p>Two ways for a customer to have no shop to ask about, and both are ordinary: one that was
   * put in the world by something other than a shop's own spawner and so never learned which
   * Counter it came for, and one whose Counter belongs to a shop this player holds nothing in. In
   * both cases the mod must do nothing at all — not open a window, and not claim the click —
   * because a customer is a mob like any other as far as leads and name tags are concerned, and
   * cancelling a click that produced no dialogue is how a mob stops being leadable.
   *
   * <p>The event is the real one and the handler is the real handler, so what is asserted is the
   * whole of the path a right-click takes through this mod. A window that opened in some earlier
   * tick is not a thing this test can be fooled by: the shop's Counter is either known to the
   * customer or it is not.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aClickWithNoShopIsRefused(GameTestHelper helper) {
    Player owner = helper.makeMockPlayer(GameType.CREATIVE);
    CounterBlockEntity counter = aShopOwnedBy(helper, owner);

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          Customer unplaced = putACustomerInTheWorld(helper, counter, false);
          PlayerInteractEvent.EntityInteract someoneElse =
              new PlayerInteractEvent.EntityInteract(
                  joinAMockServerPlayer(helper), InteractionHand.MAIN_HAND, unplaced);
          CustomerDialogue.onEntityInteract(someoneElse);
          helper.assertFalse(
              someoneElse.isCanceled(),
              "a customer whose shop nobody knows must leave the click to vanilla");
          helper.assertFalse(unplaced.isRemoved(), "and a refused click must not dismiss it");

          Customer atTheShop = putACustomerInItsPlace(helper, counter);
          PlayerInteractEvent.EntityInteract byAStranger =
              new PlayerInteractEvent.EntityInteract(
                  joinAMockServerPlayer(helper), InteractionHand.MAIN_HAND, atTheShop);
          CustomerDialogue.onEntityInteract(byAStranger);
          helper.assertFalse(
              byAStranger.isCanceled(),
              "a player with no part in the shop gets no dialogue, and the click is still theirs");
          helper.assertFalse(
              atTheShop.isRemoved(), "and nothing happened to the customer they clicked");
          helper.succeed();
        });
  }

  // ------------------------------------------------------------------ helpers

  /**
   * A stakeholder's right-click opens the dialogue, and the click stops there.
   *
   * <p>The one thing this proves that the button tests cannot: that the entry point works end to
   * end. A right-click reaches the handler, the handler works out the shop and the player's place
   * in it, and the server opens a window — which is asserted by looking at the window the player
   * now has, because a menu that was created and never handed over would be the failure that
   * matters. It is also where the click being <em>claimed</em> is checked: a dialogue that opened
   * without claiming the click would leave vanilla to put a lead on the customer at the same time.
   *
   * <p>What no game test can see is the screen drawn from that window: a dedicated server has none.
   * See {@code CustomerDialogueScreen} for that half, which needs eyes on a client.
   *
   * @param helper the running test's handle on its own copy of the world
   */
  private static void aStakeholdersClickOpensIt(GameTestHelper helper) {
    ServerPlayer owner = joinAMockServerPlayer(helper);
    CounterBlockEntity counter = aShopOwnedBy(helper, owner);

    onceTheShopHasAField(
        helper,
        counter,
        () -> {
          Customer customer = putACustomerInItsPlace(helper, counter);
          PlayerInteractEvent.EntityInteract click =
              new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, customer);

          CustomerDialogue.onEntityInteract(click);

          helper.assertTrue(
              click.isCanceled(),
              "the shopkeeper's click on their own customer is the dialogue's, not vanilla's");
          helper.assertTrue(
              owner.containerMenu instanceof CustomerDialogueMenu,
              Component.literal(
                  "the server should have opened the dialogue for the shop's own owner, but the"
                      + " window they have is "
                      + owner.containerMenu));
          helper.succeed();
        });
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
   * Puts a paved shop in the world and opens it.
   *
   * <p>The pavement is what gives the shop a front — a line begins in the cell in front of the
   * Counter, and a Counter with a block there has nowhere to put anybody — so the shop is built on
   * three cells of it and left facing east, which is the direction the ground runs in.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param owner who is putting the Counter down, who will own the shop
   * @return the Counter's block entity, now holding an open shop
   */
  private static CounterBlockEntity aShopOwnedBy(GameTestHelper helper, Player owner) {
    for (int x = 0; x <= PAVEMENT; x++) {
      for (int z = -1; z <= 1; z++) {
        helper.setBlock(new BlockPos(x, COUNTER.getY() - 1, z), Blocks.STONE);
      }
    }

    helper.setBlock(
        COUNTER,
        ModBlocks.COUNTER.get().defaultBlockState().setValue(CounterBlock.FACING, Direction.EAST));
    BlockState state = helper.getLevel().getBlockState(helper.absolutePos(COUNTER));
    ((CounterBlock) state.getBlock())
        .setPlacedBy(
            helper.getLevel(),
            helper.absolutePos(COUNTER),
            state,
            owner,
            new ItemStack(ModItems.COUNTER.get()));

    CounterBlockEntity counter = helper.getBlockEntity(COUNTER, CounterBlockEntity.class);
    helper.assertTrue(
        ShopData.of(helper.getLevel())
            .ledger()
            .setOpen(counter.shopId().orElseThrow(), owner.getUUID(), true),
        "the owner of a shop that has just been placed should be able to open it");
    return counter;
  }

  /**
   * Puts a customer in the world, standing in the next free place of the shop's line.
   *
   * <p>Seated the way the shop's own spawner seats one — ask for a place, attach the route and the
   * place, then put the customer in the world — so that what a button press acts on is a customer
   * the shop knows about. It is put down in the cell of its own place rather than made to walk in,
   * because walking is a subject of its own in {@code CustomerGameTests} and this is about a press.
   *
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @return the customer, already in the world and holding a place in the line
   */
  private static Customer putACustomerInItsPlace(
      GameTestHelper helper, CounterBlockEntity counter) {
    return putACustomerInTheWorld(helper, counter, true);
  }

  /**
   * @param helper the running test's handle on its own copy of the world
   * @param counter the shop's Counter
   * @param giveItAPlace whether the customer is to be given a place in the shop's line and a route
   * @return the customer, already in the world
   */
  private static Customer putACustomerInTheWorld(
      GameTestHelper helper, CounterBlockEntity counter, boolean giveItAPlace) {
    CustomerQueue queue = counter.queue();
    OptionalInt seat = giveItAPlace ? queue.freeSeat() : OptionalInt.empty();
    if (giveItAPlace && seat.isEmpty()) {
      throw new AssertionError("the shop's line is full, so there is no place to put a customer");
    }

    BlockPos where =
        seat.isEmpty()
            ? new BlockPos(PAVEMENT, COUNTER.getY(), 1)
            : helper.absolutePos(seatInTheTemplate(counter, seat.getAsInt()));
    Customer customer =
        ModEntities.CUSTOMER
            .get()
            .create(
                helper.getLevel(),
                spawned -> {
                  if (giveItAPlace) {
                    spawned.setRoute(counter.pathField(), counter.getBlockPos());
                    spawned.setSeat(queue, seat.getAsInt());
                  }
                },
                where,
                EntitySpawnReason.EVENT,
                true,
                false);
    if (customer == null) {
      throw new AssertionError("the customer entity type could not build one");
    }
    if (giveItAPlace) {
      queue.occupy(seat.getAsInt(), customer);
    }
    helper.getLevel().addFreshEntity(customer);
    return customer;
  }

  /**
   * @param counter the shop's Counter
   * @param seat a place in the shop's line
   * @return that place as a cell of the template
   */
  private static BlockPos seatInTheTemplate(CounterBlockEntity counter, int seat) {
    io.github.kreestijan.alchemizer.core.machine.RelativePos cell =
        counter
            .queue()
            .seatCell(seat)
            .orElseThrow(
                () ->
                    new AssertionError(
                        "the shop's line has no place " + seat + " to put a customer in"));
    return new BlockPos(cell.x(), COUNTER.getY() + cell.y(), cell.z());
  }

  /**
   * Runs a body once the shop has a navigation field, which is when it has a line to seat anybody
   * in.
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
    FlowField field = counter.pathField().current().orElse(null);
    if (field != null) {
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
                + " ticks, so the shop has no line and nothing to seat a customer in"));
    helper.runAfterDelay(
        FIELD_POLL_TICKS, () -> onceTheShopHasAField(helper, counter, tries - 1, then));
  }

  /**
   * Signs a player on to the test server.
   *
   * <p>The dialogue's entry point is a right-click, and it is acted on only for a player the server
   * actually has — so the tests that drive the click need one the server's player list holds. See
   * {@code CounterGameTests} for why this is the only supported way to do it, and why it is
   * deprecated.
   *
   * @param helper the running test's handle on its own copy of the world
   * @return the player, now online
   */
  @SuppressWarnings("removal")
  private static ServerPlayer joinAMockServerPlayer(GameTestHelper helper) {
    return helper.makeMockServerPlayerInLevel();
  }
}
