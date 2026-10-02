package com.choculaterie;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.network.ClearHistoryPayload;
import com.choculaterie.network.UndoRedoPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CtrlZ implements ModInitializer {
	public static final String MOD_ID = "ctrl-z";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(UndoRedoPayload.TYPE, UndoRedoPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClearHistoryPayload.TYPE, ClearHistoryPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UndoRedoPayload.TYPE, (payload, context) -> {
			if (payload.redo()) {
				BlockHistory.redo(context.player());
			} else {
				BlockHistory.undo(context.player());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ClearHistoryPayload.TYPE, (payload, context) -> {
			BlockHistory.clear();
			context.player().sendSystemMessage(Component.literal("Ctrl-Z history cleared"), true);
		});
	}
}
