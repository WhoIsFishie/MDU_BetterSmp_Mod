package com.mvdevsunion.mixin;

import com.mvdevsunion.MvDevsUnionBetterSMP;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(RecipeManager.class)
public class DisableEnderEyeRecipeMixin {

	@Inject(
		method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;",
		at = @At("RETURN"),
		cancellable = true
	)
	private <C extends RecipeInput, T extends Recipe<C>> void blockEnderEyeCraft(
			RecipeType<T> type, C input, World world,
			CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
		if (!MvDevsUnionBetterSMP.CONFIG.disableEnd) return;
		cir.getReturnValue().ifPresent(entry -> {
			if (entry.value().getResult(world.getRegistryManager()).isOf(Items.ENDER_EYE)) {
				cir.setReturnValue(Optional.empty());
			}
		});
	}
}
