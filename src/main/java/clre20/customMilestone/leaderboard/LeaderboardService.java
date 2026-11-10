package clre20.customMilestone.leaderboard;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.Database;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * 排行榜：定時在 I/O 執行緒查詢一次並快取，開介面與 PlaceholderAPI 只讀快取。
 */
public final class LeaderboardService {

    private final CustomMilestone plugin;
    private volatile List<Database.TopEntry> custom = List.of();
    private volatile List<Database.TopEntry> withVanilla = List.of();
    private volatile long updatedAt;
    private BukkitTask task;

    public LeaderboardService(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long period = plugin.config().leaderboardRefreshMinutes() * 60L * 20L;
        // 啟動 10 秒後第一次計算，之後依設定間隔
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 200L, period);
    }

    public void refresh() {
        // 先把線上玩家的最新資料寫入，排名才會包含剛達成的成就
        plugin.data().flush();
        int size = 45;
        plugin.data().topAsync(false, size).thenAccept(list -> custom = List.copyOf(list));
        plugin.data().topAsync(true, size).thenAccept(list -> {
            withVanilla = List.copyOf(list);
            updatedAt = System.currentTimeMillis();
        });
    }

    public List<Database.TopEntry> top(boolean vanilla) {
        List<Database.TopEntry> list = vanilla ? withVanilla : custom;
        int size = plugin.config().leaderboardSize();
        return list.size() > size ? list.subList(0, size) : list;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }
}
