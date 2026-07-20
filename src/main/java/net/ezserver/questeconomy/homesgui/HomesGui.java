package net.ezserver.questeconomy.homesgui;

import net.ezserver.questeconomy.QuestEconomy;
import net.ezserver.questeconomy.teleport.TeleportHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.user.OnlineUser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A native homes menu that shows each home's coin cost and charges (with confirm) on click. */
public class HomesGui implements Listener, CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final QuestEconomy plugin;
    private final TeleportHandler teleport;
    private HuskHomesAPI api;
    private final Map<UUID, List<Entry>> viewing = new HashMap<>();

    private record Entry(String name, Location loc, int cost) {}

    public HomesGui(QuestEconomy plugin, TeleportHandler teleport) {
        this.plugin = plugin;
        this.teleport = teleport;
    }

    /** Hook the HuskHomes API. Returns false if HuskHomes isn't installed. */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("HuskHomes") == null) return false;
        try {
            this.api = HuskHomesAPI.getInstance();
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook the HuskHomes API: " + t.getMessage());
            return false;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { plugin.msg().send(sender, "players-only"); return true; }
        openFor(p);
        return true;
    }

    /** Intercept HuskHomes' /homelist so our costed menu opens instead. */
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        String cmd = (msg.startsWith("/") ? msg.substring(1) : msg).split(" ")[0].toLowerCase();
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (cmd.equals("homelist") || cmd.equals("homes")) {
            e.setCancelled(true);
            openFor(e.getPlayer());
        }
    }

    private void openFor(Player p) {
        if (api == null) { p.sendMessage(MM.deserialize("<red>Homes are unavailable right now.")); return; }
        OnlineUser user = api.adaptUser(p);
        api.getUserHomes(user).thenAccept(homes ->
                Bukkit.getScheduler().runTask(plugin, () -> build(p, homes)));
    }

    private void build(Player p, List<Home> homes) {
        List<Entry> entries = new ArrayList<>();
        for (Home h : homes) {
            World w = Bukkit.getWorld(h.getWorld().getName());
            if (w == null) continue;
            Location loc = new Location(w, h.getX(), h.getY(), h.getZ());
            int cost = teleport.homeTeleportCost(p.getLocation(), loc);
            entries.add(new Entry(h.getName(), loc, cost));
        }
        viewing.put(p.getUniqueId(), entries);

        int rows = Math.max(1, (int) Math.ceil(entries.size() / 9.0));
        int size = Math.min(54, rows * 9);
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, size, MM.deserialize("<dark_aqua>Your Homes"));
        holder.inv = inv;

        for (int i = 0; i < entries.size() && i < size; i++) {
            Entry e = entries.get(i);
            String costLine = e.cost() <= 0 ? "<green>Free" : "<yellow>" + e.cost() + " Copper Coins";
            inv.setItem(i, item(Material.RED_BED, "<aqua>" + e.name(),
                    List.of("<gray>Cost to teleport: " + costLine, "<yellow>Click to teleport")));
        }
        if (entries.isEmpty()) {
            inv.setItem(0, item(Material.BARRIER, "<red>No homes yet", List.of("<gray>Use /sethome to make one.")));
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        List<Entry> entries = viewing.get(p.getUniqueId());
        if (entries == null || slot < 0 || slot >= entries.size()) return;
        Entry entry = entries.get(slot);
        p.closeInventory();
        teleport.requestPaidTeleport(p, entry.loc(), entry.cost(), entry.name());
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(MM.deserialize(s).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }
}
