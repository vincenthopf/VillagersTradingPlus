package com.lion.villagertradingplus.tradeoffers;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * A create()-time wrapper that applies global economy tuning to any produced offer: a config-driven
 * scale on the currency (first buy item) count and an optional time-of-day price variance. Kept
 * generic so it applies to every trade type without editing each adapter.
 *
 * <p>Like conditional trades, the price is fixed when the offer is generated (villager level-up), so
 * time-of-day variance differs between offers rather than changing live.
 */
public final class PricingTradeFactory implements ItemListing {

    private final ItemListing delegate;

    private PricingTradeFactory(ItemListing delegate) {
        this.delegate = delegate;
    }

    /** Wraps only when some pricing tuning is actually active; otherwise returns the delegate as-is. */
    public static ItemListing wrapIfNeeded(ItemListing delegate, JsonObject trade) {
        boolean costScale = VillagerTradingPlus.CONFIG.trade_cost_scale != 1.0f;
        boolean timeOfDay = VillagerTradingPlus.CONFIG.enable_time_of_day_pricing;
        return (costScale || timeOfDay) ? new PricingTradeFactory(delegate) : delegate;
    }

    @Override
    public MerchantOffer getOffer(Entity entity, RandomSource random) {
        MerchantOffer offer = delegate.getOffer(entity, random);
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
        ItemCost originalFirst = offer.getItemCostA();
        ItemCost first = new ItemCost(
                originalFirst.item(),
                scaleCount(originalFirst.count(), originalFirst.itemStack().getMaxStackSize(), factor),
                originalFirst.components());

        return new MerchantOffer(first, offer.getItemCostB(), offer.getResult(),
                offer.getUses(), offer.getMaxUses(), offer.getXp(),
                offer.getPriceMultiplier(), offer.getDemand());
    }

    /** The wrapped factory, so the catalogue can enumerate it and re-apply pricing itself. */
    public ItemListing delegate() {
        return this.delegate;
    }

    /** Shared so catalogue rows are priced exactly the way a real generated offer would be. */
    public static int scaleCount(int count, int maxCount, float factor) {
        return Mth.clamp(Math.round(count * factor), 1, maxCount);
    }

    public float priceFactor(Entity entity) {
        float factor = VillagerTradingPlus.CONFIG.trade_cost_scale;
        if (VillagerTradingPlus.CONFIG.enable_time_of_day_pricing) {
            float variance = VillagerTradingPlus.CONFIG.time_of_day_price_variance;
            long timeOfDay = entity.level().getOverworldClockTime() % 24000L;
            double swing = Math.sin(2.0 * Math.PI * (timeOfDay / 24000.0));
            factor *= (float) (1.0 + variance * swing);
        }
        return factor;
    }
}
