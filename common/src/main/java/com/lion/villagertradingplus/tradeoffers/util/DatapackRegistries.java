package com.lion.villagertradingplus.tradeoffers.util;

import java.lang.ref.WeakReference;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

/**
 * Access to the datapack (dynamic) registries while trade files are being parsed.
 *
 * <p>Enchantments - and everything else a datapack can define - moved into dynamic registries in
 * 1.21: an {@link net.minecraft.resources.Identifier} on its own no longer names one, the registry loaded
 * for this world has to be asked. Resource reloaders get no registry handed to them, but they do not
 * need one passed either: {@code DataPackContents.reload} runs {@code ReloadableRegistries.reload}
 * first and only builds the reloaders afterwards, so every dynamic registry is fully populated by the
 * time a trade file is read. The {@code DataPackContents} constructor is where that manager is
 * captured, see {@code DataPackContentsMixin}.
 *
 * <p>Held weakly on purpose: the manager owns every datapack registry of a world, and a static strong
 * reference would keep the whole set alive after the player has left it.
 */
public final class DatapackRegistries {

    private static volatile WeakReference<RegistryAccess> current = new WeakReference<>(null);

    private DatapackRegistries() {
    }

    public static void set(RegistryAccess registryManager) {
        current = new WeakReference<>(registryManager);
    }

    /**
     * {@return the requested dynamic registry, or empty when no datapack load is in progress}
     *
     * <p>Empty is not an error case that can be recovered from here - it means something is parsing
     * trades outside a datapack load, which nothing in this mod does. Callers report it and carry on
     * without the feature rather than losing the trade.
     */
    public static <T> Optional<Registry<T>> registry(ResourceKey<? extends Registry<? extends T>> key) {
        RegistryAccess manager = current.get();
        return manager == null ? Optional.empty() : manager.lookup(key);
    }

    /**
     * {@return a lookup over all loaded registries, for codecs that need one}
     *
     * <p>{@link RegistryAccess} is itself a {@link HolderLookup.Provider}, so this is
     * the same object as {@link #registry} reads - handed out whole for
     * {@link net.minecraft.resources.RegistryOps}.
     */
    public static Optional<RegistryAccess> access() {
        return Optional.ofNullable(current.get());
    }

    public static Optional<HolderLookup.Provider> lookup() {
        return Optional.ofNullable(current.get());
    }
}
