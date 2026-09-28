package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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
import net.minecraft.world.entity.npc.VillagerTrades;

/**
 * Merges mod-defined JSON trades into the vanilla wandering-trader pool
 * ({@link VillagerTrades#WANDERING_TRADER_TRADES}). That pool has two tiers: level 1 ("common",
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

    /** Vanilla pools by level (1-based), snapshotted once. */
    private static Int2ObjectMap<VillagerTrades.ItemListing[]> vanillaBaseline = null;
    /** Vanilla's own draw count per level, so a tier we never touch keeps its vanilla behaviour. */
    private static Int2IntMap vanillaCounts = null;

    private static final Map<Integer, List<VillagerTrades.ItemListing>> accumulator = new HashMap<>();
    private static boolean replaceVanilla = false;

    /** Merged pools by level. Null until the first reload has run. */
    private static volatile Int2ObjectMap<VillagerTrades.ItemListing[]> mergedPools = null;

    private WanderingTraderTradeLoader() {}

    private static void ensureBaseline() {
        if (vanillaBaseline != null) {
            return;
        }
        vanillaBaseline = new Int2ObjectOpenHashMap<>();
        vanillaCounts = new Int2IntOpenHashMap();

        List<Pair<VillagerTrades.ItemListing[], Integer>> vanilla = VillagerTrades.WANDERING_TRADER_TRADES;
        for (int index = 0; index < vanilla.size(); index++) {
            Pair<VillagerTrades.ItemListing[], Integer> pair = vanilla.get(index);
            int level = index + 1;
            vanillaBaseline.put(level, pair.getLeft());
            vanillaCounts.put(level, pair.getRight().intValue());
        }
    }

    /** Resets accumulated state at the start of a reload. Call before deserializing files. */
    public static void begin() {
        ensureBaseline();
        accumulator.clear();
        replaceVanilla = false;
    }

    public static void setReplace(boolean replace) {
        if (replace) {
            replaceVanilla = true;
        }
    }

    public static void add(int level, VillagerTrades.ItemListing factory) {
        accumulator.computeIfAbsent(level, k -> new ArrayList<>()).add(factory);
    }

    /** Rebuilds the wandering-trader pool from the vanilla snapshot plus accumulated mod trades. */
    public static void apply() {
        ensureBaseline();

        Set<Integer> levels = new HashSet<>(accumulator.keySet());
        vanillaBaseline.keySet().forEach(level -> levels.add((int) level));

        Int2ObjectMap<VillagerTrades.ItemListing[]> rebuilt = new Int2ObjectOpenHashMap<>();
        for (int level : levels) {
            VillagerTrades.ItemListing[] base = replaceVanilla
                    ? new VillagerTrades.ItemListing[0]
                    : vanillaBaseline.getOrDefault(level, new VillagerTrades.ItemListing[0]);

            List<VillagerTrades.ItemListing> extra = accumulator.getOrDefault(level, List.of());
            VillagerTrades.ItemListing[] merged = ArrayUtils.addAll(base, extra.toArray(new VillagerTrades.ItemListing[0]));

            // Never leave a tier empty: vanilla's rare pick does random.nextInt(pool.length).
            if (merged.length == 0) {
                merged = vanillaBaseline.getOrDefault(level, new VillagerTrades.ItemListing[0]);
            }

            rebuilt.put(level, merged);
        }

        mergedPools = rebuilt;
    }

    /** The pool for a tier (1 = common, 2 = rare), or null if that tier does not exist. */
    public static VillagerTrades.ItemListing[] poolForLevel(int level) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> pools = mergedPools;
        if (pools != null) {
            return pools.get(level);
        }
        ensureBaseline();
        return vanillaBaseline.get(level);
    }

    /** How many tiers exist. Vanilla has two. */
    public static int levelCount() {
        Int2ObjectMap<VillagerTrades.ItemListing[]> pools = mergedPools;
        if (pools != null) {
            return pools.size();
        }
        ensureBaseline();
        return vanillaBaseline.size();
    }

    /**
     * What vanilla's {@code fillRecipes} iterates, in tier order. Vanilla reads the draw count out
     * of each pair, so tier 1 takes its count from the config here rather than from the snapshot.
     */
    public static List<Pair<VillagerTrades.ItemListing[], Integer>> pools() {
        ensureBaseline();
        Int2ObjectMap<VillagerTrades.ItemListing[]> pools = mergedPools != null ? mergedPools : vanillaBaseline;

        List<Pair<VillagerTrades.ItemListing[], Integer>> out = new ArrayList<>(pools.size());
        for (int level : new TreeSet<>(pools.keySet())) {
            int count = level == 1
                    ? VillagerTradingPlus.CONFIG.trade_offers_wandering_trader
                    : vanillaCounts.getOrDefault(level, 1);
            out.add(Pair.of(pools.get(level), count));
        }
        return out;
    }
}
