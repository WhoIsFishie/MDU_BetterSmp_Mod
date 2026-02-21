package com.mvdevsunion.mixin;

import com.mvdevsunion.MvDevsUnionBetterSMP;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(RecipeManager.class)
public class DisableEnderEyeRecipeMixin {

	@Inject(
		method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
		at = @At("RETURN"),
		cancellable = true,
		require = 0
	)
	private <C extends RecipeInput, T extends Recipe<C>> void blockEnderEyeCraft(
			RecipeType<T> type, C input, Level world,
			CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
		if (!MvDevsUnionBetterSMP.CONFIG.disableEnd) return;
		cir.getReturnValue().ifPresent(holder -> {
			if (holder.id().toString().equals("minecraft:ender_eye")) {
				cir.setReturnValue(Optional.empty());
			}
		});
	}
}
