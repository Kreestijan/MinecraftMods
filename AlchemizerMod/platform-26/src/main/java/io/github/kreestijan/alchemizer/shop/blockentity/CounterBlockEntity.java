package io.github.kreestijan.alchemizer.shop.blockentity;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.config.ConfigCache;
import io.github.kreestijan.alchemizer.core.pathing.PathWear;
import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import io.github.kreestijan.alchemizer.core.shop.ShopRole;
import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.registry.ModBlockEntities;
import io.github.kreestijan.alchemizer.registry.ModDataComponents;
import io.github.kreestijan.alchemizer.shop.CustomerQueue;
import io.github.kreestijan.alchemizer.shop.CustomerSpawner;
import io.github.kreestijan.alchemizer.shop.ShopCodecs;
import io.github.kreestijan.alchemizer.shop.ShopData;
import io.github.kreestijan.alchemizer.shop.block.CounterBlock;
import io.github.kreestijan.alchemizer.shop.pathing.ShopPathField;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The Counter's block entity: which shop stands here, and nothing else.
 *
 * <p><b>It holds an id, not a shop.</b> Owner, co-owners and whether the shop is trading all live
 * in the level's saved data under this id. That is not indirection for its own sake — it is the
 * design's requirement that breaking a Counter and putting it back restores everything, which is
 * only possible if the state lives somewhere the block does not. What is here is the one field that
 * ties the block to that record.
 *
 * <p>Writing the id into this block entity's own save data <em>and</em> exposing it as an implicit
 * component is not duplication. They answer two different questions: the save data is how the block
 * remembers which shop it is across a chunk unload, and the component is how the item remembers
 * when the block is broken — vanilla reads the implicit components onto the drop through the loot
 * table and puts them back on placement, so neither direction needs code.
 *
 * <p><b>The shop's arrivals are driven from here.</b> The navigation field and the arrival clock
 * are per shop and belong to the Counter, so they live on the block entity and are advanced by its
 * own server tick. That is what makes a shop's cadence its own: a Counter that is loaded ticks its
 * own field and its own clock, and two shops in one village never share either.
 */
public class CounterBlockEntity extends BlockEntity {

  private static final String KEY_SHOP_ID = "shop_id";

  private @Nullable ShopId shopId;

  /**
   * The shop's navigation field: where a customer may stand, and which way the Counter is.
   *
   * <p>Its build is spread over ticks by the same block entity tick that calls this class's, which
   * is what keeps a field of several thousand cells off a single server tick.
   */
  private final ShopPathField pathField;

  /**
   * The shop's queue: which of its customers is standing where.
   *
   * <p>The Counter owns it because the Counter is the only writer for its shop — one arrival is one
   * seat taken and one departure is one seat given up, both decided here rather than by the
   * customers themselves.
   */
  private final CustomerQueue queue;

  /**
   * What the shop's customers have walked over.
   *
   * <p>In memory, and thrown away with this block entity: a Counter that unloads or is broken
   * forgets every pass, and a world that restarts starts its paths over. That is deliberate rather
   * than unfinished — a per-cell counter saved with the world would be a great deal of stored data
   * kept for a threshold of a handful of passes.
   */
  private final PathWear wear;

  /** The shop's arrival clock, which is what decides when the next customer appears. */
  private final CustomerSpawner spawner;

  /**
   * Whether a failure in this Counter's tick has already been reported.
   *
   * <p>A block entity that throws on every tick would otherwise fill the log with the same line
   * twenty times a second. The failure is still not fatal — a shop whose own clock is broken simply
   * has no customers — so this is about the log and nothing else.
   */
  private boolean reportedTickFailure;

