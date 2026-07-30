package com.lion.villagertradingplus.tradeoffers.catalog;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire format for the catalogue, sent server-to-client on {@link #CHANNEL}.
 *
 * <p>The whole tier ships in one packet so the panel can page and scroll locally with no round-trip.
 * That makes packet size the thing to watch: {@code CustomPayloadS2CPacket} rejects payloads over
 * 1 MiB, measured <em>before</em> compression and enforced on both encode and decode. The row cap in
 * {@link CatalogBuilder#MAX_ENTRIES} is not sufficient on its own — a single heavy-NBT modded item
 * can run to tens of kilobytes — so rows are written into a scratch buffer and cut off at
 * {@link #MAX_PAYLOAD_BYTES}. Whatever gets dropped is reported to the player, never silently
 * swallowed.
 */
public final class TradeCatalogPacket {

    public static final Identifier CHANNEL = new Identifier("villagertradingplus", "trade_catalog");

    private static final int MAX_PAYLOAD_BYTES = 700_000;

    private TradeCatalogPacket() {
    }

    /**
     * @param level     the tier this catalogue describes
     * @param maxLevel  how many tiers the merchant has (5 for villagers, 2 for the wandering trader)
     * @param entries   the rows that fit
     * @param omitted   how many rows exist beyond those, for the "showing N of M" line
     */
    public record Payload(int level, int maxLevel, List<CatalogEntry> entries, int omitted) {
    }

    public static PacketByteBuf write(int level, int maxLevel, List<CatalogEntry> entries, int alreadySkipped) {
        // Rows go into a scratch buffer first: the count has to precede them, and a varint cannot be
        // back-patched once written.
        PacketByteBuf scratch = new PacketByteBuf(Unpooled.buffer());
        int written = 0;
        for (CatalogEntry entry : entries) {
            if (scratch.writerIndex() > MAX_PAYLOAD_BYTES) {
                break;
            }
            entry.write(scratch);
            written++;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(level);
        buf.writeVarInt(maxLevel);
        buf.writeVarInt(alreadySkipped + (entries.size() - written));
        buf.writeVarInt(written);
        buf.writeBytes(scratch);
        scratch.release();
        return buf;
    }

    public static Payload read(PacketByteBuf buf) {
        int level = buf.readVarInt();
        int maxLevel = buf.readVarInt();
        int omitted = buf.readVarInt();

        int count = buf.readVarInt();
        List<CatalogEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(CatalogEntry.read(buf));
        }
        return new Payload(level, maxLevel, List.copyOf(entries), omitted);
    }
}
