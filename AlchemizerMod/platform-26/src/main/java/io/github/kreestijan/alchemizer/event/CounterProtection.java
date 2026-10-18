package io.github.kreestijan.alchemizer.event;

import io.github.kreestijan.alchemizer.core.shop.ShopId;
import io.github.kreestijan.alchemizer.core.shop.ShopRole;
import io.github.kreestijan.alchemizer.shop.ShopData;
import io.github.kreestijan.alchemizer.shop.block.CounterBlock;
import io.github.kreestijan.alchemizer.shop.blockentity.CounterBlockEntity;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Stops anyone but a shop's own people from taking its Counter down.
 *
 * <p><b>Why this has to be an event and not a block method.</b> A block cannot refuse to be broken
 * from inside itself — by the time any of its own hooks run, the decision has been made. The one
 * place a break can be turned back is before it happens, which is what this listener is.
 *
 * <p><b>An unclaimed Counter is nobody's and anybody may remove it.</b> That is not a gap: a
 * Counter placed by a dispenser, or one whose shop could not be claimed, would otherwise be a block
 * its placer cannot break and no one else will, sitting in the world forever. A shop with no owner
 * has no owner to protect it from.
 *
 * <p>Note that this refuses an operator and a creative-mode player too. The design says only owners
 * and co-owners may break it, without carving anyone out, and the way back from a mistake — the
 * shop's own record — is not something a break would have given them anyway.
 */
public final class CounterProtection {

  /**
   * @param event the break being attempted
   */
  public static void onBreakBlock(BreakBlockEvent event) {
    // The event arrives on both sides; only the server's answer counts.
    if (!(event.getLevel() instanceof ServerLevel server)
        || !(event.getState().getBlock() instanceof CounterBlock)
        || !(server.getBlockEntity(event.getPos()) instanceof CounterBlockEntity counter)) {
      return;
    }

    Optional<ShopId> shopId = counter.shopId();
    if (shopId.isEmpty()) {
      return;
    }

    ShopRole role = ShopData.of(server).ledger().roleOf(shopId.get(), event.getPlayer().getUUID());
    if (!role.isStakeholder()) {
      event.setCanceled(true);

      // Without this the server refuses the break and the client carries on believing it happened,
      // leaving a Counter that is there for everyone else and gone for the player who swung at it.
      event.setNotifyClient(true);
    }
  }

  private CounterProtection() {}
}
