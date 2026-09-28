package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.ItemStackSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

public abstract class JsonTradeOffer {
    protected int maxUses;
    protected int experience;
    protected float priceMultiplier;
    protected int demand;

    @NotNull
    public abstract VillagerTrades.ItemListing deserialize(JsonObject json);

    protected void loadDefaultStats(JsonObject jsonObject) {
        this.maxUses = readInt(jsonObject, "max_uses", 12);
        this.experience = readInt(jsonObject, "villager_experience", 5);
        this.demand = readInt(jsonObject, "demand", 0);
        // Global price-multiplier scale from config (1.0 keeps the JSON value unchanged).
        float base = readFloat(jsonObject, "price_multiplier", 0.05f);
        this.priceMultiplier = base * VillagerTradingPlus.CONFIG.trade_price_multiplier_scale;
    }

    /**
     * Builds a vanilla offer using the loaded stats, including the initial {@code demand}. Uses the
     * 8-arg {@link MerchantOffer} constructor so the demand field is honoured by vanilla price math.
     */
    protected MerchantOffer buildOffer(ItemStack buy, ItemStack second, ItemStack sell) {
        return new MerchantOffer(traded(buy), tradedOrEmpty(second), sell, 0, maxUses, experience, priceMultiplier, demand);
    }

    /**
     * Turns a parsed buy stack into the {@link ItemCost} the offer wants.
     * <p>
     * A {@code TradedItem} is a match rule rather than a stack: it names the item, the count, and the
     * components a player's stack must carry. Everything the JSON put on the buy side - a name, an
     * enchantment, a potion - therefore has to become part of that rule. Dropping it would leave a
     * trade that used to demand an enchanted pickaxe quietly accepting a plain one.
     */
    protected static ItemCost traded(ItemStack stack) {
        ItemCost tradedItem = new ItemCost(stack.getItem(), stack.getCount());
        DataComponentPatch changes = stack.getComponentsPatch();
        if (changes.isEmpty()) {
            return tradedItem;
        }
        return tradedItem.withComponents(builder -> {
            addChanges(builder, changes);
            return builder;
        });
    }

    /** The empty stack means "no second slot", which the offer expresses as an absent optional. */
    protected static Optional<ItemCost> tradedOrEmpty(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(traded(stack));
    }

    // The component type and its value are a matched pair by construction, but ComponentChanges
    // erases that link to ComponentType<?> plus Optional<?>, so the cast cannot be avoided here.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addChanges(DataComponentExactPredicate.Builder builder, DataComponentPatch changes) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : changes.entrySet()) {
            // A *removed* component has no predicate equivalent - only presence can be demanded.
            entry.getValue().ifPresent(value -> builder.expect((DataComponentType) entry.getKey(), value));
        }
    }

    public static int readInt(JsonObject object, String key, int defaultValue) {
        return object.has(key) ? object.get(key).getAsInt() : defaultValue;
    }

    public static float readFloat(JsonObject object, String key, float defaultValue) {
        return object.has(key) ? object.get(key).getAsFloat() : defaultValue;
    }

    public static String readString(JsonObject object, String key, String defaultValue) {
        return object.has(key) ? object.get(key).getAsString() : defaultValue;
    }

    public static ResourceLocation readIdentifier(JsonObject object, String key, String defaultValue) {
        return object.has(key) ? ResourceLocation.tryParse(object.get(key).getAsString()) : ResourceLocation.parse(defaultValue);
    }

    public static ItemStack getItemStackFromJson(JsonObject json) {
        return ItemStackSerializer.fromJson(json, true);
    }

    public static ItemStack getItemStackFromJsonWithoutCount(JsonObject json) {
        return ItemStackSerializer.fromJson(json, false);
    }
}
