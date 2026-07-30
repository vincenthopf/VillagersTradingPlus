package com.lion.villagertradingplus.platform;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal server-to-client messaging, following the same {@code @ExpectPlatform} split as
 * {@link RegistryHelper} and {@link ConfigDirectory}.
 */
public class NetworkHelper {

    /** Every channel the mod uses. Forge has to create its channels before the registry locks. */
    public static final List<Identifier> CHANNELS = List.of(TradeCatalogPacket.CHANNEL);

    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerClientReceiver(Identifier channel, Consumer<PacketByteBuf> receiver) {
        throw new AssertionError();
    }
}
