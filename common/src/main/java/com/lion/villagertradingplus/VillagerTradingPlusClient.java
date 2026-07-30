package com.lion.villagertradingplus;

import com.lion.villagertradingplus.client.TradeCatalogClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Client-side counterpart of {@link VillagerTradingPlus}: hooks up the packet receiver that feeds the
 * trade catalog panel.
 */
public class VillagerTradingPlusClient {

	@Environment(EnvType.CLIENT)
	public static void init() {
		TradeCatalogClientState.register();
	}
}
