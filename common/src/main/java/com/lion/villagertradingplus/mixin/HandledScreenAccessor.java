package com.lion.villagertradingplus.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code HandledScreen.cancelNextRelease}.
 *
 * <p>A click on the catalogue panel is outside the trade screen's background rect, which vanilla
 * reads as "clicked outside the GUI" (slot id -999), and with a non-empty cursor stack that throws
 * the held item on the ground. Cancelling {@code mouseClicked} stops the press half, but the
 * release half repeats the same check in {@code HandledScreen.mouseReleased}, which
 * {@code MerchantScreen} does not override (so it cannot be injected into from a MerchantScreen
 * mixin) and whose flag is private (so it cannot be shadowed from one either).
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("cancelNextRelease")
    void villagertradingplus$setCancelNextRelease(boolean cancelNextRelease);
}
