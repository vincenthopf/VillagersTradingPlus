package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.ItemListing;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import com.lion.villagertradingplus.tradeoffers.TradePools;
import com.lion.villagertradingplus.tradeoffers.VillagerTradeTable;
import net.minecraft.world.entity.player.Player;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


// Extends MerchantEntity so the inherited members this mixin uses (fillRecipesFromPool, getOffers,
// getWorld) resolve through the real hierarchy; @Shadow only sees members declared on the target itself.
@Mixin(Villager.class)
public abstract class VillagerEntityMixin extends AbstractVillager implements TradeControl {

    public VillagerEntityMixin(EntityType<? extends AbstractVillager> entityType, Level world) {
        super(entityType, world);
    }

    @Shadow public abstract VillagerData getVillagerData();

    @Shadow public abstract void setVillagerData(VillagerData villagerData);

    @Shadow public abstract void setVillagerXp(int experience);

    @Shadow public abstract void refreshBrain(ServerLevel world);

    /** Per-level offer counts (index = level 1..5), so a single tier can be re-rolled in place. */
    @Unique private int[] villagertradingplus$levelCounts = null;

    @Shadow
    private void updateSpecialPrices(Player player) {
        throw new AssertionError();
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$updateTrades(ServerLevel level, CallbackInfo ci) {
        Int2ObjectMap<ItemListing[]> map = villagertradingplus$tradesForProfession();
        if (map == null || map.isEmpty()) {
            return;
        }
        ItemListing[] pool = map.get(getVillagerData().level());
        if (pool == null) {
            return;
        }
        TradePools.addOffers(this, getOffers(), pool, VillagerTradingPlus.CONFIG.trade_offers_per_level);
        Player tradingPlayer = getTradingPlayer();
        if (tradingPlayer != null) {
            updateSpecialPrices(tradingPlayer);
        }
        ci.cancel();
    }

    // Entity NBT went through ReadView/WriteView in 1.21.6: writeCustomDataToNbt/readCustomDataFromNbt
    // no longer exist. Renaming only the @Inject target would have compiled and then silently never
    // fired, which is exactly the failure mode the 1.21.1 port ran into.
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void villagertradingplus$writeLevelCounts(ValueOutput view, CallbackInfo ci) {
        if (villagertradingplus$levelCounts != null) {
            view.putIntArray("VillagerTradingPlusLevelCounts", villagertradingplus$levelCounts);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void villagertradingplus$readLevelCounts(ValueInput view, CallbackInfo ci) {
        villagertradingplus$levelCounts =
                view.getIntArray("VillagerTradingPlusLevelCounts").orElse(null);
    }

    @Override
    public boolean villagertradingplus$supportsLevels() {
        return true;
    }

    @Override
    public void villagertradingplus$rerollAll() {
        Int2ObjectMap<ItemListing[]> map = villagertradingplus$tradesForProfession();

        MerchantOffers offers = getOffers();
        offers.clear();

        int level = getVillagerData().level();
        int[] counts = new int[level + 1];

        if (map != null) {
            for (int tier = 1; tier <= level; tier++) {
                ItemListing[] pool = map.get(tier);
                if (pool == null) {
                    continue;
                }
                int before = offers.size();
                TradePools.addOffers(this, offers, pool, VillagerTradingPlus.CONFIG.trade_offers_per_level);
                counts[tier] = offers.size() - before;
            }
        }

        villagertradingplus$levelCounts = counts;
    }

    @Override
    public void villagertradingplus$rerollLevel(int targetLevel) {
        int level = getVillagerData().level();
        if (targetLevel < 1 || targetLevel > level) {
            return;
        }

        MerchantOffers offers = getOffers();
        int[] counts = villagertradingplus$levelCounts;

        // Fall back to a full re-roll if we have no reliable per-level mapping (e.g. pre-existing villager).
        if (counts == null || counts.length <= level || villagertradingplus$sum(counts, 1, level) != offers.size()) {
            villagertradingplus$rerollAll();
            return;
        }

        Int2ObjectMap<ItemListing[]> map = villagertradingplus$tradesForProfession();
        ItemListing[] pool = map == null ? null : map.get(targetLevel);

        int start = villagertradingplus$sum(counts, 1, targetLevel - 1);
        int oldCount = counts[targetLevel];
        for (int i = 0; i < oldCount; i++) {
            offers.remove(start);
        }

        MerchantOffers fresh = new MerchantOffers();
        if (pool != null) {
            TradePools.addOffers(this, fresh, pool, VillagerTradingPlus.CONFIG.trade_offers_per_level);
        }
        offers.addAll(start, fresh);
        counts[targetLevel] = fresh.size();
    }

    @Override
    public void villagertradingplus$setLevel(int targetLevel) {
        targetLevel = Mth.clamp(targetLevel, VillagerData.MIN_VILLAGER_LEVEL, VillagerData.MAX_VILLAGER_LEVEL);
        setVillagerData(getVillagerData().withLevel(targetLevel));
        setVillagerXp(VillagerData.getMinXpPerLevel(targetLevel));
        villagertradingplus$rerollAll();
        if (level() instanceof ServerLevel serverWorld) {
            refreshBrain(serverWorld);
        }
    }

    /**
     * The trade table is keyed by {@link ResourceKey} since 1.21.6, and a villager's profession is a
     * {@link net.minecraft.core.Holder} that need not carry one - an inline entry
     * has a value but no key. Such a villager simply has no vanilla trade pool, hence the null.
     */
    @Unique
    private Int2ObjectMap<ItemListing[]> villagertradingplus$tradesForProfession() {
        return getVillagerData().profession().unwrapKey()
                .map(VillagerTradeTable.TRADES::get)
                .orElse(null);
    }

    @Unique
    private static int villagertradingplus$sum(int[] counts, int fromInclusive, int toInclusive) {
        int total = 0;
        for (int i = fromInclusive; i <= toInclusive && i < counts.length; i++) {
            total += counts[i];
        }
        return total;
    }
}
