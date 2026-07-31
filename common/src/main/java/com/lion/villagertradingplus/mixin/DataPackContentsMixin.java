package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.CommandManager;
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
@Mixin(DataPackContents.class)
public class DataPackContentsMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void villagertradingplus$captureDatapackRegistries(
            DynamicRegistryManager.Immutable dynamicRegistryManager,
            FeatureSet enabledFeatures,
            CommandManager.RegistrationEnvironment environment,
            int functionPermissionLevel,
            CallbackInfo ci) {
        DatapackRegistries.set(dynamicRegistryManager);
    }
}