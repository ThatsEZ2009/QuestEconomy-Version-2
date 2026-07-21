package net.ezserver.questeconomy.quest;

import net.ezserver.questeconomy.QuestEconomy;
import net.ezserver.questeconomy.coin.CoinType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/** Loads the quest pool, tracks each player's 3 active quests, handles rollover / reroll / rewards,
 *  and saves everything to questdata.yml (no database driver needed). */
public class QuestManager {

    // ---------- data types ----------
    public enum QuestCategory { MINING, COMBAT, FARMING, TRAVEL, DELIVERY }
    public enum QuestTier { EASY, MEDIUM, HARD, ELITE }
    public enum ObjType { BLOCK_BREAK, HARVEST, KILL, BREED, TRAVEL, DELIVERY }

    public static final class QuestDef {
        public final String id;
        public final QuestCategory category;
        public final QuestTier tier;
        public final ObjType type;
        public final Set<Material> blocks;
        public final Set<EntityType> entities;
        public final Set<Material> items;
        public final int amount;
        public final String nameTemplate;

        QuestDef(String id, QuestCategory c, QuestTier t, ObjType type, Set<Material> blocks,
                 Set<EntityType> entities, Set<Material> items, int amount, String name) {
            this.id = id; this.category = c; this.tier = t; this.type = type;
            this.blocks = blocks; this.entities = entities; this.items = items;
            this.amount = amount; this.nameTemplate = name;
        }

        public String display() { return nameTemplate.replace("<amt>", String.valueOf(amount)); }
    }

    public static final class ActiveQuest {
        public String defId;
        public int progress;
        public boolean completed;
        public transient QuestDef def;
        ActiveQuest(String id) { this.defId = id; }
    }

    static final class PlayerData {
        List<ActiveQuest> quests = new ArrayList<>();
        long refresh;       // epoch millis when quests should regenerate
        int totalCompleted; // lifetime quests finished (for the leaderboard)
        String name = "";   // last known username, so the leaderboard can show it
    }

    // ---------- fields ----------
    private final QuestEconomy plugin;
    private final List<QuestDef> pool = new ArrayList<>();
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Set<String> boards = new HashSet<>();
    private final Set<String> mints = new HashSet<>();
    private final Random rng = new Random();

    private int refreshHours = 12;
    private int rerollCost = 2;
    private final EnumMap<QuestTier, Integer> reward = new EnumMap<>(QuestTier.class);

    private File dataFile;
    private FileConfiguration data;

    public QuestManager(QuestEconomy plugin) { this.plugin = plugin; }

    public int rerollCost() { return rerollCost; }

    // ---------- lifecycle ----------
    public void load() {
        loadPool();
        loadData();
    }

    private void loadPool() {
        pool.clear();
        File f = new File(plugin.getDataFolder(), "quests.yml");
        if (!f.exists()) plugin.saveResource("quests.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        refreshHours = cfg.getInt("settings.refresh-hours", 12);
        rerollCost = cfg.getInt("settings.reroll-cost", 2);
        reward.put(QuestTier.EASY, cfg.getInt("rewards.easy", 1));
        reward.put(QuestTier.MEDIUM, cfg.getInt("rewards.medium", 2));
        reward.put(QuestTier.HARD, cfg.getInt("rewards.hard", 3));
        reward.put(QuestTier.ELITE, cfg.getInt("rewards.elite", 7));

        List<Map<?, ?>> list = cfg.getMapList("pool");
        for (Map<?, ?> m : list) {
            try {
                String id = str(m, "id");
                QuestCategory cat = QuestCategory.valueOf(str(m, "category").toUpperCase());
                QuestTier tier = QuestTier.valueOf(str(m, "tier").toUpperCase());
                ObjType type = ObjType.valueOf(str(m, "type").toUpperCase());
                int amount = ((Number) m.get("amount")).intValue();
                String name = str(m, "name");

                Set<Material> blocks = materials(m.get("blocks"));
                Set<Material> items = materials(m.get("items"));
                Set<EntityType> ents = entities(m.get("entities"));

                pool.add(new QuestDef(id, cat, tier, type, blocks, ents, items, amount, name));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad quest entry: " + m + " (" + ex.getMessage() + ")");
            }
        }
        plugin.getLogger().info("Loaded " + pool.size() + " quest definitions.");
    }

    private static String str(Map<?, ?> m, String k) { Object o = m.get(k); return o == null ? null : o.toString(); }

    private static Set<Material> materials(Object o) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        if (o instanceof List<?> l) for (Object s : l) {
            Material mat = Material.matchMaterial(s.toString());
            if (mat != null) out.add(mat);
        }
        return out;
    }

