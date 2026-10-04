package com.choculaterie.keybind;

import com.mojang.blaze3d.platform.InputConstants;

public final class KeyCombo {
	private int[] keys;

	public KeyCombo(int... keys) {
		set(keys);
	}

	public void set(int... keys) {
		this.keys = keys.clone();
	}

	public int[] keys() {
		return keys;
	}

	public boolean containsKey(int key) {
		for (int k : keys) {
			if (k == key) {
				return true;
			}
		}
		return false;
	}

	public boolean isCompletedBy(int pressedKey, int modifiers) {
		if (keys.length == 0 || !containsKey(pressedKey)) {
			return false;
		}
		for (int k : keys) {
			if (k != pressedKey && !isHeld(k, modifiers)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isHeld(int key, int modifiers) {
		return switch (key) {
			case InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL -> (modifiers & InputConstants.MOD_CONTROL) != 0;
			case InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT -> (modifiers & InputConstants.MOD_SHIFT) != 0;
			case InputConstants.KEY_LALT, InputConstants.KEY_RALT -> (modifiers & InputConstants.MOD_ALT) != 0;
			case InputConstants.KEY_LGUI, InputConstants.KEY_RGUI -> (modifiers & InputConstants.MOD_SUPER) != 0;
			default -> InputConstants.isKeyDown(key);
		};
	}

	public String display() {
		if (keys.length == 0) {
			return "Unbound";
		}
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < keys.length; i++) {
			if (i > 0) {
				text.append(" + ");
			}
			text.append(InputConstants.Type.KEYBOARD.getOrCreate(keys[i]).getDisplayName().getString());
		}
		return text.toString();
	}
}
