package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.ItemStackSerializer;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.ComponentPredicate;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public abstract class JsonTradeOffer {
    protected int maxUses;
    protected int experience;
    protected float priceMultiplier;
    protected int demand;

    @NotNull
    public abstract TradeOffers.Factory deserialize(JsonObject json);

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
     * 8-arg {@link TradeOffer} constructor so the demand field is honoured by vanilla price math.
     */
    protected TradeOffer buildOffer(ItemStack buy, ItemStack second, ItemStack sell) {
        return new TradeOffer(traded(buy), tradedOrEmpty(second), sell, 0, maxUses, experience, priceMultiplier, demand);
    }

    /**
     * Turns a parsed buy stack into the {@link TradedItem} the offer wants.
     * <p>
     * A {@code TradedItem} is a match rule rather than a stack: it names the item, the count, and the
     * components a player's stack must carry. Everything the JSON put on the buy side - a name, an
     * enchantment, a potion - therefore has to become part of that rule. Dropping it would leave a
     * trade that used to demand an enchanted pickaxe quietly accepting a plain one.
     */
    protected static TradedItem traded(ItemStack stack) {
        TradedItem tradedItem = new TradedItem(stack.getItem(), stack.getCount());
        ComponentChanges changes = stack.getComponentChanges();
        if (changes.isEmpty()) {
            return tradedItem;
        }
        return tradedItem.withComponents(builder -> addChanges(builder, changes));
    }

    /** The empty stack means "no second slot", which the offer expresses as an absent optional. */
    protected static Optional<TradedItem> tradedOrEmpty(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(traded(stack));
    }

    // The component type and its value are a matched pair by construction, but ComponentChanges
    // erases that link to ComponentType<?> plus Optional<?>, so the cast cannot be avoided here.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addChanges(ComponentPredicate.Builder builder, ComponentChanges changes) {
        for (Map.Entry<ComponentType<?>, Optional<?>> entry : changes.entrySet()) {
            // A *removed* component has no predicate equivalent - only presence can be demanded.
            entry.getValue().ifPresent(value -> builder.add((ComponentType) entry.getKey(), value));
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

    public static Identifier readIdentifier(JsonObject object, String key, String defaultValue) {
        return object.has(key) ? Identifier.tryParse(object.get(key).getAsString()) : Identifier.of(defaultValue);
    }

    public static ItemStack getItemStackFromJson(JsonObject json) {
        return ItemStackSerializer.fromJson(json, true);
    }

    public static ItemStack getItemStackFromJsonWithoutCount(JsonObject json) {
        return ItemStackSerializer.fromJson(json, false);
    }
}
