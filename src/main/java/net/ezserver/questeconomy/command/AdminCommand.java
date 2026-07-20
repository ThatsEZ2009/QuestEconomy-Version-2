package net.ezserver.questeconomy.command;

import net.ezserver.questeconomy.QuestEconomy;
import net.ezserver.questeconomy.coin.CoinType;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /qadmin givecoins|setboard|removeboard|reload */
public class AdminCommand implements CommandExecutor, TabCompleter {

    // Bump this every time a new build is shipped, so /qadmin version confirms what's running.
    public static final String BUILD = "Pass 4 — build 6 (homes GUI with costs)";

    private final QuestEconomy plugin;

    public AdminCommand(QuestEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // version is available to everyone, before the admin check
        if (args.length >= 1 && args[0].equalsIgnoreCase("version")) {
            sender.sendMessage("§6[QuestEconomy] §7Running: §f" + BUILD);
            return true;
        }
        if (!sender.hasPermission("questeconomy.admin")) {
            plugin.msg().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/qadmin givecoins | setboard | removeboard | reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.msg().reload();
                plugin.quests().load();
                plugin.msg().send(sender, "reloaded");
            }
            case "setboard", "removeboard" -> {
                if (!(sender instanceof Player p)) { plugin.msg().send(sender, "players-only"); return true; }
                Block b = p.getTargetBlockExact(6);
                if (b == null || b.getType().isAir()) { plugin.msg().send(p, "look-at-block"); return true; }
                if (args[0].equalsIgnoreCase("setboard")) {
                    if (plugin.quests().addBoard(b.getLocation())) plugin.msg().send(p, "board-set");
                    else plugin.msg().send(p, "board-exists");
                } else {
                    if (plugin.quests().removeBoard(b.getLocation())) plugin.msg().send(p, "board-removed");
                    else plugin.msg().send(p, "not-a-board");
                }
            }
            case "setmint", "removemint" -> {
                if (!(sender instanceof Player p)) { plugin.msg().send(sender, "players-only"); return true; }
                Block b = p.getTargetBlockExact(6);
                if (b == null || b.getType().isAir()) { plugin.msg().send(p, "look-at-block"); return true; }
                if (args[0].equalsIgnoreCase("setmint")) {
                    if (plugin.quests().addMint(b.getLocation())) plugin.msg().send(p, "mint-set");
                    else plugin.msg().send(p, "mint-exists");
                } else {
                    if (plugin.quests().removeMint(b.getLocation())) plugin.msg().send(p, "mint-removed");
                    else plugin.msg().send(p, "not-a-mint");
                }
            }
            case "givecoins" -> {
                if (args.length < 4) {
                    plugin.msg().send(sender, "usage-givecoins");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.msg().send(sender, "player-not-found");
                    return true;
                }
                CoinType type = CoinType.byKey(args[2]);
                if (type == null) {
                    plugin.msg().send(sender, "unknown-coin");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    plugin.msg().send(sender, "usage-givecoins");
                    return true;
                }
                if (amount < 1) amount = 1;

                var leftover = target.getInventory().addItem(plugin.coinItem().create(type, amount));
                for (ItemStack drop : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), drop);
                }
                plugin.msg().send(sender, "coins-given",
                        "amount", String.valueOf(amount), "coin", type.display, "player", target.getName());
                plugin.msg().send(target, "coins-received",
                        "amount", String.valueOf(amount), "coin", type.display);
            }
            default -> sender.sendMessage("/qadmin givecoins | setboard | removeboard | reload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(Arrays.asList("givecoins", "setboard", "removeboard", "setmint", "removemint", "reload", "version"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("givecoins")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("givecoins")) {
            return filter(Arrays.asList("copper", "silver", "gold", "diamond"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> in, String pre) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (s.toLowerCase().startsWith(pre.toLowerCase())) out.add(s);
        return out;
    }
}
