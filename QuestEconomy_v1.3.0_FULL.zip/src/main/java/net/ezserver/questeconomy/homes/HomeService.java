package net.ezserver.questeconomy.homes;

import net.ezserver.questeconomy.QuestEconomy;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Purchasable extra homes. Stores purchase counts and grants HuskHomes' max_homes permission. */
public class HomeService implements Listener, CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final QuestEconomy plugin;
    private final Map<UUID, Integer> purchased = new HashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private File file;
    private FileConfiguration data;

    public HomeService(QuestEconomy plugin) { this.plugin = plugin; }

    public void load() {
        file = new File(plugin.getDataFolder(), "homedata.yml");
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
        purchased.clear();
        ConfigurationSection ps = data.getConfigurationSection("players");
        if (ps != null) for (String k : ps.getKeys(false)) {
            try { purchased.put(UUID.fromString(k), ps.getInt(k)); } catch (Exception ignored) {}
        }
        for (Player p : Bukkit.getOnlinePlayers()) applyLimit(p);
    }

    public void save() {
        if (data == null) return;
        data.set("players", null);
        for (Map.Entry<UUID, Integer> e : purchased.entrySet()) data.set("players." + e.getKey(), e.getValue());
        try { data.save(file); } catch (IOException ex) { plugin.getLogger().warning("Could not save homedata.yml: " + ex.getMessage()); }
    }

    private int freeHomes() { return plugin.getConfig().getInt("homes.free", 2); }

    private int bought(UUID id) { return purchased.getOrDefault(id, 0); }

    /** Price of the NEXT home slot for a player, or -1 if disabled. */
    private int nextPrice(UUID id) {
        List<Integer> prices = plugin.getConfig().getIntegerList("homes.prices");
        int b = bought(id);
        if (b < prices.size()) return prices.get(b);
        return plugin.getConfig().getInt("homes.price-after", 20);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { applyLimit(e.getPlayer()); }

    /** Grant huskhomes.max_homes.<free + purchased> via a permission attachment. */
    public void applyLimit(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) { try { p.removeAttachment(old); } catch (IllegalArgumentException ignored) {} }
        int total = freeHomes() + bought(p.getUniqueId());
        PermissionAttachment att = p.addAttachment(plugin);
        att.setPermission("huskhomes.max_homes." + total, true);
        attachments.put(p.getUniqueId(), att);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { plugin.msg().send(sender, "players-only"); return true; }
        UUID id = p.getUniqueId();
        int total = freeHomes() + bought(id);
        int price = nextPrice(id);

        if (args.length >= 1 && args[0].equalsIgnoreCase("confirm")) {
            if (!plugin.coins().has(p, price)) { plugin.msg().send(p, "buyhome-cant-afford", "cost", String.valueOf(price)); return true; }
            plugin.coins().pay(p, price);
            purchased.merge(id, 1, Integer::sum);
            applyLimit(p);
            save();
            plugin.msg().send(p, "buyhome-bought", "total", String.valueOf(total + 1), "cost", String.valueOf(price));
            return true;
        }

        p.sendMessage(MM.deserialize("<gold>[Quests]</gold> <gray>You have <white>" + total + "</white> home slots. "
                + "Next slot costs <yellow>" + price + " coins</yellow>. "
                + "<click:run_command:'/buyhome confirm'><green><bold>[Buy]</bold></green></click>"));
        return true;
    }
}
