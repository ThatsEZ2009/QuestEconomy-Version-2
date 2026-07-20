package net.ezserver.questeconomy.quest;

import net.ezserver.questeconomy.QuestEconomy;
import net.ezserver.questeconomy.quest.QuestManager.ActiveQuest;
import net.ezserver.questeconomy.quest.QuestManager.ObjType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Everything the quest system reacts to: block/kill/farm/travel tracking, the Quest Board block,
 *  the /quests GUI, reroll and turn-in clicks. */
public class QuestHandler implements Listener, CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] QUEST_SLOTS = {11, 13, 15};
    private static final int[] BUTTON_SLOTS = {20, 22, 24};

    private final QuestEconomy plugin;
    private final QuestManager qm;
    private final Set<String> playerPlaced = new HashSet<>();
    private final Map<UUID, Double> travelAccum = new HashMap<>();

    public QuestHandler(QuestEconomy plugin, QuestManager qm) {
        this.plugin = plugin;
        this.qm = qm;
    }

    // ================= tracking =================

    private String locKey(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        playerPlaced.add(locKey(e.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Material m = b.getType();
        boolean wasPlaced = playerPlaced.remove(locKey(b));

        // Harvesting a fully-grown crop always counts (even if the player planted it).
        if (b.getBlockData() instanceof Ageable age && age.getAge() == age.getMaximumAge()) {
            qm.progress(p, ObjType.HARVEST, d -> d.blocks.contains(m), 1);
        }
        // Mining only counts natural (not player-placed) blocks, to stop place-and-break farming.
        if (!wasPlaced) {
            qm.progress(p, ObjType.BLOCK_BREAK, d -> d.blocks.contains(m), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        var type = e.getEntityType();
        qm.progress(killer, ObjType.KILL, d -> d.entities.contains(type), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p)) return;
        var type = e.getEntityType();
        qm.progress(p, ObjType.BREED, d -> d.entities.contains(type), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        var from = e.getFrom();
        var to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double acc = travelAccum.merge(e.getPlayer().getUniqueId(), Math.sqrt(dx * dx + dz * dz), Double::sum);
        int whole = (int) Math.floor(acc);
        if (whole >= 1) {
            travelAccum.put(e.getPlayer().getUniqueId(), acc - whole);
            qm.progress(e.getPlayer(), ObjType.TRAVEL, d -> true, whole);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        travelAccum.remove(e.getPlayer().getUniqueId());
        qm.save();
    }

    // ================= quest board =================

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !qm.isBoard(b.getLocation())) return;
        e.setCancelled(true);
        openGui(e.getPlayer());
    }

    // ================= /quests command =================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.msg().send(sender, "players-only");
            return true;
        }
        openGui(p);
        return true;
    }

    // ================= GUI =================

    private static final class QuestHolder implements InventoryHolder {
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    public void openGui(Player p) {
        List<ActiveQuest> quests = qm.getQuests(p);
        QuestHolder holder = new QuestHolder();
        // Custom GUI background (Java resource pack). Bedrock players see a plain title.
        String bg = "" + (char) 0xE010 + (char) 0xE001;
        Inventory inv = Bukkit.createInventory(holder, 27,
                MM.deserialize("<font:questeconomy:gui>" + bg + "</font>"));
        holder.inv = inv;

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        long refresh = qm.refreshAt(p);
        long mins = Math.max(0, (refresh - System.currentTimeMillis()) / 60000L);
        inv.setItem(4, named(Material.CLOCK, "<gold>Daily Quests",
                List.of("<gray>New quests in <white>" + (mins / 60) + "h " + (mins % 60) + "m</white>",
                        "<gray>Complete them for coins.")));

        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            if (i < quests.size()) {
                ActiveQuest aq = quests.get(i);
                inv.setItem(QUEST_SLOTS[i], questItem(aq));
                inv.setItem(BUTTON_SLOTS[i], aq.completed
                        ? named(Material.LIME_DYE, "<green>Completed", List.of("<gray>Reward already paid."))
                        : named(Material.EMERALD, "<yellow>Reroll",
                            List.of("<gray>Get a new " + aq.def.category.name().toLowerCase() + " quest.",
                                    "<gray>Cost: <white>" + qm.rerollCost() + " coins</white>")));
            }
        }
        p.openInventory(inv);
    }

    private ItemStack questItem(ActiveQuest aq) {
        Material icon = switch (aq.def.category) {
            case MINING -> Material.IRON_PICKAXE;
            case COMBAT -> Material.IRON_SWORD;
            case FARMING -> Material.WHEAT;
            case TRAVEL -> Material.LEATHER_BOOTS;
            case DELIVERY -> Material.CHEST;
        };
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + aq.def.display());
        lore.add("<gray>Progress: <white>" + aq.progress + " / " + aq.def.amount + "</white>");
        lore.add("<gray>Reward: <yellow>" + qm.tierReward(aq.def.tier) + " coins</yellow> <dark_gray>(" + aq.def.tier.name().toLowerCase() + ")</dark_gray>");
        if (aq.completed) lore.add("<green>✔ Completed");
        else if (aq.def.type == ObjType.DELIVERY) lore.add("<yellow>Click to turn in items");
        return named(icon, (aq.completed ? "<green>" : "<aqua>") + aq.def.category.name(), lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof QuestHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= 27) return;

        // reroll buttons
        for (int i = 0; i < BUTTON_SLOTS.length; i++) {
            if (raw == BUTTON_SLOTS[i]) {
                if (qm.reroll(p, i)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    openGui(p);
                } else {
                    plugin.msg().send(p, "not-enough-coins");
                }
                return;
            }
        }
        // delivery turn-in (click the quest icon)
        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            if (raw == QUEST_SLOTS[i]) {
                List<ActiveQuest> quests = qm.getQuests(p);
                if (i < quests.size() && quests.get(i).def.type == ObjType.DELIVERY && !quests.get(i).completed) {
                    if (qm.turnIn(p, quests.get(i))) openGui(p);
                    else plugin.msg().send(p, "nothing-to-turn-in");
                }
                return;
            }
        }
    }

    // ================= item helpers =================

    private ItemStack pane(Material m, String name) {
        return named(m, name, List.of());
    }

    private ItemStack named(Material m, String name, List<String> loreLines) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String s : loreLines) lore.add(MM.deserialize(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }
        it.setItemMeta(meta);
        return it;
    }
}
