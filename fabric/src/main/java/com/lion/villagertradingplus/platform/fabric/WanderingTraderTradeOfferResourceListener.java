package com.lion.villagertradingplus.platform.fabric;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.profiler.Profiler;

import java.util.Map;

public class WanderingTraderTradeOfferResourceListener extends JsonDataLoader<JsonElement> implements IdentifiableResourceReloadListener {

    public WanderingTraderTradeOfferResourceListener() {
        super(Codecs.JSON_ELEMENT, ResourceFinder.json("wandering_trader_trades"));
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.of(VillagerTradingPlus.MOD_ID, "wandering_trader_data_loader");
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
