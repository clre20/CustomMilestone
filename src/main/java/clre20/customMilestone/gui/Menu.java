package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 箱子 UI 基底：每個格子綁定一個左鍵動作。
 */
public abstract class Menu {

    protected final CustomMilestone plugin;
    protected final Player viewer;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(CustomMilestone plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract String title();

    protected int rows() {
        return 6;
    }

    protected abstract void draw();

    public void open() {
        MilestoneHolder holder = new MilestoneHolder(plugin, this);
        inventory = Bukkit.createInventory(holder, rows() * 9, Text.color(title()));
        holder.setInventory(inventory);
        actions.clear();
        draw();
        viewer.openInventory(inventory);
    }

    /** 重新繪製目前的介面（不重開視窗）。 */
    public void refresh() {
        if (inventory == null) {
            open();
            return;
        }
        inventory.clear();
        actions.clear();
        draw();
    }

    protected void set(int slot, ItemStack item, Consumer<Player> action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    void click(int slot) {
        Consumer<Player> a = actions.get(slot);
        if (a != null) a.accept(viewer);
    }

    // ===== 常用按鈕 =====

    protected ItemStack button(Material mat, String nameKey, String loreKey, String... kv) {
        return Items.of(mat, plugin.messages().raw(nameKey, kv),
                loreKey == null ? List.of() : plugin.messages().list(loreKey, kv));
    }

    protected void fillBorder(int fromSlot, int toSlot) {
        ItemStack filler = Items.filler();
        for (int i = fromSlot; i <= toSlot; i++) set(i, filler);
    }

    /** 分頁導覽列（最下排）。 */
    protected void navigation(int page, int pages, Runnable back, Consumer<Integer> goTo) {
        fillBorder(45, 53);
        if (page > 0) {
            set(45, button(Material.ARROW, "gui.common.prev", "gui.common.page-lore",
                    "page", String.valueOf(page + 1), "pages", String.valueOf(Math.max(1, pages))), p -> goTo.accept(page - 1));
        }
        if (page + 1 < pages) {
            set(53, button(Material.ARROW, "gui.common.next", "gui.common.page-lore",
                    "page", String.valueOf(page + 1), "pages", String.valueOf(Math.max(1, pages))), p -> goTo.accept(page + 1));
        }
        if (back != null) {
            set(49, button(Material.OAK_DOOR, "gui.common.back", null), p -> back.run());
        } else {
            set(49, button(Material.BARRIER, "gui.common.close", null), Player::closeInventory);
        }
    }

    protected static int pages(int count, int perPage) {
        return Math.max(1, (count + perPage - 1) / perPage);
    }
}
