package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import com.choculaterie.history.TaggedAction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin implements TaggedAction {

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

	@Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"))
	private void ctrlz$tagOnSpawn(EntityType<?> entityType, Level level, CallbackInfo ci) {
		if (!level.isClientSide()) {
			ctrlz$actionId = BlockHistory.currentActionId();
		}
	}

	@Inject(method = "explode", at = @At("HEAD"))
	private void ctrlz$begin(CallbackInfo ci) {
		BlockHistory.pushAction(ctrlz$actionId, Cause.EXPLOSION);
	}

	@Inject(method = "explode", at = @At("RETURN"))
	private void ctrlz$end(CallbackInfo ci) {
		BlockHistory.popAction();
	}
}
