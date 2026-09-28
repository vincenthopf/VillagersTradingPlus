package com.lion.villagertradingplus.tradeoffers;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

public final class PendingTradeFiles {

    private static Map<Identifier, JsonElement> defaults = Map.of();
    private static Map<Identifier, JsonElement> additions = Map.of();
    private static Map<Identifier, JsonElement> wanderingTrader = Map.of();
    private static boolean pending;

    private PendingTradeFiles() {
    }

    public static synchronized void setDefaults(Map<Identifier, JsonElement> files) {
        defaults = new TreeMap<>(files);
        pending = true;
    }

    public static synchronized void setAdditions(Map<Identifier, JsonElement> files) {
        additions = new TreeMap<>(files);
        pending = true;
    }

    public static synchronized void setWanderingTrader(Map<Identifier, JsonElement> files) {
        wanderingTrader = new TreeMap<>(files);
        pending = true;
    }

    public static synchronized void apply() {
        if (!pending) {
            return;
        }
        pending = false;

        TradeOfferRegistryLoader.begin();
        defaults.forEach((identifier, json) -> {
            if (json.isJsonObject()) {
                VillagerTradingPlus.LOGGER.info("Deserializing default trades from: {}", identifier);
                TradeOfferManager.deserializeJson(json.getAsJsonObject());
            }
        });
        TradeMerger.installDefaults(TradeOfferRegistryLoader.getRegistryForLoading());

        additions.forEach((identifier, json) -> {
            if (json.isJsonObject()) {
                VillagerTradingPlus.LOGGER.info("Deserializing added trades from: {}", identifier);
                TradeOfferManager.deserializeJson(json.getAsJsonObject());
            }
        });
        TradeMerger.mergeIntoVanilla();

        WanderingTraderTradeLoader.begin();
        wanderingTrader.forEach((identifier, json) -> {
            if (json.isJsonObject()) {
                VillagerTradingPlus.LOGGER.info("Deserializing wandering trader trades from: {}", identifier);
                TradeOfferManager.deserializeWanderingTraderJson(json.getAsJsonObject());
            }
        });
        WanderingTraderTradeLoader.apply();
    }
}
