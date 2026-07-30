package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
        // Only the key is resolved here. Enchantments live in a dynamic registry since 1.21, so the
        // entry itself does not exist until a world is loaded - and deserialization has no world.
        RegistryKey<Enchantment> enchantmentKey = enchantmentId == null
                ? null
                : RegistryKey.of(RegistryKeys.ENCHANTMENT, enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(currency, enchantmentKey, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack currency;
        @Nullable
        private final RegistryKey<Enchantment> enchantmentKey;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack currency, @Nullable RegistryKey<Enchantment> enchantmentKey, int level, int maxUses, int experience, float multiplier) {
            this.currency = currency;
            this.enchantmentKey = enchantmentKey;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            if (this.enchantmentKey != null) {
                // An unknown id simply yields a blank book rather than losing the whole trade; a
                // datapack may name an enchantment that this world does not have loaded.
                entity.getWorld().getRegistryManager()
                        .get(RegistryKeys.ENCHANTMENT)
                        .getEntry(this.enchantmentKey)
                        .ifPresent(enchantment -> EnchantedBookItem.addEnchantment(book,
                                new EnchantmentLevelEntry(enchantment, this.level)));
            }
            return new TradeOffer(traded(this.currency.copy()), book, this.maxUses, this.experience, this.multiplier);
        }
    }
}
