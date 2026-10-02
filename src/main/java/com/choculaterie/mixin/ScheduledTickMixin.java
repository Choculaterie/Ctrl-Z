package com.choculaterie.mixin;

import com.choculaterie.history.TaggedAction;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ScheduledTick.class)
public abstract class ScheduledTickMixin implements TaggedAction {

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
}
