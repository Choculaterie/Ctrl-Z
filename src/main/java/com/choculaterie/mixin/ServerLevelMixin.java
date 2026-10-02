package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.TaggedAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

	@Inject(method = "addFreshEntity", at = @At("RETURN"))
	private void ctrlz$captureAdd(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() || !BlockHistory.isTracking()) {
			return;
		}
		ServerLevel self = (ServerLevel) (Object) this;
		BlockHistory.recordEntityAdded(self, entity);
	}

	@Redirect(method = "blockEvent", at = @At(value = "NEW", target = "Lnet/minecraft/world/level/BlockEventData;"))
	private BlockEventData ctrlz$tagBlockEvent(BlockPos pos, Block block, int paramA, int paramB) {
		BlockEventData event = new BlockEventData(pos, block, paramA, paramB);
		if (BlockHistory.isTracking()) {
			((TaggedAction) (Object) event).ctrlz$setActionId(BlockHistory.currentActionId());
		}
		return event;
	}

	@Inject(method = "doBlockEvent", at = @At("HEAD"))
	private void ctrlz$beginBlockEvent(BlockEventData eventData, CallbackInfoReturnable<Boolean> cir) {
		BlockHistory.pushAction(((TaggedAction) (Object) eventData).ctrlz$getActionId(), null);
	}

	@Inject(method = "doBlockEvent", at = @At("RETURN"))
	private void ctrlz$endBlockEvent(BlockEventData eventData, CallbackInfoReturnable<Boolean> cir) {
		BlockHistory.popAction();
	}
}
