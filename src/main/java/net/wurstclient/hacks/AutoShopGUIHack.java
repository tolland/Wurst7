/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.stream.StreamSupport;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autoshopgui.NavigationStep;
import net.wurstclient.hacks.autoshopgui.ShopConfig;
import net.wurstclient.hacks.autoshopgui.ShopState;
import net.wurstclient.hacks.autoshopgui.ShopTarget;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FaceTargetSetting;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto shop gui", "auto shop", "shop bot", "auto trade",
	"auto trading", "ShopGUIPlus", "shop gui plus"})
public final class AutoShopGUIHack extends Hack implements UpdateListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withoutPacketSpam(this, FaceTarget.SERVER);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final SliderSetting clickDelay = new SliderSetting("Click delay",
		"Delay between GUI clicks.\n"
			+ "Should be at least 100ms for most servers.",
		250, 50, 1000, 10, ValueDisplay.INTEGER.withSuffix("ms"));
	
	private final CheckboxSetting debugMode = new CheckboxSetting("Debug mode",
		"Shows detailed information about GUI detection and slot parsing.",
		false);
	
	private ShopConfig config;
	private ShopState state = ShopState.FIND_NPC;
	private Entity targetNPC;
	private ShopTarget currentTarget;
	private int navigationStep = 0;
	private long lastClickTime = 0;
	
	public AutoShopGUIHack()
	{
		super("AutoShopGUI");
		setCategory(Category.OTHER);
		addSetting(range);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(clickDelay);
		addSetting(debugMode);
	}
	
	@Override
	protected void onEnable()
	{
		// Load config file
		config = ShopConfig.load();
		if(config == null || config.getTargets().isEmpty())
		{
			ChatUtils.error("No shop config found or config is empty.");
			ChatUtils.message(
				"Please create a config file at: .minecraft/wurst/shopgui.json");
			ChatUtils.message("See documentation for config format examples.");
			setEnabled(false);
			return;
		}
		
		// Initialize state
		state = ShopState.FIND_NPC;
		targetNPC = null;
		currentTarget = null;
		navigationStep = 0;
		
		EVENTS.add(UpdateListener.class, this);
		ChatUtils.message("AutoShopGUI started with "
			+ config.getTargets().size() + " target(s) configured.");
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		
		// Close any open screens
		if(MC.screen != null)
			MC.player.closeContainer();
		
		state = ShopState.FIND_NPC;
		targetNPC = null;
		currentTarget = null;
		navigationStep = 0;
	}
	
	@Override
	public void onUpdate()
	{
		// Respect click delay
		if(System.currentTimeMillis() - lastClickTime < clickDelay.getValueI())
			return;
		
		switch(state)
		{
			case FIND_NPC:
			findTargetNPC();
			break;
			
			case OPEN_SHOP:
			openShop();
			break;
			
			case NAVIGATE_MENU:
			navigateMenu();
			break;
			
			case EXECUTE_TRADE:
			executeTrade();
			break;
			
			case COMPLETE:
			handleComplete();
			break;
			
			case ERROR:
			handleError();
			break;
		}
	}
	
	private void findTargetNPC()
	{
		if(config.getTargets().isEmpty())
		{
			ChatUtils.error("No targets configured.");
			state = ShopState.ERROR;
			return;
		}
		
		// Get next target (for now, just use the first one)
		currentTarget = config.getTargets().get(0);
		
		// Find NPC by name
		double rangeSq = range.getValueSq();
		targetNPC = StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> !e.isRemoved())
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq).filter(e -> {
				String name = e.getName().getString();
				return name.contains(currentTarget.getNpcName());
			}).findFirst().orElse(null);
		
		if(targetNPC == null)
		{
			if(debugMode.isChecked())
				ChatUtils
					.message("Looking for NPC: " + currentTarget.getNpcName());
			return;
		}
		
		ChatUtils.message("Found NPC: " + targetNPC.getName().getString());
		
		// Debug: Show entity details
		if(debugMode.isChecked())
		{
			ChatUtils.message("  Entity Type: " + targetNPC.getType());
			ChatUtils.message(
				"  Entity Class: " + targetNPC.getClass().getSimpleName());
			ChatUtils.message("  Position: " + targetNPC.blockPosition());
			ChatUtils.message("  Distance: " + String.format("%.2f",
				Math.sqrt(MC.player.distanceToSqr(targetNPC))));
		}
		
		state = ShopState.OPEN_SHOP;
	}
	
	private void openShop()
	{
		if(targetNPC == null)
		{
			state = ShopState.FIND_NPC;
			return;
		}
		
		// Check if already in a GUI
		if(MC.screen instanceof AbstractContainerScreen)
		{
			state = ShopState.NAVIGATE_MENU;
			navigationStep = 0;
			return;
		}
		
		// Check cooldown
		if(MC.missTime > 0)
			return;
		
		// Check range
		if(MC.player.distanceToSqr(targetNPC) > range.getValueSq())
		{
			ChatUtils.error("NPC is out of range.");
			state = ShopState.ERROR;
			return;
		}
		
		// Create hit result for the NPC
		AABB box = targetNPC.getBoundingBox();
		Vec3 start = RotationUtils.getEyesPos();
		Vec3 end = box.getCenter();
		Vec3 hitVec = box.clip(start, end).orElse(start);
		EntityHitResult hitResult = new EntityHitResult(targetNPC, hitVec);
		
		// Face the NPC
		faceTarget.face(end);
		
		// Right-click the NPC (try both interaction methods)
		InteractionHand hand = InteractionHand.MAIN_HAND;
		var result1 =
			MC.gameMode.interactAt(MC.player, targetNPC, hitResult, hand);
		if(!result1.consumesAction())
		{
			
			ChatUtils.message("Trying alternate interaction method...");
			MC.gameMode.interact(MC.player, targetNPC, hand);
		}
		
		// Swing hand
		swingHand.swing(hand);
		
		// Set cooldown
		MC.missTime = 4;
		lastClickTime = System.currentTimeMillis();
		
		if(debugMode.isChecked())
		{
			ChatUtils.message("Attempted to open shop GUI (interaction result: "
				+ result1 + ")");
			if(MC.screen != null)
				ChatUtils.message("  Current screen: "
					+ MC.screen.getClass().getSimpleName());
			else
				ChatUtils.message("  No screen opened yet");
		}
	}
	
	private void navigateMenu()
	{
		if(!(MC.screen instanceof AbstractContainerScreen<?> screen))
		{
			ChatUtils.warning("Shop GUI closed unexpectedly.");
			state = ShopState.OPEN_SHOP;
			return;
		}
		
		// Check if we've completed all navigation steps
		if(navigationStep >= currentTarget.getNavigation().size())
		{
			state = ShopState.EXECUTE_TRADE;
			return;
		}
		
		// Get current navigation step
		NavigationStep navStep =
			currentTarget.getNavigation().get(navigationStep);

		// Debug: show screen title and slot info
		if(debugMode.isChecked())
		{
			String title = screen.getTitle().getString();
			ChatUtils.message("Screen: " + title + ", Clicking slot: "
				+ navStep.getSlot() + " (" + navStep.getClickType()
				+ " click) (step " + (navigationStep + 1) + "/"
				+ currentTarget.getNavigation().size() + ")");
		}

		// Click the slot
		clickSlot(screen, navStep);
		navigationStep++;
		lastClickTime = System.currentTimeMillis();
	}
	
	private void executeTrade()
	{
		if(!(MC.screen instanceof AbstractContainerScreen))
		{
			ChatUtils.warning("Shop GUI closed before trade execution.");
			state = ShopState.COMPLETE;
			return;
		}
		
		ChatUtils.message("Trade executed for: " + currentTarget.getItemName());
		
		// Close the screen
		MC.player.closeContainer();
		state = ShopState.COMPLETE;
	}
	
	private void handleComplete()
	{
		ChatUtils.message("AutoShopGUI completed successfully!");
		setEnabled(false);
	}
	
	private void handleError()
	{
		ChatUtils.error("AutoShopGUI encountered an error.");
		setEnabled(false);
	}
	
	private void clickSlot(AbstractContainerScreen<?> screen,
		NavigationStep navStep)
	{
		int slotIndex = navStep.getSlot();
		if(slotIndex < 0 || slotIndex >= screen.getMenu().slots.size())
		{
			ChatUtils.error("Invalid slot index: " + slotIndex);
			return;
		}

		Slot slot = screen.getMenu().slots.get(slotIndex);

		// Debug: show what we're clicking
		if(debugMode.isChecked())
		{
			ItemStack stack = slot.getItem();
			if(!stack.isEmpty())
			{
				String itemName = stack.getHoverName().getString();
				ChatUtils.message("Clicking: " + itemName + " ("
					+ navStep.getClickType() + " click)");
			}
		}

		// Click the slot with the specified click type
		int mouseButton =
			navStep.getClickType() == NavigationStep.ClickType.RIGHT ? 1 : 0;
		MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,
			slot.index, mouseButton, ClickType.PICKUP, MC.player);
	}
	
	public boolean isDebugMode()
	{
		return debugMode.isChecked();
	}
}
