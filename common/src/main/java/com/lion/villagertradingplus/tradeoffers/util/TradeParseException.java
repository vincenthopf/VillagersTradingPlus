package com.lion.villagertradingplus.tradeoffers.util;

/**
 * Thrown while deserializing a trade that cannot produce a usable offer; most often an item id that
 * does not resolve, e.g. one belonging to a mod that is not installed.
 *
 * <p>Caught at the single choke point {@code TradeOfferManager.deserializeTrade}, which logs the reason
 * and skips just that trade. Failing here is deliberate: silently substituting {@link
 * net.minecraft.world.item.ItemStack#EMPTY} used to leave a blank slot in a villager's trade list with no
 * indication of what went wrong.
 */
public class TradeParseException extends RuntimeException {

    public TradeParseException(String message) {
        super(message);
    }
}
