package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.ItemListing;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.lion.villagertradingplus.tradeoffers.TradePools;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

// See VillagerEntityMixin: fillRecipesFromPool/getOffers are declared on MerchantEntity, not on the
// target itself, so the mixin must extend it rather than @Shadow them.
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixin extends AbstractVillager implements TradeControl {

    public WanderingTraderMixin(EntityType<? extends AbstractVillager> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$mergedPools(ServerLevel level, CallbackInfo ci) {
        List<Pair<ItemListing[], Integer>> pools = WanderingTraderTradeLoader.pools(this);
        if (pools.isEmpty()) {
            return;
        }
        MerchantOffers offers = getOffers();
        for (Pair<ItemListing[], Integer> pool : pools) {
            TradePools.addOffers(this, offers, pool.getLeft(), pool.getRight());
        }
        ci.cancel();
    }

    @Override
    public void villagertradingplus$rerollAll() {
        MerchantOffers offers = getOffers();
        offers.clear();

        ItemListing[] common = WanderingTraderTradeLoader.poolForLevel(1);
        ItemListing[] rare = WanderingTraderTradeLoader.poolForLevel(2);

        if (common != null) {
            TradePools.addOffers(this, offers, common, VillagerTradingPlus.CONFIG.trade_offers_wandering_trader);
        }
        if (rare != null) {
            TradePools.addOffers(this, offers, rare, 1);
        }
    }
}
