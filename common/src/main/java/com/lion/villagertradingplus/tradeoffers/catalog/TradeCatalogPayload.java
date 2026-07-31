package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * One slice of a trade catalogue on the wire.
 *
 * <p>The catalogue is serialised whole and then cut into slices, because a single
 * {@code CustomPayloadS2CPacket} may not exceed 1 MiB - measured before compression and enforced on
 * both encode and decode, so overshooting disconnects the client rather than degrading. A tier of
 * heavy modded items can pass that on its own.
 *
 * <p>Slices arrive in order on a reliable channel, so the framing needs no session id: a slice with
 * {@code index == 0} starts a new catalogue and discards anything half-received, which is exactly
 * what should happen when a player flips to another tier mid-transfer. See
 * {@link TradeCatalogPacket.Reassembler}.
 *
 * @param index  position of this slice, counted from 0
 * @param count  how many slices the catalogue was cut into
 * @param data   the raw bytes of this slice
 */
public record TradeCatalogPayload(int index, int count, byte[] data) implements CustomPayload {

    public static final CustomPayload.Id<TradeCatalogPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VillagerTradingPlus.MOD_ID, "trade_catalog"));

    /**
     * Bounded on decode: the length prefix comes off the wire, so an unbounded array codec would let
     * a bad packet size an allocation for us.
     */
    public static final PacketCodec<PacketByteBuf, TradeCatalogPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TradeCatalogPayload::index,
            PacketCodecs.VAR_INT, TradeCatalogPayload::count,
            PacketCodecs.byteArray(TradeCatalogPacket.MAX_SLICE_BYTES), TradeCatalogPayload::data,
            TradeCatalogPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
