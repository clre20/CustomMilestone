package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 箱子 UI 事件：一律取消原始點擊（防止拿走物品），只處理上方介面的左鍵。
 */
public final class GuiListener implements Listener {

    private static final long CLICK_COOLDOWN_MS = 150;

    private final CustomMilestone plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public GuiListener(CustomMilestone plugin) {
        this.plugin = plugin;
        Items.init(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof MilestoneHolder holder)) return;
        e.setCancelled(true);
        if (e.getClick() != ClickType.LEFT || e.getClickedInventory() == null) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        boolean bottom = e.getClickedInventory() != top;
        // 點下方自己的背包：只交給需要「選取背包物品」的介面（例如獎勵編輯），物品不會被移動
        org.bukkit.inventory.ItemStack picked = null;
        if (bottom) {
            if (!holder.getMenu().acceptsBottomClicks()) return;
            org.bukkit.inventory.ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType().isAir()) return;
            picked = cur.clone();
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        lastClick.put(p.getUniqueId(), now);
        int slot = e.getRawSlot();
        org.bukkit.inventory.ItemStack item = picked;
        // 下一個 tick 再執行動作，避免在點擊事件中直接開關介面
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory() == top) {
                if (item != null) holder.getMenu().clickBottom(item);
                else holder.getMenu().click(slot);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof MilestoneHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastClick.remove(e.getPlayer().getUniqueId());
    }

    /** 關閉所有玩家開著的本插件介面（重載、關閉插件時）。 */
    public static void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof MilestoneHolder) {
                p.closeInventory();
            }
        }
    }
}
