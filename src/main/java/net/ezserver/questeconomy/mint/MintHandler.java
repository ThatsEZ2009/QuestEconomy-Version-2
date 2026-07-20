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

/** The Coin Mint block + GUI: merge coins up and break them down. Two-way, value-exact. */
public class MintHandler implements Listener, CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // GUI slot -> action
    private static final int C_TO_S = 10, C_TO_G = 11, C_TO_D = 12, G_TO_D = 13;
    private static final int B_SILVER = 19, B_GOLD = 20, B_DIAMOND = 21;

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

    private static final class MintHolder implements InventoryHolder {
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
        int gld = plugin.coins().amountOf(p, CoinType.GOLD);
        int dia = plugin.coins().amountOf(p, CoinType.DIAMOND);
        int total = plugin.coins().countCoins(p);

        inv.setItem(4, named(Material.PAPER, "<gold>Your Wallet", List.of(
                "<gray>Copper: <white>" + cop + "</white>",
                "<gray>Silver: <white>" + sil + "</white>",
                "<gray>Gold: <white>" + gld + "</white>",
                "<gray>Diamond: <white>" + dia + "</white>",
                "<gray>Total value: <yellow>" + total + " coins</yellow>")));

        // Row labels
        inv.setItem(9, named(Material.LIME_STAINED_GLASS_PANE, "<green><bold>MERGE UP</bold>", List.of("<gray>Combine coins into bigger ones.", "<gray>Each click converts one.")));
        inv.setItem(18, named(Material.ORANGE_STAINED_GLASS_PANE, "<gold><bold>BREAK DOWN</bold>", List.of("<gray>Split a coin into smaller ones.", "<gray>Each click breaks one.")));

        inv.setItem(C_TO_S, coinButton(CoinType.SILVER, "<aqua>Copper → Silver", List.of("<gray>Costs <white>2 Copper</white>", "<gray>Gives <white>1 Silver</white>", "<yellow>Click to convert one")));
        inv.setItem(C_TO_G, coinButton(CoinType.GOLD, "<aqua>Copper → Gold", List.of("<gray>Costs <white>5 Copper</white>", "<gray>Gives <white>1 Gold</white>", "<yellow>Click to convert one")));
        inv.setItem(C_TO_D, coinButton(CoinType.DIAMOND, "<aqua>Copper → Diamond", List.of("<gray>Costs <white>10 Copper</white>", "<gray>Gives <white>1 Diamond</white>", "<yellow>Click to convert one")));
        inv.setItem(G_TO_D, coinButton(CoinType.DIAMOND, "<aqua>Gold → Diamond", List.of("<gray>Costs <white>2 Gold</white>", "<gray>Gives <white>1 Diamond</white>", "<yellow>Click to convert one")));

        inv.setItem(B_SILVER, coinButton(CoinType.SILVER, "<gold>Break Silver", List.of("<gray>Costs <white>1 Silver</white>", "<gray>Gives <white>2 Copper</white>", "<yellow>Click to break one")));
        inv.setItem(B_GOLD, coinButton(CoinType.GOLD, "<gold>Break Gold", List.of("<gray>Costs <white>1 Gold</white>", "<gray>Gives <white>5 Copper</white>", "<yellow>Click to break one")));
        inv.setItem(B_DIAMOND, coinButton(CoinType.DIAMOND, "<gold>Break Diamond", List.of("<gray>Costs <white>1 Diamond</white>", "<gray>Gives <white>2 Gold</white>", "<yellow>Click to break one")));

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MintHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        var coins = plugin.coins();
        boolean did = false;

        // Every button now converts exactly ONE step per click (merges and breaks both).
        switch (slot) {
            case C_TO_S -> { if (coins.amountOf(p, CoinType.COPPER)  >= 2)  { coins.removeType(p, CoinType.COPPER, 2);   coins.giveType(p, CoinType.SILVER, 1);  did = true; } }
            case C_TO_G -> { if (coins.amountOf(p, CoinType.COPPER)  >= 5)  { coins.removeType(p, CoinType.COPPER, 5);   coins.giveType(p, CoinType.GOLD, 1);    did = true; } }
            case C_TO_D -> { if (coins.amountOf(p, CoinType.COPPER)  >= 10) { coins.removeType(p, CoinType.COPPER, 10);  coins.giveType(p, CoinType.DIAMOND, 1); did = true; } }
            case G_TO_D -> { if (coins.amountOf(p, CoinType.GOLD)    >= 2)  { coins.removeType(p, CoinType.GOLD, 2);     coins.giveType(p, CoinType.DIAMOND, 1); did = true; } }
            case B_SILVER  -> { if (coins.amountOf(p, CoinType.SILVER)  >= 1) { coins.removeType(p, CoinType.SILVER, 1);  coins.giveType(p, CoinType.COPPER, 2); did = true; } }
            case B_GOLD    -> { if (coins.amountOf(p, CoinType.GOLD)    >= 1) { coins.removeType(p, CoinType.GOLD, 1);    coins.giveType(p, CoinType.COPPER, 5); did = true; } }
            case B_DIAMOND -> { if (coins.amountOf(p, CoinType.DIAMOND) >= 1) { coins.removeType(p, CoinType.DIAMOND, 1); coins.giveType(p, CoinType.GOLD, 2);   did = true; } }
            default -> { return; }
        }

        if (did) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
            open(p);
        } else {
            plugin.msg().send(p, "mint-nothing");
        }
    }

    // ---- item helpers ----
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
