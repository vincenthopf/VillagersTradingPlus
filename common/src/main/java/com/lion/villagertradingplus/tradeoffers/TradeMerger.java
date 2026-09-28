package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/// Folds everything [TradeOfferRegistryLoader] collected during a reload into [VillagerTradeTable].
///
/// Vanilla trades are data-driven trade sets since 26.1, so the vanilla part of the table is rebuilt
/// from the trade set registry of the world being loaded at the start of every reload.
public final class TradeMerger {

    private TradeMerger() {
    }

    public static void resetToVanilla() {
        VillagerTradeTable.TRADES.clear();
        DatapackRegistries.access().ifPresent(registries ->
                VillagerTradeTable.TRADES.putAll(VanillaTrades.villagerTable(registries)));
    }

    public static void installDefaults(Map<ResourceKey<VillagerProfession>, ? extends Int2ObjectMap<ItemListing[]>> defaults) {
        VillagerTradeTable.TRADES.putAll(defaults);
    }

    public static void mergeIntoVanilla() {
        TradeOfferRegistryLoader.getRegistryForLoading().forEach((profession, loadedTrades) -> {
            Int2ObjectMap<ItemListing[]> target =
                    VillagerTradeTable.TRADES.computeIfAbsent(profession, ignored -> new Int2ObjectOpenHashMap<>());

            loadedTrades.forEach((int level, ItemListing[] loadedLevelTrades) -> {
                ItemListing[] existing = target.get(level);
                target.put(level, Stream.concat(
                                existing != null ? Arrays.stream(existing) : Stream.empty(),
                                Arrays.stream(loadedLevelTrades))
                        .distinct()
                        .toArray(ItemListing[]::new));
            });
        });
    }
}
