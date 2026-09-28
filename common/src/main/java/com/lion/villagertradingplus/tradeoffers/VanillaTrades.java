package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

public final class VanillaTrades {

    public static final List<ResourceKey<TradeSet>> WANDERING_TRADER_SETS = List.of(
            TradeSets.WANDERING_TRADER_BUYING,
            TradeSets.WANDERING_TRADER_UNCOMMON,
            TradeSets.WANDERING_TRADER_COMMON);

    private VanillaTrades() {
    }

    public static @Nullable LootContext lootContext(Entity trader, RandomSource random, Optional<Identifier> randomSequence) {
        if (!(trader.level() instanceof ServerLevel level)) {
            return null;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, trader.position())
                .withParameter(LootContextParams.THIS_ENTITY, trader)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE);
        LootContext.Builder builder = new LootContext.Builder(params);
        if (random != null) {
            builder.withOptionalRandomSource(random);
        }
        return builder.create(randomSequence);
    }

    public static Map<ResourceKey<VillagerProfession>, Int2ObjectMap<ItemListing[]>> villagerTable(RegistryAccess registries) {
        Map<ResourceKey<VillagerProfession>, Int2ObjectMap<ItemListing[]>> table = new HashMap<>();
        Registry<TradeSet> tradeSets = registries.lookupOrThrow(Registries.TRADE_SET);
        for (Map.Entry<ResourceKey<VillagerProfession>, VillagerProfession> entry : BuiltInRegistries.VILLAGER_PROFESSION.entrySet()) {
            Int2ObjectMap<ItemListing[]> levels = new Int2ObjectOpenHashMap<>();
            for (int level = VillagerData.MIN_VILLAGER_LEVEL; level <= VillagerData.MAX_VILLAGER_LEVEL; level++) {
                ResourceKey<TradeSet> key = entry.getValue().getTrades(level);
                if (key == null) {
                    continue;
                }
                Optional<TradeSet> set = tradeSets.getOptional(key);
                if (set.isPresent()) {
                    levels.put(level, listings(set.get()));
                }
            }
            if (!levels.isEmpty()) {
                table.put(entry.getKey(), levels);
            }
        }
        return table;
    }

    public static @Nullable TradeSet tradeSet(RegistryAccess registries, ResourceKey<TradeSet> key) {
        return registries.lookupOrThrow(Registries.TRADE_SET).getOptional(key).orElse(null);
    }

    public static ItemListing[] listings(TradeSet set) {
        return set.trades().stream()
                .map(trade -> (ItemListing) new VanillaTradeListing(trade, set.randomSequence()))
                .toArray(ItemListing[]::new);
    }

    public record VanillaTradeListing(Holder<VillagerTrade> trade, Optional<Identifier> randomSequence) implements ItemListing {
        @Override
        public @Nullable MerchantOffer getOffer(Entity trader, RandomSource random) {
            LootContext context = lootContext(trader, random, this.randomSequence);
            return context == null ? null : this.trade.value().getOffer(context);
        }
    }
}
