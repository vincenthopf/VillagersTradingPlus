package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Accumulates catalogue rows while {@link CatalogExpansion} walks a tier's factory pool.
 *
 * <p>A trade's metadata is not all known at the point a row is emitted: a {@code weighted_pool}
 * knows the weights but not the items, and the leaf factory knows the items but not that it sits
 * inside a pool at all. So the walk maintains three scope stacks (conditions, pool weight, price
 * factor) that each row snapshots on the way out. Push on the way down, pop on the way back up, and
 * arbitrarily nested pools compose without any expander knowing its own depth.
 */
public final class CatalogBuilder {

    /**
     * Hard row cap. The binding constraint downstream is the 1 MiB {@code CustomPayloadS2CPacket}payload limit.
     **/
    public static final int MAX_ENTRIES = 512;

    /** Fixed seed for the unenumerable-factory fallback, so those rows never change between opens. */
    private static final long CATALOG_SEED = 0L;

    private record ConditionGroup(List<ConditionInfo> infos, boolean satisfied) {
    }

    private final List<CatalogEntry> entries = new ArrayList<>();

    /** {weight, totalWeight} of each enclosing weighted_pool, innermost last. */
    private final Deque<int[]> weightStack = new ArrayDeque<>();
    private final List<ConditionGroup> conditionStack = new ArrayList<>();

    /**
     * Saved {@code composedShare} / {@code composedPriceFactor} values, restored on pop. Kept as
     * snapshots rather than unwound by division so nesting cannot accumulate float drift.
     */
    private final Deque<Float> shareStack = new ArrayDeque<>();
    private final Deque<Float> priceFactorStack = new ArrayDeque<>();

    private final int tierPoolSize;
    private final int tierPicks;

    private float composedShare = 1.0f;
    private float composedPriceFactor = 1.0f;
    private int skipped;

    /** Flattened view of {@link #conditionStack}, rebuilt only when the stack actually changes. */
    private List<ConditionInfo> flatConditions = List.of();
    private boolean conditionsMet = true;

    public CatalogBuilder(int tierPoolSize, int tierPicks) {
        this.tierPoolSize = tierPoolSize;
        this.tierPicks = tierPicks;
    }

    /** Clears all scope state before walking the next top-level factory in the tier pool. */
    public void reset() {
        this.weightStack.clear();
        this.conditionStack.clear();
        this.shareStack.clear();
        this.priceFactorStack.clear();
        this.composedShare = 1.0f;
        this.composedPriceFactor = 1.0f;
        refreshConditions();
    }

    // --- scopes --------------------------------------------------------------------------------

    /**
     * Enters a {@code weighted_pool} branch. The weight is both displayed verbatim ("3 of 14") and
     * folded into the row's overall probability.
     */
    public void pushWeight(int weight, int totalWeight) {
        this.weightStack.addLast(new int[]{weight, totalWeight});
        pushShare(totalWeight > 0 ? (float) weight / totalWeight : 1.0f);
    }

    public void popWeight() {
        this.weightStack.pollLast();
        popShare();
    }

    /**
     * Narrows the probability of everything added until {@link #popShare} without claiming a JSON
     * weight. Used where a factory splits uniformly over variants it enumerates: one enchantment
     * of forty, one tag member of eight. That is a real chance the player wants to see but is not
     * a {@code weight} anyone wrote down.
     */
    public void pushShare(float share) {
        this.shareStack.addLast(this.composedShare);
        this.composedShare *= share;
    }

    public void popShare() {
        Float saved = this.shareStack.pollLast();
        if (saved != null) {
            this.composedShare = saved;
        }
    }

    /**
     * Enters a conditional branch. Nested gates all have to open, so a row's conditions are the
     * concatenation of every enclosing group and it counts as satisfied only when all of them are.
     */
    public void pushConditions(List<ConditionInfo> infos, boolean satisfied) {
        this.conditionStack.add(new ConditionGroup(infos, satisfied));
        refreshConditions();
    }

    public void popConditions() {
        if (!this.conditionStack.isEmpty()) {
            this.conditionStack.remove(this.conditionStack.size() - 1);
            refreshConditions();
        }
    }

