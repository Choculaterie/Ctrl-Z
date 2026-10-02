package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import com.choculaterie.history.TaggedAction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.function.BiConsumer;

@Mixin(LevelTicks.class)
public abstract class LevelTicksMixin {

	@Unique
	private long ctrlz$currentTickId;

	@Inject(method = "schedule", at = @At("HEAD"))
	private void ctrlz$tagSchedule(ScheduledTick<?> tick, CallbackInfo ci) {
		if (BlockHistory.isTracking() && (tick.type() instanceof Block || tick.type() instanceof Fluid)) {
			((TaggedAction) (Object) tick).ctrlz$setActionId(BlockHistory.currentActionId());
		}
	}

	@Redirect(method = "runCollectedTicks", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
	private Object ctrlz$capturePoll(Queue<?> queue) {
		Object entry = queue.poll();
		ctrlz$currentTickId = entry instanceof TaggedAction tagged ? tagged.ctrlz$getActionId() : 0;
		return entry;
	}

	@Redirect(method = "runCollectedTicks", at = @At(value = "INVOKE", target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"))
	private void ctrlz$wrapAccept(BiConsumer<Object, Object> consumer, Object pos, Object type) {
		BlockHistory.pushAction(ctrlz$currentTickId, Cause.REDSTONE_TICK);
		try {
			consumer.accept(pos, type);
		} finally {
			BlockHistory.popAction();
		}
	}
}
