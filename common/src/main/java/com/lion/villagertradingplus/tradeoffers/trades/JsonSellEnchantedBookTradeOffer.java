package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

public class JsonSellEnchantedBookTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public VillagerTrades.ItemListing deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJsonWithoutCount(JsonFields.requireObject(json, "sell_enchanted_book trade", "currency"));

        return new Factory(currency, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements VillagerTrades.ItemListing, CatalogExpandable {
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(ItemStack currency, int maxUses, int experience, float multiplier, int demand) {
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        public MerchantOffer getOffer(Entity entity, net.minecraft.util.RandomSource random) {
            List<Holder<Enchantment>> list = available(entity.level());
            if (list.isEmpty()) {
                return null;
            }
            Holder<Enchantment> enchantment = list.get(random.nextInt(list.size()));
            int level = Mth.nextInt(random, enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
            int price = clampPrice(2 + random.nextInt(5 + level * 10) + 3 * level, enchantment);

            return new MerchantOffer(traded(new ItemStack(currency.getItem(), price)), tradedOrEmpty(new ItemStack(Items.BOOK)),
                    book(enchantment, level), 0, this.maxUses, this.experience, this.multiplier, this.demand);
        }

        /**
         * The enchantment and level are enumerable even though the price is not, so every pair gets a
         * row showing the cheapest it can be, and the tooltip carries the full range.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<Holder<Enchantment>> available = available(merchant.level());
            for (Holder<Enchantment> enchantment : available) {
                // create() picks an enchantment uniformly, then a level uniformly within it.
                int levels = enchantment.value().getMaxLevel() - enchantment.value().getMinLevel() + 1;
                out.pushShare(1.0f / (available.size() * levels));
                for (int level = enchantment.value().getMinLevel(); level <= enchantment.value().getMaxLevel(); level++) {
                    if (out.isFull()) {
                        out.countSkipped(1);
                        continue;
                    }
                    // create() rolls 2 + nextInt(5 + 10*level) + 3*level, so the extremes are
                    // 2 + 3*level and 6 + 13*level before the treasure doubling and the 64 cap.
                    int minPrice = clampPrice(2 + 3 * level, enchantment);
                    int maxPrice = clampPrice(6 + 13 * level, enchantment);

                    out.add(new ItemStack(currency.getItem(), minPrice), new ItemStack(Items.BOOK),
                            book(enchantment, level), maxUses, experience, multiplier, demand,
                            minPrice, maxPrice);
                }
                out.popShare();
            }
        }

        /**
         * Which enchantments a villager may sell is data-driven now: the old
         * isAvailableForEnchantedBookOffer() flag became the #minecraft:tradeable tag, so datapacks
         * can widen or narrow this list without touching code.
         */
        private static List<Holder<Enchantment>> available(Level world) {
            List<Holder<Enchantment>> list = new ArrayList<>();
            world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                    .filter(entry -> entry.is(EnchantmentTags.TRADEABLE))
                    .forEach(list::add);
            return list;
        }

        private static ItemStack book(Holder<Enchantment> enchantment, int level) {
            return EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, level));
        }

        private static int clampPrice(int price, Holder<Enchantment> enchantment) {
            if (enchantment.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                price *= 2;
            }
            return Math.min(price, 64);
        }
    }
}
