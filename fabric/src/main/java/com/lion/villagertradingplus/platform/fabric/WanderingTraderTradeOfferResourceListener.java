package com.lion.villagertradingplus.platform.fabric;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.Map;

public class WanderingTraderTradeOfferResourceListener extends JsonDataLoader implements IdentifiableResourceReloadListener {

    public WanderingTraderTradeOfferResourceListener() {
        super(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().setLenient().create(), "wandering_trader_trades");
    }

    @Override
    public Identifier getFabricId() {
        return new Identifier(VillagerTradingPlus.MOD_ID, "wandering_trader_data_loader");
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
