package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.Registry;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the dynamic registries of the world being loaded to {@link DatapackRegistries}.
 *
 * <p>This constructor is the one point that has them and runs before any resource reloader:
 * {@code DataPackContents.reload} completes {@code ReloadableRegistries.reload} first, builds the
 * contents second, and starts the reload third. Trade files are therefore parsed with a fully
 * populated enchantment registry available. Vanilla passes the same manager to its own registry-aware
 * loaders here ({@code RecipeManager}, {@code ServerAdvancementLoader}); a mod reloader has no way to
 * be handed it, hence the capture.
 */
@Mixin(ReloadableServerResources.class)
public class DataPackContentsMixin {

    /**
     * The parameter list has to match the constructor exactly, and Mixin only checks that when it
     * applies the injection - a stale signature builds green and then kills the game at startup with
     * "Invalid descriptor". As of 1.21.6 the constructor takes the combined registries plus a
     * wrapper lookup and a list of pending tag loads, where it used to take one immutable manager.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void villagertradingplus$captureDatapackRegistries(
            ReloadableServerRegistries.LoadResult loadingContext,
            FeatureFlagSet enabledFeatures,
            Commands.CommandSelection commandSelection,
            List<Registry.PendingTags<?>> postponedTags,
            PermissionSet functionCompilationPermissions,
            List<DataComponentInitializers.PendingComponents<?>> newComponents,
            CallbackInfo ci) {
        DatapackRegistries.set(loadingContext.layers().compositeAccess());
    }
}