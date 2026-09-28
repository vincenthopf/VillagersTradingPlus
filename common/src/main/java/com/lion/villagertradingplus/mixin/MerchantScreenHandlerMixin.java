package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.gui.TradeGuiActions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes our custom trade-screen button clicks (sent via the vanilla ButtonClick packet) to the
 * server-side {@link TradeGuiActions}. Runs only server-side, where the handler's merchant is the
 * real entity; the catalog's {@code SimpleMerchant} (not a {@link AbstractVillager}) is ignored.
 *
 * <p>Targets {@link AbstractContainerMenu} rather than {@link MerchantMenu} because
 * {@code onButtonClick} is only declared on the superclass, and {@code @Inject}, unlike
 * {@code @Shadow}, can only bind to a method physically present in the target class. The
 * {@code instanceof} guard below narrows it back to merchant screens.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MerchantScreenHandlerMixin {

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$onButtonClick(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof MerchantMenu handler)) {
            return;
        }
        Merchant merchant = ((MerchantScreenHandlerAccessor) handler).getMerchant();
        if (!(merchant instanceof AbstractVillager entity) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (TradeGuiActions.handle(serverPlayer, entity, id)) {
            cir.setReturnValue(true);
        }
    }
}
