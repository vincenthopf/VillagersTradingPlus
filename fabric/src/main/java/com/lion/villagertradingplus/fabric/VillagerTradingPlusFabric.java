package com.lion.villagertradingplus.fabric;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.platform.fabric.DefaultTradeOfferResourceListener;
import com.lion.villagertradingplus.platform.fabric.TradeOfferResourceListener;
import com.lion.villagertradingplus.platform.fabric.WanderingTraderTradeOfferResourceListener;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;

public class VillagerTradingPlusFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		VillagerTradingPlus.init();

		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new DefaultTradeOfferResourceListener());
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new TradeOfferResourceListener());
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new WanderingTraderTradeOfferResourceListener());
	}
}
