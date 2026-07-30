package com.lion.villagertradingplus;

import com.lion.villagertradingplus.config.OmegaConfig;
import com.lion.villagertradingplus.config.VTPConfig;
import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the library.
 * <p>
 * A consumer mod does not call anything here. It ships JSON under its own namespace and the resource
 * listeners pick it up on every reload - see the files under {@code data/villagertradingplus/available_trade_examples}
 * for the format.
 */
public class VillagerTradingPlus {
	public static final String MOD_ID = "villagertradingplus";
	public static final VTPConfig CONFIG = OmegaConfig.register(VTPConfig.class);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static VTPConfig getConfig() {
		return CONFIG;
	}

	public static void init() {
		NetworkHelper.init();
		TradeOfferManager.registerTradeOffers();
	}

	public static String createStringID(String name) {
		return MOD_ID + ":" + name;
	}
}
