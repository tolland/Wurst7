/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hack;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonObject;

import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.json.WsonObject;

public final class HackVisibilityFile
{
	private final Path path;
	
	private VisibilityMode mode = VisibilityMode.SHOW_ALL;
	private final Set<String> hackNames = new LinkedHashSet<>();
	
	public HackVisibilityFile(Path path)
	{
		this.path = path;
	}
	
	public void load()
	{
		try
		{
			WsonObject wson = JsonUtils.parseFileToObject(path);
			
			String modeStr = wson.getString("mode");
			mode = VisibilityMode.fromString(modeStr);
			
			wson.getArray("hacks").getAllStrings().forEach(hackNames::add);
			
		}catch(NoSuchFileException e)
		{
			// The file doesn't exist yet. Use defaults and create it.
			save();
			
		}catch(IOException | JsonException e)
		{
			System.out.println("Couldn't load " + path.getFileName());
			e.printStackTrace();
		}
	}
	
	public void save()
	{
		JsonObject json = new JsonObject();
		json.addProperty("mode", mode.name);
		
		com.google.gson.JsonArray hacksArray = new com.google.gson.JsonArray();
		hackNames.forEach(hacksArray::add);
		json.add("hacks", hacksArray);
		
		try
		{
			JsonUtils.toJson(json, path);
			
		}catch(IOException | JsonException e)
		{
			System.out.println("Couldn't save " + path.getFileName());
			e.printStackTrace();
		}
	}
	
	private void debugLog(String message)
	{
		System.out.println("[AutoShopGUI] " + message);
	}
	
	public boolean isVisible(String hackName)
	{
		// debugLog(
		// "Checking visibility for hack: " + hackName + " in mode: " + mode);
		switch(mode)
		{
			case SHOW_ALL:
			return true;
			
			case WHITELIST:
			return hackNames.contains(hackName);
			
			case BLACKLIST:
			return !hackNames.contains(hackName);
			
			default:
			return true;
		}
	}
	
	public void setVisible(String hackName, boolean visible)
	{
		switch(mode)
		{
			case WHITELIST:
			if(visible)
				hackNames.add(hackName);
			else
				hackNames.remove(hackName);
			break;
			
			case BLACKLIST:
			if(visible)
				hackNames.remove(hackName);
			else
				hackNames.add(hackName);
			break;
			
			case SHOW_ALL:
			// Can't modify visibility in SHOW_ALL mode
			break;
		}
		
		save();
	}
	
	public VisibilityMode getMode()
	{
		return mode;
	}
	
	public void setMode(VisibilityMode mode)
	{
		this.mode = mode;
		save();
	}
	
	public Set<String> getConfiguredHacks()
	{
		return new LinkedHashSet<>(hackNames);
	}
	
	public enum VisibilityMode
	{
		SHOW_ALL("show_all"),
		WHITELIST("whitelist"),
		BLACKLIST("blacklist");
		
		private final String name;
		
		VisibilityMode(String name)
		{
			this.name = name;
		}
		
		public static VisibilityMode fromString(String name)
		{
			for(VisibilityMode mode : values())
				if(mode.name.equals(name))
					return mode;
				
			return SHOW_ALL;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
