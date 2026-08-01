package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

// See VillagerEntityMixin: fillRecipesFromPool/getOffers are declared on MerchantEntity, not on the
// target itself, so the mixin must extend it rather than @Shadow them.
@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderMixin extends MerchantEntity implements TradeControl {

    public WanderingTraderMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    /**
     * Replaces the whole vanilla pool rather than patching a constant. Up to 1.21.5 the draw count
     * was a literal 5 in {@code fillRecipes} and {@code WANDERING_TRADER_TRADES} was a mutable map,
     * so a {@code @ModifyConstant} plus writing into that map was enough. Since 1.21.6 the list is
     * immutable, each entry carries its own draw count, and the constant is gone entirely — a
     * {@code @ModifyConstant} on it would now fail the injection outright at startup.
     */
    @Redirect(
            method = "fillRecipes",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/village/TradeOffers;WANDERING_TRADER_TRADES:Ljava/util/List;",
                    opcode = org.objectweb.asm.Opcodes.GETSTATIC))
    private List<Pair<TradeOffers.Factory[], Integer>> villagertradingplus$mergedPools() {
        return WanderingTraderTradeLoader.pools();
    }

    @Override
    public void villagertradingplus$rerollAll() {
        TradeOfferList offers = getOffers();
        offers.clear();

        TradeOffers.Factory[] common = WanderingTraderTradeLoader.poolForLevel(1);
        TradeOffers.Factory[] rare = WanderingTraderTradeLoader.poolForLevel(2);

        if (common != null) {
            fillRecipesFromPool(offers, common, VillagerTradingPlus.CONFIG.trade_offers_wandering_trader);
        }
        if (rare != null) {
            fillRecipesFromPool(offers, rare, 1);
        }
    }
}
