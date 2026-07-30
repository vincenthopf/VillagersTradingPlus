package com.lion.villagertradingplus.fabric;

import com.lion.villagertradingplus.VillagerTradingPlusClient;
import net.fabricmc.api.ClientModInitializer;

public class VillagerTradingPlusClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		VillagerTradingPlusClient.init();
	}
}
