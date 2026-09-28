package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.storage.loot.LootContext;

/**
 * Merges mod-defined JSON trades into the vanilla wandering-trader pool
 * (the {@code wandering_trader} trade sets). That pool has two tiers: level 1 ("common",
 * several are picked) and level 2 ("rare", one is picked).
 *
 * <p>Kept idempotent across resource reloads by snapshotting the vanilla pool once and rebuilding
 * from that snapshot every reload, so trades are never duplicated. A file may set {@code replace}
 * to swap out the vanilla trades for a tier instead of adding to them; a tier is never left empty
 * (that would crash vanilla's rare-pick), so replacing a tier you defined no trades for keeps
 * vanilla.
 *
 * <p><b>Since 1.21.6 the merged pool is served, not written back.</b> Vanilla used to expose an
 * {@code Int2ObjectMap} that could be mutated in place; it is now an <em>immutable</em>
 * {@code List<Pair<Factory[], Integer>>} where the {@code Integer} is how many offers to draw from
 * that pool. So the result is kept here and {@code WanderingTraderMixin} redirects vanilla's field
 * read to it. Nothing writes to the vanilla list.
 */
public final class WanderingTraderTradeLoader {

    /** JSON tier names mapped to the vanilla wandering-trader pool levels. */
    public static final Map<String, Integer> LEVEL_MAPPING = new HashMap<>();

    static {
        LEVEL_MAPPING.put("common", 1);
        LEVEL_MAPPING.put("rare", 2);
    }

    /** Vanilla pools by level (1-based), rebuilt from the trade set registry on every reload. */
    private static Int2ObjectMap<ItemListing[]> vanillaBaseline = new Int2ObjectOpenHashMap<>();
    /** Vanilla's own trade set per level, so a tier we never touch keeps its vanilla draw count. */
    private static Int2ObjectMap<TradeSet> vanillaSets = new Int2ObjectOpenHashMap<>();

    private static final Map<Integer, List<ItemListing>> accumulator = new HashMap<>();
    private static boolean replaceVanilla = false;

    /** Merged pools by level. Null until the first reload has run. */
    private static volatile Int2ObjectMap<ItemListing[]> mergedPools = null;

    private WanderingTraderTradeLoader() {}

    private static void rebuildBaseline() {
        Int2ObjectMap<ItemListing[]> baseline = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<TradeSet> sets = new Int2ObjectOpenHashMap<>();
        DatapackRegistries.access().ifPresent(registries -> {
            for (int index = 0; index < VanillaTrades.WANDERING_TRADER_SETS.size(); index++) {
                TradeSet set = VanillaTrades.tradeSet(registries, VanillaTrades.WANDERING_TRADER_SETS.get(index));
                if (set != null) {
                    baseline.put(index + 1, VanillaTrades.listings(set));
                    sets.put(index + 1, set);
                }
            }
        });
        vanillaBaseline = baseline;
        vanillaSets = sets;
    }

    /** Resets accumulated state at the start of a reload. Call before deserializing files. */
    public static void begin() {
        rebuildBaseline();
        accumulator.clear();
        replaceVanilla = false;
    }

    public static void setReplace(boolean replace) {
        if (replace) {
            replaceVanilla = true;
        }
    }

    public static void add(int level, ItemListing factory) {
        accumulator.computeIfAbsent(level, k -> new ArrayList<>()).add(factory);
    }

    /** Rebuilds the wandering-trader pool from the vanilla snapshot plus accumulated mod trades. */
    public static void apply() {

        Set<Integer> levels = new HashSet<>(accumulator.keySet());
        vanillaBaseline.keySet().forEach(level -> levels.add((int) level));

        Int2ObjectMap<ItemListing[]> rebuilt = new Int2ObjectOpenHashMap<>();
        for (int level : levels) {
            ItemListing[] base = replaceVanilla
                    ? new ItemListing[0]
                    : vanillaBaseline.getOrDefault(level, new ItemListing[0]);

            List<ItemListing> extra = accumulator.getOrDefault(level, List.of());
            ItemListing[] merged = ArrayUtils.addAll(base, extra.toArray(new ItemListing[0]));

            // Never leave a tier empty: vanilla's rare pick does random.nextInt(pool.length).
            if (merged.length == 0) {
                merged = vanillaBaseline.getOrDefault(level, new ItemListing[0]);
            }

            rebuilt.put(level, merged);
        }

        mergedPools = rebuilt;
    }

    /** The pool for a tier (1 = common, 2 = rare), or null if that tier does not exist. */
    public static ItemListing[] poolForLevel(int level) {
        Int2ObjectMap<ItemListing[]> pools = mergedPools;
        if (pools != null) {
            return pools.get(level);
        }
        return vanillaBaseline.get(level);
    }

    /** How many tiers exist. Vanilla has two. */
    public static int levelCount() {
        Int2ObjectMap<ItemListing[]> pools = mergedPools;
        if (pools != null) {
            return pools.size();
        }
        return vanillaBaseline.size();
    }

    /**
     * The pools the trader draws from, in tier order, with the number of offers to draw from each.
     * Tier 1 takes its count from the config; every other tier uses its vanilla trade set amount.
     */
    public static List<Pair<ItemListing[], Integer>> pools(Entity trader) {
        Int2ObjectMap<ItemListing[]> pools = mergedPools != null ? mergedPools : vanillaBaseline;

        List<Pair<ItemListing[], Integer>> out = new ArrayList<>(pools.size());
        for (int level : new TreeSet<>(pools.keySet())) {
            int count = level == 1
                    ? VillagerTradingPlus.CONFIG.trade_offers_wandering_trader
                    : vanillaCount(level, trader);
            out.add(Pair.of(pools.get(level), count));
        }
        return out;
    }

    private static int vanillaCount(int level, Entity trader) {
        TradeSet set = vanillaSets.get(level);
        if (set == null) {
            return 1;
        }
        LootContext context = VanillaTrades.lootContext(trader, trader.getRandom(), set.randomSequence());
        return context == null ? 1 : set.calculateNumberOfTrades(context);
    }
}
