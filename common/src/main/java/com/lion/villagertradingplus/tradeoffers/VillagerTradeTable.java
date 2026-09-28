package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public final class VillagerTradeTable {

    public static final Map<ResourceKey<VillagerProfession>, Int2ObjectMap<ItemListing[]>> TRADES = new ConcurrentHashMap<>();

    private VillagerTradeTable() {
    }
}
