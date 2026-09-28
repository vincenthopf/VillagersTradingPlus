package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import org.jetbrains.annotations.NotNull;

public class JsonSellStructureMapTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public VillagerTrades.ItemListing deserialize(JsonObject json) {
        loadDefaultStats(json);

        TagKey<Structure> structure = TagKey.create(Registries.STRUCTURE, readIdentifier(json, "structure_id", ""));
        String name = readString(json, "name", "");
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "sell_structure_map trade", "buy"));

        return new Factory(buy, currency, structure, name, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements VillagerTrades.ItemListing, CatalogExpandable {
        private final ItemStack currency;
        private final ItemStack buy;
        private final String nameKey;
        private final TagKey<Structure> structure;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack buy, ItemStack currency, TagKey<Structure> structure, String nameKey, int maxUses, int experience, float multiplier) {
            this.buy = buy;
            this.currency = currency;
            this.structure = structure;
            this.nameKey = nameKey;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public MerchantOffer getOffer(Entity entity, net.minecraft.util.RandomSource random) {
            if (!(entity.level() instanceof ServerLevel serverWorld)) {
                return null;
            } else {
                BlockPos blockPos = serverWorld.findNearestMapStructure(this.structure, entity.blockPosition(), 100, true);
                if (blockPos != null) {
                    ItemStack itemStack = MapItem.create(serverWorld, blockPos.getX(), blockPos.getZ(), (byte)2, true, true);
                    MapItem.renderBiomePreviewMap(serverWorld, itemStack);
                    MapItemSavedData.addTargetDecoration(itemStack, blockPos, "+", MapDecorationTypes.RED_X);
                    itemStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable(this.nameKey));
                    return new MerchantOffer(traded(currency), tradedOrEmpty(buy), itemStack, this.maxUses, this.experience, this.multiplier);
                } else {
                    return null;
                }
            }
        }

        /**
         * One row, built without touching the world. {@code create} runs a 100-chunk
         * {@code locateStructure} scan on the server thread, and doing that every time a player
         * opens the catalogue would stall the server for no benefit. The row
         * shows an unfilled map with the trade's own name, which is all the panel can usefully
         * display anyway.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            ItemStack map = new ItemStack(Items.FILLED_MAP);
            map.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable(this.nameKey));
            out.add(this.currency, this.buy, map, this.maxUses, this.experience, this.multiplier, 0);
        }
    }
}
