package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * One condition attached to a catalogued trade: what it requires, and whether it holds right now for
 * the merchant the catalogue was built for.
 *
 * <p>{@code satisfied} is a snapshot. Weather- and time-based conditions flip as the world changes,
 * which is exactly why the catalogue lists conditional trades unconditionally and marks them instead
 * of hiding them.
 */
public record ConditionInfo(Component description, boolean satisfied) {

    public void write(RegistryFriendlyByteBuf buf) {
        ComponentSerialization.STREAM_CODEC.encode(buf, this.description);
        buf.writeBoolean(this.satisfied);
    }

    public static ConditionInfo read(RegistryFriendlyByteBuf buf) {
        return new ConditionInfo(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readBoolean());
    }
}
