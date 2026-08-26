/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@SearchTags({"shear aura", "auto shear", "shearaura"})
public final class ShearAuraHack extends Hack
	implements UpdateListener, HandleInputListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Determines how far ShearAura will reach to shear sheep.", 3.5, 1, 10,
		0.05, ValueDisplay.DECIMAL);
	
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	private final SliderSetting speedRandMS =
		new SliderSetting("Speed randomization",
			"Helps you bypass anti-cheat plugins by varying the delay between"
				+ " shears.\n\n" + "\u00b1100ms is recommended for Vulcan.\n\n"
				+ "0 (off) is fine for NoCheat+, AAC, Grim, Verus, Spartan, and"
				+ " vanilla servers.",
			100, 0, 1000, 50, ValueDisplay.INTEGER.withPrefix("\u00b1")
				.withSuffix("ms").withLabel(0, "off"));
	
	private final CheckboxSetting debug =
		new CheckboxSetting("Debug", "Show debug messages in chat.", false);
	
	private Animal target;
	
	public ShearAuraHack()
	{
		super("ShearAura");
		setCategory(Category.OTHER);
		addSetting(range);
		addSetting(speed);
		addSetting(speedRandMS);
		addSetting(debug);
	}
	
	@Override
	protected void onEnable()
	{
		speed.resetTimer(speedRandMS.getValue());
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		target = null;
	}
	
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		double rangeSq = range.getValueSq();
		
		List<Sheep> sheepInRange = EntityUtils.getValidAnimals()
			.filter(e -> e instanceof Sheep).map(e -> (Sheep)e)
			.filter(e -> player.distanceToSqr(e) <= rangeSq)
			.collect(Collectors.toList());
		
		List<Sheep> targets = sheepInRange.stream().filter(e -> !e.isSheared())
			.sorted(Comparator.comparingDouble(player::distanceToSqr))
			.collect(Collectors.toList());
		
		if(targets.isEmpty())
		{
			target = null;
			return;
		}
		
		// check if holding shears
		if(!isHoldingShears())
		{
			selectShears();
			return;
		}
		
		target = targets.get(0);
		
		WURST.getRotationFaker()
			.faceVectorPacket(target.getBoundingBox().getCenter());
	}
	
	@Override
	public void onHandleInput()
	{
		if(target == null)
			return;
		
		// don't interfere with other actions
		if(MC.gameMode.isDestroying())
			return;
		
		if(MC.player.isHandsBusy())
			return;
		
		// check if still holding shears
		InteractionHand hand = getHandWithShears();
		if(hand == null)
			return;
		
		speed.updateTimer();
		if(!speed.isTimeToAttack())
			return;
		
		MultiPlayerGameMode im = MC.gameMode;
		LocalPlayer player = MC.player;
		
		// create realistic hit result
		AABB box = target.getBoundingBox();
		Vec3 start = RotationUtils.getEyesPos();
		Vec3 end = box.getCenter();
		Vec3 hitVec = box.clip(start, end).orElse(start);
		EntityHitResult hitResult = new EntityHitResult(target, hitVec);
		
		InteractionResult actionResult =
			im.interactAt(player, target, hitResult, hand);
		
		if(!actionResult.consumesAction())
			actionResult = im.interact(player, target, hand);
		
		if(actionResult instanceof InteractionResult.Success success
			&& success.swingSource() == InteractionResult.SwingSource.CLIENT)
			player.swing(hand);
		
		speed.resetTimer(speedRandMS.getValue());
		target = null;
	}
	
	private boolean isHoldingShears()
	{
		LocalPlayer player = MC.player;
		return isShears(player.getMainHandItem().getItem())
			|| isShears(player.getOffhandItem().getItem());
	}
	
	private InteractionHand getHandWithShears()
	{
		LocalPlayer player = MC.player;
		if(isShears(player.getMainHandItem().getItem()))
			return InteractionHand.MAIN_HAND;
		if(isShears(player.getOffhandItem().getItem()))
			return InteractionHand.OFF_HAND;
		return null;
	}
	
	private boolean isShears(Item item)
	{
		return item == Items.SHEARS;
	}
	
	private void selectShears()
	{
		InventoryUtils.selectItem(stack -> isShears(stack.getItem()), 36);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(target == null)
			return;
		
		AABB box = EntityUtils.getLerpedBox(target, partialTicks);
		int color = 0xC0FFFFFF; // White for sheep
		RenderUtils.drawOutlinedBox(matrixStack, box, color, false);
	}
}
