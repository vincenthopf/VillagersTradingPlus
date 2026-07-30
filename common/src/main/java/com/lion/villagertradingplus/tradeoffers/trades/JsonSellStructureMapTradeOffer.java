package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.NotNull;

public class JsonSellStructureMapTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        TagKey<Structure> structure = TagKey.of(RegistryKeys.STRUCTURE, readIdentifier(json, "structure_id", ""));
        String name = readString(json, "name", "");
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());
        ItemStack buy = getItemStackFromJson(json.get("buy").getAsJsonObject());

        return new Factory(buy, currency, structure, name, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
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

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
                return null;
            } else {
                BlockPos blockPos = serverWorld.locateStructure(this.structure, entity.getBlockPos(), 100, true);
                if (blockPos != null) {
                    ItemStack itemStack = FilledMapItem.createMap(serverWorld, blockPos.getX(), blockPos.getZ(), (byte)2, true, true);
                    FilledMapItem.fillExplorationMap(serverWorld, itemStack);
                    MapState.addDecorationsNbt(itemStack, blockPos, "+", MapDecorationTypes.RED_X);
                    itemStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.translatable(this.nameKey));
                    return new TradeOffer(traded(currency), tradedOrEmpty(buy), itemStack, this.maxUses, this.experience, this.multiplier);
                } else {
                    return null;
                }
            }
        }

        /**
         * One row, built without touching the world. {@code create} runs a 100-chunk
         * {@code locateStructure} scan on the server thread; doing that just to populate a preview —
         * every time a player opens the catalogue — would stall the server for no benefit. The row
         * shows an unfilled map with the trade's own name, which is all the panel can usefully
         * display anyway.
         */
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            ItemStack map = new ItemStack(Items.FILLED_MAP);
            map.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.translatable(this.nameKey));
            out.add(this.currency, this.buy, map, this.maxUses, this.experience, this.multiplier, 0);
        }
    }
}
