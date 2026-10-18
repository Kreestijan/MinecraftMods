package io.github.kreestijan.alchemizer.shop.network;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.shop.CounterView;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The one packet this mod sends: a Counter's shop, from the server that owns it to the player
 * looking at it.
 *
 * <p><b>Why a packet exists here when nothing else needed one.</b> Every other screen draws
 * numbers, and numbers travel in the container synchroniser's own slots for free. A list of player
 * names is not a number, and an encoding that squeezed names into ints would be worse than the
 * packet. So there is one clientbound payload, sent when a menu opens and again whenever the shop
 * changes.
 *
 * <p><b>There is no serverbound half.</b> The client answers with button ids through {@code
 * clickMenuButton}, exactly as the brewing screens do, so a press is still an index the server
 * resolves against its own state and nothing the client says is trusted. That asymmetry is the
 * point of doing it this way: the payload carries what the server decided, and the only thing
 * travelling the other way is an intention.
 *
 * <p><b>Sent to one player, never broadcast.</b> Two people can have the same Counter open, so the
 * payload names the block it is about; a client showing a different Counter ignores it rather than
 * drawing someone else's shop.
 */
public final class CounterNetwork {

  /** The wire version NeoForge handshakes on. Bump it whenever a payload's shape changes. */
  private static final String VERSION = "1";

  private CounterNetwork() {}

  /**
   * @param modEventBus the bus this mod's registration events arrive on
   */
  public static void register(IEventBus modEventBus) {
    modEventBus.addListener(CounterNetwork::onRegisterPayloads);
  }

  /**
   * @param event the payload registry, open for this mod to add to
   */
  public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar(VERSION);
    registrar.playToClient(ViewPayload.TYPE, ViewPayload.CODEC, ViewPayload::handle);
  }

  /**
   * @param player who should be told
   * @param pos the Counter the view is about
   * @param view the shop as it now stands
   */
  public static void sendTo(ServerPlayer player, BlockPos pos, CounterView view) {
    PacketDistributor.sendToPlayer(player, new ViewPayload(pos, view));
  }

  /**
   * A shop, on its way to one player.
   *
   * @param pos which Counter it is about
   * @param view the shop's facts
   */
  public record ViewPayload(BlockPos pos, CounterView view) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ViewPayload> TYPE =
        new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "counter_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ViewPayload::pos,
            CounterView.STREAM_CODEC,
            ViewPayload::view,
            ViewPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
    }

    /**
     * Files the view where the screen will look for it.
     *
     * <p>Names no client type, so this class loads on a dedicated server with nothing missing — the
     * store it writes into is a plain map, and only the screen cares that it is a client reading
     * it.
     *
     * @param context where the payload arrived
     */
    public static void handle(ViewPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> CounterViewStore.accept(payload.pos(), payload.view()));
    }
  }

  /**
   * The most recent shop view the server has sent, and which Counter it was about.
   *
   * <p>One pair rather than a map, because only one Counter menu is open at a time: a client has
   * one screen, and opening a second Counter replaces the first. A map would be a cache that grows
   * for a lifetime and is looked at once.
   *
   * <p>Deliberately not a client-only class. It holds a position and a record of strings, both of
   * which are ordinary types, so keeping it here means the payload's handler needs no client-only
   * reference and this file loads unchanged on a dedicated server.
   */
  public static final class CounterViewStore {

    private static volatile BlockPos latestPos;
    private static volatile CounterView latestView;

    private CounterViewStore() {}

    /**
     * @param pos the Counter the view is about
     * @param view the shop as the server sees it
     */
    static void accept(BlockPos pos, CounterView view) {
      latestPos = pos;
      latestView = view;
    }

    /**
     * @param pos the Counter the asking screen is showing
     * @return that Counter's view, or empty when the server has not sent one for it
     */
    public static Optional<CounterView> forCounter(BlockPos pos) {
      if (latestView == null || !pos.equals(latestPos)) {
        return Optional.empty();
      }
      return Optional.of(latestView);
    }

    /** Forgets what was sent, which a screen does when it closes. */
    public static void clear() {
      latestPos = null;
      latestView = null;
    }
  }
}
