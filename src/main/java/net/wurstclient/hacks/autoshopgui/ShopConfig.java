/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autoshopgui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.wurstclient.WurstClient;
import net.wurstclient.util.ChatUtils;

/**
 * Configuration for AutoShopGUI, loaded from shopgui.json.
 */
public final class ShopConfig
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private final List<ShopTarget> targets;

	public ShopConfig()
	{
		this.targets = new ArrayList<>();
	}

	public static ShopConfig load()
	{
		Path configPath = getConfigPath();

		if(!Files.exists(configPath))
		{
			// Create default config
			ShopConfig defaultConfig = createDefault();
			defaultConfig.save();
			return defaultConfig;
		}

		try
		{
			String json = Files.readString(configPath);
			return fromJson(json);

		}catch(IOException e)
		{
			System.err
				.println("Failed to load ShopGUI config: " + e.getMessage());
			return null;
		}
	}

	public void save()
	{
		Path configPath = getConfigPath();

		try
		{
			// Create parent directories if they don't exist
			Files.createDirectories(configPath.getParent());

			// Write JSON to file
			String json = toJson();
			Files.writeString(configPath, json);

		}catch(IOException e)
		{
			System.err
				.println("Failed to save ShopGUI config: " + e.getMessage());
		}
	}

	private static ShopConfig fromJson(String jsonString)
	{
		ShopConfig config = new ShopConfig();
		JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();

		if(root.has("targets"))
		{
			JsonArray targetsArray = root.getAsJsonArray("targets");
			for(int i = 0; i < targetsArray.size(); i++)
			{
				JsonObject targetJson = targetsArray.get(i).getAsJsonObject();
				ShopTarget target = ShopTarget.fromJson(targetJson);
				if(target.isEnabled())
					config.addTarget(target);
			}
		}

		return config;
	}

	private String toJson()
	{
		JsonObject root = new JsonObject();
		JsonArray targetsArray = new JsonArray();

		for(ShopTarget target : targets)
			targetsArray.add(target.toJson());

		root.add("targets", targetsArray);

		// Pretty print with Gson
		return new com.google.gson.GsonBuilder().setPrettyPrinting().create()
			.toJson(root);
	}

	private static ShopConfig createDefault()
	{
		ShopConfig config = new ShopConfig();

		// Add example target
		List<Integer> exampleNav = new ArrayList<>();
		exampleNav.add(10); // Example: click slot 10 for category
		exampleNav.add(15); // Example: click slot 15 for item
		exampleNav.add(11); // Example: click slot 11 to confirm

		ShopTarget example = new ShopTarget("Shop Keeper", "Diamond Sword",
			"buy", 1000, 1, exampleNav, false);

		config.addTarget(example);

		return config;
	}

	private static Path getConfigPath()
	{
		Path wurstFolder = WURST.getWurstFolder();

		Path configPath = wurstFolder.resolve("shopgui.json");

		ChatUtils.message("ShopGUI config path: " + configPath.toString());

		return configPath;
	}

	public void addTarget(ShopTarget target)
	{
		targets.add(target);
	}

	public List<ShopTarget> getTargets()
	{
		return targets;
	}
}
