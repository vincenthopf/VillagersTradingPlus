package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.ItemListing;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sells an enchanted book with a fixed enchantment at a fixed level (e.g. a guaranteed
 * Blast Protection I book), unlike {@code sell_enchanted_book} which picks a random enchantment.
 */
public class JsonSellSpecificEnchantedBookTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public ItemListing deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        Identifier enchantmentId = Identifier.tryParse(readString(json, "enchantment", "minecraft:unbreaking"));
        // Only the key is resolved here. Enchantments live in a dynamic registry since 1.21, so the
        // entry itself does not exist until a world is loaded - and deserialization has no world.
        ResourceKey<Enchantment> enchantmentKey = enchantmentId == null
                ? null
                : ResourceKey.create(Registries.ENCHANTMENT, enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(currency, enchantmentKey, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements ItemListing {
        private final ItemStack currency;
        @Nullable
        private final ResourceKey<Enchantment> enchantmentKey;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack currency, @Nullable ResourceKey<Enchantment> enchantmentKey, int level, int maxUses, int experience, float multiplier) {
            this.currency = currency;
            this.enchantmentKey = enchantmentKey;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public MerchantOffer getOffer(Entity entity, net.minecraft.util.RandomSource random) {
            // forEnchantment builds the stack instead of mutating one, so the blank book is the
            // fallback rather than the starting point. An unknown id still just yields that blank
            // book rather than losing the whole trade; a datapack may name an enchantment that this
            // world does not have loaded.
            ItemStack book = this.enchantmentKey == null
                    ? new ItemStack(Items.ENCHANTED_BOOK)
                    : entity.level().registryAccess()
                            .lookupOrThrow(Registries.ENCHANTMENT)
                            .get(this.enchantmentKey)
                            .map(enchantment -> EnchantmentHelper.createBook(
                                    new EnchantmentInstance(enchantment, this.level)))
                            .orElseGet(() -> new ItemStack(Items.ENCHANTED_BOOK));

            return new MerchantOffer(traded(this.currency.copy()), book, this.maxUses, this.experience, this.multiplier);
        }
    }
}
