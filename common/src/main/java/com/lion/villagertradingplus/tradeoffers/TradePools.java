package com.lion.villagertradingplus.tradeoffers;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class TradePools {

    private TradePools() {
    }

    public static void addOffers(Entity trader, MerchantOffers offers, ItemListing[] pool, int count) {
        RandomSource random = trader.getRandom();
        List<ItemListing> remaining = Lists.newArrayList(pool);
        int added = 0;
        while (added < count && !remaining.isEmpty()) {
            MerchantOffer offer = remaining.remove(random.nextInt(remaining.size())).getOffer(trader, random);
            if (offer != null) {
                offers.add(offer);
                added++;
            }
        }
    }
}
