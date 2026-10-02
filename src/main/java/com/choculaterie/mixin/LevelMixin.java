package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
public abstract class LevelMixin {

	@Redirect(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"
		)
	)
	private BlockState ctrlz$captureAndSetBlockState(LevelChunk chunk, BlockPos pos, BlockState newState, int flags) {
		Level self = (Level) (Object) this;
		if (!BlockHistory.isTracking() || !(self instanceof ServerLevel level)) {
			return chunk.setBlockState(pos, newState, flags);
		}

		BlockState oldState = level.getBlockState(pos);
		if (oldState == newState) {
			return chunk.setBlockState(pos, newState, flags);
		}

		CompoundTag oldNbt = BlockHistory.snapshotBlockEntity(level, pos);
		int slot = BlockHistory.reserveBlock(level, pos, oldState, oldNbt, newState);
		BlockState result = chunk.setBlockState(pos, newState, flags);
		if (result != null) {
			CompoundTag newNbt = BlockHistory.snapshotBlockEntity(level, pos);
			BlockHistory.finalizeBlock(slot, newNbt);
		} else {
			BlockHistory.cancelBlock(slot, oldState, oldNbt);
		}
		return result;
	}
}
