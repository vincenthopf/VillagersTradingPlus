package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JsonSellPotionTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_potion trade", "sell"));
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "sell_potion trade", "convertible"));
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
            List<RegistryEntry<Potion>> list = brewable(entity.getWorld());
            if (list.isEmpty()) {
                return null;
            }

            RegistryEntry<Potion> potion = list.get(random.nextInt(list.size()));

            return new TradeOffer(waterBottle(), Optional.of(tradedCurrency()), sellStack(potion),
                    this.maxUses, this.experience, this.multiplier);
        }

        /** The potion pool is a fixed registry scan, so every brewable result gets its own row. */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<RegistryEntry<Potion>> brewable = brewable(merchant.getWorld());
            out.pushShare(brewable.isEmpty() ? 1.0f : 1.0f / brewable.size());
            for (RegistryEntry<Potion> potion : brewable) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }
                out.add(waterBottle().itemStack(), currency, sellStack(potion), this.maxUses, this.experience,
                        this.multiplier, 0);
            }
            out.popShare();
        }

        /**
         * Brewability moved off the registry and onto the world in 1.21: recipes are data-driven now,
         * so the answer depends on the loaded datapacks rather than on the potion alone.
         */
        private static List<RegistryEntry<Potion>> brewable(World world) {
            return Registries.POTION.streamEntries()
                    .filter(entry -> !entry.value().getEffects().isEmpty()
                            && world.getBrewingRecipeRegistry().isBrewable(entry))
                    .collect(Collectors.toList());
        }

        /**
         * What the player hands in. As a {@link TradedItem} this is a match rule rather than a stack,
         * so the water potion has to be spelled out as a component predicate - the same way vanilla
         * builds its own potion trades.
         */
        private TradedItem waterBottle() {
            return new TradedItem(this.buy.getItem(), this.buy.getCount())
                    .withComponents(builder -> builder.add(DataComponentTypes.POTION_CONTENTS,
                            new PotionContentsComponent(Potions.WATER)));
        }

        private TradedItem tradedCurrency() {
            return new TradedItem(this.currency.getItem(), this.currency.getCount());
        }

        private ItemStack sellStack(RegistryEntry<Potion> potion) {
            ItemStack stack = new ItemStack(this.sell.getItem(), 1);
            stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potion));
            return stack;
        }
    }
}
