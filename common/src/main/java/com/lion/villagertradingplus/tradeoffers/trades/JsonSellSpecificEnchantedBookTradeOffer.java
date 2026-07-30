package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sells an enchanted book with a fixed enchantment at a fixed level (e.g. a guaranteed
 * Blast Protection I book), unlike {@code sell_enchanted_book} which picks a random enchantment.
 */
public class JsonSellSpecificEnchantedBookTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        Identifier enchantmentId = Identifier.tryParse(readString(json, "enchantment", "minecraft:unbreaking"));
        Enchantment enchantment = enchantmentId == null ? null : Registries.ENCHANTMENT.get(enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(currency, enchantment, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack currency;
        @Nullable
        private final Enchantment enchantment;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack currency, @Nullable Enchantment enchantment, int level, int maxUses, int experience, float multiplier) {
            this.currency = currency;
            this.enchantment = enchantment;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            if (this.enchantment != null) {
                EnchantedBookItem.addEnchantment(book, new EnchantmentLevelEntry(this.enchantment, this.level));
            }
            return new TradeOffer(traded(this.currency.copy()), book, this.maxUses, this.experience, this.multiplier);
        }
    }
}
