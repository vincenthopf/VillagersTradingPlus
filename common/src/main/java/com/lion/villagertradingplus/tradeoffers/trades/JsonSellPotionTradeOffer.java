package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class JsonSellPotionTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(json.get("sell").getAsJsonObject());
        ItemStack buy = getItemStackFromJson(json.get("convertible").getAsJsonObject());
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());

        return new Factory(buy, sell, currency, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final ItemStack buy;
        private final ItemStack sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack buy, ItemStack sell, ItemStack currency, int maxUses, int experience, float multiplier) {
            this.buy = buy;
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            List<Potion> list = brewable();

            Potion potion = list.get(random.nextInt(list.size()));

            return new TradeOffer(waterBottle(), currency, sellStack(potion), this.maxUses, this.experience, this.multiplier);
        }

        /** The potion pool is a fixed registry scan, so every brewable result gets its own row. */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<Potion> brewable = brewable();
            out.pushShare(brewable.isEmpty() ? 1.0f : 1.0f / brewable.size());
            for (Potion potion : brewable) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }
                out.add(waterBottle(), currency, sellStack(potion), this.maxUses, this.experience,
                        this.multiplier, 0);
            }
            out.popShare();
        }

        private static List<Potion> brewable() {
            return Registries.POTION.stream()
                    .filter((potion) -> !potion.getEffects().isEmpty() && BrewingRecipeRegistry.isBrewable(potion))
                    .collect(Collectors.toList());
        }

        /** Copies first: {@link PotionUtil#setPotion} writes NBT in place, and {@code buy} is shared. */
        private ItemStack waterBottle() {
            return PotionUtil.setPotion(this.buy.copy(), Potions.WATER);
        }

        private ItemStack sellStack(Potion potion) {
            return PotionUtil.setPotion(new ItemStack(this.sell.getItem(), 1), potion);
        }
    }
}
