package com.nisovin.shopkeepers.ui.defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.ShopkeeperTradeEvent;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.TradingRecipe;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.ui.AbstractShopkeeperUIHandler;
import com.nisovin.shopkeepers.ui.AbstractUIType;
import com.nisovin.shopkeepers.util.ConfigUtils;
import com.nisovin.shopkeepers.util.ItemUtils;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.MerchantUtils;
import com.nisovin.shopkeepers.util.PermissionUtils;
import com.nisovin.shopkeepers.util.TextUtils;

public class TradingHandler extends AbstractShopkeeperUIHandler {

	// TODO Move into protected variables instead?
	/**
	 * Holds gathered information about the currently handled trade.
	 */
	protected static class TradeData {
		/**
		 * The inventory click event which originally triggered the trade.
		 * <p>
		 * Do not modify this event or any of the involved items! This has to be kept cancelled!
		 */
		public InventoryClickEvent clickEvent;
		/**
		 * The involved merchant inventory.
		 */
		public MerchantInventory merchantInventory;
		/**
		 * The trading player.
		 */
		public Player tradingPlayer;
		/**
		 * The involved player inventory.
		 */
		public PlayerInventory playerInventory;
		/**
		 * The used trading recipe.
		 */
		public TradingRecipe tradingRecipe;
		/**
		 * The item offered by the player matching the first required item of the used trading recipe (not necessarily
		 * the item in the first slot), not <code>null</code> or empty.
		 * <p>
		 * The type equals that of the required item from the trading recipe. The metadata however can differ, but still
		 * be accepted for the trade depending on the item matching rules of the used minecraft version and the
		 * shopkeeper settings (ex. strict item comparison disabled).
		 * <p>
		 * Note: This is not a copy and might get modified once the trade gets applied! The stack size matches the
		 * original stack size of the item used by the player.
		 */
		public ItemStack offeredItem1;
		/**
		 * The item offered by the player matching the second required item of the used trading recipe (not necessarily
		 * the item in the second slot), can be <code>null</code>.
		 * <p>
		 * The type equals that of the required item from the trading recipe. The metadata however can differ, but still
		 * be accepted for the trade depending on the item matching rules of the used minecraft version and the
		 * shopkeeper settings (ex. strict item comparison disabled).
		 * <p>
		 * Note: This is not a copy and might get modified once the trade gets applied! The stack size matches the
		 * original stack size of the item used by the player.
		 */
		public ItemStack offeredItem2;
		/**
		 * Whether the <code>offeredItem1</code> and <code>offeredItem2</code> are placed in reverse or regular order
		 * inside the trading slots of the merchant inventory.
		 */
		public boolean swappedItemOrder;

		protected TradeData() {
		}

		// Separate from constructor to allow evolution without affecting sub-classes
		private void setup(	InventoryClickEvent clickEvent, MerchantInventory merchantInventory, Player tradingPlayer,
							TradingRecipe tradingRecipe, ItemStack offeredItem1, ItemStack offeredItem2, boolean swappedItemOrder) {
			this.clickEvent = clickEvent;
			this.merchantInventory = merchantInventory;
			this.tradingPlayer = tradingPlayer;
			this.playerInventory = tradingPlayer.getInventory();
			this.tradingRecipe = tradingRecipe;
			this.offeredItem1 = offeredItem1;
			this.offeredItem2 = offeredItem2;
			this.swappedItemOrder = swappedItemOrder;
		}
	}

	// Those slot ids match both raw slot ids and regular slot ids for the merchant inventory view with the merchant
	// inventory at the top:
	protected static final int BUY_ITEM_1_SLOT_ID = 0;
	protected static final int BUY_ITEM_2_SLOT_ID = 1;
	protected static final int RESULT_ITEM_SLOT_ID = 2;

	// Counts the trades triggered by the last click-event:
	protected int tradeCounter = 0;

