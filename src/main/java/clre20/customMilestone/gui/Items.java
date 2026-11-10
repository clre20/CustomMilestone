package clre20.customMilestone.gui;

import clre20.customMilestone.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 介面按鈕物品。每個按鈕都帶有 PDC 簽署 custommilestone:gui = CustomMilestone。
 */
public final class Items {

    private static NamespacedKey signatureKey;

    private Items() {
    }

    static void init(org.bukkit.plugin.Plugin plugin) {
        signatureKey = new NamespacedKey(plugin, "gui");
    }

    public static ItemStack of(Material mat, String name, List<String> lore) {
        ItemStack st = new ItemStack(mat == null || mat.isAir() || !mat.isItem() ? Material.PAPER : mat);
        return decorate(st, name, lore, false);
    }

    public static ItemStack decorate(ItemStack st, String name, List<String> lore, boolean glow) {
        ItemMeta meta = st.getItemMeta();
        if (meta == null) return st;
        if (name != null) meta.displayName(Text.item(name));
        if (lore != null) meta.lore(Text.itemLines(lore));
        meta.addItemFlags(ItemFlag.values());
        if (glow) meta.setEnchantmentGlintOverride(true);
        if (signatureKey != null) {
            meta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, MilestoneHolder.SIGNATURE);
        }
        st.setItemMeta(meta);
        return st;
    }

    /** 以 Component 設定名稱與 Lore（原版成就的翻譯文字用）。 */
    public static ItemStack withComponents(ItemStack st, net.kyori.adventure.text.Component name,
                                           List<net.kyori.adventure.text.Component> lore, boolean glow) {
        ItemMeta meta = st.getItemMeta();
        if (meta == null) return st;
        meta.displayName(name.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null);
        if (signatureKey != null) {
            meta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, MilestoneHolder.SIGNATURE);
        }
        st.setItemMeta(meta);
        return st;
    }

    /** 獎勵預覽：保留物品原本的名稱與附魔顯示，只在 Lore 後面追加說明。 */
    public static ItemStack preview(ItemStack original, List<String> extraLore) {
        ItemStack st = original.clone();
        ItemMeta meta = st.getItemMeta();
        if (meta == null) return st;
        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        List<net.kyori.adventure.text.Component> old = meta.lore();
        if (old != null) lore.addAll(old);
        lore.addAll(Text.itemLines(extraLore));
        meta.lore(lore);
        if (signatureKey != null) {
            meta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, MilestoneHolder.SIGNATURE);
        }
        st.setItemMeta(meta);
        return st;
    }

    public static ItemStack head(java.util.UUID uuid, String playerName, String name, List<String> lore) {
        ItemStack st = new ItemStack(Material.PLAYER_HEAD);
        if (st.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
            // 只放 UUID 與名稱，不在伺服器端查詢皮膚（由客戶端自行載入）
            sm.setPlayerProfile(org.bukkit.Bukkit.createProfile(uuid, playerName == null || playerName.isEmpty() ? null : playerName));
            st.setItemMeta(sm);
        }
        return decorate(st, name, lore, false);
    }

    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }
}
