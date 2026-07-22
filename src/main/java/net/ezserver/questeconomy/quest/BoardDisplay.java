package net.ezserver.questeconomy.quest;

import net.ezserver.questeconomy.QuestEconomy;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Renders ONE tidy floating label per quest-board, no matter how many blocks the board is
 * made of. The old problem was per-block labels stacking on top of each other into an
 * unreadable "Daily QuDaily Qu..." mess; this groups adjacent board blocks into a single
 * cluster and puts one centred label above it.
 *
 * On every refresh it also removes any TextDisplay sitting on/near a board block first, which
 * cleans up the garbled leftover entities that older builds baked into the world.
 */
public class BoardDisplay {

    private static final String TAG = "qe_board";

    private final QuestEconomy plugin;
    private final QuestManager qm;
    private final Set<UUID> spawned = new HashSet<>();

    public BoardDisplay(QuestEconomy plugin, QuestManager qm) {
        this.plugin = plugin;
        this.qm = qm;
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("board.label-enabled", true); }
    private String labelText() { return plugin.getConfig().getString("board.label", "<dark_aqua>Daily Quests"); }
    private double yOffset() { return plugin.getConfig().getDouble("board.label-y-offset", 1.4); }

    /** Remove every label we spawned (called on disable and at the start of a refresh). */
    public void removeAll() {
        for (UUID id : spawned) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        spawned.clear();
    }

    /** Rebuild all board labels from scratch, cleaning up leftovers first. */
    public void refresh() {
        removeAll();

        List<Location> blocks = new ArrayList<>();
        for (String key : qm.boardKeys()) {
            Location l = parse(key);
            if (l != null) blocks.add(l);
        }
        if (blocks.isEmpty()) return;

        // 1) nuke any TextDisplay near a board block (our own old labels + stale garbled leftovers)
        cleanupNear(blocks);
        if (!enabled()) return;

        // 2) one label per connected cluster of board blocks
        for (List<Location> group : cluster(blocks)) {
            Location loc = topCenter(group);
            if (loc == null) continue;
            World w = loc.getWorld();
            if (w == null || !w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            TextDisplay td = w.spawn(loc, TextDisplay.class);
            td.text(MiniMessage.miniMessage().deserialize(labelText()));
            td.setBillboard(Display.Billboard.CENTER);
            td.addScoreboardTag(TAG);
            spawned.add(td.getUniqueId());
        }
    }

    /** Delete any TextDisplay within a few blocks of a board block. */
    private void cleanupNear(List<Location> blocks) {
        Set<World> worlds = new HashSet<>();
        for (Location l : blocks) if (l.getWorld() != null) worlds.add(l.getWorld());
        for (World w : worlds) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof TextDisplay)) continue;
                Location el = e.getLocation();
                for (Location b : blocks) {
                    if (b.getWorld() != w) continue;
                    if (Math.abs(el.getX() - (b.getBlockX() + 0.5)) <= 3.0
                            && Math.abs(el.getZ() - (b.getBlockZ() + 0.5)) <= 3.0
                            && Math.abs(el.getY() - b.getBlockY()) <= 4.0) {
                        e.remove();
                        break;
                    }
                }
            }
        }
    }

    /** Group board blocks that touch (within 1 block on every axis) into clusters. */
    private List<List<Location>> cluster(List<Location> blocks) {
        List<List<Location>> clusters = new ArrayList<>();
        boolean[] used = new boolean[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            if (used[i]) continue;
            List<Location> group = new ArrayList<>();
            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(i);
            used[i] = true;
            while (!stack.isEmpty()) {
                int cur = stack.pop();
                group.add(blocks.get(cur));
                for (int j = 0; j < blocks.size(); j++) {
                    if (used[j]) continue;
                    if (adjacent(blocks.get(cur), blocks.get(j))) {
                        used[j] = true;
                        stack.push(j);
                    }
                }
            }
            clusters.add(group);
        }
        return clusters;
    }

    private boolean adjacent(Location a, Location b) {
        if (a.getWorld() != b.getWorld()) return false;
        return Math.abs(a.getBlockX() - b.getBlockX()) <= 1
                && Math.abs(a.getBlockY() - b.getBlockY()) <= 1
                && Math.abs(a.getBlockZ() - b.getBlockZ()) <= 1;
    }

    private Location topCenter(List<Location> group) {
        World w = group.get(0).getWorld();
        double sx = 0, sz = 0;
        int maxY = Integer.MIN_VALUE;
        for (Location l : group) {
            sx += l.getBlockX() + 0.5;
            sz += l.getBlockZ() + 0.5;
            maxY = Math.max(maxY, l.getBlockY());
        }
        int n = group.size();
        return new Location(w, sx / n, maxY + yOffset(), sz / n);
    }

    private Location parse(String key) {
        int i3 = key.lastIndexOf(',');
        if (i3 < 0) return null;
        int i2 = key.lastIndexOf(',', i3 - 1);
        int i1 = key.lastIndexOf(',', i2 - 1);
        if (i1 < 0 || i2 < 0) return null;
        String worldName = key.substring(0, i1);
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        try {
            int x = Integer.parseInt(key.substring(i1 + 1, i2));
            int y = Integer.parseInt(key.substring(i2 + 1, i3));
            int z = Integer.parseInt(key.substring(i3 + 1));
            return new Location(w, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
