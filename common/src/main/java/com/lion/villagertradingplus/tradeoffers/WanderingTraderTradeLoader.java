package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.village.TradeOffers;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges mod-defined JSON trades into the vanilla wandering-trader pool
 * ({@link TradeOffers#WANDERING_TRADER_TRADES}). That pool has two tiers: level 1 ("common",
 * several are picked) and level 2 ("rare", one is picked).
 *
 * <p>Kept idempotent across resource reloads by snapshotting the vanilla pool once and rebuilding
 * from that snapshot every reload, so trades are never duplicated. A file may set {@code replace}
 * to swap out the vanilla trades for a tier instead of adding to them; a tier is never left empty
 * (that would crash vanilla's rare-pick), so replacing a tier you defined no trades for keeps
 * vanilla.
 */
public final class WanderingTraderTradeLoader {

    /** JSON tier names mapped to the vanilla wandering-trader pool levels. */
    public static final Map<String, Integer> LEVEL_MAPPING = new HashMap<>();

    static {
        LEVEL_MAPPING.put("common", 1);
        LEVEL_MAPPING.put("rare", 2);
    }

    private static Int2ObjectMap<TradeOffers.Factory[]> vanillaBaseline = null;
    private static final Map<Integer, List<TradeOffers.Factory>> accumulator = new HashMap<>();
    private static boolean replaceVanilla = false;

    private WanderingTraderTradeLoader() {}

    private static void ensureBaseline() {
        if (vanillaBaseline == null) {
            vanillaBaseline = new Int2ObjectOpenHashMap<>(TradeOffers.WANDERING_TRADER_TRADES);
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

    public static void add(int level, TradeOffers.Factory factory) {
        accumulator.computeIfAbsent(level, k -> new ArrayList<>()).add(factory);
    }

    /** Rebuilds the wandering-trader pool from the vanilla snapshot plus accumulated mod trades. */
    public static void apply() {
        ensureBaseline();

        Set<Integer> levels = new HashSet<>(accumulator.keySet());
        vanillaBaseline.keySet().forEach(level -> levels.add((int) level));

        for (int level : levels) {
            TradeOffers.Factory[] base = replaceVanilla
                    ? new TradeOffers.Factory[0]
                    : vanillaBaseline.getOrDefault(level, new TradeOffers.Factory[0]);

            List<TradeOffers.Factory> extra = accumulator.getOrDefault(level, List.of());
            TradeOffers.Factory[] merged = ArrayUtils.addAll(base, extra.toArray(new TradeOffers.Factory[0]));

            // Never leave a tier empty: vanilla's rare pick does random.nextInt(pool.length).
            if (merged.length == 0) {
                merged = vanillaBaseline.getOrDefault(level, new TradeOffers.Factory[0]);
            }

            TradeOffers.WANDERING_TRADER_TRADES.put(level, merged);
        }
    }
}
