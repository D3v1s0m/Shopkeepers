package com.nisovin.shopkeepers.ui;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.InventoryView;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.api.ui.UIType;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.ui.state.UIState;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

/**
 * Handles one specific type of user interface window.
 */
public abstract class UIHandler {

	private final AbstractUIType uiType;

	// Heuristic detection of automatically triggered shift left-clicks:
	private static final long AUTOMATIC_SHIFT_LEFT_CLICK_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
	private long lastManualClickNanos = 0L;
	private int lastManualClickedSlotId = -1;
	private boolean isAutomaticShiftLeftClick = false;

	protected UIHandler(AbstractUIType uiType) {
		Validate.notNull(uiType, "uiType is null");
		this.uiType = uiType;
	}

	/**
	 * Gets the {@link UIType}.
	 * 
	 * @return the UI type, not <code>null</code>
	 */
	public final AbstractUIType getUIType() {
		return uiType;
	}

	protected void debugNotOpeningUI(Player player, String reason) {
		Validate.notNull(player, "player is null");
		Validate.notEmpty(reason, "reason is null or empty");
		Log.debug(() -> "Not opening UI '" + this.getUIType().getIdentifier() + "' for player "
				+ player.getName() + ": " + reason);
	}

	/**
	 * Checks whether the given player is allowed to access to this interface.
	 * <p>
	 * This may for example perform any necessary permission checks.
	 * <p>
	 * This is for example checked as part of {@link #canOpen(Player, boolean)}, but limited to
	 * access permission related checks.
	 * 
	 * @param player
	 *            the player, not <code>null</code>
	 * @param silent
	 *            <code>false</code> to inform the player if the access is denied
	 * @return <code>true</code> if the given player has access to this interface
	 */
	public abstract boolean canAccess(Player player, boolean silent);

	/**
	 * Checks whether the given player can open this interface.
	 * <p>
	 * This gets for example called when a player requests this interface.
	 * <p>
	 * This is expected to call {@link #canAccess(Player, boolean)}, but may perform additional
	 * checks. For example, the trading UI of a shop may return <code>false</code> if there are
	 * currently no trades available.
	 * 
	 * @param player
	 *            the player, not <code>null</code>
	 * @param silent
	 *            <code>false</code> to inform the player if they cannot open the UI currently
	 * @return <code>true</code> if the given player is currently allowed to open this interface
	 */
	public boolean canOpen(Player player, boolean silent) {
		return this.canAccess(player, silent);
	}

	/**
	 * Opens the interface window for the player of the given {@link UISession}.
	 * <p>
	 * Generally {@link #canOpen(Player, boolean)} should be checked before calling this method.
	 * However, this method should not rely on that.
	 * <p>
	 * If opening the window fails, the given {@link UISession} will be ended.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param uiState
	 *            a previously captured {@link UIState} to {@link #restoreState(UISession, UIState)
	 *            restore}, or {@link UIState#EMPTY}
	 * @return <code>true</code> if the interface window was successfully opened
	 */
	protected abstract boolean openWindow(UISession uiSession, UIState uiState);

	/**
	 * Checks whether the given inventory view is managed by this UI handler.
	 * <p>
	 * The UI registry already keeps track of a player's currently open UI. This additional check
	 * verifies, heuristically, in a best-effort manner, that the inventory view the player is
	 * interacting with actually corresponds to the inventory view expected by this UI handler. The
	 * result of this method is checked before any inventory events are passed through to this
	 * handler.
	 * 
	 * @param view
	 *            an inventory view, not <code>null</code>
	 * @return <code>true</code> if the given inventory view has been opened and is handled by this
	 *         UI handler
	 */
	protected abstract boolean isWindow(InventoryView view);

	/**
	 * Checks if the player has this UI open currently.
	 * 
	 * @param player
	 *            the player, not <code>null</code>
	 * @return <code>true</code> if this UI is open currently
	 */
	protected final boolean isOpen(Player player) {
		SKUISession session = SKShopkeepersPlugin.getInstance().getUIRegistry().getUISession(player);
		if (session == null || session.getUIHandler() != this) return false;
		return (Settings.disableInventoryVerification || this.isWindow(player.getOpenInventory()));
	}

