package net.ezserver.questeconomy.util;

import net.ezserver.questeconomy.QuestEconomy;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Loads messages.yml and sends MiniMessage-formatted messages with a prefix. */
public class Messages {

    private final QuestEconomy plugin;
    private FileConfiguration cfg;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public Messages(QuestEconomy plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    public String raw(String key) {
        return cfg.getString(key, key);
    }

    /** Send a prefixed message. Extra args are (placeholder, value) pairs, replacing &lt;placeholder&gt;. */
    public void send(CommandSender to, String key, String... repl) {
        String prefix = cfg.getString("prefix", "");
        to.sendMessage(MM.deserialize(prefix + apply(cfg.getString(key, key), repl)));
    }

    private String apply(String s, String... repl) {
        for (int i = 0; i + 1 < repl.length; i += 2) {
            s = s.replace("<" + repl[i] + ">", repl[i + 1]);
        }
        return s;
    }
}
