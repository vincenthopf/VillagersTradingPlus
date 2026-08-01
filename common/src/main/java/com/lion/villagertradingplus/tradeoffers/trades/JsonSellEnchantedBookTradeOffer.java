package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.world.World;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JsonSellEnchantedBookTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJsonWithoutCount(json.get("currency").getAsJsonObject());

        return new Factory(currency, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
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

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            List<RegistryEntry<Enchantment>> list = available(entity.getWorld());
            if (list.isEmpty()) {
                return null;
            }
            RegistryEntry<Enchantment> enchantment = list.get(random.nextInt(list.size()));
            int level = MathHelper.nextInt(random, enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
            int price = clampPrice(2 + random.nextInt(5 + level * 10) + 3 * level, enchantment);

            return new TradeOffer(traded(new ItemStack(currency.getItem(), price)), tradedOrEmpty(new ItemStack(Items.BOOK)),
                    book(enchantment, level), 0, this.maxUses, this.experience, this.multiplier, this.demand);
        }

        /**
         * The enchantment and level are enumerable even though the price is not, so every pair gets a
         * row showing the cheapest it can be, and the tooltip carries the full range.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<RegistryEntry<Enchantment>> available = available(merchant.getWorld());
            for (RegistryEntry<Enchantment> enchantment : available) {
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
        private static List<RegistryEntry<Enchantment>> available(World world) {
            List<RegistryEntry<Enchantment>> list = new ArrayList<>();
            world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).streamEntries()
                    .filter(entry -> entry.isIn(EnchantmentTags.TRADEABLE))
                    .forEach(list::add);
            return list;
        }

        private static ItemStack book(RegistryEntry<Enchantment> enchantment, int level) {
            return EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(enchantment, level));
        }

        private static int clampPrice(int price, RegistryEntry<Enchantment> enchantment) {
            if (enchantment.isIn(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                price *= 2;
            }
            return Math.min(price, 64);
        }
    }
}
