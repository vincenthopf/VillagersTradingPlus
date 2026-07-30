package com.lion.villagertradingplus.platform.neoforge;

import com.lion.villagertradingplus.platform.NetworkHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class NetworkHelperImpl {

    private static final String PROTOCOL_VERSION = "1";

    private static final Map<Identifier, SimpleChannel> CHANNELS = new HashMap<>();

    /**
     * Populated only on a client. The message handler is registered on both sides — Forge requires
     * symmetric channel registration for version negotiation — but on a server there is never a
     * receiver to dispatch to, which keeps client-only classes off the server's classpath.
     */
    private static final Map<Identifier, Consumer<PacketByteBuf>> CLIENT_RECEIVERS = new HashMap<>();

    private NetworkHelperImpl() {
    }

    public static void init() {
        for (Identifier channel : NetworkHelper.CHANNELS) {
            CHANNELS.computeIfAbsent(channel, NetworkHelperImpl::createChannel);
        }
    }

    public static void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        SimpleChannel simpleChannel = CHANNELS.get(channel);
        if (simpleChannel == null) {
            throw new IllegalStateException("Unregistered channel " + channel + "; add it to NetworkHelper.CHANNELS");
        }
        simpleChannel.send(PacketDistributor.PLAYER.with(() -> player), new Payload(readAll(buf)));
    }

    public static void registerClientReceiver(Identifier channel, Consumer<PacketByteBuf> receiver) {
        CLIENT_RECEIVERS.put(channel, receiver);
    }

    private static SimpleChannel createChannel(Identifier id) {
        SimpleChannel channel = NetworkRegistry.newSimpleChannel(id, () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

        channel.registerMessage(0, Payload.class,
                (payload, out) -> out.writeBytes(payload.data()),
                in -> new Payload(readAll(in)),
                (payload, context) -> {
                    NetworkEvent.Context ctx = context.get();
                    ctx.enqueueWork(() -> {
                        Consumer<PacketByteBuf> receiver = CLIENT_RECEIVERS.get(id);
                        if (receiver != null) {
                            receiver.accept(new PacketByteBuf(Unpooled.wrappedBuffer(payload.data())));
                        }
                    });
                    ctx.setPacketHandled(true);
                });

        return channel;
    }

    /** Forge's SimpleChannel is message-typed; the mod's payloads are already raw buffers. */
    private record Payload(byte[] data) {
    }

    private static byte[] readAll(io.netty.buffer.ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
