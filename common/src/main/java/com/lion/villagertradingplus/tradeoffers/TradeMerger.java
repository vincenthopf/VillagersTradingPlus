package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Folds everything {@link TradeOfferRegistryLoader} collected during a reload into vanilla's
 * {@code PROFESSION_TO_LEVELED_TRADE}. Shared by both loaders, which differ only in how they hook
 * into the resource reload.
 */
public final class TradeMerger {

    /**
     * Vanilla's arrays as they were before this mod first touched them, per profession and level.
     *
     * <p>{@link TradeOfferRegistryLoader#begin()} clears what <em>we</em> collected, but the vanilla
     * map is global and survives every reload. Re-parsing produces fresh {@code Factory} instances,
     * so the identity-based {@code distinct()} below cannot recognise them as the trades already
     * merged one reload ago - and the pool grew on every {@code /reload} and on every re-entry into a
     * world within the same session. Restoring this baseline first makes merging idempotent.
     */
    private static final Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> VANILLA_BASELINE = new HashMap<>();

    private TradeMerger() {
    }

    public static void mergeIntoVanilla() {
        restoreBaseline();

        TradeOfferRegistryLoader.getRegistryForLoading().forEach((profession, loadedTrades) -> {
            // Professions with no vanilla trades at all have no entry yet. Walking the loaded trades
            // rather than the vanilla map is what lets a mod ship a profession of its own and fill it
            // purely from JSON - iterating the vanilla side would drop those on the floor.
            Int2ObjectMap<TradeOffers.Factory[]> target =
                    TradeOffers.PROFESSION_TO_LEVELED_TRADE.computeIfAbsent(profession, ignored -> new Int2ObjectOpenHashMap<>());

            loadedTrades.forEach((level, loadedLevelTrades) -> {
                TradeOffers.Factory[] existing = target.get(level.intValue());
                rememberBaseline(profession, level.intValue(), existing);

                // distinct() works on identity here, which is the point: the default listener already
                // put these very Factory instances into the vanilla map, so re-merging them within one
                // reload is a no-op instead of offering every default trade twice.
                target.put(level.intValue(), Stream.concat(
                                existing != null ? Arrays.stream(existing) : Stream.empty(),
                                Arrays.stream(loadedLevelTrades))
                        .distinct()
                        .toArray(TradeOffers.Factory[]::new));
            });
        });
    }

    /** Records what was in the vanilla map before the first append, and only then. */
    private static void rememberBaseline(VillagerProfession profession, int level, TradeOffers.Factory[] existing) {
        Int2ObjectMap<TradeOffers.Factory[]> levels =
                VANILLA_BASELINE.computeIfAbsent(profession, ignored -> new Int2ObjectOpenHashMap<>());
        if (!levels.containsKey(level)) {
            // null is meaningful: that level did not exist in vanilla at all, so restoring it means
            // removing the entry again rather than writing an empty array.
            levels.put(level, existing == null ? null : existing.clone());
        }
    }

    private static void restoreBaseline() {
        VANILLA_BASELINE.forEach((profession, levels) -> {
            Int2ObjectMap<TradeOffers.Factory[]> target = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(profession);
            if (target == null) {
                return;
            }
            levels.forEach((level, original) -> {
                if (original == null) {
                    target.remove(level.intValue());
                } else {
                    target.put(level.intValue(), original.clone());
                }
            });
        });
    }
}
