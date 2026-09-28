package com.lion.villagertradingplus.tradeoffers.catalog;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One row of the trade catalogue: the three display stacks plus every piece of metadata the JSON
 * defined but vanilla {@link net.minecraft.world.item.trading.MerchantOffer} throws away.
 *
 * <p>Built server-side by {@link CatalogBuilder} and shipped to the client whole, because none of
 * this survives a {@code TradeOffer} round-trip.
 *
 * @param poolWeight       weight within the innermost {@code weighted_pool}, or 0 when the trade is
 *                         not inside one
 * @param poolTotalWeight  total weight of that pool, or 0
 * @param poolShare        composed probability across <em>all</em> nesting levels (1.0 when not
 *                         pooled); differs from {@code poolWeight / poolTotalWeight} only for
 *                         pools nested inside other pools
 * @param tierPoolSize     how many trades the merchant's tier pool holds
 * @param tierPicks        how many of them the merchant actually rolls ({@code trade_offers_per_level})
 * @param minPrice         lowest first-buy count this trade can produce
 * @param maxPrice         highest; equal to {@code minPrice} when the price is fixed
 */
public record CatalogEntry(
        ItemStack firstBuy,
        ItemStack secondBuy,
        ItemStack sell,
        int poolWeight,
        int poolTotalWeight,
        float poolShare,
        int tierPoolSize,
        int tierPicks,
        int maxUses,
        int villagerExperience,
        float priceMultiplier,
        int demand,
        int minPrice,
        int maxPrice,
        List<ConditionInfo> conditions,
        boolean conditionsMet) {

    /** Whether this row came out of a {@code weighted_pool} and so has a meaningful weight. */
    public boolean isPooled() {
        return this.poolTotalWeight > 0;
    }

    /** Whether the first-buy count varies between rolls, so the displayed price is only a floor. */
    public boolean hasPriceRange() {
        return this.maxPrice > this.minPrice;
    }

    // OPTIONAL_PACKET_CODEC rather than the plain one: the second buy slot is empty on most trades,
    // and only the optional codec accepts an empty stack.
    public void write(RegistryFriendlyByteBuf buf) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.firstBuy);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.secondBuy);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.sell);
        buf.writeVarInt(this.poolWeight);
        buf.writeVarInt(this.poolTotalWeight);
        buf.writeFloat(this.poolShare);
        buf.writeVarInt(this.tierPoolSize);
        buf.writeVarInt(this.tierPicks);
        buf.writeVarInt(this.maxUses);
        buf.writeVarInt(this.villagerExperience);
        buf.writeFloat(this.priceMultiplier);
        buf.writeVarInt(this.demand);
        buf.writeVarInt(this.minPrice);
        buf.writeVarInt(this.maxPrice);
        buf.writeVarInt(this.conditions.size());
        for (ConditionInfo condition : this.conditions) {
            condition.write(buf);
        }
        buf.writeBoolean(this.conditionsMet);
    }

    public static CatalogEntry read(RegistryFriendlyByteBuf buf) {
        ItemStack firstBuy = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack secondBuy = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack sell = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int poolWeight = buf.readVarInt();
        int poolTotalWeight = buf.readVarInt();
        float poolShare = buf.readFloat();
        int tierPoolSize = buf.readVarInt();
        int tierPicks = buf.readVarInt();
        int maxUses = buf.readVarInt();
        int villagerExperience = buf.readVarInt();
        float priceMultiplier = buf.readFloat();
        int demand = buf.readVarInt();
        int minPrice = buf.readVarInt();
        int maxPrice = buf.readVarInt();

        int conditionCount = buf.readVarInt();
        List<ConditionInfo> conditions = new ArrayList<>(conditionCount);
        for (int i = 0; i < conditionCount; i++) {
            conditions.add(ConditionInfo.read(buf));
        }

        return new CatalogEntry(
                firstBuy,
                secondBuy,
                sell,
                poolWeight,
                poolTotalWeight,
                poolShare,
                tierPoolSize,
                tierPicks,
                maxUses,
                villagerExperience,
                priceMultiplier,
                demand,
                minPrice,
                maxPrice,
                List.copyOf(conditions),
                buf.readBoolean()
        );
    }
}
