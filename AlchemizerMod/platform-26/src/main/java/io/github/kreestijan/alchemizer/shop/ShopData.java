package io.github.kreestijan.alchemizer.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.core.shop.ShopLedger;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

/**
 * Every shop in one level, as the world saves it.
 *
 * <p><b>Per level, not global.</b> {@code ServerLevel.getDataStorage()} is the chunk source's own
 * storage, so a shop's records live with the dimension its Counter stands in — which is only right,
 * since the Counter does. The cost is that a Counter item carried through a portal arrives
 * somewhere that has never heard of its id; the ledger treats that as a fresh shop that keeps the
 * id, which is the least surprising thing it could do.
 *
 * <p><b>Why a saved-data file rather than block-entity NBT.</b> The design requires that breaking a
 * Counter and putting it back restores everything, so the state has to outlive the block, its
 * chunk, and the session. Block entities cannot: they are saved with the chunk they stand in and
 * dropped with the block.
 *
 * <p>This is a thin wrapper. The rules are in {@link ShopLedger}, which is pure Java and testable
 * without a world; this adds the two things a world insists on, a storage type and a codec.
 */
public final class ShopData extends SavedData {

  private static final Logger LOGGER = Alchemizer.LOGGER;

  /**
   * How many shops one level will write down.
   *
   * <p>A bound exists at all because the save loader hands the codec an accounting of unlimited
   * heap, so the only limit on a hand-edited file is the file. This one is high enough that no
   * legitimate world reaches it and low enough that a corrupted file cannot exhaust memory — the
   * same trade {@code RecipeBookView} makes with its entry cap. Past it, the excess is dropped with
   * a log line rather than failing the load, because a world that will not open is worse than one
   * missing shops it was never going to use.
   */
  public static final int MAX_SHOPS = 4096;

  public static final Codec<ShopData> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      ShopCodecs.SHOP_RECORD.listOf().fieldOf("shops").forGetter(ShopData::records))
                  .apply(instance, ShopData::fromRecords));

  public static final SavedDataType<ShopData> TYPE =
      new SavedDataType<>(
          Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "shops"),
          ShopData::new,
          CODEC,
          // There is no mod-facing DataFixType, and this is the generic slot vanilla keeps for data
          // it has no specific fixer for. Migration is done by the codec's own defaulted fields and
          // a
          // version field when one is needed, not by the data fixer.
          DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

  private final ShopLedger ledger;

  public ShopData() {
    this.ledger = new ShopLedger();
  }

  private ShopData(List<ShopRecord> records) {
    this.ledger = new ShopLedger(records);
  }

  /**
   * @param level the level whose shops are wanted
   * @return that level's shops, read from disk the first time it is asked
   */
  public static ShopData of(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(TYPE);
  }

  /**
   * @param records what the file held
   * @return a store over them, with anything past the cap dropped and named in the log
   */
  private static ShopData fromRecords(List<ShopRecord> records) {
    if (records.size() > MAX_SHOPS) {
      LOGGER.warn(
          "the shop file holds {} shops, past the cap of {}; the rest are being ignored",
          records.size(),
          MAX_SHOPS);
      return new ShopData(records.subList(0, MAX_SHOPS));
    }
    return new ShopData(records);
  }

  /**
   * @return the shops themselves, which is where every rule about them lives
   */
  public ShopLedger ledger() {
    return ledger;
  }

  /**
   * @return the records to write out
   */
  private List<ShopRecord> records() {
    return ledger.toList();
  }

  /**
   * Marks the file dirty.
   *
   * <p>A named method rather than leaving callers to remember {@code setDirty()}: every mutation of
   * the ledger has to be followed by it, and the one time it is forgotten the shop quietly reverts
   * on the next restart, months later, with nothing in the log to explain it.
   */
  public void changed() {
    setDirty();
  }
}
