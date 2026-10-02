package com.choculaterie.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public final class CauseSettings {
	public enum Category {
		BLOCKS,
		ENTITIES,
		INVENTORY
	}

	private static final String CONFIG_FILE = "ctrl-z-causes.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Map<Category, EnumMap<Cause, Boolean>> enabled = new EnumMap<>(Category.class);

	static {
		for (Category category : Category.values()) {
			EnumMap<Cause, Boolean> causes = new EnumMap<>(Cause.class);
			for (Cause cause : Cause.values()) {
				causes.put(cause, true);
			}
			enabled.put(category, causes);
		}
		load();
	}

	private CauseSettings() {}

	public static boolean isEnabled(Category category, Cause cause) {
		return enabled.get(category).get(cause);
	}

	public static void setEnabled(Category category, Cause cause, boolean value) {
		enabled.get(category).put(cause, value);
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
			if (json == null) {
				return;
			}
			for (Category category : Category.values()) {
				if (!json.has(category.name())) {
					continue;
				}
				JsonObject categoryJson = json.getAsJsonObject(category.name());
				EnumMap<Cause, Boolean> causes = enabled.get(category);
				for (Cause cause : Cause.values()) {
					if (categoryJson.has(cause.name())) {
						causes.put(cause, categoryJson.get(cause.name()).getAsBoolean());
					}
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
			for (Category category : Category.values()) {
				JsonObject categoryJson = new JsonObject();
				for (Map.Entry<Cause, Boolean> entry : enabled.get(category).entrySet()) {
					categoryJson.addProperty(entry.getKey().name(), entry.getValue());
				}
				json.add(category.name(), categoryJson);
			}
			try (FileWriter writer = new FileWriter(file)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException ignored) {
		}
	}
}