    private static Set<EntityType> entities(Object o) {
        Set<EntityType> out = EnumSet.noneOf(EntityType.class);
        if (o instanceof List<?> l) for (Object s : l) {
            try { out.add(EntityType.valueOf(s.toString().toUpperCase())); } catch (Exception ignored) {}
        }
        return out;
    }

    QuestDef defById(String id) {
        for (QuestDef d : pool) if (d.id.equals(id)) return d;
        return null;
    }

    // ---------- persistence ----------
    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "questdata.yml");
        if (!dataFile.exists()) {
            try { dataFile.getParentFile().mkdirs(); dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        boards.clear();
        boards.addAll(data.getStringList("boards"));
        mints.clear();
        mints.addAll(data.getStringList("mints"));

        players.clear();
        ConfigurationSection ps = data.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    PlayerData pd = new PlayerData();
                    pd.refresh = ps.getLong(key + ".refresh", 0L);
                    pd.totalCompleted = ps.getInt(key + ".total", 0);
                    pd.name = ps.getString(key + ".name", "");
                    ConfigurationSection qs = ps.getConfigurationSection(key + ".quests");
                    if (qs != null) {
                        for (String slot : qs.getKeys(false)) {
                            ActiveQuest aq = new ActiveQuest(qs.getString(slot + ".id"));
                            aq.progress = qs.getInt(slot + ".progress", 0);
                            aq.completed = qs.getBoolean(slot + ".completed", false);
                            aq.def = defById(aq.defId);
                            if (aq.def != null) pd.quests.add(aq);
                        }
                    }
                    players.put(id, pd);
                } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        if (data == null) return;
        data.set("boards", new ArrayList<>(boards));
        data.set("mints", new ArrayList<>(mints));
        data.set("players", null);
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            String base = "players." + e.getKey();
            data.set(base + ".refresh", e.getValue().refresh);
            data.set(base + ".total", e.getValue().totalCompleted);
            data.set(base + ".name", e.getValue().name);
            int i = 0;
            for (ActiveQuest aq : e.getValue().quests) {
                String qb = base + ".quests." + i++;
                data.set(qb + ".id", aq.defId);
                data.set(qb + ".progress", aq.progress);
                data.set(qb + ".completed", aq.completed);
            }
        }
        try { data.save(dataFile); } catch (IOException ex) { plugin.getLogger().warning("Could not save questdata.yml: " + ex.getMessage()); }
    }

    // ---------- quest assignment ----------
    public List<ActiveQuest> getQuests(Player p) {
        PlayerData pd = players.computeIfAbsent(p.getUniqueId(), k -> new PlayerData());
        long now = System.currentTimeMillis();
        if (pd.quests.isEmpty() || now >= pd.refresh) {
            regenerate(pd);
        }
        return pd.quests;
    }

    private void regenerate(PlayerData pd) {
        pd.quests.clear();
        List<QuestDef> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rng);
        int count = Math.min(3, shuffled.size());
        for (int i = 0; i < count; i++) {
            ActiveQuest aq = new ActiveQuest(shuffled.get(i).id);
            aq.def = shuffled.get(i);
            pd.quests.add(aq);
        }
        pd.refresh = System.currentTimeMillis() + refreshHours * 3600_000L;
    }

    public long refreshAt(Player p) {
        PlayerData pd = players.get(p.getUniqueId());
        return pd == null ? 0 : pd.refresh;
    }

    /** Reroll one slot to a new random quest of the SAME category. Returns true on success. */
    public boolean reroll(Player p, int slot) {
        PlayerData pd = players.get(p.getUniqueId());
        if (pd == null || slot < 0 || slot >= pd.quests.size()) return false;
        if (!plugin.coins().has(p, rerollCost)) return false;

        QuestCategory cat = pd.quests.get(slot).def.category;
        List<QuestDef> same = new ArrayList<>();
        for (QuestDef d : pool) if (d.category == cat) same.add(d);
        if (same.isEmpty()) return false;

        plugin.coins().pay(p, rerollCost);
        QuestDef pick = same.get(rng.nextInt(same.size()));
        ActiveQuest aq = new ActiveQuest(pick.id);
        aq.def = pick;
        pd.quests.set(slot, aq);
        save();
        return true;
    }

    // ---------- progress ----------
    public void progress(Player p, ObjType type, Predicate<QuestDef> matcher, int amount) {
        PlayerData pd = players.get(p.getUniqueId());
        if (pd == null) return;
        // Progress is kept in memory and flushed by the periodic autosave / on quit / on disable,
        // so we never write to disk on hot paths like block-break or movement.
        for (ActiveQuest aq : pd.quests) {
            if (aq.completed || aq.def == null) continue;
            if (aq.def.type != type || !matcher.test(aq.def)) continue;
            aq.progress = Math.min(aq.def.amount, aq.progress + amount);
            if (aq.progress >= aq.def.amount) complete(p, aq);
        }
    }

    /** Delivery turn-in: consume matching items from inventory up to what's still needed. */
    public boolean turnIn(Player p, ActiveQuest aq) {
        if (aq.completed || aq.def == null || aq.def.type != ObjType.DELIVERY) return false;
        int need = aq.def.amount - aq.progress;
        if (need <= 0) return false;

        int have = 0;
        for (var it : p.getInventory().getContents()) {
            if (it != null && aq.def.items.contains(it.getType())) have += it.getAmount();
        }
        if (have <= 0) return false;

        int take = Math.min(need, have);
        int remaining = take;
        var contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            var it = contents[i];
            if (it != null && aq.def.items.contains(it.getType())) {
                int amt = it.getAmount();
                if (amt <= remaining) { remaining -= amt; p.getInventory().setItem(i, null); }
                else { it.setAmount(amt - remaining); remaining = 0; }
            }
        }
        aq.progress += take;
        if (aq.progress >= aq.def.amount) complete(p, aq);
        save();
        return true;
    }

    private void complete(Player p, ActiveQuest aq) {
        aq.completed = true;
        PlayerData pd = players.get(p.getUniqueId());
        if (pd != null) {
            pd.totalCompleted++;
            pd.name = p.getName();
        }
        int coins = reward.getOrDefault(aq.def.tier, 1);
        plugin.coins().giveType(p, CoinType.COPPER, coins); // quests pay Copper only
        p.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<gold>[Quests]</gold> <green>Completed: <white>" + aq.def.display() + "</white> — earned <#e0913a>" + coins + " Copper</#e0913a>!"));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
    }

    // ---------- stats / admin ----------

    /** Lifetime quests completed by a player. */
    public int totalCompleted(UUID id) {
        PlayerData pd = players.get(id);
        return pd == null ? 0 : pd.totalCompleted;
    }

    /** Top questers (name -> total), highest first. */
    public List<Map.Entry<String, Integer>> topQuesters(int limit) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        for (PlayerData pd : players.values()) {
            if (pd.totalCompleted <= 0) continue;
            String n = (pd.name == null || pd.name.isEmpty()) ? "Unknown" : pd.name;
            out.add(new AbstractMap.SimpleEntry<>(n, pd.totalCompleted));
        }
        out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Wipe a player's active quests so they get a fresh set. Keeps their lifetime total. */
    public void resetQuests(UUID id) {
        PlayerData pd = players.get(id);
        if (pd == null) return;
        pd.quests.clear();
        pd.refresh = 0L;
        save();
    }

    // ---------- quest boards ----------
    private String key(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public boolean isBoard(Location l) { return boards.contains(key(l)); }

    public boolean addBoard(Location l) {
        boolean added = boards.add(key(l));
        if (added) save();
        return added;
    }

    public boolean removeBoard(Location l) {
        boolean removed = boards.remove(key(l));
        if (removed) save();
        return removed;
    }

    public boolean isMint(Location l) { return mints.contains(key(l)); }

    public boolean addMint(Location l) {
        boolean added = mints.add(key(l));
        if (added) save();
        return added;
    }

    public boolean removeMint(Location l) {
        boolean removed = mints.remove(key(l));
        if (removed) save();
        return removed;
    }

    public int tierReward(QuestTier t) { return reward.getOrDefault(t, 1); }
}
