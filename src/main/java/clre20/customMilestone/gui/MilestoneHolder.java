package clre20.customMilestone.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * 本插件所有箱子 UI 的 InventoryHolder（插件簽署）。
 * 其他插件可用 {@code holder instanceof InventoryHolder h && "CustomMilestone".equals(...)} 或
 * 檢查按鈕物品上的 PDC 標籤 custommilestone:gui 來辨識來源。
 */
public final class MilestoneHolder implements InventoryHolder {

    public static final String SIGNATURE = "CustomMilestone";

    private final Plugin plugin;
    private final Menu menu;
    private Inventory inventory;

    MilestoneHolder(Plugin plugin, Menu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public String getSignature() {
        return SIGNATURE;
    }

    public Menu getMenu() {
        return menu;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
