package com.lion.villagertradingplus.neoforge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.VillagerTradingPlusClient;
import com.lion.villagertradingplus.platform.neoforge.DefaultTradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.NetworkHelperImpl;
import com.lion.villagertradingplus.platform.neoforge.TradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.WanderingTraderTradeOfferResourceListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

@Mod(VillagerTradingPlus.MOD_ID)
public class VillagerTradingPlusNeoForge {

    /**
     * NeoForge hands the mod bus to the constructor. The two buses are not interchangeable: payload
     * registration is a mod-bus event fired once during startup, while the reload listener hook is a
     * game-bus event fired on every datapack load.
     */
    public VillagerTradingPlusNeoForge(IEventBus modEventBus) {
        VillagerTradingPlus.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            VillagerTradingPlusClient.init();
        }

        modEventBus.addListener(NetworkHelperImpl::register);
        NeoForge.EVENT_BUS.addListener(VillagerTradingPlusNeoForge::registerResourceReloader);
    }

    // Renamed from AddReloadListenerEvent, and listeners are now keyed so NeoForge can order them.
    // The ids mirror the getFabricId() values the Fabric side registers under.
    private static void registerResourceReloader(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.of(VillagerTradingPlus.MOD_ID, "default_villager_data_loader"),
                new DefaultTradeOfferResourceListener());
        event.addListener(Identifier.of(VillagerTradingPlus.MOD_ID, "villager_data_loader"),
                new TradeOfferResourceListener());
        event.addListener(Identifier.of(VillagerTradingPlus.MOD_ID, "wandering_trader_data_loader"),
                new WanderingTraderTradeOfferResourceListener());
    }
}