    /**
     * Enters a pricing wrapper. In practice there is only ever one, since {@code weighted_pool} strips the
     * wrapper off its sub-trades so the cost scale is not applied twice, but factors still compose
     * multiplicatively here so the catalogue keeps matching a generated offer whatever the nesting.
     */
    public void pushPriceFactor(float factor) {
        this.priceFactorStack.addLast(this.composedPriceFactor);
        this.composedPriceFactor *= factor;
    }

    public void popPriceFactor() {
        Float saved = this.priceFactorStack.pollLast();
        if (saved != null) {
            this.composedPriceFactor = saved;
        }
    }

    // --- emission ------------------------------------------------------------------------------

    /** Adds a row whose price is fixed. */
    public void add(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell,
                    int maxUses, int villagerExperience, float priceMultiplier, int demand) {
        int price = firstBuy.getCount();
        add(firstBuy, secondBuy, sell, maxUses, villagerExperience, priceMultiplier, demand, price, price);
    }

    /**
     * Adds a row whose price varies between {@code rawMinPrice} and {@code rawMaxPrice} (both
     * pre-pricing-factor). The displayed stack shows the low end.
     */
    public void add(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell,
                    int maxUses, int villagerExperience, float priceMultiplier, int demand,
                    int rawMinPrice, int rawMaxPrice) {
        if (isFull()) {
            this.skipped++;
            return;
        }

        int minPrice = scale(rawMinPrice, firstBuy.getMaxCount());
        int maxPrice = scale(rawMaxPrice, firstBuy.getMaxCount());

        ItemStack shownFirstBuy = firstBuy.copy();
        shownFirstBuy.setCount(minPrice);

        int[] innermost = this.weightStack.peekLast();
        int weight = innermost == null ? 0 : innermost[0];
        int totalWeight = innermost == null ? 0 : innermost[1];

        this.entries.add(new CatalogEntry(shownFirstBuy, secondBuy.copy(), sell.copy(),
                weight, totalWeight, this.composedShare, this.tierPoolSize, this.tierPicks,
                maxUses, villagerExperience, priceMultiplier, demand, minPrice, maxPrice,
                this.flatConditions, this.conditionsMet));
    }

    /** Adds a row straight from a generated offer, reading its economics back off the offer. */
    public void addOffer(TradeOffer offer) {
        int price = offer.getOriginalFirstBuyItem().getCount();
        addOffer(offer, price, price);
    }

    public void addOffer(TradeOffer offer, int rawMinPrice, int rawMaxPrice) {
        add(offer.getOriginalFirstBuyItem(), offer.getSecondBuyItem(), offer.getSellItem(),
                offer.getMaxUses(), offer.getMerchantExperience(), offer.getPriceMultiplier(),
                offer.getDemandBonus(), rawMinPrice, rawMaxPrice);
    }

    /**
     * Fallback for factories that cannot enumerate themselves: rolls the factory once with a fixed
     * seed, so the row is stable across openings even when {@code create} is random.
     */
    public void addSampled(TradeOffers.Factory factory, Entity merchant) {
        TradeOffer offer = factory.create(merchant, Random.create(CATALOG_SEED));
        if (offer != null) {
            addOffer(offer);
        }
    }

    // --- caps ----------------------------------------------------------------------------------

    public boolean isFull() {
        return this.entries.size() >= MAX_ENTRIES;
    }

    /** Records rows an expander chose not to emit because the cap was already reached. */
    public void countSkipped(int count) {
        this.skipped += Math.max(0, count);
    }

    public List<CatalogEntry> entries() {
        return this.entries;
    }

    /** How many rows exist beyond the ones collected, so the panel can say what it is hiding. */
    public int skipped() {
        return this.skipped;
    }

    // --- internals -----------------------------------------------------------------------------

    private void refreshConditions() {
        if (this.conditionStack.isEmpty()) {
            this.flatConditions = List.of();
            this.conditionsMet = true;
            return;
        }

        List<ConditionInfo> flat = new ArrayList<>();
        boolean met = true;
        for (ConditionGroup group : this.conditionStack) {
            flat.addAll(group.infos());
            met &= group.satisfied();
        }
        this.flatConditions = List.copyOf(flat);
        this.conditionsMet = met;
    }

    private int scale(int rawPrice, int maxCount) {
        return PricingTradeFactory.scaleCount(rawPrice, maxCount, this.composedPriceFactor);
    }
}
