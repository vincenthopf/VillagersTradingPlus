package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the catalogue the server last sent.
 *
 * <p>Static because the packet and the screen have no reference to each other: the reply can land
 * before the panel has laid out, and it has to survive the panel being toggled off and on. Only one
 * catalogue is ever in flight (a player can have exactly one trade screen open), so a single slot
 * is enough.
 */
@Environment(EnvType.CLIENT)
public final class TradeCatalogClientState {

    @Nullable
    private static TradeCatalogPacket.Payload current;

    /** Set when a request goes out, cleared by its reply, so the panel can show a placeholder. */
    private static boolean awaitingReply;

    private TradeCatalogClientState() {
    }

    public static void register() {
        NetworkHelper.registerClientReceiver(TradeCatalogPacket.CHANNEL, buf -> {
            current = TradeCatalogPacket.read(buf);
            awaitingReply = false;
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
