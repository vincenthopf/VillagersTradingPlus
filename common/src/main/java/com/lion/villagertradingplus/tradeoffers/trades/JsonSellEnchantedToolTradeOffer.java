package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import org.jetbrains.annotations.NotNull;

public class JsonSellEnchantedToolTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public VillagerTrades.ItemListing deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_enchanted_tool trade", "sell"));
        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        return new Factory(sell, currency, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements VillagerTrades.ItemListing, CatalogExpandable {
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

        public MerchantOffer getOffer(Entity entity, net.minecraft.util.RandomSource random) {
            int power = MIN_POWER + random.nextInt(MAX_POWER - MIN_POWER + 1);
            // The pool of candidate enchantments is passed in explicitly now instead of being implied by
            // a boolean: #minecraft:in_enchanting_table is what "as an enchanting table would" means.
            // Copied rather than rebuilt from the item alone, so the JSON's count and any name, lore
            // or component sugar on the sell stack survive the random enchanting pass.
            ItemStack itemStack = EnchantmentHelper.enchantItem(random, this.sell.copy(), power,
                    entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE).stream());

            return new MerchantOffer(traded(new ItemStack(currency.getItem(), price(power))), itemStack,
                    this.maxUses, this.experience, multiplier);
        }

        /**
         * Not enumerable: {@link EnchantmentHelper#enchantItem} picks an opaque combination of
         * enchantments, so there is no finite variant list to walk. One fixed-seed sample stands in,
         * which at least keeps the row identical between openings, and the price range is stated
         * exactly since it follows from the power roll.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            MerchantOffer sample = getOffer(merchant, RandomSource.create(0L));
            if (sample != null) {
                out.addOffer(sample, price(MIN_POWER), price(MAX_POWER));
            }
        }

        private int price(int power) {
            return Math.min(this.currency.getCount() + power, 64);
        }
    }
}
