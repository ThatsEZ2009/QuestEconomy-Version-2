package net.ezserver.questeconomy.mint;

import net.ezserver.questeconomy.QuestEconomy;
import net.ezserver.questeconomy.coin.CoinType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** The Coin Mint: convert Copper <-> Silver (Silver is worth 3 Copper). One coin per click. */
public class MintHandler implements Listener, CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final int MERGE_SLOT = 11;   // 3 Copper -> 1 Silver
    private static final int BREAK_SLOT = 15;   // 1 Silver -> 3 Copper

    private final QuestEconomy plugin;

    public MintHandler(QuestEconomy plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !plugin.quests().isMint(b.getLocation())) return;
        e.setCancelled(true);
        open(e.getPlayer());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { plugin.msg().send(sender, "players-only"); return true; }
        open(p);
        return true;
    }

    private static final class MintHolder implements net.ezserver.questeconomy.util.PluginGui {
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        MintHolder holder = new MintHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, MM.deserialize("<gold>Coin Mint"));
        holder.inv = inv;

        ItemStack filler = simple(Material.BROWN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int cop = plugin.coins().amountOf(p, CoinType.COPPER);
        int sil = plugin.coins().amountOf(p, CoinType.SILVER);
        int total = plugin.coins().countCoins(p);

        inv.setItem(4, named(Material.PAPER, "<gold>Your Wallet", List.of(
                "<#e0913a>Copper: <white>" + cop + "</white>",
                "<#c2c8ce>Silver: <white>" + sil + "</white>",
                "<gray>Total value: <yellow>" + total + " coins</yellow>",
                "<dark_gray>1 Silver = 3 Copper")));

        inv.setItem(MERGE_SLOT, coinButton(CoinType.SILVER, "<green>Copper → Silver", List.of(
                "<gray>Costs <#e0913a>3 Copper</#e0913a>",
                "<gray>Gives <#c2c8ce>1 Silver</#c2c8ce>",
                "<yellow>Click to convert one")));

        inv.setItem(BREAK_SLOT, coinButton(CoinType.COPPER, "<gold>Break Silver", List.of(
                "<gray>Costs <#c2c8ce>1 Silver</#c2c8ce>",
                "<gray>Gives <#e0913a>3 Copper</#e0913a>",
                "<yellow>Click to break one")));

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MintHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        var coins = plugin.coins();
        boolean did = false;

        if (slot == MERGE_SLOT) {
            if (coins.amountOf(p, CoinType.COPPER) >= 3) {
                coins.removeType(p, CoinType.COPPER, 3);
                coins.giveType(p, CoinType.SILVER, 1);
                did = true;
            }
        } else if (slot == BREAK_SLOT) {
            if (coins.amountOf(p, CoinType.SILVER) >= 1) {
                coins.removeType(p, CoinType.SILVER, 1);
                coins.giveType(p, CoinType.COPPER, 3);
                did = true;
            }
        } else {
            return;
        }

        if (did) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
            open(p);
        } else {
            plugin.msg().send(p, "mint-nothing");
        }
    }

    private ItemStack coinButton(CoinType icon, String name, List<String> lore) {
        ItemStack it = plugin.coinItem().create(icon, 1);
        applyName(it, name, lore);
        return it;
    }

    private ItemStack simple(Material m, String name) {
        ItemStack it = new ItemStack(m);
        applyName(it, name, List.of());
        return it;
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        applyName(it, name, lore);
        return it;
    }

    private void applyName(ItemStack it, String name, List<String> loreLines) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String s : loreLines) lore.add(MM.deserialize(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }
        it.setItemMeta(meta);
    }
}
