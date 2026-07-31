package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.VillagerTradingPlus;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire format for the catalogue, sent server-to-client as a sequence of {@link TradeCatalogPayload}
 * slices.
 *
 * <p>The whole tier ships at once so the panel can page and scroll locally with no round-trip, which
 * makes size the thing to watch: a single {@code CustomPayloadS2CPacket} is capped at 1 MiB and a tier
 * of heavy modded items can pass that alone. So the catalogue is serialised into one buffer and then
 * cut into {@link #MAX_SLICE_BYTES} pieces; the client puts them back together.
 *
 * <p>{@link #MAX_TOTAL_BYTES} still bounds the whole thing - not because the transport demands it any
 * more, but because a client should not be made to hold an unbounded catalogue in memory. Rows that
 * do not fit are counted, never silently swallowed: the count reaches the panel as {@code omitted}
 * and becomes its "showing N of M" line.
 */
public final class TradeCatalogPacket {

    /**
     * Payload budget per slice. Comfortably under the 1 MiB packet ceiling, and small enough that the
     * per-slice framing cost stays irrelevant.
     */
    public static final int MAX_SLICE_BYTES = 32 * 1024;

    /** Ceiling on one catalogue across all its slices. */
    private static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;

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

    /**
     * Serialises a catalogue and cuts it into slices ready to send.
     *
     * <p>Rows go into a scratch buffer before the header, because the count has to precede them and a
     * varint cannot be back-patched once written.
     */
    public static List<TradeCatalogPayload> write(DynamicRegistryManager registries, int level, int maxLevel,
                                                  List<CatalogEntry> entries, int alreadySkipped) {
        RegistryByteBuf scratch = new RegistryByteBuf(Unpooled.buffer(), registries);
        int written = 0;
        for (CatalogEntry entry : entries) {
            if (scratch.writerIndex() > MAX_TOTAL_BYTES) {
                break;
            }
            entry.write(scratch);
            written++;
        }

        RegistryByteBuf body = new RegistryByteBuf(Unpooled.buffer(), registries);
        body.writeVarInt(level);
        body.writeVarInt(maxLevel);
        body.writeVarInt(alreadySkipped + (entries.size() - written));
        body.writeVarInt(written);
        body.writeBytes(scratch);
        scratch.release();

        byte[] all = new byte[body.readableBytes()];
        body.readBytes(all);
        body.release();

        return slice(all);
    }

    private static List<TradeCatalogPayload> slice(byte[] all) {
        // An empty catalogue is still one slice: the header alone is meaningful, and the client would
        // otherwise wait forever for a transfer that never starts.
        int count = Math.max(1, (all.length + MAX_SLICE_BYTES - 1) / MAX_SLICE_BYTES);
        List<TradeCatalogPayload> slices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * MAX_SLICE_BYTES;
            int to = Math.min(all.length, from + MAX_SLICE_BYTES);
            byte[] part = new byte[to - from];
            System.arraycopy(all, from, part, 0, part.length);
            slices.add(new TradeCatalogPayload(i, count, part));
        }
        return slices;
    }

    /**
     * Puts the slices of one catalogue back together on the client.
     *
     * <p>Not thread-safe and does not need to be: slices are handed over on the client thread, one
     * catalogue at a time, because a player has exactly one trade screen open.
     */
    public static final class Reassembler {

        private final List<byte[]> received = new ArrayList<>();
        private int expected;
        private int size;

        /**
         * {@return the finished catalogue once its last slice has arrived, otherwise {@code null}}
         *
         * <p>A slice with index 0 always starts over. Anything out of sequence is dropped along with
         * the partial transfer it belongs to - the alternative is decoding a spliced buffer, which
         * fails far less legibly.
         */
        @Nullable
        public Payload accept(TradeCatalogPayload slice, DynamicRegistryManager registries) {
            if (slice.index() == 0) {
                reset();
                this.expected = slice.count();
            } else if (slice.index() != this.received.size() || slice.count() != this.expected) {
                VillagerTradingPlus.LOGGER.warn(
                        "Discarding an out-of-sequence trade catalogue slice ({} of {}, expected {} of {}).",
                        slice.index(), slice.count(), this.received.size(), this.expected);
                reset();
                return null;
            }

            this.size += slice.data().length;
            if (this.size > MAX_TOTAL_BYTES) {
                VillagerTradingPlus.LOGGER.warn("Discarding an oversized trade catalogue ({} bytes).", this.size);
                reset();
                return null;
            }

            this.received.add(slice.data());
            if (this.received.size() < this.expected) {
                return null;
            }

            byte[] all = new byte[this.size];
            int at = 0;
            for (byte[] part : this.received) {
                System.arraycopy(part, 0, all, at, part.length);
                at += part.length;
            }
            reset();

            RegistryByteBuf buf = new RegistryByteBuf(Unpooled.wrappedBuffer(all), registries);
            try {
                return read(buf);
            } catch (Exception e) {
                VillagerTradingPlus.LOGGER.error("Could not read the trade catalogue.", e);
                return null;
            }
        }

        private void reset() {
            this.received.clear();
            this.expected = 0;
            this.size = 0;
        }
    }

    private static Payload read(RegistryByteBuf buf) {
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
