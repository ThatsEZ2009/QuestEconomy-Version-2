package net.ezserver.questeconomy.coin;

import net.ezserver.questeconomy.QuestEconomy;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Builds coin ItemStacks and identifies them via a PersistentDataContainer tag. */
public class CoinItem {

    private final QuestEconomy plugin;
    private final NamespacedKey typeKey;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public CoinItem(QuestEconomy plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "coin_type");
    }

    public NamespacedKey key() {
        return typeKey;
    }

    public ItemStack create(CoinType type, int amount) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("coins.base-material", "PAPER"));
        if (mat == null) mat = Material.PAPER;

        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        int value = plugin.valueOf(type);
        meta.displayName(MM.deserialize("<color:" + type.hex + ">" + type.display + " Coin</color>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize("<gray>Worth <white>" + value + "</white> coin" + (value == 1 ? "" : "s"))
                .decoration(TextDecoration.ITALIC, false)));
        meta.setCustomModelData(plugin.modelDataOf(type));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    public CoinType typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return CoinType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isCoin(ItemStack item) {
        return typeOf(item) != null;
    }
}
