package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.tradeoffers.ConditionalTradeFactory;
import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import com.lion.villagertradingplus.tradeoffers.RegistryRebindFactory;
import com.lion.villagertradingplus.tradeoffers.conditions.ParsedConditions;
import com.lion.villagertradingplus.tradeoffers.conditions.TradeCondition;
import net.minecraft.entity.Entity;
import net.minecraft.village.TradeOffers;

import java.util.List;

/**
 * Turns a trade factory into catalogue rows.
 *
 * <p>{@code TradeOfferManager.deserializeTrade} layers wrappers around every factory it builds, and
 * it does so recursively, so a sub-trade inside a {@code weighted_pool} carries its own wrappers
 * too. This walk peels them off one at a time, recording what each contributes, and only then asks
 * the factory underneath to enumerate itself. That is also the whole trick behind showing gated
 * trades: it reaches past {@link ConditionalTradeFactory} instead of calling through it, so a trade
 * whose condition currently fails is listed and marked rather than silently missing.
 */
public final class CatalogExpansion {

    private CatalogExpansion() {
    }

    /** Expands one top-level factory from a tier pool. */
    public static void expandTrade(TradeOffers.Factory factory, Entity merchant, CatalogBuilder out) {
        out.reset();
        expand(factory, merchant, out);
    }

    /** Expands a factory in the current scope. Called recursively by pool expanders. */
    public static void expand(TradeOffers.Factory factory, Entity merchant, CatalogBuilder out) {
        if (out.isFull()) {
            out.countSkipped(1);
            return;
        }

        if (factory instanceof PricingTradeFactory pricing) {
            out.pushPriceFactor(pricing.priceFactor(merchant));
            expand(pricing.delegate(), merchant, out);
            out.popPriceFactor();
            return;
        }

        if (factory instanceof ConditionalTradeFactory conditional) {
            TradeCondition condition = conditional.condition();
            // Third-party conditions registered through the one-arg TradeConditions.register still
            // gate correctly; they just cannot describe themselves, so the row shows as gated
            // without a reason line.
            List<ConditionInfo> infos = condition instanceof ParsedConditions parsed
                    ? parsed.describe(merchant)
                    : List.of();
            out.pushConditions(infos, condition.test(merchant));
            expand(conditional.delegate(), merchant, out);
            out.popConditions();
            return;
        }

        // Transparent to the catalogue: it only rebinds registry entries on a created offer, and the
        // rows below have to keep reaching the factory that knows how to enumerate itself.
        if (factory instanceof RegistryRebindFactory rebind) {
            expand(rebind.delegate(), merchant, out);
            return;
        }

        if (factory instanceof CatalogExpandable expandable) {
            expandable.expandCatalog(merchant, out);
            return;
        }

        out.addSampled(factory, merchant);
    }
}
