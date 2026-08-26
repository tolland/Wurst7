/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui.Component;
import net.wurstclient.clickgui.components.BlockComponent;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.text.WText;

public final class BlockSetting extends Setting
{
	private String blockName = "";
	private final String defaultName;
	private final boolean allowAir;
	private final Predicate<Block> filter;

	public BlockSetting(String name, WText description, String blockName,
		boolean allowAir)
	{
		this(name, description, blockName, allowAir, block -> true);
	}

	public BlockSetting(String name, WText description, String blockName,
		boolean allowAir, Predicate<Block> filter)
	{
		super(name, description);
		
		this.allowAir = allowAir;
		this.filter = Objects.requireNonNull(filter);

		Block block = BlockUtils.getBlockFromNameOrID(blockName);
		Objects.requireNonNull(block);
		if(!isAllowed(block))
			throw new IllegalArgumentException(
				"Block \"" + blockName + "\" is not allowed");

		this.blockName = BlockUtils.getName(block);
		
		defaultName = this.blockName;
	}
	
	public BlockSetting(String name, String descriptionKey, String blockName,
		boolean allowAir)
	{
		this(name, WText.translated(descriptionKey), blockName, allowAir);
	}
	
	public BlockSetting(String name, String descriptionKey, String blockName,
		boolean allowAir, Predicate<Block> filter)
	{
		this(name, WText.translated(descriptionKey), blockName, allowAir,
			filter);
	}

	public BlockSetting(String name, String blockName, boolean allowAir)
	{
		this(name, WText.empty(), blockName, allowAir);
	}
	
	public BlockSetting(String name, String blockName, boolean allowAir,
		Predicate<Block> filter)
	{
		this(name, WText.empty(), blockName, allowAir, filter);
	}

	/**
	 * @return this setting's {@link Block}. Cannot be null.
	 */
	public Block getBlock()
	{
		return BlockUtils.getBlockFromName(blockName);
	}
	
	public String getBlockName()
	{
		return blockName;
	}
	
	public String getShortBlockName()
	{
		return blockName.replace("minecraft:", "");
	}
	
	public void setBlock(Block block)
	{
		if(block == null)
			return;
		
		if(!isAllowed(block))
			return;
		
		String newName = Objects.requireNonNull(BlockUtils.getName(block));
		
		if(blockName.equals(newName))
			return;
		
		blockName = newName;
		WurstClient.INSTANCE.saveSettings();
	}
	
	public void setBlockName(String blockName)
	{
		Block block = BlockUtils.getBlockFromNameOrID(blockName);
		Objects.requireNonNull(block);
		
		setBlock(block);
	}
	
	@Override
	public void resetToDefault()
	{
		blockName = defaultName;
		WurstClient.INSTANCE.saveSettings();
	}
	
	@Override
	public Component getComponent()
	{
		return new BlockComponent(this);
	}
	
	@Override
	public void fromJson(JsonElement json)
	{
		try
		{
			String rawName = JsonUtils.getAsString(json);
			
			Identifier id = Identifier.tryParse(rawName);
			if(id == null)
				throw new JsonException("Discarding Block \"" + rawName
					+ "\" as it is not a valid identifier");
			
			String name = id.toString();
			Block block = BlockUtils.getBlockFromName(name);
			if(block == null)
				throw new JsonException("Discarding Block \"" + rawName
					+ "\" as it is not a known block");

			if(!isAllowed(block))
				throw new JsonException("Discarding Block \"" + rawName
					+ "\" as this setting does not allow that block");
			
			blockName = name;
			
		}catch(JsonException e)
		{
			e.printStackTrace();
			resetToDefault();
		}
	}
	
	@Override
	public JsonElement toJson()
	{
		return new JsonPrimitive(blockName);
	}
	
	@Override
	public JsonObject exportWikiData()
	{
		JsonObject json = new JsonObject();
		json.addProperty("name", getName());
		json.addProperty("description", getDescription());
		json.addProperty("type", "Block");
		json.addProperty("defaultValue", defaultName);
		json.addProperty("allowAir", allowAir);
		return json;
	}
	
	private boolean isAllowed(Block block)
	{
		return (allowAir || !(block instanceof AirBlock)) && filter.test(block);
	}

	@Override
	public Set<PossibleKeybind> getPossibleKeybinds(String featureName)
	{
		String fullName = featureName + " " + getName();
		
		String command = ".setblock " + featureName.toLowerCase() + " ";
		command += getName().toLowerCase().replace(" ", "_") + " ";
		
		LinkedHashSet<PossibleKeybind> pkb = new LinkedHashSet<>();
		// Can't just list all the blocks here. Would need to change UI to allow
		// user to choose a block after selecting this option.
		// pkb.add(new PossibleKeybind(command + "dirt", "Set " + fullName + "
		// to dirt"));
		pkb.add(new PossibleKeybind(command + "reset", "Reset " + fullName));
		
		return pkb;
	}
}
