package com.lion.villagertradingplus.platform.neoforge;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeMerger;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.profiler.Profiler;

import java.util.Map;

public class DefaultTradeOfferResourceListener extends JsonDataLoader<JsonElement> implements ResourceReloader {

    public DefaultTradeOfferResourceListener() {
        super(Codecs.JSON_ELEMENT, ResourceFinder.json("default_villager_trades"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, Profiler profiler) {
        // First listener of the trade chain, so this is where a reload starts from a clean slate.
        TradeOfferRegistryLoader.begin();

        loader.forEach((identifier, jsonElement) -> {
            if (!jsonElement.isJsonObject()) {
                return;
            }

            // The file name, not a field out of the file. Reading the JSON here would put an
            // unguarded access in front of the guard inside deserializeJson - which is exactly how
            // a file without a "profession" key used to abort the whole resource reload.
            VillagerTradingPlus.LOGGER.info("Deserializing default trades from: {}", identifier);

            TradeOfferManager.deserializeJson(jsonElement.getAsJsonObject());
        });

        TradeMerger.installDefaults(TradeOfferRegistryLoader.getRegistryForLoading());
    }
}