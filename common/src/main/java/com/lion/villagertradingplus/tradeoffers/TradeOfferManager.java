package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.google.gson.*;
import com.lion.villagertradingplus.tradeoffers.conditions.TradeConditions;
import com.lion.villagertradingplus.tradeoffers.trades.*;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.village.VillagerProfession;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class TradeOfferManager {
    public static final Map<String, Integer> professionMapping = new HashMap<>();
    public static final Map<Identifier, JsonTradeOffer> tradeOfferRegistry = new HashMap<>();

    static {
        professionMapping.put("novice", 1);
        professionMapping.put("apprentice", 2);
        professionMapping.put("journeyman", 3);
        professionMapping.put("expert", 4);
        professionMapping.put("master", 5);
    }

    public static void registerTradeOffers() {
        VillagerTradingPlus.LOGGER.info("Registered JSON trade offer adapter.");
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_item"), new JsonSellItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"buy_item"), new JsonBuyItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"process_item"), new JsonProcessItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_potion"), new JsonSellPotionTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_enchanted_tool"), new JsonSellEnchantedToolTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_specific_enchanted_tool"), new JsonSellSpecificEnchantedToolTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_enchanted_book"), new JsonSellEnchantedBookTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_specific_enchanted_book"), new JsonSellSpecificEnchantedBookTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_map"), new JsonSellStructureMapTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"weighted_pool"), new JsonWeightedPoolTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"buy_tagged_item"), new JsonBuyTaggedItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_tagged_item"), new JsonSellTaggedItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_enchanted_book_from_list"), new JsonSellEnchantedBookFromListTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"multi_input"), new JsonMultiInputTradeOffer());
    }

    /**
     * Namespace the trade types used before this system became a library. Files written against
     * VillagersPlus name their types {@code villagersplus:sell_item} and so on, and there is no way
     * for their authors to know that the types have a new home - so those ids keep resolving here.
     */
    private static final String LEGACY_NAMESPACE = "villagersplus";

    /** Legacy type ids already warned about, so a big datapack logs each one once and not per trade. */
    private static final Set<String> REPORTED_LEGACY_TYPES = ConcurrentHashMap.newKeySet();

    /**
     * Resolves a trade type, accepting three spellings: the canonical
     * {@code villagertradingplus:sell_item}, a bare {@code sell_item}, and the legacy
     * {@code villagersplus:sell_item}.
     */
    @Nullable
    private static JsonTradeOffer findAdapter(String type) {
        Identifier id = Identifier.tryParse(type);
        if (id == null) {
            return null;
        }

        JsonTradeOffer adapter = tradeOfferRegistry.get(id);
        if (adapter != null) {
            return adapter;
        }

        // A bare "sell_item" parses as minecraft:sell_item, which is never what the author meant.
        if (type.indexOf(':') < 0) {
            return tradeOfferRegistry.get(Identifier.of(VillagerTradingPlus.MOD_ID, id.getPath()));
        }

        if (LEGACY_NAMESPACE.equals(id.getNamespace())) {
            adapter = tradeOfferRegistry.get(Identifier.of(VillagerTradingPlus.MOD_ID, id.getPath()));
            if (adapter != null && REPORTED_LEGACY_TYPES.add(type)) {
                VillagerTradingPlus.LOGGER.warn(
                        "Trade type '{}' still uses the old namespace; it now lives at '{}:{}'. Still accepted, but please update the file.",
                        type, VillagerTradingPlus.MOD_ID, id.getPath());
            }
            return adapter;
        }

        return null;
    }

    public static void deserializeJson(JsonObject jsonRoot) {
        Identifier professionId = Identifier.tryParse(jsonRoot.get("profession").getAsString());

        // The trade table is keyed by RegistryKey since 1.21.6. The registry is still consulted, but
        // only to reject a profession that does not exist - registering under a typo'd key would
        // otherwise succeed silently and the trades would never be offered to anyone.
        if (professionId == null || Registries.VILLAGER_PROFESSION.getOptionalValue(professionId).isEmpty()) {
            VillagerTradingPlus.LOGGER.error("Unknown villager profession in trade file: {}", professionId);
            return;
        }

        RegistryKey<VillagerProfession> profession =
                RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION, professionId);
        deserializeTrades(jsonRoot, (integer, factory)
                -> TradeOfferRegistryLoader.registerVillagerTrade(profession, integer, factory));
    }

    /**
     * Parses a wandering-trader trade file. Unlike profession files, trades are keyed by tier name
     * ({@code common} -> pool level 1, {@code rare} -> level 2) and fed into
     * {@link WanderingTraderTradeLoader}. An optional top-level {@code "replace"} swaps out the
     * vanilla trades instead of adding to them.
     */
    public static void deserializeWanderingTraderJson(JsonObject jsonRoot) {
        if (jsonRoot.has("replace")) {
            WanderingTraderTradeLoader.setReplace(jsonRoot.get("replace").getAsBoolean());
        }

        for (Map.Entry<String, JsonElement> entry : jsonRoot.get("trades").getAsJsonObject().entrySet()) {
            Integer level = WanderingTraderTradeLoader.LEVEL_MAPPING.get(entry.getKey());
            if (level == null) {
                VillagerTradingPlus.LOGGER.error("Unknown wandering trader trade tier: " + entry.getKey() + " (use 'common' or 'rare').");
                continue;
            }

            for (JsonElement tradeElement : entry.getValue().getAsJsonArray()) {
                JsonObject trade = tradeElement.getAsJsonObject();
                TradeOffers.Factory factory = deserializeTrade(trade);
                if (factory == null) {
                    VillagerTradingPlus.LOGGER.error("Wandering trader trade type broken: " + trade);
                } else {
                    WanderingTraderTradeLoader.add(level, factory);
                }
            }
        }
    }

    private static void deserializeTrades(@NotNull JsonObject jsonRoot, BiConsumer<Integer, TradeOffers.Factory> tradeConsumer) {
        for (Map.Entry<String, JsonElement> entry : jsonRoot.get("trades").getAsJsonObject().entrySet()) {

            Integer level = professionMapping.get(entry.getKey());
            if (level == null) {
                VillagerTradingPlus.LOGGER.error("Unknown villager trade tier: " + entry.getKey()
                        + " (use novice, apprentice, journeyman, expert or master).");
                continue;
            }

            for (JsonElement tradeElement : entry.getValue().getAsJsonArray()) {
                JsonObject trade = tradeElement.getAsJsonObject();
                TradeOffers.Factory factory = deserializeTrade(trade);

                // deserializeTrade already logged why; just record which tier lost a trade.
                if (factory == null) {
                    VillagerTradingPlus.LOGGER.warn("Dropped a broken trade from tier '{}': {}", entry.getKey(), trade);
                } else {
                    tradeConsumer.accept(level, factory);
                }
            }
        }
    }

    /**
     * Central choke-point every trade flows through — both top-level trades and nested ones
     * (e.g. inside {@code weighted_pool}). Deserializes the adapter, then layers the cross-cutting
     * {@code conditions} block and global pricing on top so those features apply to every type
     * without editing individual adapters. Returns {@code null} when the trade type is unknown.
     */
    @Nullable
    public static TradeOffers.Factory deserializeTrade(JsonObject trade) {
        String type = readString(trade, "type");
        JsonTradeOffer adapter = findAdapter(type);
        if (adapter == null) {
            VillagerTradingPlus.LOGGER.warn("Skipping trade with unknown type '{}'.", type);
            return null;
        }

        try {
            // Wrapped innermost, under the conditional and pricing layers, so weighted_pool still
            // recognises the PricingTradeFactory it strips off its sub-trades.
            TradeOffers.Factory factory = RegistryRebindFactory.wrap(adapter.deserialize(trade));

            if (VillagerTradingPlus.CONFIG.enable_conditional_trades && trade.has("conditions")) {
                boolean orLogic = "or".equalsIgnoreCase(readString(trade, "logic"));
                factory = new ConditionalTradeFactory(factory,
                        TradeConditions.parse(trade.getAsJsonArray("conditions"), orLogic));
            }

            return PricingTradeFactory.wrapIfNeeded(factory, trade);
        } catch (TradeParseException e) {
            // Skip only this trade: one bad item id must not take the rest of the datapack with it.
            VillagerTradingPlus.LOGGER.warn("Skipping '{}' trade: {}", type, e.getMessage());
            return null;
        }
    }

    private static String readString(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }
}
