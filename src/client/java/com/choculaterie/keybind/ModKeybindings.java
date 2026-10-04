package com.choculaterie.keybind;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

public final class ModKeybindings {
	public static KeyMapping OPEN_MENU_KEY_BINDING;

	public static void initialize() {
		OPEN_MENU_KEY_BINDING = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.ctrl-z.open_menu",
				InputConstants.KEY_Z,
				KeyMapping.Category.MISC
			)
		);
	}

	private ModKeybindings() {}
}
