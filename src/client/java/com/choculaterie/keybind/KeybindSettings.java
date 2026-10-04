package com.choculaterie.keybind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public final class KeybindSettings {
	private static final String CONFIG_FILE = "ctrl-z-keybinds.json";
	private static final String KEY_FORMAT = "sdl";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final KeyCombo UNDO = new KeyCombo(InputConstants.KEY_LCONTROL, InputConstants.KEY_Z);
	public static final KeyCombo REDO = new KeyCombo(InputConstants.KEY_LCONTROL, InputConstants.KEY_Y);

	private static boolean creativeOnly = false;
	private static boolean blockOtherKeybinds = true;

	static {
		load();
	}

	private KeybindSettings() {}

	public static boolean isCreativeOnly() {
		return creativeOnly;
	}

	public static void setCreativeOnly(boolean creativeOnly) {
		KeybindSettings.creativeOnly = creativeOnly;
		save();
	}

	public static boolean isBlockOtherKeybinds() {
		return blockOtherKeybinds;
	}

	public static void setBlockOtherKeybinds(boolean blockOtherKeybinds) {
		KeybindSettings.blockOtherKeybinds = blockOtherKeybinds;
		save();
	}

	public static void save() {
		try {
			File file = getConfigFile();
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			JsonObject json = new JsonObject();
			json.addProperty("keyFormat", KEY_FORMAT);
			json.add("undo", toJson(UNDO));
			json.add("redo", toJson(REDO));
			json.addProperty("creativeOnly", creativeOnly);
			json.addProperty("blockOtherKeybinds", blockOtherKeybinds);
			try (FileWriter writer = new FileWriter(file)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException ignored) {
		}
	}

	private static void load() {
		File file = getConfigFile();
		if (!file.exists()) {
			return;
		}
		boolean legacy = false;
		try (FileReader reader = new FileReader(file)) {
			JsonObject json = GSON.fromJson(reader, JsonObject.class);
			if (json == null) {
				return;
			}
			legacy = !json.has("keyFormat") || !KEY_FORMAT.equals(json.get("keyFormat").getAsString());
			if (json.has("undo")) {
				fromJson(json.get("undo"), UNDO, legacy);
			}
			if (json.has("redo")) {
				fromJson(json.get("redo"), REDO, legacy);
			}
			if (json.has("creativeOnly")) {
				creativeOnly = json.get("creativeOnly").getAsBoolean();
			}
			if (json.has("blockOtherKeybinds")) {
				blockOtherKeybinds = json.get("blockOtherKeybinds").getAsBoolean();
			}
		} catch (IOException | RuntimeException ignored) {
		}
		if (legacy) {
			save();
		}
	}

	private static JsonArray toJson(KeyCombo combo) {
		JsonArray array = new JsonArray();
		for (int key : combo.keys()) {
			array.add(key);
		}
		return array;
	}

	private static void fromJson(com.google.gson.JsonElement element, KeyCombo combo, boolean legacy) {
		java.util.List<Integer> keys = new java.util.ArrayList<>();
		if (element.isJsonArray()) {
			for (com.google.gson.JsonElement key : element.getAsJsonArray()) {
				keys.add(legacy ? LegacyGlfwKeys.toKey(key.getAsInt()) : key.getAsInt());
			}
		} else if (element.isJsonObject()) {
			JsonObject old = element.getAsJsonObject();
			if (!old.has("key")) {
				return;
			}
			if (old.has("ctrl") && old.get("ctrl").getAsBoolean()) {
				keys.add(InputConstants.KEY_LCONTROL);
			}
			if (old.has("shift") && old.get("shift").getAsBoolean()) {
				keys.add(InputConstants.KEY_LSHIFT);
			}
			if (old.has("alt") && old.get("alt").getAsBoolean()) {
				keys.add(InputConstants.KEY_LALT);
			}
			keys.add(LegacyGlfwKeys.toKey(old.get("key").getAsInt()));
		}
		boolean requestedKeys = !keys.isEmpty();
		keys.removeIf(key -> key < 0);
		if (requestedKeys && keys.isEmpty()) {
			return;
		}
		combo.set(keys.stream().mapToInt(Integer::intValue).toArray());
	}

	private static File getConfigFile() {
		File configDir = FabricLoader.getInstance().getConfigDir().toFile();
		return new File(configDir, CONFIG_FILE);
	}
}
