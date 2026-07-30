package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

public class JsonProcessItemTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(json.get("sell").getAsJsonObject());
        ItemStack buy = getItemStackFromJson(json.get("convertible").getAsJsonObject());
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());

        return new Factory(buy, sell, currency, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack buy;
        private final ItemStack sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(ItemStack buy, ItemStack sell, ItemStack currency, int maxUses, int experience, float multiplier, int demand) {
            this.buy = buy;
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            return new TradeOffer(buy, currency, sell, 0, this.maxUses, this.experience, this.multiplier, this.demand);
        }

    }
}
