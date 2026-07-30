package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// See VillagerEntityMixin: fillRecipesFromPool/getOffers are declared on MerchantEntity, not on the
// target itself, so the mixin must extend it rather than @Shadow them.
@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderMixin extends MerchantEntity implements TradeControl {

    public WanderingTraderMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @ModifyConstant(method = "fillRecipes", constant = @Constant(intValue = 5))
    private int changeTradeOfferCount(int value) {
        return VillagerTradingPlus.CONFIG.trade_offers_wandering_trader;
    }

    @Override
    public void villagertradingplus$rerollAll() {
        TradeOfferList offers = getOffers();
        offers.clear();

        TradeOffers.Factory[] common = TradeOffers.WANDERING_TRADER_TRADES.get(1);
        TradeOffers.Factory[] rare = TradeOffers.WANDERING_TRADER_TRADES.get(2);

        if (common != null) {
            fillRecipesFromPool(offers, common, VillagerTradingPlus.CONFIG.trade_offers_wandering_trader);
        }
        if (rare != null) {
            fillRecipesFromPool(offers, rare, 1);
        }
    }
}