	/**
	 * Captures the current {@link UIState} for the given session.
	 * <p>
	 * Not all types of UIs may support this, or may be able to fully restore all aspects of the
	 * current UI session.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @return the {@link UIState}, not <code>null</code>, but can be {@link UIState#EMPTY}
	 */
	protected UIState captureState(UISession uiSession) {
		return UIState.EMPTY; // Not supported by default
	}

	/**
	 * Checks if the given {@link UIState} is accepted by this UI.
	 * <p>
	 * By default, only {@link UIState#EMPTY} is accepted.
	 * 
	 * @param uiState
	 *            the {@link UIState}, not <code>null</code>
	 * @return <code>true</code> if the {@link UIState} is accepted
	 */
	protected boolean isAcceptedState(UIState uiState) {
		if (uiState == UIState.EMPTY) return true;
		return false;
	}

	/**
	 * Validates the given {@link UIState} according to {@link #isAcceptedState(UIState)}.
	 * 
	 * @param uiState
	 *            the {@link UIState}, not <code>null</code>
	 * @throws IllegalArgumentException
	 *             if the given {@link UIState} is not {@link #isAcceptedState(UIState) accepted} by
	 *             this UI
	 */
	protected final void validateState(UIState uiState) {
		Validate.notNull(uiState, "uiState is null");
		Validate.isTrue(this.isAcceptedState(uiState),
				() -> "uiState of type " + uiState.getClass().getName()
						+ " is not accepted by UI of type " + this.getUIType().getIdentifier());
	}

	/**
	 * Tries to restore a previously {@link #captureState(UISession) captured} {@link UIState} in a
	 * best-effort manner.
	 * <p>
	 * Any current state is silently replaced with the captured state.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param uiState
	 *            the {@link UIState}, not <code>null</code>
	 * @throws IllegalArgumentException
	 *             if the given {@link UIState} is not {@link #isAcceptedState(UIState) accepted} by
	 *             this UI
	 */
	protected void restoreState(UISession uiSession, UIState uiState) {
		this.validateState(uiState);
		// Not supported by default.
	}

	/**
	 * This is called when an {@link UISession} handled by this {@link UIHandler} has ended.
	 * <p>
	 * If the {@link UISession} has ended not due to a received {@link InventoryCloseEvent} but for
	 * another reason (e.g. due to a call to {@link UISession#abort()}), the provided inventory
	 * close event argument is <code>null</code>.
	 * <p>
	 * This is also called when the UI session has ended because {@link #openWindow(UISession)}
	 * could not successfully open the UI window.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param closeEvent
	 *            the corresponding inventory close event, can be <code>null</code>
	 */
	protected void onInventoryClose(UISession uiSession, @Nullable InventoryCloseEvent closeEvent) {
		// Callback for subclasses.
	}

	// Handling of interface window interactions

	/**
	 * Additionally requested types of {@link InventoryEvent}s.
	 * <p>
	 * By default, only {@link InventoryClickEvent}, {@link InventoryDragEvent}, and
	 * {@link InventoryCloseEvent} are forwarded to {@link UIHandler}s. This method can be
	 * overridden by {@link UIHandler}s to request callbacks for additional types of inventory
	 * events via {@link #onInventoryEventEarly(UISession, InventoryEvent)} and
	 * {@link #onInventoryEventLate(UISession, InventoryEvent)}.
	 * <p>
	 * It is only effective to request event types for which a corresponding normally registered
	 * event handler would also be able to receive respective events. For example, it has no effect
	 * to request the base type {@link InventoryEvent}, because the various subtypes use their own
	 * {@link HandlerList}s to keep track of registered event handlers. The way the Bukkit event
	 * system works is that any called event is only forwarded to event handlers that have been
	 * registered at the closest {@link HandlerList} in the event's type hierarchy.
	 * <p>
	 * The returned Set of requested event types is expected to not change over time but be fixed.
	 * 
	 * @return the additionally requested inventory event types, not <code>null</code>
	 */
	protected Set<? extends Class<? extends InventoryEvent>> getAdditionalInventoryEvents() {
		return Collections.emptySet();
	}

