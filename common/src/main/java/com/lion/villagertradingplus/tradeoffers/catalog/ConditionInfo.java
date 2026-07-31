package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/**
 * One condition attached to a catalogued trade: what it requires, and whether it holds right now for
 * the merchant the catalogue was built for.
 *
 * <p>{@code satisfied} is a snapshot. Weather- and time-based conditions flip as the world changes,
 * which is exactly why the catalogue lists conditional trades unconditionally and marks them instead
 * of hiding them.
 */
public record ConditionInfo(Text description, boolean satisfied) {

    public void write(RegistryByteBuf buf) {
        TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, this.description);
        buf.writeBoolean(this.satisfied);
    }

    public static ConditionInfo read(RegistryByteBuf buf) {
        return new ConditionInfo(TextCodecs.REGISTRY_PACKET_CODEC.decode(buf), buf.readBoolean());
    }
}
