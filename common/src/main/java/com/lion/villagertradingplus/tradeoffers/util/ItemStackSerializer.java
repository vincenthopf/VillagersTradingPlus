package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Central item-stack parser for the JSON trade system. Backwards compatible with the original
 * {@code {"item": ..., "count": ...}} shape, plus optional NBT/component sugar:
 * {@code name}, {@code lore}, {@code enchantments}, {@code color}, {@code potion},
 * {@code skull_owner}, {@code book} and a raw {@code nbt} (SNBT) escape hatch applied last.
 *
 * <p>An unresolvable or missing item id raises {@link TradeParseException} so the whole trade is
 * skipped with a logged reason — a blank slot in a villager's trade list is worse than no trade.
 * Malformed sugar (e.g. a bad {@code nbt} string) is still logged and skipped rather than throwing,
 * since the stack itself remains usable.
 */
public final class ItemStackSerializer {

    private ItemStackSerializer() {
    }

    public static ItemStack fromJson(JsonObject json) {
        return fromJson(json, true);
    }

    public static ItemStack fromJson(JsonObject json, boolean withCount) {
        JsonElement idElement = json.get("item");
        if (idElement == null) {
            throw new TradeParseException("trade item is missing the \"item\" key: " + json);
        }

        String id = idElement.getAsString();
        Optional<Item> item = Registries.ITEM.getOrEmpty(Identifier.tryParse(id));
        if (item.isEmpty()) {
            throw new TradeParseException("unknown item id \"" + id
                    + "\" (is the mod that provides it installed?)");
        }

        int count = withCount && json.has("count") ? json.get("count").getAsInt() : 1;
        ItemStack stack = new ItemStack(item.get(), count);
        applyComponents(stack, json);
        return stack;
    }

    /**
     * Applies the optional NBT/component sugar onto an already-built stack. Public so tag-resolved
     * stacks ({@link Ingredient}) can reuse the same sugar per random pick.
     */
    public static void applyComponents(ItemStack stack, JsonObject json) {
        if (stack.isEmpty()) {
            return;
        }

        if (json.has("name")) {
            stack.setCustomName(parseText(json.get("name")));
        }

        if (json.has("lore")) {
            applyLore(stack, json.getAsJsonArray("lore"));
        }

        if (json.has("enchantments")) {
            applyEnchantments(stack, json.getAsJsonArray("enchantments"));
        }

        // No DyeableItem check any more: dyeing is a component, so any item can carry a colour and
        // the game simply ignores it on items that do not render one.
        if (json.has("color")) {
            stack.set(DataComponentTypes.DYED_COLOR,
                    new DyedColorComponent(parseColor(json.get("color").getAsString()), true));
        }

        if (json.has("potion")) {
            Registries.POTION.getEntry(Identifier.tryParse(json.get("potion").getAsString()))
                    .ifPresent(potion -> stack.set(DataComponentTypes.POTION_CONTENTS,
                            new PotionContentsComponent(potion)));
        }

        if (json.has("skull_owner")) {
            stack.getOrCreateNbt().putString("SkullOwner", json.get("skull_owner").getAsString());
        }

        if (json.has("book")) {
            applyBook(stack, json.getAsJsonObject("book"));
        }

        // Raw SNBT escape hatch, applied LAST so it can override or add anything above.
        if (json.has("nbt")) {
            applyRawNbt(stack, json.get("nbt").getAsString());
        }
    }

    private static void applyLore(ItemStack stack, JsonArray lore) {
        NbtList loreList = new NbtList();
        for (JsonElement line : lore) {
            loreList.add(NbtString.of(Text.Serializer.toJson(parseText(line))));
        }
        stack.getOrCreateSubNbt("display").put("Lore", loreList);
    }

    private static void applyEnchantments(ItemStack stack, JsonArray enchantments) {
        boolean isBook = stack.getItem() instanceof EnchantedBookItem;
        for (JsonElement element : enchantments) {
            JsonObject obj = element.getAsJsonObject();
            Enchantment enchantment = Registries.ENCHANTMENT.get(Identifier.tryParse(obj.get("id").getAsString()));
            if (enchantment == null) {
                VillagerTradingPlus.LOGGER.error("Unknown enchantment in trade item: " + obj.get("id").getAsString());
                continue;
            }
            int level = obj.has("lvl") ? obj.get("lvl").getAsInt() : 1;
            if (isBook) {
                EnchantedBookItem.addEnchantment(stack, new EnchantmentLevelEntry(enchantment, level));
            } else {
                stack.addEnchantment(enchantment, level);
            }
        }
    }

    private static void applyBook(ItemStack stack, JsonObject book) {
        NbtCompound nbt = stack.getOrCreateNbt();
        if (book.has("title")) {
            nbt.putString("title", book.get("title").getAsString());
        }
        if (book.has("author")) {
            nbt.putString("author", book.get("author").getAsString());
        }
        if (book.has("pages")) {
            NbtList pages = new NbtList();
            for (JsonElement page : book.getAsJsonArray("pages")) {
                pages.add(NbtString.of(Text.Serializer.toJson(parseText(page))));
            }
            nbt.put("pages", pages);
        }
    }

    private static void applyRawNbt(ItemStack stack, String snbt) {
        try {
            NbtCompound parsed = StringNbtReader.parse(snbt);
            stack.getOrCreateNbt().copyFrom(parsed);
        } catch (Exception e) {
            VillagerTradingPlus.LOGGER.error("Failed to parse trade item nbt: " + snbt, e);
        }
    }

    /**
     * Parses text that may be a plain literal string, a JSON text component given as a string
     * (e.g. {@code "{\"text\":\"Hi\",\"color\":\"gold\"}"}), or a raw JSON object/array component.
     * A bare string that is not JSON is treated as literal text; strict component parsing is only
     * attempted when the string looks like JSON, and any parse failure falls back to a literal.
     */
    private static Text parseText(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            String trimmed = raw.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    Text parsed = Text.Serializer.fromJson(raw);
                    if (parsed != null) {
                        return parsed;
                    }
                } catch (Exception ignored) {
                    // fall through to literal
                }
            }
            return Text.literal(raw);
        }

        try {
            Text parsed = Text.Serializer.fromJson(element);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ignored) {
            // fall through to empty
        }
        return Text.empty();
    }

    /** Parses a color as an integer or a {@code #RRGGBB} hex string. */
    private static int parseColor(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            return Integer.parseInt(trimmed.substring(1), 16);
        }
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            return Integer.parseInt(trimmed.substring(2), 16);
        }
        return Integer.parseInt(trimmed);
    }
}