	// Called by UIListener.
	void informOnInventoryEventEarly(UISession uiSession, InventoryEvent event) {
		this.onInventoryEventEarly(uiSession, event);

		// Invoke dedicated event handling methods:
		if (event instanceof InventoryClickEvent) {
			this.informOnInventoryClickEarly(uiSession, (InventoryClickEvent) event);
		} else if (event instanceof InventoryDragEvent) {
			this.informOnInventoryDragEarly(uiSession, (InventoryDragEvent) event);
		}
	}

	/**
	 * Called early ({@link EventPriority#HIGH}) for handled {@link InventoryEvent}s for inventory
	 * views that are {@link #isOpen(Player) managed} by this {@link UIHandler}.
	 * <p>
	 * This method is only guaranteed to be called for inventory events that are either handled by
	 * default, or that have been explicitly requested by this {@link UIHandler} via
	 * {@link #getAdditionalInventoryEvents()}. However, it is possible that this method may also be
	 * called for inventory events that have not been explicitly requested by this
	 * {@link UIHandler}. The {@link UIHandler} should therefore take care to ignore any unexpected
	 * types of inventory events.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param event
	 *            the inventory event, not <code>null</code>
	 * @see #onInventoryEventLate(UISession, InventoryEvent)
	 */
	protected void onInventoryEventEarly(UISession uiSession, InventoryEvent event) {
		// Callback for subclasses.
	}

	// Called by UIListener.
	void informOnInventoryEventLate(UISession uiSession, InventoryEvent event) {
		this.onInventoryEventLate(uiSession, event);

		// Invoke dedicated event handling methods:
		if (event instanceof InventoryClickEvent) {
			this.informOnInventoryClickLate(uiSession, (InventoryClickEvent) event);
		} else if (event instanceof InventoryDragEvent) {
			this.informOnInventoryDragLate(uiSession, (InventoryDragEvent) event);
		}
	}

	/**
	 * Called early ({@link EventPriority#LOW}) for handled {@link InventoryEvent}s for inventory
	 * views that are {@link #isOpen(Player) managed} by this {@link UIHandler}.
	 * <p>
	 * This method is only guaranteed to be called for inventory events that are either handled by
	 * default, or that have been explicitly requested by this {@link UIHandler} via
	 * {@link #getAdditionalInventoryEvents()}. However, it is possible that this method may also be
	 * called for inventory events that have not been explicitly requested by this
	 * {@link UIHandler}. The {@link UIHandler} should therefore take care to ignore any unexpected
	 * types of inventory events.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param event
	 *            the inventory event, not <code>null</code>
	 * @see #onInventoryEventEarly(UISession, InventoryEvent)
	 */
	protected void onInventoryEventLate(UISession uiSession, InventoryEvent event) {
		// Callback for subclasses.
	}

	/**
	 * Returns whether the currently handled inventory click is, according to our heuristic, an
	 * automatically triggered shift left-click due to a shift double left-click by the player.
	 * <p>
	 * Shift double left-clicks are supposed to move all matching items to the other inventory.
	 * Minecraft implements this by triggering a shift left-click with
	 * {@link InventoryAction#MOVE_TO_OTHER_INVENTORY} for all inventory slots that contain a
	 * matching item. Plugins cannot differentiate between these automatically triggered clicks and
	 * regular shift left-clicks by the player.
	 * <p>
	 * We use a heuristic to detect (and then possibly ignore) these automatically triggered clicks:
	 * We assume that any shift left-clicks that occur within
	 * {@link UIHandler#AUTOMATIC_SHIFT_LEFT_CLICK_NANOS} on a slot different to the previously
	 * clicked slot are automatically triggered.
	 * <p>
	 * Limitations (TODO): We cannot use a much lower time span (e.g. limiting it to 1 or 2 ticks),
	 * because the automatically triggered clicks may arrive quite some time later (up to 150 ms
	 * later on a local server and possibly more with network delay involved). Also, this does not
	 * work for automatic clicks triggered for the same slot. Since the automatically triggered
	 * clicks may arrive quite some time later, we cannot differentiate them from manual fast
	 * clicking.
	 * 
	 * @return <code>true</code> if we detected an automatically triggered shift left-click
	 */
	protected boolean isAutomaticShiftLeftClick() {
		return isAutomaticShiftLeftClick;
	}

