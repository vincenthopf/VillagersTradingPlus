package com.lion.villagertradingplus.platform.fabric;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeMerger;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.Map;

public class DefaultTradeOfferResourceListener extends SimpleJsonResourceReloadListener<JsonElement> implements IdentifiableResourceReloadListener {

    public DefaultTradeOfferResourceListener() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("default_villager_trades"));
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"default_villager_data_loader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, ProfilerFiller profiler) {
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