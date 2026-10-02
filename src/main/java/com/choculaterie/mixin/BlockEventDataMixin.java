package com.choculaterie.mixin;

import com.choculaterie.history.TaggedAction;
import net.minecraft.world.level.BlockEventData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEventData.class)
public abstract class BlockEventDataMixin implements TaggedAction {

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
