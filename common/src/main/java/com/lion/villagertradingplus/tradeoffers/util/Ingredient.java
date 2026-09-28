package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * A tag-or-item holder for the input side of a trade. When built from a {@code "tag"} it resolves
 * to a random member at {@link #resolve} time (called inside {@code Factory.create}); when built
 * from an {@code "item"} it is a fixed stack (with any NBT sugar baked in).
 *
 * <p>Vanilla {@link net.minecraft.world.item.trading.MerchantOffer} matches one concrete {@link ItemStack}, so a
 * tag input is realised as a single random member per generated offer, not an "any-of" match.
 */
public final class Ingredient {

    private final ItemStack fixed;          // non-null when built from "item"
    private final TagKey<net.minecraft.world.item.Item> tag; // non-null when built from "tag"
    private final int count;
    private final JsonObject componentSugar; // reapplied to each tag pick

    private Ingredient(ItemStack fixed, TagKey<net.minecraft.world.item.Item> tag, int count, JsonObject componentSugar) {
        this.fixed = fixed;
        this.tag = tag;
        this.count = count;
        this.componentSugar = componentSugar;
    }

    public static Ingredient fromJson(JsonObject json) {
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        if (json.has("tag")) {
            TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM,
                    ResourceLocation.tryParse(json.get("tag").getAsString()));
            return new Ingredient(null, tag, count, json);
        }
        return new Ingredient(ItemStackSerializer.fromJson(json), null, count, null);
    }

    /** Whether this ingredient resolves from a tag (so it may vary per generated offer). */
    public boolean isTag() {
        return tag != null;
    }

    /** Resolves a concrete stack. Fixed ingredients copy; tag ingredients pick a random member. */
    public ItemStack resolve(RandomSource random) {
        if (tag == null) {
            return fixed.copy();
        }

        Optional<HolderSet.Named<net.minecraft.world.item.Item>> entries = BuiltInRegistries.ITEM.get(tag);
        if (entries.isEmpty() || entries.get().size() == 0) {
            return ItemStack.EMPTY;
        }

        HolderSet.Named<net.minecraft.world.item.Item> list = entries.get();
        return build(list.get(random.nextInt(list.size())));
    }

    /**
     * Every stack this ingredient could resolve to: one entry for a fixed ingredient, one per tag
     * member otherwise. Used by the trade catalogue, which lists all the variants a trade can take
     * rather than sampling one.
     */
    public List<ItemStack> resolveAll() {
        if (tag == null) {
            return List.of(fixed.copy());
        }

        Optional<HolderSet.Named<net.minecraft.world.item.Item>> entries = BuiltInRegistries.ITEM.get(tag);
        if (entries.isEmpty()) {
            return List.of();
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (Holder<net.minecraft.world.item.Item> entry : entries.get()) {
            stacks.add(build(entry));
        }
        return stacks;
    }

    private ItemStack build(Holder<net.minecraft.world.item.Item> entry) {
        ItemStack stack = new ItemStack(entry.value(), count);
        if (componentSugar != null) {
            ItemStackSerializer.applyComponents(stack, componentSugar);
        }
        return stack;
    }
}
