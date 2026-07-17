package net.ezserver.questeconomy.coin;

import net.ezserver.questeconomy.QuestEconomy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Stops coins from being stored in ender chests / shulker boxes (and hoppered into them). */
public class CoinListener implements Listener {

    private final QuestEconomy plugin;
    private final CoinItem coins;

    public CoinListener(QuestEconomy plugin, CoinItem coins) {
        this.plugin = plugin;
        this.coins = coins;
    }

    private boolean blocked(InventoryType type) {
        if (type == InventoryType.ENDER_CHEST)
            return plugin.getConfig().getBoolean("protection.block-ender-chests", true);
        if (type == InventoryType.SHULKER_BOX)
            return plugin.getConfig().getBoolean("protection.block-shulker-boxes", true);
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top == null || !blocked(top.getType())) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        // Shift-clicking a coin from the player inventory into the blocked container.
        if (e.getClick().isShiftClick() && e.getClickedInventory() != null
                && e.getClickedInventory().getType() != top.getType()
                && coins.isCoin(e.getCurrentItem())) {
            deny(e);
            return;
        }

        // Placing / swapping a coin directly into the blocked container.
        if (e.getClickedInventory() != null && e.getClickedInventory().getType() == top.getType()) {
            if (coins.isCoin(e.getCursor()) || coins.isCoin(e.getCurrentItem())) {
                deny(e);
                return;
            }
            if (e.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hot = ((Player) e.getWhoClicked()).getInventory().getItem(e.getHotbarButton());
                if (coins.isCoin(hot)) deny(e);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top == null || !blocked(top.getType())) return;
        if (!coins.isCoin(e.getOldCursor())) return;
        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (!plugin.getConfig().getBoolean("protection.block-hopper-transfer", true)) return;
        if (!coins.isCoin(e.getItem())) return;
        InventoryType dest = e.getDestination().getType();
        if (dest == InventoryType.SHULKER_BOX || dest == InventoryType.ENDER_CHEST) {
            e.setCancelled(true);
        }
    }

    private void deny(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p) {
            plugin.msg().send(p, "coin-blocked-storage");
        }
    }
}
