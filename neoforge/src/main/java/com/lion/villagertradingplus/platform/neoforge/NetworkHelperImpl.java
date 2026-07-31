package com.lion.villagertradingplus.platform.neoforge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/**
 * NeoForge networking. Payload types are registered from {@code RegisterPayloadHandlersEvent} on the
 * mod event bus, which is why {@link #init()} does nothing itself: unlike Fabric, the registration
 * cannot happen whenever the mod feels like it, only while that event is being fired.
 */
public final class NetworkHelperImpl {

    /** Bumped when the payload format changes incompatibly; NeoForge refuses mismatched clients. */
    private static final String PROTOCOL_VERSION = "1";

    private static Consumer<TradeCatalogPayload> catalogReceiver;

    private NetworkHelperImpl() {
    }

    /** Nothing to do here — see {@link #register(RegisterPayloadHandlersEvent)}. */
    public static void init() {
    }

    public static void sendToPlayer(ServerPlayerEntity player, CustomPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        catalogReceiver = receiver;
    }

    /**
     * Called from the mod event bus. The handler is registered on both sides — NeoForge negotiates
     * the channel and would reject a client that never declared it — but only a client ever has a
     * receiver to dispatch to, which keeps client-only classes off a server's classpath.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(TradeCatalogPayload.ID, TradeCatalogPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    Consumer<TradeCatalogPayload> receiver = catalogReceiver;
                    if (receiver != null) {
                        receiver.accept(payload);
                    }
                }));
        VillagerTradingPlus.LOGGER.debug("Registered the trade catalogue payload.");
    }
}
