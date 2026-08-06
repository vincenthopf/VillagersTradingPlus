package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;

import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sells a tool with a fixed enchantment at a fixed level (e.g. a guaranteed Fortune III pickaxe),
 * unlike {@code sell_enchanted_tool} which enchants randomly.
 */
public class JsonSellSpecificEnchantedToolTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_specific_enchanted_tool trade", "sell"));
        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        Identifier enchantmentId = Identifier.tryParse(readString(json, "enchantment", "minecraft:fortune"));
        Enchantment enchantment = enchantmentId == null ? null : Registries.ENCHANTMENT.get(enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(sell, currency, enchantment, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack sell;
        private final ItemStack currency;
        @Nullable
        private final Enchantment enchantment;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack sell, ItemStack currency, @Nullable Enchantment enchantment, int level, int maxUses, int experience, float multiplier) {
            this.sell = sell;
            this.currency = currency;
            this.enchantment = enchantment;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            ItemStack itemStack = new ItemStack(this.sell.getItem(), this.sell.getCount());
            if (this.enchantment != null) {
                itemStack.addEnchantment(this.enchantment, this.level);
            }
            return new TradeOffer(this.currency.copy(), itemStack, this.maxUses, this.experience, this.multiplier);
        }
    }
}
