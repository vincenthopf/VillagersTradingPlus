package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/**
 * One condition attached to a catalogued trade: what it requires, and whether it holds right now for
 * the merchant the catalogue was built for.
 *
 * <p>{@code satisfied} is a snapshot. Weather- and time-based conditions flip as the world changes,
 * which is exactly why the catalogue lists conditional trades unconditionally and marks them instead
 * of hiding them.
 */
public record ConditionInfo(Text description, boolean satisfied) {

    public void write(PacketByteBuf buf) {
        buf.writeText(this.description);
        buf.writeBoolean(this.satisfied);
    }

    public static ConditionInfo read(PacketByteBuf buf) {
        return new ConditionInfo(buf.readText(), buf.readBoolean());
    }
}
