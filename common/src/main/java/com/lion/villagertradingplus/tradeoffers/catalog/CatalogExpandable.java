package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.world.entity.Entity;

/**
 * Implemented by trade factories that can enumerate every offer they are able to produce, so the
 * catalogue can list them instead of guessing by repeated random sampling.
 *
 * <p>Factories that do not implement this are handled by {@link CatalogBuilder#addSampled}, which
 * calls {@code create} once with a fixed seed, correct for every already-deterministic trade type,
 * and a stable representative for the handful that are genuinely unenumerable.
 *
 * <p>Implementations must not mutate world state and must not perform expensive lookups: this runs
 * on the server thread every time a player opens the catalogue. In particular do not run structure
 * searches; see {@code JsonSellStructureMapTradeOffer}.
 */
public interface CatalogExpandable {

    /**
     * Appends one row per variant this factory can produce. Implementations should stop early when
     * {@link CatalogBuilder#isFull()} reports the entry cap has been reached, and should call
     * {@link CatalogBuilder#countSkipped(int)} for anything they then leave out so the panel can say
     * how much it is hiding.
     */
    void expandCatalog(Entity merchant, CatalogBuilder out);
}
