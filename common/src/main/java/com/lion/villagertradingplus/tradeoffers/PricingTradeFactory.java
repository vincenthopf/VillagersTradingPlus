package com.lion.villagertradingplus.tradeoffers;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.TradeOffers;

/**
 * A create()-time wrapper that applies global economy tuning to any produced offer: a config-driven
 * scale on the currency (first buy item) count and an optional time-of-day price variance. Kept
 * generic so it applies to every trade type without editing each adapter.
 *
 * <p>Like conditional trades, the price is fixed when the offer is generated (villager level-up), so
 * time-of-day variance differs between offers rather than changing live.
 */
public final class PricingTradeFactory implements TradeOffers.Factory {

    private final TradeOffers.Factory delegate;

    private PricingTradeFactory(TradeOffers.Factory delegate) {
        this.delegate = delegate;
    }

    /** Wraps only when some pricing tuning is actually active; otherwise returns the delegate as-is. */
    public static TradeOffers.Factory wrapIfNeeded(TradeOffers.Factory delegate, JsonObject trade) {
        boolean costScale = VillagerTradingPlus.CONFIG.trade_cost_scale != 1.0f;
        boolean timeOfDay = VillagerTradingPlus.CONFIG.enable_time_of_day_pricing;
        return (costScale || timeOfDay) ? new PricingTradeFactory(delegate) : delegate;
    }

    @Override
    public TradeOffer create(Entity entity, Random random) {
        TradeOffer offer = delegate.create(entity, random);
        if (offer == null) {
            return null;
        }

        float factor = priceFactor(entity);
        if (factor == 1.0f) {
            return offer;
        }

        // Rebuilt rather than copied: the buy side is a match rule now, and rebuilding it from its
        // own item and component predicate keeps whatever the trade demanded while only the count
        // moves. Scaling a display stack instead would quietly drop those requirements.
        TradedItem originalFirst = offer.getFirstBuyItem();
        TradedItem first = new TradedItem(
                originalFirst.item(),
                scaleCount(originalFirst.count(), originalFirst.itemStack().getMaxCount(), factor),
                originalFirst.components());

        return new TradeOffer(first, offer.getSecondBuyItem(), offer.getSellItem(),
                offer.getUses(), offer.getMaxUses(), offer.getMerchantExperience(),
                offer.getPriceMultiplier(), offer.getDemandBonus());
    }

    /** The wrapped factory, so the catalogue can enumerate it and re-apply pricing itself. */
    public TradeOffers.Factory delegate() {
        return this.delegate;
    }

    /** Shared so catalogue rows are priced exactly the way a real generated offer would be. */
    public static int scaleCount(int count, int maxCount, float factor) {
        return MathHelper.clamp(Math.round(count * factor), 1, maxCount);
    }

    public float priceFactor(Entity entity) {
        float factor = VillagerTradingPlus.CONFIG.trade_cost_scale;
        if (VillagerTradingPlus.CONFIG.enable_time_of_day_pricing) {
            float variance = VillagerTradingPlus.CONFIG.time_of_day_price_variance;
            long timeOfDay = entity.getWorld().getTimeOfDay() % 24000L;
            double swing = Math.sin(2.0 * Math.PI * (timeOfDay / 24000.0));
            factor *= (float) (1.0 + variance * swing);
        }
        return factor;
    }
}
