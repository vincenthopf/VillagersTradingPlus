package com.lion.villagertradingplus.platform;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Minimal server-to-client messaging, following the same {@code @ExpectPlatform} split as
 * {@link RegistryHelper} and {@link ConfigDirectory}.
 *
 * <p>Typed to {@link CustomPacketPayload} rather than raw buffers since 1.20.5: a payload type has to be
 * registered with its codec before anything can be sent, which is what {@link #init()} does per
 * platform.
 */
public class NetworkHelper {

    /** Registers every payload type the mod sends. Must run before the first send. */
    public static void init() {
        com.lion.villagertradingplus.platform.fabric.NetworkHelperImpl.init();
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        com.lion.villagertradingplus.platform.fabric.NetworkHelperImpl.sendToPlayer(player, payload);
    }

    /** Client only. The receiver runs on the client thread. */
    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        com.lion.villagertradingplus.platform.fabric.NetworkHelperImpl.registerCatalogReceiver(receiver);
    }
}
