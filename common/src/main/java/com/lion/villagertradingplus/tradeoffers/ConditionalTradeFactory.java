package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.tradeoffers.conditions.TradeCondition;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Wraps another trade factory and yields its offer only when the condition passes. Returning
 * {@code null} plugs directly into vanilla's existing null-filtering during offer selection, so a
 * failed condition simply means the trade is not offered by that villager.
 */
public final class ConditionalTradeFactory implements ItemListing {

    private final ItemListing delegate;
    private final TradeCondition condition;

    public ConditionalTradeFactory(ItemListing delegate, TradeCondition condition) {
        this.delegate = delegate;
        this.condition = condition;
    }

    @Override
    public MerchantOffer getOffer(Entity entity, RandomSource random) {
        if (!condition.test(entity)) {
            return null;
        }
        return delegate.getOffer(entity, random);
    }

    /**
     * The wrapped factory. The trade catalogue reaches past the gate deliberately: it lists gated
     * trades whatever their condition currently says, and marks them, rather than showing a list
     * that silently shrinks when it starts raining.
     */
    public ItemListing delegate() {
        return this.delegate;
    }

    public TradeCondition condition() {
        return this.condition;
    }
}
