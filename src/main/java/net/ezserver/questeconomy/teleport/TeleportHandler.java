package net.ezserver.questeconomy.teleport;

import net.ezserver.questeconomy.QuestEconomy;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Charges coins for HuskHomes teleports without depending on the HuskHomes library.
 * It notes which command a player ran (/home or /tpa), then catches the resulting
 * Bukkit teleport, cancels it, shows a confirm, and re-teleports after payment.
 */
public class TeleportHandler implements Listener, CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private enum Intent { HOME, TPA }
    private record IntentData(Intent type, long expiry) {}
    private record Pending(Location dest, int cost, String label) {}

    private final QuestEconomy plugin;
    private final Map<UUID, IntentData> intents = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();

    public TeleportHandler(QuestEconomy plugin) { this.plugin = plugin; }

    // 1) Note the command the player ran so we know how to price the teleport it triggers.
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String cmd = raw.split(" ")[0].toLowerCase();
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        long now = System.currentTimeMillis();
        UUID id = e.getPlayer().getUniqueId();
        if (cmd.equals("home")) intents.put(id, new IntentData(Intent.HOME, now + 20_000L));
        else if (cmd.equals("tpa")) intents.put(id, new IntentData(Intent.TPA, now + 60_000L));
    }

    // 2) Catch the actual teleport, price it, and gate it behind a confirm.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (bypass.remove(id)) return; // this is our own post-payment teleport

        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.PLUGIN && cause != PlayerTeleportEvent.TeleportCause.COMMAND) return;

        IntentData it = intents.remove(id);
        if (it == null || System.currentTimeMillis() > it.expiry()) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        int cost;
        String label;
        if (it.type() == Intent.HOME) { cost = homeCost(from, to); label = "your home"; }
        else { cost = plugin.getConfig().getInt("teleport.tpa-cost", 1); label = "your friend"; }

        if (cost <= 0) return; // free teleport, let it happen

        if (!plugin.coins().has(p, cost)) {
            e.setCancelled(true);
            plugin.msg().send(p, "tp-need-coins", "cost", String.valueOf(cost));
            return;
        }

        if (!plugin.getConfig().getBoolean("teleport.confirm", true)) {
            plugin.coins().pay(p, cost);
            plugin.msg().send(p, "tp-paid", "cost", String.valueOf(cost));
            return; // let the teleport proceed
        }

        // Confirm flow: cancel this teleport, remember the destination, ask to confirm.
        e.setCancelled(true);
        pending.put(id, new Pending(to.clone(), cost, label));
        p.sendMessage(MM.deserialize("<gold>[Quests]</gold> <gray>Teleport to <white>" + label
                + "</white> for <yellow>" + cost + " Copper Coins</yellow>? "
                + "<click:run_command:'/qetp'><green><bold>[Confirm]</bold></green></click> "
                + "<click:run_command:'/qetpcancel'><red>[Cancel]</red></click>"));
    }

    private int homeCost(Location from, Location to) {
        var cfg = plugin.getConfig();
        if (from.getWorld() != to.getWorld()) return cfg.getInt("teleport.home.cross-world-cost", 3);
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= cfg.getInt("teleport.home.free-distance", 150)) return 0;
        if (d <= cfg.getInt("teleport.home.d1-max", 2500)) return cfg.getInt("teleport.home.d1-cost", 1);
        if (d <= cfg.getInt("teleport.home.d2-max", 4000)) return cfg.getInt("teleport.home.d2-cost", 2);
        if (d <= cfg.getInt("teleport.home.d3-max", 6000)) return cfg.getInt("teleport.home.d3-cost", 3);
        return cfg.getInt("teleport.home.cap-cost", 4);
    }

    // 3) Confirm / cancel commands (run by the clickable message).
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return true;
        UUID id = p.getUniqueId();
        if (command.getName().equalsIgnoreCase("qetpcancel")) {
            pending.remove(id);
            plugin.msg().send(p, "tp-cancelled");
            return true;
        }
        Pending pd = pending.remove(id);
        if (pd == null) { plugin.msg().send(p, "tp-nothing"); return true; }
        if (!plugin.coins().has(p, pd.cost())) { plugin.msg().send(p, "tp-need-coins", "cost", String.valueOf(pd.cost())); return true; }
        plugin.coins().pay(p, pd.cost());
        bypass.add(id);
        p.teleport(pd.dest());
        plugin.msg().send(p, "tp-paid", "cost", String.valueOf(pd.cost()));
        return true;
    }
}
