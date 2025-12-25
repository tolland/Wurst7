/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
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
import net.wurstclient.hacks.autoshopgui.QuantitySequencePlanner;
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
		"Minimum delay between GUI clicks.\n"
			+ "With 'Wait for update' enabled, this is just the minimum wait time.",
		250, 50, 1000, 10, ValueDisplay.INTEGER.withSuffix("ms"));
	
	private final CheckboxSetting waitForUpdate = new CheckboxSetting(
		"Wait for GUI update",
		"Wait for the inventory to update after each click instead of using fixed delays.\n"
			+ "More reliable on laggy servers but may be slightly slower.",
		true);
	
	private final SliderSetting maxWaitTime = new SliderSetting("Max wait time",
		"Maximum time to wait for GUI update before proceeding anyway.\n"
			+ "Only used when 'Wait for update' is enabled.",
		2000, 500, 5000, 100, ValueDisplay.INTEGER.withSuffix("ms"));
	
	private final CheckboxSetting debugMode = new CheckboxSetting("Debug mode",
		"Shows detailed information about GUI detection and slot parsing.",
		false);
	
	private ShopConfig config;
	private ShopState state = ShopState.FIND_NPC;
	private Entity targetNPC;
	private ShopTarget currentTarget;
	private int navigationStep = 0;
	private long lastClickTime = 0;
	private boolean waitingForGuiUpdate = false;
	private long guiUpdateWaitStart = 0;
	private String lastScreenTitle = "";
	private int lastInventoryHash = 0;
	
	// Multi-sell tracking
	private int totalItemsToSell = 0;
	private List<List<Integer>> allClickSequences = new ArrayList<>();
	private int currentSequenceIndex = 0;
	private List<Integer> currentClickSequence = new ArrayList<>();
	private int currentClickIndex = 0;
	
	// Sequence planner (using legacy config for backward compatibility)
	private final QuantitySequencePlanner sequencePlanner =
		new QuantitySequencePlanner(
			QuantitySequencePlanner.ButtonConfig.legacyConfig());
	
	public AutoShopGUIHack()
	{
		super("AutoShopGUI");
		setCategory(Category.OTHER);
		addSetting(range);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(clickDelay);
		addSetting(waitForUpdate);
		addSetting(maxWaitTime);
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
		waitingForGuiUpdate = false;
		guiUpdateWaitStart = 0;
		lastScreenTitle = "";
		lastInventoryHash = 0;
		
		// Reset multi-sell tracking
		totalItemsToSell = 0;
		allClickSequences.clear();
		currentSequenceIndex = 0;
		currentClickSequence.clear();
		currentClickIndex = 0;
		
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
		waitingForGuiUpdate = false;
		guiUpdateWaitStart = 0;
		
		// Reset multi-sell tracking
		totalItemsToSell = 0;
		allClickSequences.clear();
		currentSequenceIndex = 0;
		currentClickSequence.clear();
		currentClickIndex = 0;
	}
	
	@Override
	public void onUpdate()
	{
		// If waiting for GUI update, check if it's ready
		if(waitingForGuiUpdate)
		{
			if(checkGuiUpdated() || System.currentTimeMillis()
				- guiUpdateWaitStart > maxWaitTime.getValueI())
			{
				waitingForGuiUpdate = false;
				if(debugMode.isChecked())
					ChatUtils.message("  GUI updated, proceeding...");
			}else
			{
				return; // Still waiting
			}
		}
		
		// Respect minimum click delay
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
			/*
			 * If nav was closed by user, its likely being blown up by a creeper
			 * probably not a good idea to keep going
			 */
			state = ShopState.ERROR;
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
		
		// Start waiting for GUI update if enabled
		if(waitForUpdate.isChecked())
		{
			waitingForGuiUpdate = true;
			guiUpdateWaitStart = System.currentTimeMillis();
			captureGuiState(screen);
			if(debugMode.isChecked())
				ChatUtils.message("  Waiting for GUI update (max "
					+ maxWaitTime.getValueI() + "ms)...");
		}
		
		navigationStep++;
		lastClickTime = System.currentTimeMillis();
	}
	
	private void executeTrade()
	{
		if(!(MC.screen instanceof AbstractContainerScreen<?> screen))
		{
			ChatUtils.warning("Shop GUI closed before trade execution.");
			state = ShopState.COMPLETE;
			return;
		}
		
		// First time entering trade state - generate all sequences upfront
		if(allClickSequences.isEmpty())
		{
			totalItemsToSell =
				countItemsInInventory(currentTarget.getItemName());
			if(totalItemsToSell == 0)
			{
				ChatUtils.warning("No items found in inventory matching: "
					+ currentTarget.getItemName());
				MC.player.closeContainer();
				state = ShopState.COMPLETE;
				return;
			}
			
			// Use the planner to generate ALL sequences for the full sell
			var operationSequences =
				sequencePlanner.planFullSellSequence(totalItemsToSell);
			
			// Convert all operation sequences to slot sequences
			for(var ops : operationSequences)
			{
				allClickSequences.add(sequencePlanner.operationsToSlots(ops));
			}
			
			var plan = sequencePlanner.analyzeSellPlan(totalItemsToSell);
			ChatUtils.message("Starting auto-sell: " + totalItemsToSell + " "
				+ currentTarget.getItemName() + " (partial: "
				+ plan.partialStackQty + ", full stacks: " + plan.fullStackCount
				+ ", total sequences: " + allClickSequences.size() + ")");
			
			// Start with first sequence
			currentSequenceIndex = 0;
			currentClickSequence = allClickSequences.get(0);
			currentClickIndex = 0;
			
			if(debugMode.isChecked())
				ChatUtils.message("Starting sequence 1/"
					+ allClickSequences.size() + ": " + currentClickSequence);
		}
		
		// Execute current click in sequence
		if(currentClickIndex < currentClickSequence.size())
		{
			int slotToClick = currentClickSequence.get(currentClickIndex);
			
			if(debugMode.isChecked())
				ChatUtils.message("  Clicking slot " + slotToClick + " (step "
					+ (currentClickIndex + 1) + "/"
					+ currentClickSequence.size() + ")");
			
			// Click the slot (always left click for quantity adjustment)
			clickSlotByIndex(screen, slotToClick);
			
			// Start waiting for GUI update if enabled
			if(waitForUpdate.isChecked())
			{
				waitingForGuiUpdate = true;
				guiUpdateWaitStart = System.currentTimeMillis();
				captureGuiState(screen);
			}
			
			currentClickIndex++;
			lastClickTime = System.currentTimeMillis();
			return;
		}
		
		// Current sequence complete - move to next sequence if available
		currentSequenceIndex++;
		
		if(currentSequenceIndex < allClickSequences.size())
		{
			// Start next sequence
			currentClickSequence = allClickSequences.get(currentSequenceIndex);
			currentClickIndex = 0;
			
			if(debugMode.isChecked())
				ChatUtils.message("Starting sequence "
					+ (currentSequenceIndex + 1) + "/"
					+ allClickSequences.size() + ": " + currentClickSequence);
			return;
		}
		
		// All sequences complete!
		ChatUtils.message("All items sold successfully! Total: "
			+ totalItemsToSell + " " + currentTarget.getItemName());
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
	
	private void clickSlotByIndex(AbstractContainerScreen<?> screen,
		int slotIndex)
	{
		if(slotIndex < 0 || slotIndex >= screen.getMenu().slots.size())
		{
			ChatUtils.error("Invalid slot index: " + slotIndex);
			return;
		}
		
		Slot slot = screen.getMenu().slots.get(slotIndex);
		
		// Always left click for quantity adjustments
		MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,
			slot.index, 0, ClickType.PICKUP, MC.player);
	}
	
	private void captureGuiState(AbstractContainerScreen<?> screen)
	{
		lastScreenTitle = screen.getTitle().getString();
		lastInventoryHash = calculateInventoryHash(screen);
	}
	
	private int countItemsInInventory(String itemName)
	{
		int total = 0;
		for(ItemStack stack : MC.player.getInventory().getNonEquipmentItems())
		{
			if(!stack.isEmpty()
				&& stack.getHoverName().getString().contains(itemName))
			{
				total += stack.getCount();
			}
		}
		return total;
	}
	
	/**
	 * Generate click sequence to reach target quantity.
	 * Delegates to QuantitySequencePlanner for the actual planning logic.
	 * Returns slot indices to click.
	 */
	private List<Integer> generateClickSequence(int targetQuantity)
	{
		var operations = sequencePlanner.planSequence(targetQuantity);
		
		if(debugMode.isChecked())
		{
			ChatUtils.message("  Sequence for qty " + targetQuantity + ": "
				+ operations.toString());
		}
		
		return sequencePlanner.operationsToSlots(operations);
	}
	
	private boolean checkGuiUpdated()
	{
		if(!(MC.screen instanceof AbstractContainerScreen<?> screen))
			return true; // Screen closed, consider it "updated"
			
		// Check if screen title changed (navigated to different page)
		String currentTitle = screen.getTitle().getString();
		if(!currentTitle.equals(lastScreenTitle))
		{
			if(debugMode.isChecked())
				ChatUtils.message("  Screen changed: " + lastScreenTitle
					+ " -> " + currentTitle);
			return true;
		}
		
		// Check if inventory contents changed
		int currentHash = calculateInventoryHash(screen);
		if(currentHash != lastInventoryHash)
		{
			if(debugMode.isChecked())
				ChatUtils.message("  Inventory contents changed");
			return true;
		}
		
		return false;
	}
	
	private int calculateInventoryHash(AbstractContainerScreen<?> screen)
	{
		int hash = 0;
		List<String> inventoryRows = new ArrayList<>();
		StringBuilder currentRow = new StringBuilder();
		
		for(int i = 0; i < screen.getMenu().slots.size(); i++)
		{
			Slot slot = screen.getMenu().slots.get(i);
			ItemStack stack = slot.getItem();
			
			// Hash all slots (empty or not)
			if(!stack.isEmpty())
			{
				hash = hash * 31 + stack.getItem().hashCode();
				hash = hash * 31 + stack.getCount();
			}
			
			// Build debug string only for non-empty slots
			if(!stack.isEmpty())
			{
				if(currentRow.length() > 0)
					currentRow.append(" | ");
				currentRow.append(i).append(":")
					.append(stack.getHoverName().getString()).append("x")
					.append(stack.getCount());
			}
			
			// Start new row every 9 slots
			if((i + 1) % 9 == 0)
			{
				if(currentRow.length() > 0)
				{
					inventoryRows.add("  Row " + ((i / 9) + 1) + ": "
						+ currentRow.toString());
					currentRow = new StringBuilder();
				}
			}
		}
		
		// Add any remaining items in the last row
		if(currentRow.length() > 0)
		{
			inventoryRows
				.add("  Row " + ((screen.getMenu().slots.size() / 9) + 1) + ": "
					+ currentRow.toString());
		}
		
		// Log inventory contents when hash changes (only in debug mode)
		if(debugMode.isChecked() && hash != lastInventoryHash
			&& !inventoryRows.isEmpty())
		{
			System.out.println("[AutoShopGUI] Inventory changed:");
			for(String row : inventoryRows)
			{
				System.out.println(row);
			}
		}
		
		return hash;
	}
	
	public boolean isDebugMode()
	{
		return debugMode.isChecked();
	}
}