  /**
   * @param pos where the Counter stands
   * @param state the block state it was created with
   */
  public CounterBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.COUNTER.get(), pos, state);

    // The shop's four numbers, read once. A block entity is built when its chunk loads and a chunk
    // loads long after the config file has been read, so this is the shipping value only on the
    // paths a test builds a Counter by hand; the reason it is read here rather than per tick is
    // that two of these numbers are the size of an array and one is the interval of a countdown.
    // A config change therefore applies to the next Counter that is loaded, and the comments in the
    // config file say so.
    Tuning tuning = ConfigCache.current();
    this.pathField = new ShopPathField(tuning);
    this.queue = new CustomerQueue(tuning);
    this.wear = new PathWear(tuning.pathWearThreshold(), tuning.pathWearEnabled());
    this.spawner = new CustomerSpawner(tuning);
  }

  /**
   * @return this Counter's navigation field, built and rebuilt by its own tick
   */
  public ShopPathField pathField() {
    return pathField;
  }

  /**
   * @return the line of customers waiting at this shop, and the seats they are standing in
   */
  public CustomerQueue queue() {
    return queue;
  }

  /**
   * Which way the Counter standing here faces.
   *
   * <p>Read from the block state rather than kept in a field of this one, because it is a fact
   * about the block and there is exactly one place the game keeps it: the level hands this block
   * entity every state change through {@code setBlockState}, so a Counter that has been rotated in
   * place reads as rotated here too. The shop's queue runs the way this points, which is why the
   * queue asks for it on every redraw rather than caching it at load.
   *
   * @return the direction the Counter faces
   */
  public Direction facing() {
    return getBlockState().getValue(CounterBlock.FACING);
  }

  /**
   * @return what this shop's customers have walked over, which its queue counts footsteps into
   */
  public PathWear wear() {
    return wear;
  }

  /**
   * Sends everybody waiting at this shop away, puffing and leaving nothing behind.
   *
   * <p>The Counter's exit, and the only one: a customer is seated here and the Counter is what
   * knows who is seated, so every reason a shop stops wanting customers — it has been broken, it
   * has been closed, nobody who may trade at it is online — is answered in one place rather than by
   * each caller hunting for the entities.
   *
   * @param level the level the customers are in
   */
  public void dismissCustomers(ServerLevel level) {
    queue.dismissAll(level);
  }

  /**
   * The Counter's server tick: one slice of the field, one look at the queue, and one at the
   * arrival clock.
   *
   * <p>All three are budgeted by construction. The field spends a fixed number of cells, the queue
   * does a bounded amount of work per seat it holds — as many as the config allows and no more —
   * and the clock is a subtraction on almost every tick, so a street of shops costs a street of
   * subtractions and one field build each.
   *
   * @param level the level the Counter is in
   * @param pos where the Counter stands
   * @param state the Counter's block state
   * @param counter the block entity being ticked
   */
  public static void serverTick(
      Level level, BlockPos pos, BlockState state, CounterBlockEntity counter) {
    if (!(level instanceof ServerLevel server)) {
      return;
    }
    try {
      // The facing goes with the position because the field leads to the shop's front, and which
      // cell that is is what the Counter's facing decides.
      counter.pathField.tick(server, pos, counter.facing());
      // Before the clock, so that a seat freed this tick and the line the next customer would take
      // are both as they stand now rather than as they stood a tick ago.
      counter.queue.tick(server, counter);
      counter.spawner.tick(server, counter);
    } catch (RuntimeException failure) {
      // A tick handler must never let an exception out. The shop carries on without customers
      // rather than taking the server down with it.
      if (!counter.reportedTickFailure) {
        counter.reportedTickFailure = true;
        Alchemizer.LOGGER.error(
            "Alchemizer: the Counter at {} in {} could not be ticked",
            pos,
            server.dimension().identifier(),
            failure);
      }
    }
  }

  /**
   * @return which shop stands here, or empty for a Counter that has not been claimed yet
   */
  public Optional<ShopId> shopId() {
    return Optional.ofNullable(shopId);
  }

  /**
   * @param id the shop this Counter belongs to
   */
  public void setShopId(ShopId id) {
    this.shopId = id;
    setChanged();
  }

  /**
   * @return the shop's durable record, if this Counter has one and the level can be reached
   */
  public Optional<ShopRecord> record() {
    if (shopId == null || !(level instanceof ServerLevel server)) {
      return Optional.empty();
    }
    return ShopData.of(server).ledger().record(shopId);
  }

  /**
   * Whether the shop standing here is trading.
   *
   * <p>Asked once a tick by the shop's own clock, which is why it is this rather than {@link
   * #record()}: the record is a whole shop's contents in an {@code Optional}, and a flag read
   * eighteen thousand times a minute wants neither. A Counter with no shop is not trading, because
   * there is nothing there for anybody to wait for.
   *
   * @return whether this shop is open
   */
  public boolean trading() {
    if (shopId == null || !(level instanceof ServerLevel server)) {
      return false;
    }
    return ShopData.of(server).ledger().isOpen(shopId);
  }

  /**
   * @param player who is asking
   * @return what they are to the shop standing here
   */
  public ShopRole roleOf(UUID player) {
    if (shopId == null || !(level instanceof ServerLevel server)) {
      return ShopRole.STRANGER;
    }
    return ShopData.of(server).ledger().roleOf(shopId, player);
  }

  @Override
  protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    if (shopId != null) {
      output.store(KEY_SHOP_ID, ShopCodecs.SHOP_ID, shopId);
    }
  }

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    shopId = input.read(KEY_SHOP_ID, ShopCodecs.SHOP_ID).orElse(null);
  }

  @Override
  public void collectImplicitComponents(DataComponentMap.Builder components) {
    super.collectImplicitComponents(components);
    if (shopId != null) {
      components.set(ModDataComponents.SHOP_ID.get(), shopId);
    }
  }

  @Override
  protected void applyImplicitComponents(DataComponentGetter input) {
    super.applyImplicitComponents(input);
    shopId = input.get(ModDataComponents.SHOP_ID.get());
  }
}
