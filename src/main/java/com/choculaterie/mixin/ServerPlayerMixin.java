package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

	@Unique
	private ItemStack ctrlz$dropBefore;

	@Inject(method = "drop(Z)V", at = @At("HEAD"))
	private void ctrlz$beginDrop(boolean all, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		BlockHistory.begin(Cause.DROP_ITEM);
		BlockHistory.markActor(self);
		ctrlz$dropBefore = self.getInventory().getSelectedItem().copy();
	}

	@Inject(method = "drop(Z)V", at = @At("RETURN"))
	private void ctrlz$endDrop(boolean all, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (ctrlz$dropBefore != null) {
			BlockHistory.recordHeldItemChange(self, ctrlz$dropBefore, self.getInventory().getSelectedItem());
			ctrlz$dropBefore = null;
		}
		BlockHistory.end();
	}
}
