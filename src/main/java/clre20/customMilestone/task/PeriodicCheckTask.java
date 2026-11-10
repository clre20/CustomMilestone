package clre20.customMilestone.task;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.milestone.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.UUID;

/**
 * 定時檢查（遊玩時間、等級、生物群系、座標範圍、原版統計、原版成就、完成數量…）。
 * 不監聽玩家移動；每隔 interval 秒把線上玩家排入佇列，之後每個 tick 只處理 players-per-tick 位。
 */
public final class PeriodicCheckTask {

    private final CustomMilestone plugin;
    private final ArrayDeque<UUID> queue = new ArrayDeque<>();
    private BukkitTask timer;
    private BukkitTask worker;

    public PeriodicCheckTask(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long interval = plugin.config().periodicIntervalSeconds() * 20L;
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::enqueue, interval, interval);
    }

    private boolean anyPeriodic() {
        for (TriggerType t : TriggerType.values()) {
            if (t.periodic() && plugin.registry().hasType(t)) return true;
        }
        return false;
    }

    private void enqueue() {
        if (!queue.isEmpty() || !anyPeriodic()) return;
        for (Player p : Bukkit.getOnlinePlayers()) queue.add(p.getUniqueId());
        if (queue.isEmpty() || worker != null) return;
        worker = Bukkit.getScheduler().runTaskTimer(plugin, this::work, 1L, 1L);
    }

    private void work() {
        int budget = plugin.config().playersPerTick();
        while (budget-- > 0 && !queue.isEmpty()) {
            Player p = Bukkit.getPlayer(queue.poll());
            if (p != null) plugin.progress().checkPeriodic(p);
        }
        if (queue.isEmpty() && worker != null) {
            worker.cancel();
            worker = null;
        }
    }

    public void stop() {
        if (timer != null) timer.cancel();
        if (worker != null) worker.cancel();
        timer = null;
        worker = null;
        queue.clear();
    }
}
