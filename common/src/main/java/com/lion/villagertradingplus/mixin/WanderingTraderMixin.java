package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

// See VillagerEntityMixin: fillRecipesFromPool/getOffers are declared on MerchantEntity, not on the
// target itself, so the mixin must extend it rather than @Shadow them.
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixin extends AbstractVillager implements TradeControl {

    public WanderingTraderMixin(EntityType<? extends AbstractVillager> entityType, Level world) {
        super(entityType, world);
    }

    /**
     * Replaces the whole vanilla pool rather than patching a constant. {@code WANDERING_TRADER_TRADES}
     * is an immutable list whose entries carry their own draw count, and {@code fillRecipes} holds no
     * count constant left to modify: a {@code @ModifyConstant} fails its injection at startup.
     */
    @Redirect(
            method = "updateTrades",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/npc/VillagerTrades;WANDERING_TRADER_TRADES:Ljava/util/List;",
                    opcode = org.objectweb.asm.Opcodes.GETSTATIC))
    private List<Pair<VillagerTrades.ItemListing[], Integer>> villagertradingplus$mergedPools() {
        return WanderingTraderTradeLoader.pools();
    }

    @Override
    public void villagertradingplus$rerollAll() {
        MerchantOffers offers = getOffers();
        offers.clear();

        VillagerTrades.ItemListing[] common = WanderingTraderTradeLoader.poolForLevel(1);
        VillagerTrades.ItemListing[] rare = WanderingTraderTradeLoader.poolForLevel(2);

        if (common != null) {
            addOffersFromItemListings(offers, common, VillagerTradingPlus.CONFIG.trade_offers_wandering_trader);
        }
        if (rare != null) {
            addOffersFromItemListings(offers, rare, 1);
        }
    }
}
