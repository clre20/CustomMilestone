package clre20.customMilestone.milestone;

import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Map;

/**
 * 物品細節比對：以 Data Components 字串（例如 enchanted_book[stored_enchantments={mending:1}]）建立範本，
 * 比對時檢查「範本有的特徵，候選物品都要有」：種類、附魔（等級 ≥）、儲存附魔（等級 ≥）、藥水種類、自訂名稱。
 */
public final class ItemMatcher {

    private final String source;
    private final ItemStack template;

    private ItemMatcher(String source, ItemStack template) {
        this.source = source;
        this.template = template;
    }

    /** 解析失敗時回傳 null。 */
    public static ItemMatcher parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            ItemStack stack = Bukkit.getItemFactory().createItemStack(input.trim());
            return new ItemMatcher(input.trim(), stack);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String source() {
        return source;
    }

    public boolean matches(ItemStack candidate) {
        if (candidate == null || candidate.getType() != template.getType()) {
            return false;
        }
        if (!template.hasItemMeta()) {
            return true;
        }
        ItemMeta want = template.getItemMeta();
        ItemMeta have = candidate.getItemMeta();
        if (have == null) {
            return false;
        }
        for (Map.Entry<Enchantment, Integer> e : want.getEnchants().entrySet()) {
            if (have.getEnchantLevel(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        if (want instanceof EnchantmentStorageMeta ws) {
            if (!(have instanceof EnchantmentStorageMeta hs)) {
                return false;
            }
            for (Map.Entry<Enchantment, Integer> e : ws.getStoredEnchants().entrySet()) {
                if (hs.getStoredEnchantLevel(e.getKey()) < e.getValue()) {
                    return false;
                }
            }
        }
        if (want instanceof PotionMeta wp && wp.getBasePotionType() != null) {
            if (!(have instanceof PotionMeta hp)) {
                return false;
            }
            PotionType type = hp.getBasePotionType();
            if (type != wp.getBasePotionType()) {
                return false;
            }
        }
        if (want.hasCustomName()) {
            return have.hasCustomName() && java.util.Objects.equals(want.customName(), have.customName());
        }
        return true;
    }
}
