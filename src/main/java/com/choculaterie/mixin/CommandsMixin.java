package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {

	@Unique
	private boolean ctrlz$tracking;

	@Inject(method = "performCommand", at = @At("HEAD"))
	private void ctrlz$begin(ParseResults<CommandSourceStack> command, String commandString, CallbackInfo ci) {
		ServerPlayer player = command.getContext().getSource().getPlayer();
		ctrlz$tracking = player != null;
		if (ctrlz$tracking) {
			BlockHistory.begin(Cause.COMMAND);
		}
	}

	@Inject(method = "performCommand", at = @At("RETURN"))
	private void ctrlz$end(ParseResults<CommandSourceStack> command, String commandString, CallbackInfo ci) {
		if (ctrlz$tracking) {
			BlockHistory.end();
			ctrlz$tracking = false;
		}
	}
}
