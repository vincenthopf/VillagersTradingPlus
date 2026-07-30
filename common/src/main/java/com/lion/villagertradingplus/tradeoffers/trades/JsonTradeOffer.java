package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.ItemStackSerializer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

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
        return new TradeOffer(buy, second, sell, 0, maxUses, experience, priceMultiplier, demand);
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
        return object.has(key) ? Identifier.tryParse(object.get(key).getAsString()) : new Identifier(defaultValue);
    }

    public static ItemStack getItemStackFromJson(JsonObject json) {
        return ItemStackSerializer.fromJson(json, true);
    }

    public static ItemStack getItemStackFromJsonWithoutCount(JsonObject json) {
        return ItemStackSerializer.fromJson(json, false);
    }
}
