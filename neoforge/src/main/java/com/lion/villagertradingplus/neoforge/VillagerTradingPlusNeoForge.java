package com.lion.villagertradingplus.neoforge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.VillagerTradingPlusClient;
import com.lion.villagertradingplus.platform.neoforge.DefaultTradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.TradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.WanderingTraderTradeOfferResourceListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(VillagerTradingPlus.MOD_ID)
public class VillagerTradingPlusNeoForge {

	public VillagerTradingPlusNeoForge() {
		VillagerTradingPlus.init();

		if (FMLEnvironment.dist == Dist.CLIENT) {
			VillagerTradingPlusClient.init();
		}

		MinecraftForge.EVENT_BUS.addListener(VillagerTradingPlusNeoForge::registerResourceReloader);
	}

	private static void registerResourceReloader(AddReloadListenerEvent event) {
		event.addListener(new DefaultTradeOfferResourceListener());
		event.addListener(new TradeOfferResourceListener());
		event.addListener(new WanderingTraderTradeOfferResourceListener());
	}
}
