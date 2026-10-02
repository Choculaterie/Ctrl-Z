package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.TaggedAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements TaggedAction {

	@Unique
	private long ctrlz$actionId;

	@Unique
	private boolean ctrlz$tickPushed;

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

	@Inject(method = "remove", at = @At("HEAD"))
	private void ctrlz$captureRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (reason.shouldDestroy() && BlockHistory.isTracking() && self.level() instanceof ServerLevel level) {
			BlockHistory.recordEntityRemoved(level, self);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void ctrlz$beginTick(CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		ctrlz$tickPushed = ctrlz$actionId != 0 && !self.level().isClientSide();
		if (ctrlz$tickPushed) {
			BlockHistory.pushAction(ctrlz$actionId, null);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void ctrlz$endTick(CallbackInfo ci) {
		if (ctrlz$tickPushed) {
			BlockHistory.popAction();
		}
	}
}
