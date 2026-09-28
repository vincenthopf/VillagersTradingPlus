package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Binds an offer's enchantments to the registry of the world it is being traded in.
 *
 * <p>An enchantment on an item is a {@link Holder}, and the packet codec that writes it looks
 * that entry up <em>by identity</em> in the receiving connection's registry. An entry resolved
 * against some other instance of the enchantment registry is therefore not merely stale, it is
 * unencodable: {@code merchant_offers} fails to serialise and the server drops the player.
 *
 * <p>Item sugar ({@code "enchantments"}, and {@code "nbt"} carrying an enchantment tag) is applied
 * while the trade file is parsed, so the stack a factory holds keeps whichever entries existed then
 * - for the whole life of the factory. Re-resolving here, per created offer, is what keeps that from
 * outliving its registry.
 *
 * <p>Only the sell stack is rebound. The buy side is a {@code ComponentMapPredicate} rather than a
 * stack, and there is no lossless way to rebuild one, so a buy-side enchantment written in JSON is
 * still bound at parse time.
 */
public final class RegistryRebindFactory implements VillagerTrades.ItemListing {

    private final VillagerTrades.ItemListing delegate;

    private RegistryRebindFactory(VillagerTrades.ItemListing delegate) {
        this.delegate = delegate;
    }

    public static VillagerTrades.ItemListing wrap(VillagerTrades.ItemListing delegate) {
        return delegate == null ? null : new RegistryRebindFactory(delegate);
    }

    /** The wrapped factory, so the catalogue can walk past this wrapper to enumerate it. */
    public VillagerTrades.ItemListing delegate() {
        return this.delegate;
    }

    @Override
    public MerchantOffer getOffer(Entity entity, RandomSource random) {
        MerchantOffer offer = this.delegate.getOffer(entity, random);
        if (offer == null) {
            return null;
        }

        Registry<Enchantment> live = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack sell = offer.getResult();
        if (!rebind(sell, DataComponents.ENCHANTMENTS, live)
                & !rebind(sell, DataComponents.STORED_ENCHANTMENTS, live)) {
            return offer;
        }

        return new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(), sell,
                offer.getUses(), offer.getMaxUses(), offer.getXp(),
                offer.getPriceMultiplier(), offer.getDemand());
    }

    /**
     * Re-resolves one enchantment component against {@code live}. An enchantment this world does not
     * have is dropped rather than kept: a missing enchantment costs the trade its shine, an
     * unencodable one costs the player the connection.
     *
     * @return whether anything about the component changed
     */
    private static boolean rebind(ItemStack stack, DataComponentType<ItemEnchantments> type,
                                  Registry<Enchantment> live) {
        ItemEnchantments current = stack.get(type);
        if (current == null || current.isEmpty()) {
            return false;
        }

        ItemEnchantments.Mutable builder =
                new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        boolean changed = false;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : current.entrySet()) {
            Holder<Enchantment> bound = entry.getKey();
            Holder<Enchantment> fresh = resolve(bound, live);
            if (fresh == null) {
                changed = true;
                continue;
            }
            if (fresh != bound) {
                changed = true;
            }
            builder.set(fresh, entry.getIntValue());
        }

        if (changed) {
            stack.set(type, builder.toImmutable());
        }
        return changed;
    }

    private static Holder<Enchantment> resolve(Holder<Enchantment> entry, Registry<Enchantment> live) {
        Optional<ResourceKey<Enchantment>> key = entry.unwrapKey();
        if (key.isEmpty()) {
            return entry;
        }
        return live.get(key.get()).map(reference -> (Holder<Enchantment>) reference).orElse(null);
    }
}
