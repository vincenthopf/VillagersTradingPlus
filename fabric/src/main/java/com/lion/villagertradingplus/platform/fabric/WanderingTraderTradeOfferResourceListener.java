package com.lion.villagertradingplus.platform.fabric;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.Map;

public class WanderingTraderTradeOfferResourceListener extends SimpleJsonResourceReloadListener<JsonElement> implements IdentifiableResourceReloadListener {

    public WanderingTraderTradeOfferResourceListener() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("wandering_trader_trades"));
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, "wandering_trader_data_loader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, ProfilerFiller profiler) {
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