	private void informOnInventoryClickEarly(UISession uiSession, InventoryClickEvent event) {
		// Heuristic detection of automatically triggered shift left-clicks:
		isAutomaticShiftLeftClick = false; // Reset
		final long nowNanos = System.nanoTime();
		if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
			if (event.getRawSlot() != lastManualClickedSlotId
					&& (nowNanos - lastManualClickNanos) < AUTOMATIC_SHIFT_LEFT_CLICK_NANOS) {
				isAutomaticShiftLeftClick = true;
				Log.debug("  Detected automatically triggered shift left-click! (on different slot)");
			}
		}
		// Note: We reset these for all types of clicks, because when quickly switching between
		// shift and non-shift clicking we sometimes receive non-shift clicks that are followed by
		// the automatic shift-clicks:
		if (!isAutomaticShiftLeftClick) {
			lastManualClickNanos = nowNanos;
			lastManualClickedSlotId = event.getRawSlot();
		}

		this.onInventoryClickEarly(uiSession, event);
	}

	/**
	 * Called early ({@link EventPriority#LOW}) for {@link InventoryClickEvent}s for inventory views
	 * that are {@link #isOpen(Player) managed} by this {@link UIHandler}.
	 * <p>
	 * Any UI potentially canceling the event should consider doing so early in order for other
	 * plugins to ignore the event.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param event
	 *            the inventory click event, not <code>null</code>
	 * @see #onInventoryClickLate(UISession, InventoryClickEvent)
	 */
	protected void onInventoryClickEarly(UISession uiSession, InventoryClickEvent event) {
		// Callback for subclasses.
	}

	private void informOnInventoryClickLate(UISession uiSession, InventoryClickEvent event) {
		this.onInventoryClickLate(uiSession, event);
	}

	/**
	 * Called late ({@link EventPriority#HIGH}) for {@link InventoryClickEvent}s for inventory views
	 * that are {@link #isOpen(Player) managed} by this {@link UIHandler}.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param event
	 *            the inventory click event, not <code>null</code>
	 * @see #onInventoryClickEarly(UISession, InventoryClickEvent)
	 */
	protected void onInventoryClickLate(UISession uiSession, InventoryClickEvent event) {
		// Callback for subclasses.
	}

	private void informOnInventoryDragEarly(UISession uiSession, InventoryDragEvent event) {
		this.onInventoryDragEarly(uiSession, event);
	}

	/**
	 * Called early ({@link EventPriority#LOW}) for {@link InventoryDragEvent}s for inventory views
	 * that are {@link #isOpen(Player) managed} by this {@link UIHandler}.
	 * <p>
	 * Any UI potentially canceling the event should consider doing so early in order for other
	 * plugins to ignore the event.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param event
	 *            the inventory drag event, not <code>null</code>
	 * @see #onInventoryDragLate(UISession, InventoryDragEvent)
	 */
	protected void onInventoryDragEarly(UISession uiSession, InventoryDragEvent event) {
		// Callback for subclasses.
	}

	private void informOnInventoryDragLate(UISession uiSession, InventoryDragEvent event) {
		this.onInventoryDragLate(uiSession, event);
	}

	/**
	 * Called late ({@link EventPriority#HIGH}) for {@link InventoryDragEvent}s for inventory views
	 * that are {@link #isOpen(Player) managed} by this {@link UIHandler}.
	 * 
	 * @param uiSession
	 *            the {@link UISession}, not <code>null</code>
	 * @param event
	 *            the inventory drag event, not <code>null</code>
	 * @see #onInventoryDragEarly(UISession, InventoryDragEvent)
	 */
	protected void onInventoryDragLate(UISession uiSession, InventoryDragEvent event) {
		// Callback for subclasses.
	}
}
