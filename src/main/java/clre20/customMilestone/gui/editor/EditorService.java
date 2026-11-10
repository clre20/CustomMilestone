package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.milestone.Category;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.MilestoneRegistry;
import clre20.customMilestone.milestone.Reward;
import clre20.customMilestone.milestone.Trigger;
import clre20.customMilestone.milestone.TriggerType;
import org.bukkit.Material;

import java.util.List;

/**
 * 編輯器的修改入口：更新記憶體中的定義 → 非同步寫回 YAML → 只重新註冊有變更的原版成就。
 */
public final class EditorService {

    private final CustomMilestone plugin;
    private org.bukkit.scheduler.BukkitTask pendingSync;

    public EditorService(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    private MilestoneRegistry registry() {
        return plugin.registry();
    }

    /**
     * 重新註冊原版成就會讓所有線上玩家重讀成就資料，連續編輯時合併成 2 秒後一次執行，避免卡頓。
     */
    private void scheduleSync() {
        if (pendingSync != null) return;
        pendingSync = org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSync = null;
            plugin.advancements().sync(registry());
            int pending = plugin.advancements().pendingRestart();
            if (pending > 0) {
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission(EditorMenu.PERMISSION)) {
                        plugin.messages().send(p, "editor.restart-needed", "count", String.valueOf(pending));
                    }
                }
            }
        }, 40L);
    }

    public void saveMilestone(Milestone m) {
        registry().put(m);
        Category c = registry().category(m.categoryId());
        if (c != null) registry().saveAsync(c, plugin.database().executor());
        scheduleSync();
    }

    public void saveCategory(Category c) {
        registry().touch();
        registry().saveAsync(c, plugin.database().executor());
        scheduleSync();
    }

    public Category createCategory(String id, String name) {
        Category c = registry().createCategory(id, name);
        registry().saveAsync(c, plugin.database().executor());
        scheduleSync();
        return c;
    }

    public void deleteCategory(String id) {
        registry().removeCategory(id);
        registry().deleteFileAsync(id, plugin.database().executor());
        // 其他分類的成就可能因此失去前置條件
        for (Category c : registry().categories()) registry().saveAsync(c, plugin.database().executor());
        scheduleSync();
    }

    public Milestone createMilestone(String categoryId, String id) {
        Trigger t = new Trigger(TriggerType.MANUAL, List.of(), 1, null, s -> { });
        Milestone m = new Milestone(id, categoryId, id, List.of(), Material.PAPER, Milestone.Frame.TASK, false,
                t, List.of(), Reward.empty());
        saveMilestone(m);
        return m;
    }

    public void deleteMilestone(String id) {
        Milestone m = registry().get(id);
        if (m == null) return;
        registry().removeMilestone(id);
        for (Category c : registry().categories()) registry().saveAsync(c, plugin.database().executor());
        scheduleSync();
    }
}
