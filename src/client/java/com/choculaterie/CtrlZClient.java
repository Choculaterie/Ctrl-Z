package com.choculaterie;

import com.choculaterie.gui.CtrlZScreen;
import com.choculaterie.keybind.KeyCombo;
import com.choculaterie.keybind.KeybindSettings;
import com.choculaterie.keybind.ModKeybindings;
import com.choculaterie.network.UndoRedoPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

public class CtrlZClient implements ClientModInitializer {
	private static int suppressedMenuClicks = 0;

	@Override
	public void onInitializeClient() {
		ModKeybindings.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(CtrlZClient::onClientTick);
	}

	private static void onClientTick(Minecraft client) {
		while (ModKeybindings.OPEN_MENU_KEY_BINDING.consumeClick()) {
			if (suppressedMenuClicks > 0) {
				suppressedMenuClicks--;
			} else {
				toggleScreen(client);
			}
		}
		suppressedMenuClicks = 0;
	}

	public static boolean onKeyPress(int action, KeyEvent event) {
		if (action == InputConstants.RELEASE) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null || client.player == null) {
			return false;
		}
		KeyCombo combo = completedCombo(event.key(), event.modifiers());
		if (combo == null) {
			return false;
		}
		if (action == InputConstants.PRESS && canUndoRedo(client)) {
			ClientPlayNetworking.send(new UndoRedoPayload(combo == KeybindSettings.REDO));
		}
		if (KeybindSettings.isBlockOtherKeybinds()) {
			return true;
		}
		if (action == InputConstants.PRESS
				&& ModKeybindings.OPEN_MENU_KEY_BINDING.matches(InputConstants.Type.KEYBOARD.getOrCreate(event.key()))) {
			suppressedMenuClicks++;
		}
		return false;
	}

	private static KeyCombo completedCombo(int pressedKey, int modifiers) {
		boolean undo = KeybindSettings.UNDO.isCompletedBy(pressedKey, modifiers);
		boolean redo = KeybindSettings.REDO.isCompletedBy(pressedKey, modifiers);
		if (undo && redo) {
			return KeybindSettings.REDO.keys().length > KeybindSettings.UNDO.keys().length
				? KeybindSettings.REDO : KeybindSettings.UNDO;
		}
		if (undo) {
			return KeybindSettings.UNDO;
		}
		return redo ? KeybindSettings.REDO : null;
	}

	private static boolean canUndoRedo(Minecraft client) {
		if (KeybindSettings.isCreativeOnly() && !client.player.isCreative()) {
			return false;
		}
		return ClientPlayNetworking.canSend(UndoRedoPayload.TYPE);
	}

	private static void toggleScreen(Minecraft client) {
		if (client.gui.screen() instanceof CtrlZScreen) {
			client.gui.setScreen(null);
		} else {
			client.gui.setScreen(new CtrlZScreen());
		}
	}
}
