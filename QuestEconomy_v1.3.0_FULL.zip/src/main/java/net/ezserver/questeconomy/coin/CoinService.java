package net.ezserver.questeconomy.coin;

import net.ezserver.questeconomy.QuestEconomy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Counting, paying (with smart change), and handing out physical coins. */
public class CoinService {

    private final QuestEconomy plugin;
    private final CoinItem coins;

    public CoinService(QuestEconomy plugin, CoinItem coins) {
        this.plugin = plugin;
        this.coins = coins;
    }

    /** Total coin VALUE a player is carrying. */
    public int countCoins(Player p) {
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            CoinType t = coins.typeOf(it);
            if (t != null) total += plugin.valueOf(t) * it.getAmount();
        }
        return total;
    }

    public boolean has(Player p, int cost) {
        return countCoins(p) >= cost;
    }

    public int amountOf(Player p, CoinType type) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (coins.typeOf(it) == type) n += it.getAmount();
        }
        return n;
    }

    /** Remove exactly 'count' coins of a specific type. Returns false (and removes nothing) if short. */
    public boolean removeType(Player p, CoinType type, int count) {
        if (amountOf(p, type) < count) return false;
        int remaining = count;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (coins.typeOf(contents[i]) == type) {
                ItemStack it = contents[i];
                int amt = it.getAmount();
                if (amt <= remaining) { remaining -= amt; p.getInventory().setItem(i, null); }
                else { it.setAmount(amt - remaining); remaining = 0; }
            }
        }
        return true;
    }

    /** Give 'count' physical coins of a specific type (drops overflow). */
    public void giveType(Player p, CoinType type, int count) {
        while (count > 0) {
            int stack = Math.min(64, count);
            var leftover = p.getInventory().addItem(coins.create(type, stack));
            for (ItemStack drop : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
            count -= stack;
        }
    }

    /** Give a coin VALUE to the player as the fewest items possible (diamonds first). Drops overflow. */
    public void give(Player p, int value) {
        if (value <= 0) return;
        for (CoinType t : CoinType.descending()) {
            int v = plugin.valueOf(t);
            if (v <= 0) continue;
            int n = value / v;
            if (n > 0) {
                value -= n * v;
                var leftover = p.getInventory().addItem(coins.create(t, n));
                for (ItemStack drop : leftover.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
        }
    }

    /** Result of a payment attempt. */
    public record PayResult(boolean success, boolean brokeLargerCoin, int change) {
        public static PayResult fail() {
            return new PayResult(false, false, 0);
        }
    }

    /** Would paying 'cost' force breaking a coin larger than the amount still owed? (Used for the confirm warning.) */
    public boolean wouldBreakLarger(Player p, int cost) {
        if (cost <= 0 || !has(p, cost)) return false;
        int removed = 0;
        for (CoinType t : CoinType.ascending()) {
            int v = plugin.valueOf(t);
            int available = amountOf(p, t);
            while (available > 0 && removed < cost) {
                if (v > (cost - removed)) return true;
                removed += v;
                available--;
            }
            if (removed >= cost) return false;
        }
        return false;
    }

    /** Take 'cost' worth of coins smallest-first, refunding any change. Caller handles confirm warnings. */
    public PayResult pay(Player p, int cost) {
        if (cost <= 0) return new PayResult(true, false, 0);
        if (!has(p, cost)) return PayResult.fail();

        int removed = 0;
        boolean broke = false;
        for (CoinType t : CoinType.ascending()) {
            int v = plugin.valueOf(t);
            while (removed < cost && amountOf(p, t) > 0) {
                if (v > (cost - removed)) broke = true;
                removeOne(p, t);
                removed += v;
            }
            if (removed >= cost) break;
        }

        int change = removed - cost;
        if (change > 0) give(p, change);
        return new PayResult(true, broke && change > 0, change);
    }

    private void removeOne(Player p, CoinType type) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (coins.typeOf(contents[i]) == type) {
                ItemStack it = contents[i];
                int amt = it.getAmount();
                if (amt <= 1) inv.setItem(i, null);
                else it.setAmount(amt - 1);
                return;
            }
        }
    }
}
