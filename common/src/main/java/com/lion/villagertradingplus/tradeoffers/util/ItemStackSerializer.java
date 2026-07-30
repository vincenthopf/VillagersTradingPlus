package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Central item-stack parser for the JSON trade system. Backwards compatible with the original
 * {@code {"item": ..., "count": ...}} shape, plus optional component sugar:
 * {@code name}, {@code lore}, {@code color}, {@code potion}, {@code skull_owner}, {@code book} and a
 * raw {@code nbt} (SNBT) escape hatch applied last.
 *
 * <p>An unresolvable or missing item id raises {@link TradeParseException} so the whole trade is
 * skipped with a logged reason — a blank slot in a villager's trade list is worse than no trade.
 * Malformed sugar is still logged and skipped rather than throwing, since the stack itself remains
 * usable.
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
     * Applies the optional component sugar onto an already-built stack. Public so tag-resolved
     * stacks ({@link Ingredient}) can reuse the same sugar per random pick.
     */
    public static void applyComponents(ItemStack stack, JsonObject json) {
        if (stack.isEmpty()) {
            return;
        }

        if (json.has("name")) {
            stack.set(DataComponentTypes.CUSTOM_NAME, parseText(json.get("name")));
        }

        if (json.has("lore")) {
            applyLore(stack, json.getAsJsonArray("lore"));
        }

        if (json.has("enchantments")) {
            // Enchantments are a dynamic registry since 1.21 and cannot be resolved here: parsing
            // happens on datapack load, where no world - and therefore no registry - exists yet.
            // The dedicated sell_enchanted_* trade types resolve theirs in create(Entity, Random),
            // which is where an entity, and with it a registry, is available.
            VillagerTradingPlus.LOGGER.warn(
                    "\"enchantments\" on a trade item is not supported on this Minecraft version; "
                            + "use the sell_enchanted_book / sell_specific_enchanted_tool trade types instead. Item: {}",
                    json.get("item"));
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
            stack.set(DataComponentTypes.PROFILE,
                    new ProfileComponent(Optional.of(json.get("skull_owner").getAsString()),
                            Optional.empty(), new com.mojang.authlib.properties.PropertyMap()));
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
        List<Text> lines = new ArrayList<>();
        for (JsonElement line : lore) {
            lines.add(parseText(line));
        }
        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
    }

    private static void applyBook(ItemStack stack, JsonObject book) {
        String title = book.has("title") ? book.get("title").getAsString() : "";
        String author = book.has("author") ? book.get("author").getAsString() : "";

        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        if (book.has("pages")) {
            for (JsonElement page : book.getAsJsonArray("pages")) {
                pages.add(RawFilteredPair.of(parseText(page)));
            }
        }

        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,
                new WrittenBookContentComponent(RawFilteredPair.of(title), author, 0, pages, true));
    }

    /**
     * The raw SNBT hatch now lands in {@code custom_data}.
     *
     * <p>This is a genuine narrowing, not a translation: before components, arbitrary NBT written
     * here was read by the game itself. {@code custom_data} is inert — it round-trips and is visible
     * to commands and other mods, but vanilla no longer acts on it. Anything that used this hatch to
     * set attributes, durability or similar has to move to the dedicated keys above.
     */
    private static void applyRawNbt(ItemStack stack, String snbt) {
        try {
            NbtCompound parsed = StringNbtReader.parse(snbt);
            stack.apply(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT,
                    existing -> NbtComponent.of(existing.copyNbt().copyFrom(parsed)));
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
                Text parsed = decodeText(com.google.gson.JsonParser.parseString(raw));
                if (parsed != null) {
                    return parsed;
                }
            }
            return Text.literal(raw);
        }

        Text parsed = decodeText(element);
        return parsed != null ? parsed : Text.empty();
    }

    /** Text.Serializer is gone; component text goes through its codec now. */
    private static Text decodeText(JsonElement element) {
        try {
            return TextCodecs.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null);
        } catch (Exception ignored) {
            return null;
        }
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
