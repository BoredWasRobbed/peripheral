package net.bored.mixin;

import net.bored.effect.OtherBaseTidy;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notices blocks players place, so the other base can tidy away anything put down near it. */
@Mixin(BlockItem.class)
public class BlockItemMixin {
	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
	private void peripheral$placed(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (cir.getReturnValue().isAccepted() && context.getPlayer() instanceof ServerPlayerEntity
				&& context.getWorld() instanceof ServerWorld world) {
			OtherBaseTidy.onPlaced(world, context.getBlockPos(), world.getBlockState(context.getBlockPos()));
		}
	}
}
