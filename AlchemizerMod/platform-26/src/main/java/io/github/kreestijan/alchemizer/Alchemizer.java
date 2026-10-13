package io.github.kreestijan.alchemizer;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Mod entrypoint. This class only wires things together — config, registries and the event bus.
 * Game logic belongs in feature classes reached from here, never in this class.
 *
 * <p>The constructor parameters are injected by FML, which recognises the types automatically.
 */
@Mod(Alchemizer.MOD_ID)
public final class Alchemizer {

    public static final String MOD_ID = "alchemizer";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Alchemizer(IEventBus modEventBus, ModContainer modContainer) {
        // Registries, event handlers and config are registered here as features land.
    }
}
