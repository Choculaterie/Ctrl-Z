package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

	@Inject(method = "explode", at = @At("HEAD"))
	private void ctrlz$begin(CallbackInfoReturnable<Integer> cir) {
		BlockHistory.begin(Cause.EXPLOSION);
	}

	@Inject(method = "explode", at = @At("RETURN"))
	private void ctrlz$end(CallbackInfoReturnable<Integer> cir) {
		BlockHistory.end();
	}
}
