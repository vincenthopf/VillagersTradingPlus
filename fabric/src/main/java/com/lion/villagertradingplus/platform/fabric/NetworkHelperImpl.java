package com.lion.villagertradingplus.platform.fabric;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

public final class NetworkHelperImpl {

    private NetworkHelperImpl() {
    }

    /**
     * Payload types are registered on both sides from common init: the codec has to exist before a
     * packet of that type can be encoded or decoded, and a client that never registered it would drop
     * the payload as unknown.
     */
    public static void init() {
        PayloadTypeRegistry.playS2C().register(TradeCatalogPayload.ID, TradeCatalogPayload.CODEC);
    }

    public static void sendToPlayer(ServerPlayerEntity player, CustomPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Environment(EnvType.CLIENT)
    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        // The payload is already a decoded, immutable record here - unlike the old raw-buffer
        // receiver, nothing has to be copied off the network thread before handing it over.
        ClientPlayNetworking.registerGlobalReceiver(TradeCatalogPayload.ID,
                (payload, context) -> context.client().execute(() -> receiver.accept(payload)));
    }
}
