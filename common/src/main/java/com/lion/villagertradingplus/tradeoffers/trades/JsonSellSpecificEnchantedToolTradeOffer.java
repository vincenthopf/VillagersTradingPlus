package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;

import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
        // Only the key is resolved here. Enchantments live in a dynamic registry since 1.21, so the
        // entry itself does not exist until a world is loaded - and deserialization has no world.
        RegistryKey<Enchantment> enchantmentKey = enchantmentId == null
                ? null
                : RegistryKey.of(RegistryKeys.ENCHANTMENT, enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(sell, currency, enchantmentKey, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack sell;
        private final ItemStack currency;
        @Nullable
        private final RegistryKey<Enchantment> enchantmentKey;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack sell, ItemStack currency, @Nullable RegistryKey<Enchantment> enchantmentKey, int level, int maxUses, int experience, float multiplier) {
            this.sell = sell;
            this.currency = currency;
            this.enchantmentKey = enchantmentKey;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            // Copied rather than rebuilt from item and count, so a name, lore or extra enchantment
            // the JSON put on the sell stack survives onto the traded tool.
            ItemStack itemStack = this.sell.copy();
            if (this.enchantmentKey != null) {
                // An unknown id simply yields an unenchanted tool rather than losing the whole trade;
                // a datapack may name an enchantment that this world does not have loaded.
                entity.getWorld().getRegistryManager()
                        .get(RegistryKeys.ENCHANTMENT)
                        .getEntry(this.enchantmentKey)
                        .ifPresent(enchantment -> itemStack.addEnchantment(enchantment, this.level));
            }
            return new TradeOffer(traded(this.currency.copy()), itemStack, this.maxUses, this.experience, this.multiplier);
        }
    }
}
