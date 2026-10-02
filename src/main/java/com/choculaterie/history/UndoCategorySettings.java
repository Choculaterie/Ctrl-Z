package com.choculaterie.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public final class UndoCategorySettings {
	private static final String CONFIG_FILE = "ctrl-z-categories.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static boolean trackBlocks = true;
	private static boolean trackEntities = true;
	private static boolean trackInventory = true;

	static {
		load();
	}

	private UndoCategorySettings() {}

	public static boolean isTrackBlocks() {
		return trackBlocks;
	}

	public static void setTrackBlocks(boolean value) {
		trackBlocks = value;
		save();
	}

	public static boolean isTrackEntities() {
		return trackEntities;
	}

	public static void setTrackEntities(boolean value) {
		trackEntities = value;
		save();
	}

	public static boolean isTrackInventory() {
		return trackInventory;
	}

	public static void setTrackInventory(boolean value) {
		trackInventory = value;
		save();
	}

	private static File getConfigFile() {
		File configDir = FabricLoader.getInstance().getConfigDir().toFile();
		return new File(configDir, CONFIG_FILE);
	}

	private static void load() {
		File file = getConfigFile();
		if (!file.exists()) {
			return;
		}
		try (FileReader reader = new FileReader(file)) {
			JsonObject json = GSON.fromJson(reader, JsonObject.class);
			if (json != null) {
				if (json.has("trackBlocks")) {
					trackBlocks = json.get("trackBlocks").getAsBoolean();
				}
				if (json.has("trackEntities")) {
					trackEntities = json.get("trackEntities").getAsBoolean();
				}
				if (json.has("trackInventory")) {
					trackInventory = json.get("trackInventory").getAsBoolean();
				}
			}
		} catch (IOException | RuntimeException ignored) {
		}
	}

	private static void save() {
		try {
			File file = getConfigFile();
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			JsonObject json = new JsonObject();
			json.addProperty("trackBlocks", trackBlocks);
			json.addProperty("trackEntities", trackEntities);
			json.addProperty("trackInventory", trackInventory);
			try (FileWriter writer = new FileWriter(file)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException ignored) {
		}
	}
}
