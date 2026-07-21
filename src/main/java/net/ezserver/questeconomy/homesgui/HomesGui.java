package net.ezserver.questeconomy.homesgui;

import net.ezserver.questeconomy.QuestEconomy;
import net.ezserver.questeconomy.homes.HomeService;
import net.ezserver.questeconomy.teleport.TeleportHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.user.OnlineUser;
import net.william278.huskhomes.user.User;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Homes menu: 10 slots showing owned homes (teleport cost) and buyable slots (unlock price). */
public class HomesGui implements Listener, CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};
    private static final int SPAWN_SLOT = 16;
    private static final int RTP_SLOT = 25;

    private final QuestEconomy plugin;
    private final TeleportHandler teleport;
    private final HomeService homeService;
    private HuskHomesAPI api;
    private final Map<UUID, Slot[]> viewing = new HashMap<>();
    /** Players currently looking at someone else's homes (admin view) - no spawn/rtp/buy buttons. */
    private final Set<UUID> adminView = new HashSet<>();

    private enum Kind { HOME, BUY, NONE }
    private record Slot(Kind kind, Location loc, int cost) {}

    public HomesGui(QuestEconomy plugin, TeleportHandler teleport, HomeService homeService) {
        this.plugin = plugin;
        this.teleport = teleport;
        this.homeService = homeService;
    }

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

    /** Intercept HuskHomes' /homelist and /home so our costed menu opens instead. */
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        String cmd = (msg.startsWith("/") ? msg.substring(1) : msg).split(" ")[0].toLowerCase();
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        // Plain /home or /homes or /homelist -> our menu. (/home <name> is left to HuskHomes so it teleports + charges.)
        boolean bare = e.getMessage().trim().split(" ").length == 1;
        if (cmd.equals("homelist") || cmd.equals("homes") || (cmd.equals("home") && bare)) {
            e.setCancelled(true);
            openFor(e.getPlayer());
        }
    }

    private void openFor(Player p) {
        if (api == null) { p.sendMessage(MM.deserialize("<red>Homes are unavailable right now.")); return; }
        adminView.remove(p.getUniqueId());
        OnlineUser user = api.adaptUser(p);
        api.getUserHomes(user).thenAccept(homes ->
                Bukkit.getScheduler().runTask(plugin, () -> build(p, homes)));
    }

    /** Admin view: browse another player's homes. Teleports are free and there's no buying. */
    public void openForTarget(Player viewer, UUID targetId, String targetName) {
        if (api == null) { viewer.sendMessage(MM.deserialize("<red>Homes are unavailable right now.")); return; }
        adminView.add(viewer.getUniqueId());
        User target = User.of(targetId, targetName);
        api.getUserHomes(target).thenAccept(homes ->
                Bukkit.getScheduler().runTask(plugin, () -> buildAdmin(viewer, homes, targetName)));
    }

    private void buildAdmin(Player viewer, List<Home> homes, String targetName) {
        Slot[] slots = new Slot[SLOTS.length];
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, MM.deserialize("<dark_aqua>" + targetName + "'s Homes"));
        holder.inv = inv;

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(4, named(Material.ENDER_PEARL, "<dark_aqua>" + targetName + "'s Homes",
                List.of("<gray>Homes set: <white>" + homes.size() + "</white>",
                        "<yellow>Click one to teleport there (free)")));

        for (int i = 0; i < SLOTS.length; i++) {
            if (i < homes.size()) {
                Home h = homes.get(i);
                World w = Bukkit.getWorld(h.getWorld().getName());
                if (w == null) { slots[i] = new Slot(Kind.NONE, null, 0); continue; }
                Location loc = new Location(w, h.getX(), h.getY(), h.getZ());
                inv.setItem(SLOTS[i], named(Material.RED_BED, "<aqua>" + h.getName(),
                        List.of("<gray>" + w.getName() + "  <white>"
                                        + (int) h.getX() + ", " + (int) h.getY() + ", " + (int) h.getZ(),
                                "<yellow>Click to teleport (free)")));
                slots[i] = new Slot(Kind.HOME, loc, 0); // cost 0 = free for admins
            } else {
                slots[i] = new Slot(Kind.NONE, null, 0);
            }
        }
        viewing.put(viewer.getUniqueId(), slots);
        viewer.openInventory(inv);
    }

    private void build(Player p, List<Home> homes) {
        int limit = homeService.currentLimit(p);
        int max = homeService.maxHomes();

        Slot[] slots = new Slot[SLOTS.length];
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, MM.deserialize("<dark_aqua>Your Homes"));
        holder.inv = inv;

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(4, named(Material.ENDER_PEARL, "<dark_aqua>Your Homes", List.of(
                "<gray>Homes: <white>" + homes.size() + " / " + limit + "</white>",
                "<gray>Buy locked slots below.")));

        for (int i = 0; i < SLOTS.length; i++) {
            int homeNum = i + 1;                 // 1..10
            int slotPos = SLOTS[i];
            if (i < homes.size()) {
                // owned home
                Home h = homes.get(i);
                World w = Bukkit.getWorld(h.getWorld().getName());
                if (w == null) { slots[i] = new Slot(Kind.NONE, null, 0); continue; }
                Location loc = new Location(w, h.getX(), h.getY(), h.getZ());
                int cost = teleport.homeTeleportCost(p.getLocation(), loc);
                String costLine = cost <= 0 ? "<green>Free" : "<#e0913a>" + cost + " Copper Coins";
                inv.setItem(slotPos, named(Material.RED_BED, "<aqua>" + h.getName(),
                        List.of("<gray>Teleport cost: " + costLine, "<yellow>Click to teleport")));
                slots[i] = new Slot(Kind.HOME, loc, cost);
            } else if (homeNum <= limit) {
                // unlocked but empty
                inv.setItem(slotPos, named(Material.LIME_STAINED_GLASS_PANE, "<green>Empty slot #" + homeNum,
                        List.of("<gray>Use <white>/sethome</white> here to fill it.")));
                slots[i] = new Slot(Kind.NONE, null, 0);
            } else if (homeNum == limit + 1 && limit < max) {
                // next buyable slot
                int price = homeService.priceForHome(homeNum);
                inv.setItem(slotPos, named(Material.GOLD_INGOT, "<gold>Unlock Home #" + homeNum,
                        List.of("<gray>Cost: <#e0913a>" + price + " Copper Coins</#e0913a>", "<yellow>Click to buy")));
                slots[i] = new Slot(Kind.BUY, null, price);
            } else {
                // locked (must buy earlier ones first)
                int price = homeService.priceForHome(homeNum);
                inv.setItem(slotPos, named(Material.BARRIER, "<dark_gray>Home #" + homeNum + " (locked)",
                        List.of("<gray>Unlock the earlier slots first.", "<dark_gray>Eventual cost: " + price + " Copper Coins")));
                slots[i] = new Slot(Kind.NONE, null, 0);
            }
        }
        // Extra travel options on the right-hand columns
        inv.setItem(SPAWN_SLOT, named(Material.COMPASS, "<green>Spawn",
                List.of("<gray>Teleport to server spawn.", "<green>Free", "<yellow>Click to go")));
        inv.setItem(RTP_SLOT, named(Material.ENDER_EYE, "<light_purple>Random Teleport",
                List.of("<gray>Drop somewhere random in the wild.", "<green>Free", "<yellow>Click to go")));

        viewing.put(p.getUniqueId(), slots);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        boolean admin = adminView.contains(p.getUniqueId());
        if (!admin) {
            if (raw == SPAWN_SLOT) { p.closeInventory(); p.performCommand("spawn"); return; }
            if (raw == RTP_SLOT)   { p.closeInventory(); p.performCommand("rtp"); return; }
        }

        Slot[] slots = viewing.get(p.getUniqueId());
        if (slots == null) return;
        int idx = -1;
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == raw) { idx = i; break; }
        if (idx < 0) return;
        Slot s = slots[idx];
        if (s == null || s.kind() == Kind.NONE) return;

        if (s.kind() == Kind.HOME) {
            p.closeInventory();
            teleport.requestPaidTeleport(p, s.loc(), s.cost(), "your home");
        } else if (s.kind() == Kind.BUY) {
            if (homeService.buyNextSlot(p)) {
                openFor(p); // refresh
            }
        }
    }

    private static final class Holder implements net.ezserver.questeconomy.util.PluginGui {
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(MM.deserialize(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }
}
