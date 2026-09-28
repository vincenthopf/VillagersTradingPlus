package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.ItemListing;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

/**
 * A trade taking two inputs for one output, using vanilla's two buy slots. The {@code sell} stack
 * may carry NBT sugar (name, enchantments, etc.) via {@link com.lion.villagertradingplus.tradeoffers.util.ItemStackSerializer}.
 *
 * <p>Note: vanilla {@link MerchantOffer} has exactly two buy slots, so "two items plus a separate
 * emerald currency" is not possible; one of the two inputs is the currency.
 *
 * <pre>
 * { "type": "villagertradingplus:multi_input",
 *   "input_a": { "item": "minecraft:emerald", "count": 5 },
 *   "input_b": { "item": "minecraft:book", "count": 1 },
 *   "sell":    { "item": "minecraft:enchanted_book",
 *                "enchantments": [ { "id": "minecraft:mending", "lvl": 1 } ] } }
 * </pre>
 */
public class JsonMultiInputTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public ItemListing deserialize(JsonObject json) {
        loadDefaultStats(json);
        ItemStack inputA = getItemStackFromJson(JsonFields.requireObject(json, "multi_input trade", "input_a"));
        ItemStack inputB = getItemStackFromJson(JsonFields.requireObject(json, "multi_input trade", "input_b"));
        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "multi_input trade", "sell"));
        return new Factory(inputA, inputB, sell, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements ItemListing {
        private final ItemStack inputA;
        private final ItemStack inputB;
        private final ItemStack sell;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(ItemStack inputA, ItemStack inputB, ItemStack sell, int maxUses, int experience, float multiplier, int demand) {
            this.inputA = inputA;
            this.inputB = inputB;
            this.sell = sell;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            return new MerchantOffer(traded(inputA.copy()), tradedOrEmpty(inputB.copy()), sell.copy(), 0, maxUses, experience, multiplier, demand);
        }
    }
}
