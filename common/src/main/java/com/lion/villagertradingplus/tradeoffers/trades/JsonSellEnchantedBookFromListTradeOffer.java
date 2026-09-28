package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.ItemListing;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

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
    public ItemListing deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJsonWithoutCount(JsonFields.requireObject(json, "sell_enchanted_book_from_list trade", "currency"));

        // Present while a datapack load is running, which is the only time this parses. Used to read
        // an enchantment's own level bounds as defaults - not to keep the entry, see Entry below.
        Optional<Registry<Enchantment>> registry = DatapackRegistries.registry(Registries.ENCHANTMENT);

        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : JsonFields.requireArray(json, "sell_enchanted_book_from_list trade", "enchantments")) {
            JsonObject obj = element.getAsJsonObject();
            String id = obj.get("id").getAsString();
            Identifier identifier = Identifier.tryParse(id);

            Optional<Holder.Reference<Enchantment>> enchantment = identifier == null
                    ? Optional.empty()
                    : registry.flatMap(r -> r.get(ResourceKey.create(Registries.ENCHANTMENT, identifier)));

            if (enchantment.isEmpty()) {
                VillagerTradingPlus.LOGGER.error("Unknown enchantment in sell_enchanted_book_from_list: {}", id);
                continue;
            }

            Enchantment value = enchantment.get().value();
            entries.add(new Entry(
                    ResourceKey.create(Registries.ENCHANTMENT, identifier),
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
    private record Entry(ResourceKey<Enchantment> key, int minLevel, int maxLevel, int weight) {
    }

    /** An {@link Entry} paired with the entry it resolved to in the world being traded in. */
    private record Resolved(Entry entry, Holder<Enchantment> enchantment) {
    }

    private static class Factory implements ItemListing, CatalogExpandable {
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
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            List<Resolved> resolved = resolve(entity.level());
            int totalWeight = totalWeight(resolved);
            if (totalWeight <= 0) {
                return null;
            }

            Resolved chosen = pick(resolved, totalWeight, random);
            int level = Mth.nextInt(random, chosen.entry().minLevel(), chosen.entry().maxLevel());

            return new MerchantOffer(traded(new ItemStack(currency.getItem(), cost(chosen.enchantment(), level))),
                    tradedOrEmpty(new ItemStack(Items.BOOK)), book(chosen.enchantment(), level),
                    0, maxUses, experience, multiplier, demand);
        }

        /**
         * Both the enchantment and its level are enumerable and the price follows from them, so every
         * (enchantment, level) pair the list can yield gets its own row with no randomness left.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<Resolved> resolved = resolve(merchant.level());
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
        private List<Resolved> resolve(Level world) {
            Registry<Enchantment> registry = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            List<Resolved> resolved = new ArrayList<>(this.entries.size());
            for (Entry entry : this.entries) {
                registry.get(entry.key()).ifPresent(enchantment -> resolved.add(new Resolved(entry, enchantment)));
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

        private ItemStack book(Holder<Enchantment> enchantment, int level) {
            return EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, level));
        }

        private int cost(Holder<Enchantment> enchantment, int level) {
            int cost = baseCost + level * costPerLevel;
            // isTreasure() is gone; which enchantments cost double in a trade is data-driven now, and
            // this is the tag vanilla created for exactly that decision.
            if (enchantment.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                cost *= treasureMultiplier;
            }
            return Mth.clamp(cost, 1, 64);
        }

        private static Resolved pick(List<Resolved> resolved, int totalWeight, RandomSource random) {
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
