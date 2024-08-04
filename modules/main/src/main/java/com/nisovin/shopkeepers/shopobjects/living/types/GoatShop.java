package com.nisovin.shopkeepers.shopobjects.living.types;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.compat.MC_1_19;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopobjects.ShopObjectData;
import com.nisovin.shopkeepers.shopobjects.living.LivingShops;
import com.nisovin.shopkeepers.shopobjects.living.SKLivingShopObjectType;
import com.nisovin.shopkeepers.ui.editor.Button;
import com.nisovin.shopkeepers.ui.editor.EditorSession;
import com.nisovin.shopkeepers.ui.editor.ShopkeeperActionButton;
import com.nisovin.shopkeepers.util.data.property.BasicProperty;
import com.nisovin.shopkeepers.util.data.property.Property;
import com.nisovin.shopkeepers.util.data.property.value.PropertyValue;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.data.serialization.java.BooleanSerializers;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;

// TODO Use actual Goal type once we only support Bukkit 1.17 upwards
public class GoatShop extends BabyableShop<Animals> {

	public static final Property<Boolean> SCREAMING = new BasicProperty<Boolean>()
			.dataKeyAccessor("screaming", BooleanSerializers.LENIENT)
			.defaultValue(false)
			.build();

	public static final Property<Boolean> LEFT_HORN = new BasicProperty<Boolean>()
			.dataKeyAccessor("leftHorn", BooleanSerializers.LENIENT)
			.defaultValue(true)
			.build();

	public static final Property<Boolean> RIGHT_HORN = new BasicProperty<Boolean>()
			.dataKeyAccessor("rightHorn", BooleanSerializers.LENIENT)
			.defaultValue(true)
			.build();

	private final PropertyValue<Boolean> screamingProperty = new PropertyValue<>(SCREAMING)
			.onValueChanged(Unsafe.initialized(this)::applyScreaming)
			.build(properties);
	private final PropertyValue<Boolean> leftHornProperty = new PropertyValue<>(LEFT_HORN)
			.onValueChanged(Unsafe.initialized(this)::applyLeftHorn)
			.build(properties);
	private final PropertyValue<Boolean> rightHornProperty = new PropertyValue<>(RIGHT_HORN)
			.onValueChanged(Unsafe.initialized(this)::applyRightHorn)
			.build(properties);

	public GoatShop(
			LivingShops livingShops,
			SKLivingShopObjectType<GoatShop> livingObjectType,
			AbstractShopkeeper shopkeeper,
			@Nullable ShopCreationData creationData
	) {
		super(livingShops, livingObjectType, shopkeeper, creationData);
	}

	@Override
	public void load(ShopObjectData shopObjectData) throws InvalidDataException {
		super.load(shopObjectData);
		screamingProperty.load(shopObjectData);
		leftHornProperty.load(shopObjectData);
		rightHornProperty.load(shopObjectData);
	}

	@Override
	public void save(ShopObjectData shopObjectData, boolean saveAll) {
		super.save(shopObjectData, saveAll);
		screamingProperty.save(shopObjectData);
		leftHornProperty.save(shopObjectData);
		rightHornProperty.save(shopObjectData);
	}

	@Override
	protected void onSpawn() {
		super.onSpawn();
		this.applyScreaming();
		this.applyLeftHorn();
		this.applyRightHorn();
	}

	@Override
	public List<Button> createEditorButtons() {
		List<Button> editorButtons = super.createEditorButtons();
		// The screaming option is hidden if shopkeeper mobs are silent.
		if (!Settings.silenceLivingShopEntities) {
			editorButtons.add(this.getScreamingEditorButton());
		}
		if (MC_1_19.isAvailable()) {
			editorButtons.add(this.getLeftHornEditorButton());
			editorButtons.add(this.getRightHornEditorButton());
		}
		return editorButtons;
	}

	// SCREAMING

	public boolean isScreaming() {
		return screamingProperty.getValue();
	}

