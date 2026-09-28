package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the catalogue the server last sent.
 *
 * <p>Static because the packet and the screen have no reference to each other: the reply can land
 * before the panel has laid out, and it has to survive the panel being toggled off and on. Only one
 * catalogue is ever in flight (a player can have exactly one trade screen open), so a single slot
 * is enough.
 */
public final class TradeCatalogClientState {

    /**
     * A catalogue arrives in slices, so the reply is assembled here before it becomes visible. Safe
     * as a single instance for the same reason as the field below: one screen, one transfer.
     */
    private static final TradeCatalogPacket.Reassembler REASSEMBLER = new TradeCatalogPacket.Reassembler();

    @Nullable
    private static TradeCatalogPacket.Payload current;

    /** Set when a request goes out, cleared by its reply, so the panel can show a placeholder. */
    private static boolean awaitingReply;

    private TradeCatalogClientState() {
    }

    public static void register() {
        NetworkHelper.registerCatalogReceiver(slice -> {
            // The registries are the client world's: reading a catalogue means reading item stacks,
            // which resolve components against them.
            if (Minecraft.getInstance().level == null) {
                return;
            }

            TradeCatalogPacket.Payload payload =
                    REASSEMBLER.accept(slice, Minecraft.getInstance().level.registryAccess());

            // Nothing to show until the last slice lands; the panel keeps its placeholder until then.
            if (payload != null) {
                current = payload;
                awaitingReply = false;
            }
        });
    }

    @Nullable
    public static TradeCatalogPacket.Payload current() {
        return current;
    }

    /**
     * Tracked as an outstanding-request flag rather than by comparing tier numbers: the server
     * clamps the request against the merchant's real tier count, so the reply's level legitimately
     * differs from the one asked for and a number comparison would never settle.
     */
    public static boolean isLoading() {
        return current == null || awaitingReply;
    }

    public static void requesting() {
        awaitingReply = true;
    }

    /** Drops the cached catalogue when a new trade screen opens, so a stale tier is never reused. */
    public static void clear() {
        current = null;
        awaitingReply = false;
    }
}
