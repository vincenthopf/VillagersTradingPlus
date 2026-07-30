package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Sells an enchanted book whose enchantment is a weighted random pick from a curated list, each
 * entry carrying its own level range. Price scales with the chosen level and doubles for treasure
 * enchantments, capped at 64 (mirrors the vanilla-style pricing of {@code sell_enchanted_book}).
 *
 * <pre>
 * { "type": "villagertradingplus:sell_enchanted_book_from_list",
 *   "currency": { "item": "minecraft:emerald" },
 *   "enchantments": [
 *     { "id": "minecraft:sharpness", "min_level": 1, "max_level": 3, "weight": 5 },
 *     { "id": "minecraft:mending",   "min_level": 1, "max_level": 1, "weight": 1 } ],
 *   "base_cost": 2, "cost_per_level": 3, "treasure_multiplier": 2 }
 * </pre>
 */
public class JsonSellEnchantedBookFromListTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJsonWithoutCount(json.get("currency").getAsJsonObject());

        List<Entry> entries = new ArrayList<>();
        int totalWeight = 0;
        for (JsonElement element : json.getAsJsonArray("enchantments")) {
            JsonObject obj = element.getAsJsonObject();
            Enchantment enchantment = Registries.ENCHANTMENT.get(Identifier.tryParse(obj.get("id").getAsString()));
            if (enchantment == null) {
                VillagerTradingPlus.LOGGER.error("Unknown enchantment in sell_enchanted_book_from_list: " + obj.get("id").getAsString());
                continue;
            }
            int min = readInt(obj, "min_level", enchantment.getMinLevel());
            int max = readInt(obj, "max_level", enchantment.getMaxLevel());
            int weight = readInt(obj, "weight", 1);
            entries.add(new Entry(enchantment, min, max, weight));
            totalWeight += weight;
        }

        int baseCost = readInt(json, "base_cost", 2);
        int costPerLevel = readInt(json, "cost_per_level", 3);
        int treasureMultiplier = readInt(json, "treasure_multiplier", 2);

        return new Factory(currency, entries, totalWeight, baseCost, costPerLevel, treasureMultiplier,
                maxUses, experience, priceMultiplier, demand);
    }

    private record Entry(Enchantment enchantment, int minLevel, int maxLevel, int weight) {
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final ItemStack currency;
        private final List<Entry> entries;
        private final int totalWeight;
        private final int baseCost;
        private final int costPerLevel;
        private final int treasureMultiplier;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(ItemStack currency, List<Entry> entries, int totalWeight, int baseCost, int costPerLevel,
                       int treasureMultiplier, int maxUses, int experience, float multiplier, int demand) {
            this.currency = currency;
            this.entries = entries;
            this.totalWeight = totalWeight;
            this.baseCost = baseCost;
            this.costPerLevel = costPerLevel;
            this.treasureMultiplier = treasureMultiplier;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        @Override
        public TradeOffer create(Entity entity, Random random) {
            if (entries.isEmpty() || totalWeight <= 0) {
                return null;
            }

            Entry chosen = pick(random);
            int level = MathHelper.nextInt(random, chosen.minLevel(), chosen.maxLevel());

            return new TradeOffer(new ItemStack(currency.getItem(), cost(chosen.enchantment(), level)),
                    new ItemStack(Items.BOOK), book(chosen.enchantment(), level),
                    0, maxUses, experience, multiplier, demand);
        }

        /**
         * Both the enchantment and its level are enumerable and the price follows from them, so every
         * (enchantment, level) pair the list can yield gets its own row with no randomness left.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            for (Entry entry : entries) {
                out.pushWeight(entry.weight(), totalWeight);
                // The level within a chosen entry is then picked uniformly, so each row is worth a
                // fraction of the entry's weight rather than all of it.
                out.pushShare(1.0f / (entry.maxLevel() - entry.minLevel() + 1));
                for (int level = entry.minLevel(); level <= entry.maxLevel(); level++) {
                    if (out.isFull()) {
                        out.countSkipped(entry.maxLevel() - level + 1);
                        break;
                    }
                    out.add(new ItemStack(currency.getItem(), cost(entry.enchantment(), level)),
                            new ItemStack(Items.BOOK), book(entry.enchantment(), level),
                            maxUses, experience, multiplier, demand);
                }
                out.popShare();
                out.popWeight();
            }
        }

        private ItemStack book(Enchantment enchantment, int level) {
            return EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, level));
        }

        private int cost(Enchantment enchantment, int level) {
            int cost = baseCost + level * costPerLevel;
            if (enchantment.isTreasure()) {
                cost *= treasureMultiplier;
            }
            return MathHelper.clamp(cost, 1, 64);
        }

        private Entry pick(Random random) {
            int roll = random.nextInt(totalWeight);
            for (Entry entry : entries) {
                roll -= entry.weight();
                if (roll < 0) {
                    return entry;
                }
            }
            return entries.get(entries.size() - 1);
        }
    }
}
