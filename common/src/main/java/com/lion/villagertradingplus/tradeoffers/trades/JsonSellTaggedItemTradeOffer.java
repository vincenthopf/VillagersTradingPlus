package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.util.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

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
    public VillagerTrades.ItemListing deserialize(JsonObject json) {
        loadDefaultStats(json);
        Ingredient sell = Ingredient.fromJson(JsonFields.requireObject(json, "sell_tagged_item trade", "sell"));
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());
        return new Factory(sell, currency, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements VillagerTrades.ItemListing, CatalogExpandable {
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
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            ItemStack resolved = sell.resolve(random);
            if (resolved.isEmpty()) {
                return null;
            }
            return new MerchantOffer(traded(currency.copy()), tradedOrEmpty(ItemStack.EMPTY), resolved, 0, maxUses, experience, multiplier, demand);
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