	public TradingHandler(AbstractUIType uiType, AbstractShopkeeper shopkeeper) {
		super(uiType, shopkeeper);
	}

	@Override
	public boolean canOpen(Player player, boolean silent) {
		assert player != null;
		if (!PermissionUtils.hasPermission(player, ShopkeepersPlugin.TRADE_PERMISSION)) {
			if (!silent) {
				Log.debug(() -> "Blocked trade window opening for " + player.getName() + ": Missing trade permission.");
				TextUtils.sendMessage(player, Settings.msgMissingTradePerm);
			}
			return false;
		}
		return true;
	}

	@Override
	protected boolean openWindow(Player player) {
		// Create and open trading window:
		Shopkeeper shopkeeper = this.getShopkeeper();
		String title = this.getInventoryTitle();
		List<TradingRecipe> recipes = shopkeeper.getTradingRecipes(player);
		return this.openTradeWindow(title, recipes, player);
	}

	protected boolean openTradeWindow(String title, List<TradingRecipe> recipes, Player player) {
		// Setup merchant:
		Merchant merchant = this.setupMerchant(title, recipes);

		// Increment 'talked-to-villager' statistic when opening trading menu:
		if (Settings.incrementVillagerStatistics) {
			player.incrementStatistic(Statistic.TALKED_TO_VILLAGER);
		}

		// Open merchant:
		return (player.openMerchant(merchant, true) != null);
	}

	protected Merchant setupMerchant(String title, List<TradingRecipe> recipes) {
		// Setup merchant:
		Merchant merchant = Bukkit.createMerchant(title);
		this.setupMerchantRecipes(merchant, recipes);
		return merchant;
	}

	protected void setupMerchantRecipes(Merchant merchant, List<TradingRecipe> recipes) {
		// Create list of merchant recipes:
		List<MerchantRecipe> merchantRecipes = this.createMerchantRecipes(recipes);
		// Set merchant's recipes:
		merchant.setRecipes(merchantRecipes);
	}

	protected List<MerchantRecipe> createMerchantRecipes(List<TradingRecipe> recipes) {
		List<MerchantRecipe> merchantRecipes = new ArrayList<>();
		for (TradingRecipe recipe : recipes) {
			merchantRecipes.add(this.createMerchantRecipe(recipe));
		}
		return merchantRecipes;
	}

	protected MerchantRecipe createMerchantRecipe(TradingRecipe recipe) {
		return MerchantUtils.createMerchantRecipe(recipe); // Default
	}

	protected String getInventoryTitle() {
		String title = this.getShopkeeper().getName(); // Can be empty
		if (title.isEmpty()) {
			title = Settings.msgTradingTitleDefault;
		}
		return Settings.msgTradingTitlePrefix + title;
	}

	protected void updateTrades(Player player) {
		// Check if the currently open inventory still corresponds to this UI:
		if (!this.isOpen(player)) return;

		InventoryView openInventory = player.getOpenInventory();
		assert openInventory.getType() == InventoryType.MERCHANT;
		MerchantInventory merchantInventory = (MerchantInventory) openInventory.getTopInventory();
		Merchant merchant = merchantInventory.getMerchant();
		List<MerchantRecipe> oldMerchantRecipes = merchant.getRecipes();

		Shopkeeper shopkeeper = this.getShopkeeper();
		List<TradingRecipe> recipes = shopkeeper.getTradingRecipes(player);
		List<MerchantRecipe> newMerchantRecipes = this.createMerchantRecipes(recipes);
		if (MerchantUtils.MERCHANT_RECIPES_EQUAL_IGNORING_USES.equals(oldMerchantRecipes, newMerchantRecipes)) {
			Log.debug(() -> "Trades are still up-to-date for player " + player.getName());
			return; // Recipes did not change
		}
		Log.debug(() -> "Updating trades for player " + player.getName());

		// It is not safe to reduce the number of trading recipes for the player, so we need to add dummy recipes:
		// TODO Check if this still applies in MC 1.14
		for (int i = recipes.size(); i < oldMerchantRecipes.size(); ++i) {
			// TODO API doesn't expect null here (but works).. use something else?
			MerchantRecipe merchantRecipe = new MerchantRecipe(null, 0, 0, false);
			newMerchantRecipes.add(merchantRecipe);
		}
		// Set merchant's recipes:
		merchant.setRecipes(newMerchantRecipes);

		// Update recipes for the client:
		NMSManager.getProvider().updateTrades(player);
	}

