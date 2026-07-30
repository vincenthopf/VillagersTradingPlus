package com.lion.villagertradingplus.platform.forge;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.Map;

public class WanderingTraderTradeOfferResourceListener extends JsonDataLoader implements ResourceReloader {

    public WanderingTraderTradeOfferResourceListener() {
        super(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().setLenient().create(), "wandering_trader_trades");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, Profiler profiler) {
        WanderingTraderTradeLoader.begin();

        loader.forEach((identifier, jsonElement) -> {
            if (!jsonElement.isJsonObject()) {
                return;
            }
            VillagerTradingPlus.LOGGER.info("Deserializing wandering trader trades from: " + identifier);
            TradeOfferManager.deserializeWanderingTraderJson(jsonElement.getAsJsonObject());
        });

        WanderingTraderTradeLoader.apply();
    }
}
