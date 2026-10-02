package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import com.choculaterie.history.TaggedAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Unique
	private CompoundTag ctrlz$beforeHurt;

	@Unique
	private ItemStack ctrlz$beforeUse;

	@Unique
	private InteractionHand ctrlz$useHand;

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void ctrlz$beginHurt(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayer || BlockHistory.isTracking()) {
			BlockHistory.begin(Cause.ENTITY_HURT);
			ctrlz$beforeHurt = BlockHistory.snapshotEntity(self);
		}
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void ctrlz$endHurt(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (ctrlz$beforeHurt != null) {
			if (!self.isRemoved()) {
				BlockHistory.recordEntityModified(level, self.getUUID(), ctrlz$beforeHurt, BlockHistory.snapshotEntity(self));
				if (!(self instanceof ServerPlayer) && self.isDeadOrDying()) {
					((TaggedAction) self).ctrlz$setActionId(BlockHistory.currentActionId());
				}
			}
			ctrlz$beforeHurt = null;
			BlockHistory.end();
		}
	}

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void ctrlz$beginUse(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayer player && !player.level().isClientSide()) {
			ctrlz$useHand = self.getUsedItemHand();
			ctrlz$beforeUse = self.getItemInHand(ctrlz$useHand).copy();
			BlockHistory.begin(Cause.USE_ITEM);
			BlockHistory.markActor(player);
		} else {
			ctrlz$beforeUse = null;
		}
	}

	@Inject(method = "completeUsingItem", at = @At("RETURN"))
	private void ctrlz$endUse(CallbackInfo ci) {
		if (ctrlz$beforeUse == null) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		ServerPlayer player = (ServerPlayer) self;
		BlockHistory.recordHeldItemChange(player, ctrlz$beforeUse, self.getItemInHand(ctrlz$useHand));
		ctrlz$beforeUse = null;
		BlockHistory.end();
	}
}
