package net.ezserver.questeconomy.util;

import org.bukkit.inventory.InventoryHolder;

/**
 * Marker for inventories owned by this plugin (Quest Board, Coin Mint, Homes menu).
 * Coin storage protection skips these, since our menus use coin items as buttons.
 */
public interface PluginGui extends InventoryHolder {
}
