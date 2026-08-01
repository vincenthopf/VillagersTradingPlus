package com.lion.villagertradingplus.platform.fabric;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeMerger;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.profiler.Profiler;

import java.util.Map;

/**
 * Loads {@code data/<any namespace>/villager_trades/<name>.json}.
 * <p>
 * This used to walk the resource packs by hand and open one fixed path per profession under the
 * mod's own namespace, which meant only files shipped by this mod were ever seen. As a library that
 * is the wrong way round, so it now takes what {@link JsonDataLoader} already collected across every
 * namespace. The file name is only a name - which profession a file belongs to is read from its
 * {@code "profession"} field, exactly as the default loader does it.
 */
public class TradeOfferResourceListener extends JsonDataLoader<JsonElement> implements IdentifiableResourceReloadListener {

    public TradeOfferResourceListener() {
        super(Codecs.JSON_ELEMENT, ResourceFinder.json("villager_trades"));
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.of(VillagerTradingPlus.MOD_ID, "villager_data_loader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, Profiler profiler) {
        loader.forEach((identifier, jsonElement) -> {
            if (!jsonElement.isJsonObject()) {
                return;
            }

            VillagerTradingPlus.LOGGER.info("Deserializing added trades from: " + identifier);

            TradeOfferManager.deserializeJson(jsonElement.getAsJsonObject());
        });

        TradeMerger.mergeIntoVanilla();
    }
}
