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
 * Villager buys any single member of an item tag (resolved to one concrete item per generated
 * offer) in exchange for a fixed reward.
 *
 * <pre>
 * { "type": "villagertradingplus:buy_tagged_item",
 *   "buy": { "tag": "minecraft:planks", "count": 8 },
 *   "reward": { "item": "minecraft:emerald", "count": 1 } }
 * </pre>
 */
public class JsonBuyTaggedItemTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);
        Ingredient buy = Ingredient.fromJson(json.get("buy").getAsJsonObject());
        ItemStack reward = getItemStackFromJson(json.get("reward").getAsJsonObject());
        return new Factory(buy, reward, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final Ingredient buy;
        private final ItemStack reward;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(Ingredient buy, ItemStack reward, int maxUses, int experience, float multiplier, int demand) {
            this.buy = buy;
            this.reward = reward;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        @Override
        public TradeOffer create(Entity entity, Random random) {
            ItemStack resolved = buy.resolve(random);
            if (resolved.isEmpty()) {
                return null;
            }
            return new TradeOffer(traded(resolved), tradedOrEmpty(ItemStack.EMPTY), reward.copy(), 0, maxUses, experience, multiplier, demand);
        }

        /** A tag input is one random member per generated offer, so the catalogue lists them all. */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<ItemStack> variants = buy.resolveAll();
            out.pushShare(variants.isEmpty() ? 1.0f : 1.0f / variants.size());
            for (ItemStack resolved : variants) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }
                out.add(resolved, ItemStack.EMPTY, reward, maxUses, experience, multiplier, demand);
            }
            out.popShare();
        }
    }
}
