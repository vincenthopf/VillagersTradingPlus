package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/// Folds everything [TradeOfferRegistryLoader] collected during a reload into vanilla's
/// `PROFESSION_TO_LEVELED_TRADE`. Shared by both loaders, which differ only in how they hook into
/// the resource reload.
public final class TradeMerger {

    /// Vanilla's table as it looked before this mod first wrote to it.
    ///
    /// The table is global and outlives every reload, while re-parsing produces fresh `Factory`
    /// instances. Identity is the only thing that can tell "already added in this pass" apart from
    /// "added one reload ago", and it holds only within a single pass, so a reload has to start
    /// from vanilla instead of from whatever the previous one left behind.
    private static Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> vanilla;

    /// Professions this mod has written to, so resetting touches nothing another mod owns.
    private static final Set<VillagerProfession> TOUCHED = new HashSet<>();

    private TradeMerger() {
    }

    /// Puts the table back the way vanilla had it. Call once at the start of a reload, before any
    /// trade file is deserialized. The first call only takes the snapshot; nothing has been
    /// written yet at that point, which is exactly why it is the one moment vanilla is observable.
    public static void resetToVanilla() {
        if (vanilla == null) {
            vanilla = new HashMap<>();
            TradeOffers.PROFESSION_TO_LEVELED_TRADE.forEach((profession, levels) -> vanilla.put(profession, copy(levels)));
            return;
        }

        for (VillagerProfession profession : TOUCHED) {
            Int2ObjectMap<TradeOffers.Factory[]> original = vanilla.get(profession);
            if (original == null) {
                // The profession did not exist in vanilla at all, so restoring it means dropping the
                // entry rather than writing an empty one.
                TradeOffers.PROFESSION_TO_LEVELED_TRADE.remove(profession);
            } else {
                TradeOffers.PROFESSION_TO_LEVELED_TRADE.put(profession, copy(original));
            }
        }
        TOUCHED.clear();
    }

    /// Installs the parsed default trades, replacing whatever a profession had before.
    /// `default_villager_trades` states a profession's base set; `villager_trades` adds to it
    /// afterwards through [#mergeIntoVanilla()].
    public static void installDefaults(Map<VillagerProfession, ? extends Int2ObjectMap<TradeOffers.Factory[]>> defaults) {
        defaults.forEach((profession, levels) -> {
            TOUCHED.add(profession);
            TradeOffers.PROFESSION_TO_LEVELED_TRADE.put(profession, levels);
        });
    }

    public static void mergeIntoVanilla() {
        TradeOfferRegistryLoader.getRegistryForLoading().forEach((profession, loadedTrades) -> {
            // Professions with no vanilla trades at all have no entry yet. Walking the loaded trades
            // rather than the vanilla map is what lets a mod ship a profession of its own and fill it
            // purely from JSON - iterating the vanilla side would drop those on the floor.
            TOUCHED.add(profession);
            Int2ObjectMap<TradeOffers.Factory[]> target =
                    TradeOffers.PROFESSION_TO_LEVELED_TRADE.computeIfAbsent(profession, ignored -> new Int2ObjectOpenHashMap<>());

            loadedTrades.forEach((level, loadedLevelTrades) -> {
                TradeOffers.Factory[] existing = target.get(level.intValue());

                // distinct() works on identity here, which is the point: installDefaults already put
                // these very Factory instances into the table, so re-merging them inside the same
                // reload is a no-op instead of offering every default trade twice.
                target.put(level.intValue(), Stream.concat(
                                existing != null ? Arrays.stream(existing) : Stream.empty(),
                                Arrays.stream(loadedLevelTrades))
                        .distinct()
                        .toArray(TradeOffers.Factory[]::new));
            });
        });
    }

    private static Int2ObjectMap<TradeOffers.Factory[]> copy(Int2ObjectMap<TradeOffers.Factory[]> levels) {
        Int2ObjectMap<TradeOffers.Factory[]> copy = new Int2ObjectOpenHashMap<>();
        levels.forEach((level, factories) -> copy.put(level.intValue(), factories.clone()));
        return copy;
    }
}
