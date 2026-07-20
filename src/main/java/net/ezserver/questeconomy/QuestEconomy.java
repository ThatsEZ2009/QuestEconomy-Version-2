package net.ezserver.questeconomy;

import net.ezserver.questeconomy.coin.CoinItem;
import net.ezserver.questeconomy.coin.CoinListener;
import net.ezserver.questeconomy.coin.CoinService;
import net.ezserver.questeconomy.coin.CoinType;
import net.ezserver.questeconomy.command.AdminCommand;
import net.ezserver.questeconomy.homes.HomeService;
import net.ezserver.questeconomy.homesgui.HomesGui;
import net.ezserver.questeconomy.mint.MintHandler;
import net.ezserver.questeconomy.quest.QuestHandler;
import net.ezserver.questeconomy.quest.QuestManager;
import net.ezserver.questeconomy.teleport.TeleportHandler;
import net.ezserver.questeconomy.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestEconomy extends JavaPlugin {

    private CoinItem coinItem;
    private CoinService coinService;
    private Messages messages;
    private QuestManager questManager;
    private HomeService homeService;

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

        // Coin Mint
        MintHandler mintHandler = new MintHandler(this);
        getServer().getPluginManager().registerEvents(mintHandler, this);
        PluginCommand mint = getCommand("mint");
        if (mint != null) mint.setExecutor(mintHandler);

        // Teleport costs
        TeleportHandler teleportHandler = new TeleportHandler(this);
        getServer().getPluginManager().registerEvents(teleportHandler, this);
        PluginCommand qetp = getCommand("qetp");
        if (qetp != null) qetp.setExecutor(teleportHandler);
        PluginCommand qetpcancel = getCommand("qetpcancel");
        if (qetpcancel != null) qetpcancel.setExecutor(teleportHandler);

        // Buy extra homes
        this.homeService = new HomeService(this);
        this.homeService.load();
        getServer().getPluginManager().registerEvents(homeService, this);
        PluginCommand buyhome = getCommand("buyhome");
        if (buyhome != null) buyhome.setExecutor(homeService);

        // Homes GUI with per-home costs (only if HuskHomes is installed)
        if (getServer().getPluginManager().getPlugin("HuskHomes") != null) {
            HomesGui homesGui = new HomesGui(this, teleportHandler);
            if (homesGui.setup()) {
                getServer().getPluginManager().registerEvents(homesGui, this);
                PluginCommand homes = getCommand("homes");
                if (homes != null) homes.setExecutor(homesGui);
                getLogger().info("Hooked HuskHomes for the homes GUI.");
            }
        }

        PluginCommand cmd = getCommand("qadmin");
        if (cmd != null) {
            AdminCommand ac = new AdminCommand(this);
            cmd.setExecutor(ac);
            cmd.setTabCompleter(ac);
        }

        getLogger().info("QuestEconomy enabled (Pass 4: coins + quests + mint + teleport + homes GUI).");
    }

    @Override
    public void onDisable() {
        if (questManager != null) questManager.save();
        if (homeService != null) homeService.save();
    }

    public HomeService homes() { return homeService; }

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
