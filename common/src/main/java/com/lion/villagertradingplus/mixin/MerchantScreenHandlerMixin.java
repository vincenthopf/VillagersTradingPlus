package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.gui.TradeGuiActions;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes our custom trade-screen button clicks (sent via the vanilla ButtonClick packet) to the
 * server-side {@link TradeGuiActions}. Runs only server-side, where the handler's merchant is the
 * real entity; the catalog's {@code SimpleMerchant} (not a {@link MerchantEntity}) is ignored.
 *
 * <p>Targets {@link ScreenHandler} rather than {@link MerchantScreenHandler} because
 * {@code onButtonClick} is only declared on the superclass, and {@code @Inject} — unlike
 * {@code @Shadow} — can only bind to a method physically present in the target class. The
 * {@code instanceof} guard below narrows it back to merchant screens.
 */
@Mixin(ScreenHandler.class)
public abstract class MerchantScreenHandlerMixin {

    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$onButtonClick(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof MerchantScreenHandler handler)) {
            return;
        }
        Merchant merchant = ((MerchantScreenHandlerAccessor) handler).getMerchant();
        if (!(merchant instanceof MerchantEntity entity) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (TradeGuiActions.handle(serverPlayer, entity, id)) {
            cir.setReturnValue(true);
        }
    }
}
