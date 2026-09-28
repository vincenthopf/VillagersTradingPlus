package com.lion.villagertradingplus.tradeoffers.gui;

import com.lion.villagertradingplus.tradeoffers.ItemListing;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.config.VTPConfig;
import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpansion;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;

/**
 * Server-side handling for the trade-screen control buttons. Actions are addressed by the button id
 * received through vanilla {@code ScreenHandler.onButtonClick} (no custom networking on the way in):
 * <ul>
 *   <li>100+level -> set villager level</li>
 *   <li>110+level -> re-roll one tier</li>
 *   <li>120       -> re-roll all tiers</li>
 *   <li>130+level -> send the read-only catalogue for a tier</li>
 * </ul>
 *
 * <p>The catalogue reply does need its own packet: it carries per-trade metadata that no vanilla
 * screen sync can express, but it rides back to a client whose merchant screen is still open, so
 * the request half stays on the vanilla channel.
 */
public final class TradeGuiActions {

    private TradeGuiActions() {
    }

    /** Returns true if the button id was one of ours and was handled. */
    public static boolean handle(ServerPlayer player, AbstractVillager merchant, int id) {
        VTPConfig cfg = VillagerTradingPlus.CONFIG;

        if (id >= 100 && id < 110) {
            if (!cfg.allow_set_villager_level || !(merchant instanceof TradeControl tc) || !tc.villagertradingplus$supportsLevels()) {
                return false;
            }
            tc.villagertradingplus$setLevel(id - 100);
            refresh(player, merchant);
            return true;
        }

        if (id >= 110 && id < 120) {
            if (!cfg.allow_trade_reroll || !(merchant instanceof TradeControl tc)) {
                return false;
            }
            tc.villagertradingplus$rerollLevel(id - 110);
            refresh(player, merchant);
            return true;
        }

        if (id == 120) {
            if (!cfg.allow_trade_reroll || !(merchant instanceof TradeControl tc)) {
                return false;
            }
            tc.villagertradingplus$rerollAll();
            refresh(player, merchant);
            return true;
        }

        if (id >= 130 && id < 140) {
            if (!cfg.allow_view_all_trades) {
                return false;
            }
            sendCatalog(player, merchant, id - 130);
            return true;
        }

        return false;
    }

    /** Re-sends the merchant's offers to the currently open trade screen so the client updates. */
    private static void refresh(ServerPlayer player, AbstractVillager merchant) {
        if (player.containerMenu instanceof MerchantMenu handler) {
            int level = merchant instanceof Villager villager ? villager.getVillagerData().level() : 1;
            player.sendMerchantOffers(handler.containerId, merchant.getOffers(), level, merchant.getVillagerXp(),
                    merchant.showProgressBar(), merchant.canRestock());
        }
    }

    /** Builds the requested tier and ships it to the panel docked beside the player's trade screen. */
    private static void sendCatalog(ServerPlayer player, AbstractVillager merchant, int requestedLevel) {
        int maxLevel = maxLevel(merchant);
        int level = Mth.clamp(requestedLevel, 1, maxLevel);

        CatalogBuilder builder = buildCatalog(merchant, level);
        // One catalogue goes out as several packets; they arrive in order and the client reassembles.
        TradeCatalogPacket.write(player.level().getServer().registryAccess(), level, maxLevel,
                        builder.entries(), builder.skipped())
                .forEach(slice -> NetworkHelper.sendToPlayer(player, slice));
    }

    /** Villagers have five tiers; the wandering trader has two pools (common, rare). */
    public static int maxLevel(AbstractVillager merchant) {
        return merchant instanceof Villager ? 5 : 2;
    }

    /**
     * Enumerates every trade a merchant could roll at a tier.
     *
     * <p>Derived from what the trade JSON <em>defines</em>, never from sampling {@code create}. The
     * old approach rolled each factory eight times and de-duplicated the results, which meant the
     * list changed size between openings: a weighted pool might not surface its rare member, a
     * random price made two identical books look like two different trades, and a conditional trade
     * simply vanished whenever its condition happened to be false. {@link CatalogExpansion} walks
     * the factories instead, so the same tier always produces the same rows.
     */
    public static CatalogBuilder buildCatalog(AbstractVillager merchant, int level) {
        ItemListing[] pool = poolFor(merchant, level);
        int poolSize = pool == null ? 0 : pool.length;

        CatalogBuilder builder = new CatalogBuilder(poolSize, picksPerLevel(merchant, level));
        if (pool == null) {
            return builder;
        }

        for (ItemListing factory : pool) {
            CatalogExpansion.expandTrade(factory, merchant, builder);
        }
        return builder;
    }

    private static int picksPerLevel(AbstractVillager merchant, int level) {
        if (merchant instanceof Villager) {
            return VillagerTradingPlus.CONFIG.trade_offers_per_level;
        }
        // Wandering trader: the common pool contributes several offers, the rare pool exactly one.
        return level == 1 ? VillagerTradingPlus.CONFIG.trade_offers_wandering_trader : 1;
    }

    private static ItemListing[] poolFor(AbstractVillager merchant, int level) {
        if (merchant instanceof Villager villager) {
            Int2ObjectMap<ItemListing[]> map =
                    villager.getVillagerData().profession().unwrapKey()
                            .map(com.lion.villagertradingplus.tradeoffers.VillagerTradeTable.TRADES::get).orElse(null);
            return map == null ? null : map.get(level);
        }
        // Wandering trader: level 1 = common, level 2 = rare.
        return WanderingTraderTradeLoader.poolForLevel(level);
    }
}
