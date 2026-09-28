package com.lion.villagertradingplus.platform.fabric;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.PendingTradeFiles;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.Map;

/**
 * Loads {@code data/<any namespace>/villager_trades/<name>.json}.
 * <p>
 * This used to walk the resource packs by hand and open one fixed path per profession under the
 * mod's own namespace, which meant only files shipped by this mod were ever seen. As a library that
 * is the wrong way round, so it now takes what {@link SimpleJsonResourceReloadListener} already collected across every
 * namespace. The file name is only a name - which profession a file belongs to is read from its
 * {@code "profession"} field, exactly as the default loader does it.
 */
public class TradeOfferResourceListener extends SimpleJsonResourceReloadListener<JsonElement> implements IdentifiableResourceReloadListener {

    public TradeOfferResourceListener() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("villager_trades"));
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, "villager_data_loader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, ProfilerFiller profiler) {
        PendingTradeFiles.setAdditions(loader);
    }
}
