package com.nisovin.shopkeepers.shopobjects.living.types;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Squid;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.compat.MC_1_17;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopobjects.ShopObjectData;
import com.nisovin.shopkeepers.shopobjects.living.LivingShops;
import com.nisovin.shopkeepers.shopobjects.living.SKLivingShopObject;
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

// TODO Use actual GlowSquid type once we only support Bukkit 1.17 upwards
public class GlowSquidShop extends SKLivingShopObject<Squid> {

	public static final Property<Boolean> DARK = new BasicProperty<Boolean>()
			.dataKeyAccessor("darkGlowSquid", BooleanSerializers.LENIENT)
			.defaultValue(false)
			.build();

	private final PropertyValue<Boolean> darkGlowSquidProperty = new PropertyValue<>(DARK)
			.onValueChanged(Unsafe.initialized(this)::applyDark)
			.build(properties);

	public GlowSquidShop(
			LivingShops livingShops,
			SKLivingShopObjectType<GlowSquidShop> livingObjectType,
			AbstractShopkeeper shopkeeper,
			@Nullable ShopCreationData creationData
	) {
		super(livingShops, livingObjectType, shopkeeper, creationData);
	}

	@Override
	public void load(ShopObjectData shopObjectData) throws InvalidDataException {
		super.load(shopObjectData);
		darkGlowSquidProperty.load(shopObjectData);
	}

	@Override
	public void save(ShopObjectData shopObjectData, boolean saveAll) {
		super.save(shopObjectData, saveAll);
		darkGlowSquidProperty.save(shopObjectData);
	}

	@Override
	protected void onSpawn() {
		super.onSpawn();
		this.applyDark();
	}

	@Override
	public List<Button> createEditorButtons() {
		List<Button> editorButtons = super.createEditorButtons();
		editorButtons.add(this.getDarkEditorButton());
		return editorButtons;
	}

	// DARK

	public boolean isDark() {
		return darkGlowSquidProperty.getValue();
	}

	public void setDark(boolean dark) {
		darkGlowSquidProperty.setValue(dark);
	}

	public void cycleDark(boolean backwards) {
		this.setDark(!this.isDark());
	}

	private void applyDark() {
		Squid entity = this.getEntity();
		if (entity == null) return; // Not spawned

		NMSManager.getProvider().setGlowSquidDark(entity, this.isDark());
	}

	private ItemStack getDarkEditorItem() {
		ItemStack iconItem;
		if (this.isDark()) {
			iconItem = new ItemStack(Material.INK_SAC);
		} else {
			Material iconType = Unsafe.assertNonNull(MC_1_17.GLOW_INK_SAC.orElse(Material.INK_SAC));
			iconItem = new ItemStack(iconType);
		}
		ItemUtils.setDisplayNameAndLore(iconItem,
				Messages.buttonGlowSquidDark,
				Messages.buttonGlowSquidDarkLore
		);
		return iconItem;
	}

	private Button getDarkEditorButton() {
		return new ShopkeeperActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				return getDarkEditorItem();
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				boolean backwards = clickEvent.isRightClick();
				cycleDark(backwards);
				return true;
			}
		};
	}
}
