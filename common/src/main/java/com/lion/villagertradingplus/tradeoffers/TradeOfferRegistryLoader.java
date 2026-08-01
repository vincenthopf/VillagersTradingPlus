package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.village.TradeOffers;
import net.minecraft.registry.RegistryKey;
import net.minecraft.village.VillagerProfession;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

public class TradeOfferRegistryLoader {
    private static final HashMap<RegistryKey<VillagerProfession>, Int2ObjectOpenHashMap<List<TradeOffers.Factory>>> TRADES_REGISTRY = new HashMap<>();

    /**
     * Drops everything accumulated by a previous load. Call at the start of a reload, before any
     * trade file is deserialized.
     *
     * <p>Without this every {@code /reload} appended a fresh copy of each trade to the same lists,
     * so pools grew on each reload within a session and the same trade could be offered — and
     * catalogued — several times over. {@code WanderingTraderTradeLoader.begin()} has always done
     * this for the trader; the villager path had no equivalent.
     */
    public static void begin() {
        TRADES_REGISTRY.clear();
    }

    public static HashMap<RegistryKey<VillagerProfession>, Int2ObjectOpenHashMap<TradeOffers.Factory[]>> getRegistryForLoading() {
        HashMap<RegistryKey<VillagerProfession>, Int2ObjectOpenHashMap<TradeOffers.Factory[]>> villagerTrades = new HashMap<>();

        TRADES_REGISTRY.forEach(((villagerProfession, listInt2ObjectOpenHashMap) -> {
            Int2ObjectOpenHashMap<TradeOffers.Factory[]> factories = villagerTrades.getOrDefault(villagerProfession, new Int2ObjectOpenHashMap<>());

            listInt2ObjectOpenHashMap.forEach((level, factoryList) -> {
                final TradeOffers.Factory[] oldFactories = factories.getOrDefault(level.intValue(), new TradeOffers.Factory[0]);
                factories.put(level.intValue(), ArrayUtils.addAll(oldFactories, factoryList.toArray(new TradeOffers.Factory[0])));
            });

            villagerTrades.put(villagerProfession, factories);
        }));


        return villagerTrades;
    }

    public static void registerVillagerTrade(RegistryKey<VillagerProfession> profession, int level, TradeOffers.Factory trade) {
        getVillagerTradeList(profession, level).add(trade);
    }

    private static List<TradeOffers.Factory> getVillagerTradeList(RegistryKey<VillagerProfession> profession, int level) {
        Int2ObjectOpenHashMap<List<TradeOffers.Factory>> villagerMap = getOrDefaultAndAdd(TRADES_REGISTRY, profession, new Int2ObjectOpenHashMap<>());
        return getOrDefaultAndAdd(villagerMap, level, new ArrayList<>());
    }

    public static <K, V> V getOrDefaultAndAdd(Map<K, V> map, K key, V defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key);
        }

        map.put(key, defaultValue);
        return defaultValue;
    }
}
