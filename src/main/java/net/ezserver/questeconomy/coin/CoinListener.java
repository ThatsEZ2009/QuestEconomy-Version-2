package net.ezserver.questeconomy.coin;

import net.ezserver.questeconomy.QuestEconomy;
import net.ezserver.questeconomy.util.PluginGui;
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

/**
 * Coin storage rules.
 * Carry-only (default): coins may not be placed in ANY container — only carried or dropped.
 * If carry-only is off, only ender chests and shulker boxes are blocked.
 */
public class CoinListener implements Listener {

    private final QuestEconomy plugin;
    private final CoinItem coins;

    public CoinListener(QuestEconomy plugin, CoinItem coins) {
        this.plugin = plugin;
        this.coins = coins;
    }

    private boolean carryOnly() {
        return plugin.getConfig().getBoolean("protection.carry-only", true);
    }

    /** True if coins must not be allowed into this inventory type. */
    private boolean blocked(InventoryType type) {
        if (carryOnly()) {
            // Everything except the player's own inventory / personal 2x2 crafting grid.
            return type != InventoryType.PLAYER && type != InventoryType.CRAFTING;
        }
        if (type == InventoryType.ENDER_CHEST)
            return plugin.getConfig().getBoolean("protection.block-ender-chests", true);
        if (type == InventoryType.SHULKER_BOX)
            return plugin.getConfig().getBoolean("protection.block-shulker-boxes", true);
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top == null) return;
        if (top.getHolder() instanceof PluginGui) return; // our own menus use coin items as buttons
        if (!blocked(top.getType())) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        // Shift-clicking a coin from the player inventory into the container.
        if (e.getClick().isShiftClick() && e.getClickedInventory() != null
                && !e.getClickedInventory().equals(top)
                && coins.isCoin(e.getCurrentItem())) {
            deny(e);
            return;
        }

        // Placing / swapping a coin directly into the container.
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(top)) {
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
        if (top == null) return;
        if (top.getHolder() instanceof PluginGui) return;
        if (!blocked(top.getType())) return;
        if (!coins.isCoin(e.getOldCursor())) return;
        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** Stop hoppers / droppers piping coins into storage. */
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (!coins.isCoin(e.getItem())) return;
        if (carryOnly()) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.getConfig().getBoolean("protection.block-hopper-transfer", true)) return;
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
