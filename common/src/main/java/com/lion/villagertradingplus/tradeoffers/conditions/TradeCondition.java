package com.lion.villagertradingplus.tradeoffers.conditions;

import net.minecraft.entity.Entity;

/**
 * A predicate evaluated when a villager's trade offers are generated (on level-up). The villager
 * {@link Entity} gives access to its {@code World} (biome, dimension, weather, time, moon phase) and
 * {@code getBlockPos()} / job-site memory.
 *
 * <p>Because offers are frozen once generated, world-state conditions (weather, time, moon phase)
 * are snapshots taken at generation time; location conditions (biome, dimension, job site) are
 * effectively static and reliable.
 */
@FunctionalInterface
public interface TradeCondition {
    boolean test(Entity villager);
}