	@Override
	protected boolean isWindow(InventoryView view) {
		return view != null && view.getType() == InventoryType.MERCHANT;
	}

	@Override
	protected void onInventoryClose(Player player, InventoryCloseEvent closeEvent) {
	}

	// TRADE PROCESSING

	// TODO This doesn't work because the client will automatically update the result slot item whenever a slot is
	// changed.
	// @Override
	// protected void onInventoryClickEarly(InventoryClickEvent clickEvent, Player player) {
	// // Clear the result item slot if we use strict item comparison and there is no valid trade:
	// // TODO We also need to do this when the player selects a trading recipe, because that will automatically insert
	// the matching items into the trading view.
	// if (!Settings.useStrictItemComparison) return;
	// if (clickEvent.isCancelled()) return;
	//
	// // This needs to happen after the event has been handled, because Minecraft will set the result slot afterwards:
	// SKUISession uiSession = SKShopkeepersPlugin.getInstance().getUIRegistry().getSession(player);
	// Bukkit.getScheduler().runTask(ShopkeepersPlugin.getInstance(), () -> {
	// if (!uiSession.isValid()) return;
	// if (clickEvent.isCancelled()) return;
	//
	// // Logs if it encounters items that are not strictly matching and then clears the result slot:
	// this.checkForTrade(clickEvent, true, false, false);
	// });
	// }

