package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

public class JsonBuyItemTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "buy_item trade", "buy"));
        ItemStack currency = getItemStackFromJson(JsonFields.requireObject(json, "buy_item trade", "reward"));

        return new Factory(buy, currency, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack buy;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(ItemStack buy, ItemStack currency, int maxUses, int experience, float multiplier, int demand) {
            this.buy = buy;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            return new TradeOffer(buy, net.minecraft.item.ItemStack.EMPTY, currency, 0, this.maxUses, this.experience, this.multiplier, this.demand);
        }

    }
}
