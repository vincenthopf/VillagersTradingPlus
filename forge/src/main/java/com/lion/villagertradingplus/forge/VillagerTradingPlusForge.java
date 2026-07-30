package com.lion.villagertradingplus.forge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.VillagerTradingPlusClient;
import com.lion.villagertradingplus.platform.forge.DefaultTradeOfferResourceListener;
import com.lion.villagertradingplus.platform.forge.TradeOfferResourceListener;
import com.lion.villagertradingplus.platform.forge.WanderingTraderTradeOfferResourceListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(VillagerTradingPlus.MOD_ID)
public class VillagerTradingPlusForge {

	public VillagerTradingPlusForge() {
		VillagerTradingPlus.init();

		if (FMLEnvironment.dist == Dist.CLIENT) {
			VillagerTradingPlusClient.init();
		}

		MinecraftForge.EVENT_BUS.addListener(VillagerTradingPlusForge::registerResourceReloader);
	}

	private static void registerResourceReloader(AddReloadListenerEvent event) {
		event.addListener(new DefaultTradeOfferResourceListener());
		event.addListener(new TradeOfferResourceListener());
		event.addListener(new WanderingTraderTradeOfferResourceListener());
	}
}
