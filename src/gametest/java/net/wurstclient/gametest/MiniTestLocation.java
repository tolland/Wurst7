/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gametest;

/**
 * The purpose of this class is to provide isolated test locations
 * for game tests, so that each test can run in its own area without
 * interfering with other tests.
 */
public class MiniTestLocation
{
	public final int baseX;
	public final int baseY;
	public final int baseZ;
	
	public MiniTestLocation(int index)
	{
		// locate in middle of chunk
		this.baseX = 8 + (index * 16);
		// avoid any generated stuff
		this.baseY = 64;
		this.baseZ = 8;
	}
	
	public String abs(int dx, int dy, int dz)
	{
		return String.format("%d %d %d", baseX + dx, baseY + dy, baseZ + dz);
	}
	
	public String relCmd(int dx, int dy, int dz)
	{
		return String.format("~%s ~%s ~%s", fmt(dx), fmt(dy), fmt(dz));
	}
	
	public String tp(float dx, float dy, float dz, float yaw, float pitch)
	{
		return String.format("tp Wurst-bot %.2f %.2f %.2f %.1f %.1f",
			baseX + dx, baseY + dy, baseZ + dz, yaw, pitch);
	}
	
	private static String fmt(int offset)
	{
		return offset == 0 ? "~" : "~" + offset;
	}
}
