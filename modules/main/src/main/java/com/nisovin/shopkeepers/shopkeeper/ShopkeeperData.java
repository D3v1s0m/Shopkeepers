package com.nisovin.shopkeepers.shopkeeper;

import com.nisovin.shopkeepers.util.data.DataContainer;
import com.nisovin.shopkeepers.util.data.DelegateDataContainer;

/**
 * A wrapper around the {@link DataContainer} that contains a shopkeeper's data.
 * <p>
 * This wrapper and its subclasses are expected to read and write through to the underlying data container. They shall
 * not copy or derive any state, because external components may directly access the underlying data container and
 * thereby bypass this wrapper.
 */
public class ShopkeeperData extends DelegateDataContainer {

	/**
	 * Gets a {@link ShopkeeperData} for the given data container.
	 * <p>
	 * If the given data container is already a {@link ShopkeeperData}, this returns the given data container itself.
	 * Otherwise, this returns a wrapper around the given data container.
	 * 
	 * @param dataContainer
	 *            the data container that contains the shopkeeper data, or <code>null</code>
	 * @return the shopkeeper data, or <code>null</code> if the given data container is <code>null</code>
	 */
	public static ShopkeeperData of(DataContainer dataContainer) {
		if (dataContainer == null) return null;
		if (dataContainer instanceof ShopkeeperData) {
			return (ShopkeeperData) dataContainer;
		} else {
			return new ShopkeeperData(dataContainer);
		}
	}

	/////

	/**
	 * Creates a new {@link ShopkeeperData}.
	 * 
	 * @param dataContainer
	 *            the underlying data container, not <code>null</code>
	 */
	protected ShopkeeperData(DataContainer dataContainer) {
		super(dataContainer);
	}
}
