package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Central item-stack parser for the JSON trade system. Backwards compatible with the original
 * {@code {"item": ..., "count": ...}} shape, plus optional component sugar:
 * {@code name}, {@code lore}, {@code enchantments}, {@code color}, {@code potion},
 * {@code skull_owner}, {@code book} and a raw {@code nbt} (SNBT) escape hatch applied last.
 *
 * <p>An unresolvable or missing item id raises {@link TradeParseException} so the whole trade is
 * skipped with a logged reason; a blank slot in a villager's trade list is worse than no trade.
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
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(id));
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
            stack.set(DataComponents.CUSTOM_NAME, parseText(json.get("name")));
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
            stack.set(DataComponents.DYED_COLOR,
                    new DyedItemColor(parseColor(json.get("color").getAsString())));
        }

        if (json.has("potion")) {
            BuiltInRegistries.POTION.get(ResourceLocation.tryParse(json.get("potion").getAsString()))
                    .ifPresent(potion -> stack.set(DataComponents.POTION_CONTENTS,
                            new PotionContents(potion)));
        }

        if (json.has("skull_owner")) {
            stack.set(DataComponents.PROFILE,
                    new ResolvableProfile(Optional.of(json.get("skull_owner").getAsString()),
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

    /**
     * Applies {@code "enchantments": [ { "id": "minecraft:looting", "lvl": 3 } ]} - the same shape as
     * before components, since the JSON is a public format.
     *
     * <p>An id no longer names an enchantment on its own: they are a dynamic registry since 1.21 and
     * have to be looked up in the one loaded for this world. {@link DatapackRegistries} holds it, and
     * it is populated well before any trade file is read, so this still resolves at parse time and the
     * result is a normal component on the stack - which means the buy side keeps working too, because
     * {@code JsonTradeOffer.traded} folds the stack's components into the match rule.
     *
     * <p>An unknown enchantment id costs its own entry, not the trade: a datapack may name one this
     * world does not have loaded.
     */
    private static void applyEnchantments(ItemStack stack, JsonArray enchantments) {
        Optional<Registry<Enchantment>> registry = DatapackRegistries.registry(Registries.ENCHANTMENT);
        if (registry.isEmpty()) {
            VillagerTradingPlus.LOGGER.error(
                    "Cannot resolve \"enchantments\" on a trade item: no datapack registries are loaded. "
                            + "The item stays unenchanted.");
            return;
        }

        // Built on top of what the stack already carries so repeated application accumulates instead
        // of overwriting - a tag-resolved Ingredient re-applies this sugar per random pick.
        ItemEnchantments.Mutable builder =
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
        boolean applied = false;

        for (JsonElement element : enchantments) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("id")) {
                VillagerTradingPlus.LOGGER.error("Enchantment on a trade item is missing its \"id\": {}", entry);
                continue;
            }

            String id = entry.get("id").getAsString();
            ResourceLocation identifier = ResourceLocation.tryParse(id);
            Optional<Holder.Reference<Enchantment>> enchantment = identifier == null
                    ? Optional.empty()
                    : registry.get().get(ResourceKey.create(Registries.ENCHANTMENT, identifier));

            if (enchantment.isEmpty()) {
                VillagerTradingPlus.LOGGER.error("Unknown enchantment in trade item: {}", id);
                continue;
            }

            builder.set(enchantment.get(), entry.has("lvl") ? entry.get("lvl").getAsInt() : 1);
            applied = true;
        }

        if (applied) {
            // Routes to stored_enchantments on an enchanted book and to enchantments on anything else,
            // which is the split the old EnchantedBookItem fork did by hand.
            EnchantmentHelper.setEnchantments(stack, builder.toImmutable());
        }
    }

    private static void applyLore(ItemStack stack, JsonArray lore) {
        List<Component> lines = new ArrayList<>();
        for (JsonElement line : lore) {
            lines.add(parseText(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static void applyBook(ItemStack stack, JsonObject book) {
        String title = book.has("title") ? book.get("title").getAsString() : "";
        String author = book.has("author") ? book.get("author").getAsString() : "";

        List<Filterable<Component>> pages = new ArrayList<>();
        if (book.has("pages")) {
            for (JsonElement page : book.getAsJsonArray("pages")) {
                pages.add(Filterable.passThrough(parseText(page)));
            }
        }

        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), author, 0, pages, true));
    }

    /**
     * The raw SNBT hatch keeps working exactly as it did before components: {@code {Unbreakable:1b}}
     * still makes the item unbreakable, {@code {Damage:100}} still damages it, {@code display}, an
     * {@code AttributeModifiers} list, {@code CustomModelData}, {@code HideFlags},
     * {@code BlockEntityTag} and the rest all still land where they belong.
     *
     * <p>Not by a hand-written translation table - by running the tag through <em>Mojang's own data
     * fixer</em>, the same {@code ItemStackComponentizationFix} that converts a 1.20.4 world on first
     * load. The input is dressed up as a pre-component item stack
     * ({@code {id, Count, tag}}, the shape {@code StackData.fromDynamic} expects), updated to the
     * current data version, and read back with {@link ItemStack#CODEC}. Whatever the fixer does not
     * recognise it leaves in {@code custom_data} by itself, which is where unknown keys used to end up
     * anyway.
     *
     * <p>Two consequences worth knowing: the translation is always as complete and as correct as
     * vanilla's own, including every future version's rules; and old item ids in the tag get renamed
     * along the way, because the fixer chain from 1.20.1 onwards runs in full.
     *
     * <p>Applied as a component patch on top of the sugar above, so this hatch keeps overriding it.
     */
    private static void applyRawNbt(ItemStack stack, String snbt) {
        try {
            CompoundTag parsed = TagParser.parseCompoundFully(snbt);
            componentize(stack, parsed).ifPresent(stack::applyComponentsAndValidate);
        } catch (Exception e) {
            VillagerTradingPlus.LOGGER.error("Failed to parse trade item nbt: " + snbt, e);
        }
    }

    /**
     * Base version the raw tag is interpreted as. 1.20.1 is where this JSON format was last written
     * against a game that still read item NBT directly, so that is what the strings in the wild mean.
     * The exact value is not critical - anything below the componentization schema (3818) runs it -
     * but a lower one also picks up the item renames from 1.20.2 through 1.20.4.
     */
    private static final int RAW_NBT_DATA_VERSION = 3465;

    private static Optional<DataComponentPatch> componentize(ItemStack stack, CompoundTag tag) {
        Optional<HolderLookup.Provider> lookup = DatapackRegistries.lookup();
        if (lookup.isEmpty()) {
            VillagerTradingPlus.LOGGER.error(
                    "Cannot convert the \"nbt\" of a trade item: no datapack registries are loaded. "
                            + "The item is used without it.");
            return Optional.empty();
        }

        CompoundTag old = new CompoundTag();
        old.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        // The count is carried by the stack, not by this patch - a fixed 1 keeps it inside the range
        // ItemStack.CODEC accepts no matter how large the trade's own count is.
        old.putInt("Count", 1);
        old.put("tag", tag);

        Dynamic<Tag> fixed = DataFixers.getDataFixer().update(
                References.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE, old),
                RAW_NBT_DATA_VERSION,
                SharedConstants.getCurrentVersion().dataVersion().version());

        // Components can reference dynamic registries (an enchantment, a potion), so plain NbtOps is
        // not enough to read the fixed stack back.
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, lookup.get());
        return ItemStack.CODEC.parse(ops, fixed.getValue())
                .resultOrPartial(error -> VillagerTradingPlus.LOGGER.error(
                        "Could not read the converted \"nbt\" of a trade item: {}", error))
                .map(ItemStack::getComponentsPatch);
    }

    /**
     * Parses text that may be a plain literal string, a JSON text component given as a string
     * (e.g. {@code "{\"text\":\"Hi\",\"color\":\"gold\"}"}), or a raw JSON object/array component.
     * A bare string that is not JSON is treated as literal text; strict component parsing is only
     * attempted when the string looks like JSON, and any parse failure falls back to a literal.
     */
    private static Component parseText(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            String trimmed = raw.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                Component parsed = decodeText(com.google.gson.JsonParser.parseString(raw));
                if (parsed != null) {
                    return parsed;
                }
            }
            return Component.literal(raw);
        }

        Component parsed = decodeText(element);
        return parsed != null ? parsed : Component.empty();
    }

    /** Text.Serializer is gone; component text goes through its codec now. */
    private static Component decodeText(JsonElement element) {
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null);
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
