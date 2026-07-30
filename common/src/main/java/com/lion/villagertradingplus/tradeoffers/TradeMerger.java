package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.village.TradeOffers;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Folds everything {@link TradeOfferRegistryLoader} collected during a reload into vanilla's
 * {@code PROFESSION_TO_LEVELED_TRADE}. Shared by both loaders, which differ only in how they hook
 * into the resource reload.
 */
public final class TradeMerger {

    private TradeMerger() {
    }

    public static void mergeIntoVanilla() {
        TradeOfferRegistryLoader.getRegistryForLoading().forEach((profession, loadedTrades) -> {
            // Professions with no vanilla trades at all have no entry yet. Walking the loaded trades
            // rather than the vanilla map is what lets a mod ship a profession of its own and fill it
            // purely from JSON - iterating the vanilla side would drop those on the floor.
            Int2ObjectMap<TradeOffers.Factory[]> target =
                    TradeOffers.PROFESSION_TO_LEVELED_TRADE.computeIfAbsent(profession, ignored -> new Int2ObjectOpenHashMap<>());

            loadedTrades.forEach((level, loadedLevelTrades) -> {
                TradeOffers.Factory[] existing = target.get(level.intValue());

                // distinct() works on identity here, which is the point: the default listener already
                // put these very Factory instances into the vanilla map, so re-merging them is a no-op
                // instead of offering every default trade twice.
                target.put(level.intValue(), Stream.concat(
                                existing != null ? Arrays.stream(existing) : Stream.empty(),
                                Arrays.stream(loadedLevelTrades))
                        .distinct()
                        .toArray(TradeOffers.Factory[]::new));
            });
        });
    }
}
