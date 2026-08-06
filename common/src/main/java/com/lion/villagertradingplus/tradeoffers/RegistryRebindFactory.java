package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

import java.util.Optional;

/**
 * Binds an offer's enchantments to the registry of the world it is being traded in.
 *
 * <p>An enchantment on an item is a {@link RegistryEntry}, and the packet codec that writes it looks
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
public final class RegistryRebindFactory implements TradeOffers.Factory {

    private final TradeOffers.Factory delegate;

    private RegistryRebindFactory(TradeOffers.Factory delegate) {
        this.delegate = delegate;
    }

    public static TradeOffers.Factory wrap(TradeOffers.Factory delegate) {
        return delegate == null ? null : new RegistryRebindFactory(delegate);
    }

    /** The wrapped factory, so the catalogue can walk past this wrapper to enumerate it. */
    public TradeOffers.Factory delegate() {
        return this.delegate;
    }

    @Override
    public TradeOffer create(Entity entity, Random random) {
        TradeOffer offer = this.delegate.create(entity, random);
        if (offer == null) {
            return null;
        }

        Registry<Enchantment> live = entity.getWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        ItemStack sell = offer.getSellItem();
        if (!rebind(sell, DataComponentTypes.ENCHANTMENTS, live)
                & !rebind(sell, DataComponentTypes.STORED_ENCHANTMENTS, live)) {
            return offer;
        }

        return new TradeOffer(offer.getFirstBuyItem(), offer.getSecondBuyItem(), sell,
                offer.getUses(), offer.getMaxUses(), offer.getMerchantExperience(),
                offer.getPriceMultiplier(), offer.getDemandBonus());
    }

    /**
     * Re-resolves one enchantment component against {@code live}. An enchantment this world does not
     * have is dropped rather than kept: a missing enchantment costs the trade its shine, an
     * unencodable one costs the player the connection.
     *
     * @return whether anything about the component changed
     */
    private static boolean rebind(ItemStack stack, ComponentType<ItemEnchantmentsComponent> type,
                                  Registry<Enchantment> live) {
        ItemEnchantmentsComponent current = stack.get(type);
        if (current == null || current.isEmpty()) {
            return false;
        }

        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        boolean changed = false;

        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : current.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> bound = entry.getKey();
            RegistryEntry<Enchantment> fresh = resolve(bound, live);
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
            stack.set(type, builder.build());
        }
        return changed;
    }

    private static RegistryEntry<Enchantment> resolve(RegistryEntry<Enchantment> entry, Registry<Enchantment> live) {
        Optional<RegistryKey<Enchantment>> key = entry.getKey();
        if (key.isEmpty()) {
            return entry;
        }
        return live.getOptional(key.get()).map(reference -> (RegistryEntry<Enchantment>) reference).orElse(null);
    }
}
