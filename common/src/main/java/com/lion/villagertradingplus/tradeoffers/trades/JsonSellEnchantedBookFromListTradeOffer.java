package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sells an enchanted book whose enchantment is a weighted random pick from a curated list, each
 * entry carrying its own level range. Price scales with the chosen level and doubles for
 * enchantments tagged {@code #minecraft:double_trade_price}, capped at 64 (mirrors the vanilla-style
 * pricing of {@code sell_enchanted_book}).
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

        // Present while a datapack load is running, which is the only time this parses. Used to read
        // an enchantment's own level bounds as defaults - not to keep the entry, see Entry below.
        Optional<Registry<Enchantment>> registry = DatapackRegistries.registry(RegistryKeys.ENCHANTMENT);

        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("enchantments")) {
            JsonObject obj = element.getAsJsonObject();
            String id = obj.get("id").getAsString();
            Identifier identifier = Identifier.tryParse(id);

            Optional<RegistryEntry.Reference<Enchantment>> enchantment = identifier == null
                    ? Optional.empty()
                    : registry.flatMap(r -> r.getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, identifier)));

            if (enchantment.isEmpty()) {
                VillagerTradingPlus.LOGGER.error("Unknown enchantment in sell_enchanted_book_from_list: {}", id);
                continue;
            }

            Enchantment value = enchantment.get().value();
            entries.add(new Entry(
                    RegistryKey.of(RegistryKeys.ENCHANTMENT, identifier),
                    readInt(obj, "min_level", value.getMinLevel()),
                    readInt(obj, "max_level", value.getMaxLevel()),
                    readInt(obj, "weight", 1)));
        }

        int baseCost = readInt(json, "base_cost", 2);
        int costPerLevel = readInt(json, "cost_per_level", 3);
        int treasureMultiplier = readInt(json, "treasure_multiplier", 2);

        return new Factory(currency, entries, baseCost, costPerLevel, treasureMultiplier,
                maxUses, experience, priceMultiplier, demand);
    }

    /**
     * Holds the enchantment's <em>key</em>, never a resolved entry: the factory outlives the registry
     * this was parsed against, and a captured entry would point into a world that has since been
     * closed. Resolution happens per call, where an {@link Entity} supplies the live registry.
     */
    private record Entry(RegistryKey<Enchantment> key, int minLevel, int maxLevel, int weight) {
    }

    /** An {@link Entry} paired with the entry it resolved to in the world being traded in. */
    private record Resolved(Entry entry, RegistryEntry<Enchantment> enchantment) {
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final ItemStack currency;
        private final List<Entry> entries;
        private final int baseCost;
        private final int costPerLevel;
        private final int treasureMultiplier;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(ItemStack currency, List<Entry> entries, int baseCost, int costPerLevel,
                       int treasureMultiplier, int maxUses, int experience, float multiplier, int demand) {
            this.currency = currency;
            this.entries = entries;
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
            List<Resolved> resolved = resolve(entity.getWorld());
            int totalWeight = totalWeight(resolved);
            if (totalWeight <= 0) {
                return null;
            }

            Resolved chosen = pick(resolved, totalWeight, random);
            int level = MathHelper.nextInt(random, chosen.entry().minLevel(), chosen.entry().maxLevel());

            return new TradeOffer(traded(new ItemStack(currency.getItem(), cost(chosen.enchantment(), level))),
                    tradedOrEmpty(new ItemStack(Items.BOOK)), book(chosen.enchantment(), level),
                    0, maxUses, experience, multiplier, demand);
        }

        /**
         * Both the enchantment and its level are enumerable and the price follows from them, so every
         * (enchantment, level) pair the list can yield gets its own row with no randomness left.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<Resolved> resolved = resolve(merchant.getWorld());
            int totalWeight = totalWeight(resolved);

            for (Resolved candidate : resolved) {
                Entry entry = candidate.entry();
                out.pushWeight(entry.weight(), totalWeight);
                // The level within a chosen entry is then picked uniformly, so each row is worth a
                // fraction of the entry's weight rather than all of it.
                out.pushShare(1.0f / (entry.maxLevel() - entry.minLevel() + 1));
                for (int level = entry.minLevel(); level <= entry.maxLevel(); level++) {
                    if (out.isFull()) {
                        out.countSkipped(entry.maxLevel() - level + 1);
                        break;
                    }
                    out.add(new ItemStack(currency.getItem(), cost(candidate.enchantment(), level)),
                            new ItemStack(Items.BOOK), book(candidate.enchantment(), level),
                            maxUses, experience, multiplier, demand);
                }
                out.popShare();
                out.popWeight();
            }
        }

        /**
         * Resolves the list against the world's enchantment registry, dropping entries this world does
         * not have. Shared by {@code create} and the catalogue so both agree on which trades exist and
         * on the weights they compete with - a missing enchantment must not leave a hole in the odds
         * that only one of the two knows about.
         */
        private List<Resolved> resolve(World world) {
            Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            List<Resolved> resolved = new ArrayList<>(this.entries.size());
            for (Entry entry : this.entries) {
                registry.getOptional(entry.key()).ifPresent(enchantment -> resolved.add(new Resolved(entry, enchantment)));
            }
            return resolved;
        }

        private static int totalWeight(List<Resolved> resolved) {
            int total = 0;
            for (Resolved candidate : resolved) {
                total += candidate.entry().weight();
            }
            return total;
        }

        private ItemStack book(RegistryEntry<Enchantment> enchantment, int level) {
            return EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(enchantment, level));
        }

        private int cost(RegistryEntry<Enchantment> enchantment, int level) {
            int cost = baseCost + level * costPerLevel;
            // isTreasure() is gone; which enchantments cost double in a trade is data-driven now, and
            // this is the tag vanilla created for exactly that decision.
            if (enchantment.isIn(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                cost *= treasureMultiplier;
            }
            return MathHelper.clamp(cost, 1, 64);
        }

        private static Resolved pick(List<Resolved> resolved, int totalWeight, Random random) {
            int roll = random.nextInt(totalWeight);
            for (Resolved candidate : resolved) {
                roll -= candidate.entry().weight();
                if (roll < 0) {
                    return candidate;
                }
            }
            return resolved.get(resolved.size() - 1);
        }
    }
}
