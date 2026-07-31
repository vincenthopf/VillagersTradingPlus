package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

public class JsonSellEnchantedToolTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(json.get("sell").getAsJsonObject());
        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        return new Factory(sell, currency, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        /** Bounds of the {@code 5 + nextInt(15)} enchanting power roll that also sets the price. */
        private static final int MIN_POWER = 5;
        private static final int MAX_POWER = 19;

        private final ItemStack sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack sell, ItemStack currency, int maxUses, int experience, float multiplier) {
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            int power = MIN_POWER + random.nextInt(MAX_POWER - MIN_POWER + 1);
            // The pool of candidate enchantments is passed in explicitly now instead of being implied by
            // a boolean: #minecraft:in_enchanting_table is what "as an enchanting table would" means.
            ItemStack itemStack = EnchantmentHelper.enchant(random, new ItemStack(this.sell.getItem()), power,
                    entity.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT)
                            .getOrCreateEntryList(EnchantmentTags.IN_ENCHANTING_TABLE).stream());

            return new TradeOffer(traded(new ItemStack(currency.getItem(), price(power))), itemStack,
                    this.maxUses, this.experience, multiplier);
        }

        /**
         * Not enumerable: {@link EnchantmentHelper#enchant} picks an opaque combination of
         * enchantments, so there is no finite variant list to walk. One fixed-seed sample stands in,
         * which at least keeps the row identical between openings, and the price range is stated
         * exactly since it follows from the power roll.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            TradeOffer sample = create(merchant, Random.create(0L));
            if (sample != null) {
                out.addOffer(sample, price(MIN_POWER), price(MAX_POWER));
            }
        }

        private int price(int power) {
            return Math.min(this.currency.getCount() + power, 64);
        }
    }
}
