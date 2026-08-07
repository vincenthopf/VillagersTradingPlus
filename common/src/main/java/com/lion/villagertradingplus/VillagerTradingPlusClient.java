package com.lion.villagertradingplus;

import com.lion.villagertradingplus.client.TradeCatalogClientState;

/**
 * Client-side counterpart of {@link VillagerTradingPlus}: hooks up the packet receiver that feeds the
 * trade catalog panel.
 */
public class VillagerTradingPlusClient {

	public static void init() {
		TradeCatalogClientState.register();
	}
}
