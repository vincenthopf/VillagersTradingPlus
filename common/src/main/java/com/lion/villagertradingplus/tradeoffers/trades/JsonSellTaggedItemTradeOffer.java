package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.util.Ingredient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Sells any single member of an item tag (resolved to one concrete item per generated offer) for a
 * fixed currency.
 *
 * <pre>
 * { "type": "villagertradingplus:sell_tagged_item",
 *   "sell": { "tag": "minecraft:saplings", "count": 1 },
 *   "priceIn": { "item": "minecraft:emerald", "count": 3 } }
 * </pre>
 */
public class JsonSellTaggedItemTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);
        Ingredient sell = Ingredient.fromJson(json.get("sell").getAsJsonObject());
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());
        return new Factory(sell, currency, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final Ingredient sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(Ingredient sell, ItemStack currency, int maxUses, int experience, float multiplier, int demand) {
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        @Override
        public TradeOffer create(Entity entity, Random random) {
            ItemStack resolved = sell.resolve(random);
            if (resolved.isEmpty()) {
                return null;
            }
            return new TradeOffer(traded(currency.copy()), tradedOrEmpty(ItemStack.EMPTY), resolved, 0, maxUses, experience, multiplier, demand);
        }

        /** A tag input is one random member per generated offer, so the catalogue lists them all. */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<ItemStack> variants = sell.resolveAll();
            out.pushShare(variants.isEmpty() ? 1.0f : 1.0f / variants.size());
            for (ItemStack resolved : variants) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }
                out.add(currency, ItemStack.EMPTY, resolved, maxUses, experience, multiplier, demand);
            }
            out.popShare();
        }
    }
}
