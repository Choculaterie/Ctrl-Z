package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import com.choculaterie.history.TaggedAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin implements TaggedAction {

	@Unique
	private long ctrlz$actionId;

	@Override
	public long ctrlz$getActionId() {
		return ctrlz$actionId;
	}

	@Override
	public void ctrlz$setActionId(long id) {
		ctrlz$actionId = id;
	}

	@Inject(method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;ZZ)V", at = @At("RETURN"))
	private void ctrlz$tagOnCreate(BlockPos worldPosition, BlockState blockState, BlockState movedState, Direction direction, boolean extending, boolean isSourcePiston, CallbackInfo ci) {
		ctrlz$actionId = BlockHistory.currentActionId();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private static void ctrlz$begin(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity entity, CallbackInfo ci) {
		if (!level.isClientSide()) {
			BlockHistory.pushAction(((TaggedAction) entity).ctrlz$getActionId(), Cause.PISTON);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private static void ctrlz$end(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity entity, CallbackInfo ci) {
		if (!level.isClientSide()) {
			BlockHistory.popAction();
		}
	}
}
