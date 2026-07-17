package net.ezserver.questeconomy;

import net.ezserver.questeconomy.coin.CoinItem;
import net.ezserver.questeconomy.coin.CoinListener;
import net.ezserver.questeconomy.coin.CoinService;
import net.ezserver.questeconomy.coin.CoinType;
import net.ezserver.questeconomy.command.AdminCommand;
import net.ezserver.questeconomy.quest.QuestHandler;
import net.ezserver.questeconomy.quest.QuestManager;
import net.ezserver.questeconomy.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestEconomy extends JavaPlugin {

    private CoinItem coinItem;
    private CoinService coinService;
    private Messages messages;
    private QuestManager questManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.coinItem = new CoinItem(this);
        this.coinService = new CoinService(this, coinItem);

        getServer().getPluginManager().registerEvents(new CoinListener(this, coinItem), this);

        // Quests
        this.questManager = new QuestManager(this);
        this.questManager.load();
        QuestHandler questHandler = new QuestHandler(this, questManager);
        getServer().getPluginManager().registerEvents(questHandler, this);
        PluginCommand quests = getCommand("quests");
        if (quests != null) quests.setExecutor(questHandler);

        // Autosave quest progress every 3 minutes (hot paths only touch memory).
        getServer().getScheduler().runTaskTimer(this, questManager::save, 20L * 60 * 3, 20L * 60 * 3);

        PluginCommand cmd = getCommand("qadmin");
        if (cmd != null) {
            AdminCommand ac = new AdminCommand(this);
            cmd.setExecutor(ac);
            cmd.setTabCompleter(ac);
        }

        getLogger().info("QuestEconomy enabled (Pass 2: coins + quests).");
    }

    @Override
    public void onDisable() {
        if (questManager != null) questManager.save();
    }

    // Coin value / model-data resolution (config overrides the enum defaults).
    public int valueOf(CoinType t) {
        return getConfig().getInt("coins.values." + t.key, t.defaultValue);
    }

    public int modelDataOf(CoinType t) {
        return getConfig().getInt("coins.model-data." + t.key, t.defaultModelData);
    }

    public CoinItem coinItem() { return coinItem; }
    public CoinService coins() { return coinService; }
    public Messages msg() { return messages; }
    public QuestManager quests() { return questManager; }
}
