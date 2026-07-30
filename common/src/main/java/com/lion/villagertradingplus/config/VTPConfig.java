package com.lion.villagertradingplus.config;

import com.lion.villagertradingplus.config.annotations.Description;

/**
 * Settings that apply to every trade the library builds, no matter which mod or datapack defined it.
 * These moved out of VillagersPlus when the trade system became a library - a consumer that wants
 * different numbers changes them here, not in its own config.
 */
public class VTPConfig implements Config {

    @Override
    public String getName() {
        return "villagertradingplus-1.0-config";
    }

    @Override
    public String getExtension() {
        return "json5";
    }

    @Override
    public String getDirectory() {
        return "villagertradingplus";
    }

    @Description("Amount of new trades per level a villager can have at max. (Default: 2)")
    public int trade_offers_per_level = 2;

    @Description("Amount of trades the wandering trader can have at max. (Default: 5)")
    public int trade_offers_wandering_trader = 5;

    @Description("Controls how strongly trade prices change over time. When a villager is used a lot its prices rise (demand), and trading builds reputation which lowers prices; this value scales how big both of those swings are for ALL trades at once. 1.0 = normal. 2.0 = prices rise and fall twice as fast. 0.0 = prices never move from demand or reputation. It does NOT change the starting price of a trade. (Default: 1.0)")
    public float trade_price_multiplier_scale = 1.0F;

    @Description("Multiplies the base cost of every trade, i.e. the amount in the first input slot. 1.0 = unchanged. 2.0 = everything is twice as expensive (a 5 emerald trade becomes 10). 0.5 = half price. The result is rounded and never goes below 1 or above a full stack. Only the first input slot is affected. (Default: 1.0)")
    public float trade_cost_scale = 1.0F;

    @Description("Some trades in the data files are set to only appear under certain conditions (for example a specific biome, dimension, or time of day). true = these conditions are checked, so a gated trade can be hidden when its condition is not met. false = conditions are ignored and every trade always shows up. (Default: true)")
    public boolean enable_conditional_trades = true;

    @Description("When true, a trade's cost is randomly raised or lowered a little based on the in-game time when that trade is first unlocked. Important: a price is locked in the moment a villager unlocks the trade (on level up) and stays fixed after that, so it does not keep changing through the day. The size of the change is set by time_of_day_price_variance below. (Default: false)")
    public boolean enable_time_of_day_pricing = false;

    @Description("Only used when enable_time_of_day_pricing is true. Sets how far the time-based price change can go, as a fraction of the cost. 0.15 = up to 15% cheaper or more expensive. 0.0 = no change. (Default: 0.15)")
    public float time_of_day_price_variance = 0.15F;

    @Description("Adds buttons to the trading screen to reset and re-roll a villager's or wandering trader's trades (all of them, or just one level). When true, ANY player can use it. (Default: false)")
    public boolean allow_trade_reroll = false;

    @Description("Adds a control to the trading screen to set a villager's level (1-5). Regenerates the villager's trades for the new level. Wandering traders have no levels and ignore it. When true, ANY player can use it. (Default: false)")
    public boolean allow_set_villager_level = false;

    @Description("Adds a button to the trading screen that opens a read-only catalog of all possible trades for a chosen level, so you can see what a villager could offer. When true, ANY player can use it. (Default: false)")
    public boolean allow_view_all_trades = false;
}
