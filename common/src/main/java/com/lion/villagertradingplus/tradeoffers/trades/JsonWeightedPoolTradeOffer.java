package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpansion;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A rarity wrapper: picks one of N sub-trades by weight at create() time. Each sub-trade is a full
 * trade object routed back through {@link TradeOfferManager#deserializeTrade}, so nested conditions
 * and pricing apply automatically. A picked sub-trade may itself yield {@code null} (e.g. its own
 * condition failed); that is the intended rarity behaviour.
 *
 * <pre>
 * { "type": "villagertradingplus:weighted_pool",
 *   "pool": [
 *     { "weight": 10, "trade": { "type": "villagertradingplus:sell_item", ... } },
 *     { "weight": 1,  "trade": { "type": "villagertradingplus:sell_enchanted_book_from_list", ... } } ] }
 * </pre>
 */
public class JsonWeightedPoolTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        List<Entry> entries = new ArrayList<>();
        int totalWeight = 0;
        for (JsonElement element : JsonFields.requireArray(json, "weighted_pool trade", "pool")) {
            JsonObject entry = element.getAsJsonObject();
            int weight = readInt(entry, "weight", 1);
            TradeOffers.Factory factory = TradeOfferManager.deserializeTrade(entry.getAsJsonObject("trade"));

            // deserializeTrade wraps everything it returns in a PricingTradeFactory, and this pool is
            // itself about to be wrapped by whoever called us. Left alone, the global cost scale would
            // hit a pooled trade twice: at trade_cost_scale 1.5 a price of 10 would come out at 23
            // instead of 15, while the same trade outside a pool came out correctly. Strip the inner
            // wrapper so pricing applies exactly once, at the outermost level. The conditional wrapper
            // sits underneath it and is preserved.
            if (factory instanceof PricingTradeFactory pricing) {
                factory = pricing.delegate();
            }

            if (factory != null && weight > 0) {
                entries.add(new Entry(weight, factory));
                totalWeight += weight;
            }
        }
        return new Factory(entries, totalWeight);
    }

    private record Entry(int weight, TradeOffers.Factory factory) {
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final List<Entry> entries;
        private final int totalWeight;

        public Factory(List<Entry> entries, int totalWeight) {
            this.entries = entries;
            this.totalWeight = totalWeight;
        }

        /**
         * Every sub-trade gets its own catalogue row rather than the pool showing as one row: the
         * whole point of listing a pool is seeing what is in it and how likely each member is.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            for (Entry entry : entries) {
                out.pushWeight(entry.weight(), totalWeight);
                CatalogExpansion.expand(entry.factory(), merchant, out);
                out.popWeight();
            }
        }

        @Override
        public TradeOffer create(Entity entity, Random random) {
            if (entries.isEmpty() || totalWeight <= 0) {
                return null;
            }
            int roll = random.nextInt(totalWeight);
            for (Entry entry : entries) {
                roll -= entry.weight();
                if (roll < 0) {
                    return entry.factory().create(entity, random);
                }
            }
            return null;
        }
    }
}