	// Late processing, so that other plugins can cancel the trading without having to rely on Shopkeepers' API.
	@Override
	protected void onInventoryClickLate(InventoryClickEvent clickEvent, Player player) {
		assert clickEvent != null && player != null;
		// Note: This expects that there are no other click-events while this event is getting processed.
		// Reset trade counter:
		tradeCounter = 0;

		Shopkeeper shopkeeper = this.getShopkeeper();
		String playerName = player.getName();
		if (clickEvent.isCancelled()) {
			Log.debug(() -> "Some plugin has cancelled the click in the trading window for "
					+ playerName + " at " + shopkeeper.getPositionString() + ".");
			return;
		}

		int rawSlot = clickEvent.getRawSlot();
		InventoryAction action = clickEvent.getAction();

		MerchantInventory merchantInventory = (MerchantInventory) clickEvent.getInventory();
		ItemStack resultItem = merchantInventory.getItem(RESULT_ITEM_SLOT_ID);
		ItemStack cursor = clickEvent.getCursor();

		// Prevent unsupported types of special clicks:
		if (action == InventoryAction.COLLECT_TO_CURSOR && ItemUtils.isSimilar(resultItem, cursor)) {
			// TODO Might no longer be supported in 1.14 and 1.15, see: https://bugs.mojang.com/browse/MC-148867
			// Weird behavior and buggy, see MC-129515
			// For now: Only allowed if the item on the cursor and inside the result slot are different.
			// TODO Maybe replicate the behavior of this inventory action, but limit its effect to the player's
			// inventory?
			Log.debug(() -> "Prevented unsupported special click in trading window by " + playerName
					+ " at " + shopkeeper.getPositionString() + ": " + action);
			clickEvent.setCancelled(true);
			ItemUtils.updateInventoryLater(player);
			return;
		}

		// All currently supported inventory actions that might trigger trades involve a click of the result slot:
		if (rawSlot != RESULT_ITEM_SLOT_ID) {
			// Not canceling the event to allow regular inventory interaction inside the player's inventory.
			return;
		}

		// Some clicks on the result slot don't trigger trades:
		if (action == InventoryAction.CLONE_STACK) {
			return;
		}

		// We are handling all types of clicks which might trigger a trade ourselves:
		clickEvent.setCancelled(true);
		ItemUtils.updateInventoryLater(player);

		// Check for a trade and prepare trade data:
		TradeData tradeData = this.checkForTrade(clickEvent, false);
		if (tradeData == null) {
			// No trade available.
			return;
		}
		assert tradeData.tradingRecipe.getResultItem().isSimilar(resultItem);

		PlayerInventory playerInventory = player.getInventory();
		boolean isCursorEmpty = ItemUtils.isEmpty(cursor);

		// Handle trade depending on used inventory action:
		// TODO: In MC 1.15.1 PICKUP_ONE and PICKUP_SOME might get triggered when clicking the result slot (test again:
		// left click, shift+left click, right click, middle click).
		if (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF) {
			if (!isCursorEmpty && (!cursor.isSimilar(resultItem) || (cursor.getAmount() + resultItem.getAmount()) > cursor.getMaxStackSize())) {
				Log.debug("Not handling trade: The cursor cannot carry the resulting items.");
				return;
			} else {
				if (this.handleTrade(tradeData)) {
					// Add result items to cursor:
					ItemStack resultCursor;
					if (isCursorEmpty) {
						resultCursor = resultItem; // No item copy required here
					} else {
						resultCursor = ItemUtils.increaseItemAmount(cursor, resultItem.getAmount());
					}
					player.setItemOnCursor(resultCursor);

					// Common apply trade:
					this.commonApplyTrade(tradeData);
				}
				this.updateTrades(player);
			}
		} else if (action == InventoryAction.DROP_ONE_SLOT || action == InventoryAction.DROP_ALL_SLOT) {
			// Not supported for now, since this might be tricky to accurately reproduce.
			// dropItemNaturally is not equivalent to the player himself dropping the item
			// and inventoryView.setItem(-999, item) doesn't set the item's thrower (and there is no API to set that,
			// nor does the inventoryView return a reference to the dropped item).
			/*if (isCursorEmpty) {
				if (this.handleTrade(tradeData)) {
					// Drop result items:
					ItemStack droppedItem = resultItem.clone(); // todo Copy required?
					// todo Call drop event first
					player.getWorld().dropItemNaturally(player.getEyeLocation(), droppedItem);
				
					// Common apply trade:
					this.commonApplyTrade(tradeData);
				}
			}*/
		} else if (action == InventoryAction.HOTBAR_SWAP) {
			int hotbarButton = clickEvent.getHotbarButton();
			if (hotbarButton >= 0 && hotbarButton <= 8 && ItemUtils.isEmpty(playerInventory.getItem(hotbarButton))) {
				if (this.handleTrade(tradeData)) {
					// Set result items to hotbar slot:
					playerInventory.setItem(hotbarButton, resultItem); // No item copy required here

					// Common apply trade:
					this.commonApplyTrade(tradeData);
				}
				this.updateTrades(player);
			}
		} else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
			// Trades as often as possible (depending on offered items and inventory space) for the current result item:
			// If the current trading recipe is no longer fulfilled, and the currently selected recipe index is 0,
			// it will switch to the next applicable trading recipe, and continue the trading if the new result item is
			// equal to the previous result item.
			while (true) {
				// Check if there is enough space in the player's inventory:
				ItemStack[] newPlayerContents = playerInventory.getStorageContents();

				// Minecraft is adding items in reverse container order (starting with hotbar slot 9),
				// so we reverse the player contents accordingly before adding items:
				// Changes write through to the original array.
				List<ItemStack> listView = Arrays.asList(newPlayerContents);
				List<ItemStack> hotbarView = listView.subList(0, 9);
				List<ItemStack> contentsView = listView.subList(9, 36);
				Collections.reverse(hotbarView);
				Collections.reverse(contentsView);

				// No item copy required here:
				if (ItemUtils.addItems(newPlayerContents, resultItem) != 0) {
					// Not enough inventory space, abort trading:
					break;
				}

				if (!this.handleTrade(tradeData)) {
					// Trade was aborted:
					break;
				}

				// Revert previous reverse:
				Collections.reverse(hotbarView);
				Collections.reverse(contentsView);

				// Apply player inventory changes:
				ItemUtils.setStorageContents(playerInventory, newPlayerContents);

				// Common apply trade:
				this.commonApplyTrade(tradeData);

				// Check if we might continue trading:
				tradeData = this.checkForTrade(clickEvent, true); // Silent
				if (tradeData == null) {
					// No trade available:
					break;
				}
				// Compare result items:
				ItemStack newResultItem = tradeData.tradingRecipe.getResultItem();
				if (!resultItem.isSimilar(newResultItem)) {
					// New result item doesn't match previous result item, abort trading (mimics Minecraft behavior):
					break;
				}
				// Update result item:
				resultItem = newResultItem;
			}
			this.updateTrades(player);
		} else {
			// The inventory action involves the result slot, but doesn't trigger a trade usually, or isn't supported
			// yet.
		}
	}

	private void clearResultSlotForInvalidTrade(MerchantInventory merchantInventory) {
		// TODO This is not working currently. The client updates the result slot contents whenever it receives a slot
		// update from the server.
		// merchantInventory.setItem(RESULT_ITEM_SLOT_ID, null);
		// ItemUtils.updateInventoryLater(merchantInventory);
		// Log.debug("Result slot cleared due to invalid trade.");
	}

	private TradeData checkForTrade(InventoryClickEvent clickEvent, boolean silent) {
		return this.checkForTrade(clickEvent, silent, silent, true);
	}

	// Checks for an available trade and does some preparation in case a trade is found.
	// Returns null if no trade could be prepared for some reason.
	private TradeData checkForTrade(InventoryClickEvent clickEvent, boolean silent, boolean slientStrictItemComparison, boolean tradingContext) {
		Player player = (Player) clickEvent.getWhoClicked();
		MerchantInventory merchantInventory = (MerchantInventory) clickEvent.getView().getTopInventory();
		ItemStack resultItem = merchantInventory.getItem(RESULT_ITEM_SLOT_ID);
		if (ItemUtils.isEmpty(resultItem)) {
			if (!silent) {
				Log.debug("Not handling trade: There is no item in the clicked result slot (no trade available).");
			}
			return null; // No trade available
		}

		// Find (and validate) the recipe Minecraft is using for the trade:
		TradingRecipe tradingRecipe = MerchantUtils.getSelectedTradingRecipe(merchantInventory);
		if (tradingRecipe == null) {
			// This should not happen..
			if (!silent) {
				Log.debug("Not handling trade: We could not find the used trading recipe!");
			}
			this.clearResultSlotForInvalidTrade(merchantInventory);
			return null;
		}
		ItemStack recipeResultItem = tradingRecipe.getResultItem();
		if (!recipeResultItem.equals(resultItem)) {
			// This should not happen..
			if (!silent) {
				if (Settings.isDebugging()) {
					Log.debug("Not handling trade: The trade result item does not match the expected item of the used trading recipe!");
					String recipeResultItemYaml = ConfigUtils.toYaml("recipeResultItem", recipeResultItem);
					String resultItemYaml = ConfigUtils.toYaml("resultItem", resultItem);
					Log.debug(recipeResultItemYaml);
					Log.debug(resultItemYaml);
				}
			}
			this.clearResultSlotForInvalidTrade(merchantInventory);
			return null;
		}

		ItemStack requiredItem1 = tradingRecipe.getItem1();
		ItemStack requiredItem2 = tradingRecipe.getItem2();
		assert !ItemUtils.isEmpty(requiredItem1);

		// Use null here instead of air for consistent behavior with previous versions:
		ItemStack offeredItem1 = ItemUtils.getNullIfEmpty(merchantInventory.getItem(BUY_ITEM_1_SLOT_ID));
		ItemStack offeredItem2 = ItemUtils.getNullIfEmpty(merchantInventory.getItem(BUY_ITEM_2_SLOT_ID));
		boolean swappedItemOrder = false;

		// Minecraft checks both combinations (item1, item2) and (item2, item1) when determining if a trading recipe
		// matches, so we need to determine the used item order for the currently used trading recipe:
		if (matches(offeredItem1, offeredItem2, requiredItem1, requiredItem2)) {
			// Order is as-is.
		} else if (matches(offeredItem1, offeredItem2, requiredItem2, requiredItem1)) {
			// Swapped order:
			swappedItemOrder = true;
			ItemStack temp = offeredItem1;
			offeredItem1 = offeredItem2;
			offeredItem2 = temp;
		} else {
			// The used item order couldn't be determined.
			// This should not happen..
			// But this might for example happen if the FailedHandler#matches implementation falls back to using
			// the stricter isSimilar for the item comparison and the involved items are not strictly similar.
			if (!silent) {
				Log.debug("Not handling trade: Could not match the offered items to the used trading recipe!");
			}
			this.clearResultSlotForInvalidTrade(merchantInventory);
			return null;
		}
		assert offeredItem1 != null;

		if (Settings.useStrictItemComparison) {
			// Verify the recipe items are perfectly matching (they can still be swapped though):
			if (!ItemUtils.isSimilar(requiredItem1, offeredItem1) || !ItemUtils.isSimilar(requiredItem2, offeredItem2)) {
				// Additional check for the debug flag, so we don't do the item comparisons if not really needed:
				if (!slientStrictItemComparison && Settings.debug) {
					String errorMsg = "The offered items do not strictly match the required items.";
					if (tradingContext) {
						this.debugPreventedTrade(player, errorMsg);
					} else {
						Log.debug(errorMsg);
					}
					Log.debug("Used trading recipe: " + ItemUtils.getSimpleRecipeInfo(tradingRecipe));
					Log.debug("Recipe item 1: " + (ItemUtils.isSimilar(requiredItem1, offeredItem1) ? "similar" : "not similar"));
					Log.debug("Recipe item 2: " + (ItemUtils.isSimilar(requiredItem2, offeredItem2) ? "similar" : "not similar"));
				}
				this.clearResultSlotForInvalidTrade(merchantInventory);
				return null;
			}
		}

		// Setup trade data:
		TradeData tradeData = this.createTradeData();
		tradeData.setup(clickEvent, merchantInventory, player, tradingRecipe, offeredItem1, offeredItem2, swappedItemOrder);
		// Custom setup by sub-classes:
		this.setupTradeData(tradeData, clickEvent);
		return tradeData;
	}

	private boolean matches(ItemStack offeredItem1, ItemStack offeredItem2, ItemStack requiredItem1, ItemStack requiredItem2) {
		int offeredItem1Amount = ItemUtils.getItemStackAmount(offeredItem1);
		int offeredItem2Amount = ItemUtils.getItemStackAmount(offeredItem2);
		int requiredItem1Amount = ItemUtils.getItemStackAmount(requiredItem1);
		int requiredItem2Amount = ItemUtils.getItemStackAmount(requiredItem2);
		return (offeredItem1Amount >= requiredItem1Amount
				&& offeredItem2Amount >= requiredItem2Amount
				&& NMSManager.getProvider().matches(offeredItem1, requiredItem1)
				&& NMSManager.getProvider().matches(offeredItem2, requiredItem2));
	}

	protected final void debugPreventedTrade(Player player, String reason) {
		Log.debug(() -> "Prevented trade by " + player.getName() + " with shopkeeper at "
				+ this.getShopkeeper().getPositionString() + ": " + reason);
	}

	/**
	 * Creates a new {@link TradeData}.
	 * <p>
	 * This can be overridden to allow sub-classes to store additional data.
	 * 
	 * @return the new trade data object
	 */
	protected TradeData createTradeData() {
		return new TradeData();
	}

	/**
	 * This can be used by sub-classes to initially fill in additional data based on the given
	 * {@link InventoryClickEvent} into the {@link TradeData}, before it gets passed around.
	 * <p>
	 * This is called after the common setup of the {@link TradeData} has been performed.
	 * 
	 * @param tradeData
	 *            the trade data
	 * @param clickEvent
	 *            the click event
	 */
	protected void setupTradeData(TradeData tradeData, InventoryClickEvent clickEvent) {
	}

	// Returns true if the trade was not aborted and is now supposed to get applied.
	private boolean handleTrade(TradeData tradeData) {
		assert tradeData != null;
		// Increase trade counter:
		tradeCounter++;

		// Check and prepare the trade:
		if (!this.prepareTrade(tradeData)) {
			// The trade got cancelled for some shopkeeper-specific reason:
			this.onTradeAborted(tradeData);
			return false;
		}

		// Call trade event, giving other plugins a chance to cancel the trade before it gets applied:
		// Prepare offered items for the event: Clone and ensure stack sizes matching the trading recipe.
		ItemStack eventOfferedItem1 = tradeData.offeredItem1.clone();
		ItemStack eventOfferedItem2 = ItemUtils.isEmpty(tradeData.offeredItem2) ? null : tradeData.offeredItem2.clone();
		eventOfferedItem1.setAmount(tradeData.tradingRecipe.getItem1().getAmount());
		if (eventOfferedItem2 != null) {
			assert tradeData.tradingRecipe.getItem2() != null;
			eventOfferedItem2.setAmount(tradeData.tradingRecipe.getItem2().getAmount());
		}

		ShopkeeperTradeEvent tradeEvent = new ShopkeeperTradeEvent(this.getShopkeeper(), tradeData.tradingPlayer,
				tradeData.clickEvent, tradeData.tradingRecipe, eventOfferedItem1, eventOfferedItem2,
				tradeData.swappedItemOrder);
		Bukkit.getPluginManager().callEvent(tradeEvent);
		if (tradeEvent.isCancelled()) {
			Log.debug("The trade got cancelled by some other plugin.");
			this.onTradeAborted(tradeData);
			return false;
		}
		// Making sure that the click event is still cancelled:
		if (!tradeData.clickEvent.isCancelled()) {
			Log.warning("Some plugin tried to uncancel the click event during trade handling!");
			tradeData.clickEvent.setCancelled(true);
		}

		// Assert: The click event and the affected inventories should not get modified during the event!

		// We are going to apply the trade now:
		this.preApplyTrade(tradeData);
		return true;
	}

	private void commonApplyTrade(TradeData tradeData) {
		// Update merchant inventory contents:
		MerchantInventory merchantInventory = tradeData.merchantInventory;
		merchantInventory.setItem(RESULT_ITEM_SLOT_ID, null); // Clear result slot, just in case

		TradingRecipe tradingRecipe = tradeData.tradingRecipe;
		ItemStack newOfferedItem1 = ItemUtils.descreaseItemAmount(tradeData.offeredItem1, ItemUtils.getItemStackAmount(tradingRecipe.getItem1()));
		ItemStack newOfferedItem2 = ItemUtils.descreaseItemAmount(tradeData.offeredItem2, ItemUtils.getItemStackAmount(tradingRecipe.getItem2()));
		// Inform the merchant inventory about the change (updates the active trading recipe and result item):
		merchantInventory.setItem(tradeData.swappedItemOrder ? BUY_ITEM_2_SLOT_ID : BUY_ITEM_1_SLOT_ID, newOfferedItem1);
		merchantInventory.setItem(tradeData.swappedItemOrder ? BUY_ITEM_1_SLOT_ID : BUY_ITEM_2_SLOT_ID, newOfferedItem2);

		// TODO Increase uses of corresponding MerchanRecipe?
		// TODO Add support for exp-rewards?
		// TODO Support modifications to the MerchantRecipe's maxUses?

		// Increment 'traded-with-villager' statistic for every trade:
		if (Settings.incrementVillagerStatistics) {
			Player player = tradeData.tradingPlayer;
			player.incrementStatistic(Statistic.TRADED_WITH_VILLAGER);
		}

		// Shopkeeper-specific application of the trade:
		this.onTradeApplied(tradeData);

		// Log trade:
		Log.debug(() -> "Trade (#" + tradeCounter + ") by " + tradeData.tradingPlayer.getName() + " with shopkeeper at "
				+ this.getShopkeeper().getPositionString() + ": " + ItemUtils.getSimpleRecipeInfo(tradingRecipe));
	}

	/**
	 * Checks whether the trade can be performed and makes any preparations required for applying the trade in case it
	 * actually gets performed.
	 * <p>
	 * This gets called for every trade a player triggered through a merchant inventory action. Depending on the
	 * inventory action multiple successive trades (even using different trading recipes) might get triggered by a
	 * single inventory action.
	 * <p>
	 * There should be no changes of the corresponding click event and the involved inventories (player, container) to
	 * be expected between this phase of the trade handling and the actual application of the trade.
	 * 
	 * @param tradeData
	 *            the trade data
	 * @return <code>true</code> to continue trade handling, <code>false</code> to cancel the trade and any successive
	 *         trades triggered by the same inventory click
	 */
	protected boolean prepareTrade(TradeData tradeData) {
		return true;
	}

	/**
	 * Called if a previously already prepared trade got aborted for some reason.
	 * <p>
	 * Does also get called if the trade got aborted by {@link #prepareTrade(TradeData)} itself.
	 * <p>
	 * This can be used to perform any necessary cleanup.
	 * 
	 * @param tradeData
	 *            the trade data
	 */
	protected void onTradeAborted(TradeData tradeData) {
	}

	/**
	 * This gets called right before a trade gets applied.
	 * <p>
	 * This can be used to perform any kind of pre-processing which needs to happen first.
	 * <p>
	 * At this phase of the trade handling, the trade should no longer get cancelled. Any conditions which could prevent
	 * a trade from getting successfully applied have to be checked inside {@link #prepareTrade(TradeData)} instead.
	 * 
	 * @param tradeData
	 *            the trade data
	 */
	protected void preApplyTrade(TradeData tradeData) {
	}

	/**
	 * This gets called right after a trade has been applied.
	 * <p>
	 * This can be used to perform any kind of post-processing which needs to happen last. For example any shopkeeper
	 * specific behavior required for applying the trade can happen here.
	 * <p>
	 * At this phase of the trade handling, the trade should no longer get cancelled. Any conditions which could prevent
	 * a trade from getting successfully applied have to be checked inside {@link #prepareTrade(TradeData)} instead.
	 * 
	 * @param tradeData
	 *            the trade data
	 */
	protected void onTradeApplied(TradeData tradeData) {
	}

	// Returns a value >= 0 and <= amount.
	protected int getAmountAfterTaxes(int amount) {
		assert amount >= 0;
		if (Settings.taxRate == 0) return amount;
		int taxes = 0;
		if (Settings.taxRoundUp) {
			taxes = (int) Math.ceil(amount * (Settings.taxRate / 100.0D));
		} else {
			taxes = (int) Math.floor(amount * (Settings.taxRate / 100.0D));
		}
		return Math.max(0, Math.min(amount - taxes, amount));
	}
}
