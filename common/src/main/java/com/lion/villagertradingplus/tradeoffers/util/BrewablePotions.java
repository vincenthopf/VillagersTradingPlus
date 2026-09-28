package com.lion.villagertradingplus.tradeoffers.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

public final class BrewablePotions {

    private BrewablePotions() {
    }

    public static boolean isBrewable(Level level, Holder<Potion> potion) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        for (RecipeHolder<?> holder : serverLevel.recipeAccess().getRecipes()) {
            if (holder.value() instanceof BrewingRecipe brewing) {
                PotionContents contents = brewing.getOutput().get(DataComponents.POTION_CONTENTS);
                if (contents != null && contents.is(potion)) {
                    return true;
                }
            }
        }
        return false;
    }
}
