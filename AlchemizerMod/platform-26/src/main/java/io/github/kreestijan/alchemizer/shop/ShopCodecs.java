package io.github.kreestijan.alchemizer.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.core.shop.ShopRecord;
import java.util.List;
import java.util.UUID;

/**
 * How the shop types are written down, kept out of {@code core} because {@code core} may not name a
 * codec at all.
 *
 * <p>{@code core}'s build deliberately has no Minecraft on its classpath, which also leaves it
 * without DataFixerUpper — so the shop's rules are plain Java and the business of reading and
 * writing them lives here, next to everything else that speaks the game's serialisation dialect.
 *
 * <p><b>Every parse failure is a {@link DataResult} error rather than an exception.</b> A
 * hand-edited save, a downgraded world, or another mod writing into this file should cost the
 * offending record and a log line, never the whole level. That is also why the two string-backed
 * types parse through {@code comapFlatMap} rather than {@code xmap}: {@code UUID.fromString} and
 * {@code ShopId}'s constructor both throw, and a throw inside a decoder is not something the save
 * loader can skip.
 */
public final class ShopCodecs {

  /**
   * A shop id, as text.
   *
   * <p>The record's own constructor does the validating, so this only has to turn its exception
   * into an answer the decoder can carry.
   */
  public static final Codec<ShopId> SHOP_ID =
      Codec.STRING.comapFlatMap(
          text -> {
            try {
              return DataResult.success(new ShopId(text));
            } catch (IllegalArgumentException malformed) {
              return DataResult.error(malformed::getMessage);
            }
          },
          ShopId::value);

  /**
   * A player, as text.
   *
   * <p>Text rather than the packed-int form vanilla uses for its own files: this is read by a human
   * far more often than by anything performance-sensitive, and a uuid a person can read is a uuid a
   * person can fix.
   */
  public static final Codec<UUID> UUID_CODEC =
      Codec.STRING.comapFlatMap(
          text -> {
            try {
              return DataResult.success(UUID.fromString(text));
            } catch (IllegalArgumentException malformed) {
              return DataResult.error(() -> "not a uuid: " + text);
            }
          },
          UUID::toString);

  /** One shop's durable state. Every field after the first is defaulted, so an older file loads. */
  public static final Codec<ShopRecord> SHOP_RECORD =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      SHOP_ID.fieldOf("id").forGetter(ShopRecord::id),
                      UUID_CODEC.fieldOf("owner").forGetter(ShopRecord::owner),
                      UUID_CODEC
                          .listOf()
                          .optionalFieldOf("co_owners", List.of())
                          .forGetter(ShopRecord::coOwners),
                      Codec.BOOL.optionalFieldOf("open", true).forGetter(ShopRecord::open),
                      Codec.BOOL.optionalFieldOf("placed", false).forGetter(ShopRecord::placed))
                  .apply(instance, ShopRecord::new));

  private ShopCodecs() {}
}