	public void setScreaming(boolean screaming) {
		screamingProperty.setValue(screaming);
	}

	public void cycleScreaming(boolean backwards) {
		this.setScreaming(!this.isScreaming());
	}

	private void applyScreaming() {
		Animals entity = this.getEntity();
		if (entity == null) return; // Not spawned

		NMSManager.getProvider().setScreamingGoat(entity, this.isScreaming());
	}

	private ItemStack getScreamingEditorItem() {
		ItemStack iconItem;
		if (this.isScreaming()) {
			iconItem = new ItemStack(Material.CARVED_PUMPKIN);
		} else {
			iconItem = new ItemStack(Material.PUMPKIN);
		}
		ItemUtils.setDisplayNameAndLore(iconItem,
				Messages.buttonGoatScreaming,
				Messages.buttonGoatScreamingLore
		);
		return iconItem;
	}

	private Button getScreamingEditorButton() {
		return new ShopkeeperActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				return getScreamingEditorItem();
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				boolean backwards = clickEvent.isRightClick();
				cycleScreaming(backwards);
				return true;
			}
		};
	}

	// LEFT HORN

	public boolean hasLeftHorn() {
		return leftHornProperty.getValue();
	}

	public void setLeftHorn(boolean hasLeftHorn) {
		leftHornProperty.setValue(hasLeftHorn);
	}

	public void cycleLeftHorn(boolean backwards) {
		this.setLeftHorn(!this.hasLeftHorn());
	}

	private void applyLeftHorn() {
		Animals entity = this.getEntity();
		if (entity == null) return; // Not spawned

		NMSManager.getProvider().setGoatLeftHorn(entity, this.hasLeftHorn());
	}

	private ItemStack getLeftHornEditorItem() {
		ItemStack iconItem;
		if (this.hasLeftHorn()) {
			Material iconType = Unsafe.assertNonNull(
					MC_1_19.GOAT_HORN.orElse(Material.LIME_STAINED_GLASS_PANE)
			);
			iconItem = new ItemStack(iconType);
		} else {
			iconItem = new ItemStack(Material.BARRIER);
		}
		return ItemUtils.setDisplayNameAndLore(iconItem,
				Messages.buttonGoatLeftHorn,
				Messages.buttonGoatLeftHornLore
		);
	}

	private Button getLeftHornEditorButton() {
		return new ShopkeeperActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				return getLeftHornEditorItem();
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				boolean backwards = clickEvent.isRightClick();
				cycleLeftHorn(backwards);
				return true;
			}
		};
	}

	// RIGHT HORN

	public boolean hasRightHorn() {
		return rightHornProperty.getValue();
	}

	public void setRightHorn(boolean hasRightHorn) {
		rightHornProperty.setValue(hasRightHorn);
	}

	public void cycleRightHorn(boolean backwards) {
		this.setRightHorn(!this.hasRightHorn());
	}

	private void applyRightHorn() {
		Animals entity = this.getEntity();
		if (entity == null) return; // Not spawned

		NMSManager.getProvider().setGoatRightHorn(entity, this.hasRightHorn());
	}

	private ItemStack getRightHornEditorItem() {
		ItemStack iconItem;
		if (this.hasRightHorn()) {
			Material iconType = Unsafe.assertNonNull(
					MC_1_19.GOAT_HORN.orElse(Material.LIME_STAINED_GLASS_PANE)
			);
			iconItem = new ItemStack(iconType);
		} else {
			iconItem = new ItemStack(Material.BARRIER);
		}
		return ItemUtils.setDisplayNameAndLore(iconItem,
				Messages.buttonGoatRightHorn,
				Messages.buttonGoatRightHornLore
		);
	}

	private Button getRightHornEditorButton() {
		return new ShopkeeperActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				return getRightHornEditorItem();
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				boolean backwards = clickEvent.isRightClick();
				cycleRightHorn(backwards);
				return true;
			}
		};
	}
}
