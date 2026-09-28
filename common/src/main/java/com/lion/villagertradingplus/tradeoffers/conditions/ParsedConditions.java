package com.lion.villagertradingplus.tradeoffers.conditions;

import com.lion.villagertradingplus.tradeoffers.catalog.ConditionInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * The parsed {@code "conditions"} block: the combined predicate plus, alongside it, a readable
 * description of each individual condition.
 *
 * <p>Implements {@link TradeCondition} itself so it drops straight into
 * {@link com.lion.villagertradingplus.tradeoffers.ConditionalTradeFactory} with the same semantics as
 * before. The per-condition breakdown exists so the trade catalogue can show <em>why</em> a trade is
 * gated and whether the gate is currently open, instead of the trade simply vanishing.
 */
public record ParsedConditions(List<Entry> entries, boolean orLogic) implements TradeCondition {

    public record Entry(Component description, TradeCondition condition) {
    }

    @Override
    public boolean test(Entity villager) {
        if (this.orLogic) {
            for (Entry entry : this.entries) {
                if (entry.condition().test(villager)) {
                    return true;
                }
            }
            // An empty OR block gates nothing, matching the AND case below.
            return this.entries.isEmpty();
        }

        for (Entry entry : this.entries) {
            if (!entry.condition().test(villager)) {
                return false;
            }
        }
        return true;
    }

    /** Evaluates every condition individually against {@code merchant} for catalogue display. */
    public List<ConditionInfo> describe(Entity merchant) {
        List<ConditionInfo> infos = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            infos.add(new ConditionInfo(entry.description(), entry.condition().test(merchant)));
        }
        return List.copyOf(infos);
    }
}
