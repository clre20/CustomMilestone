package clre20.customMilestone.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * config.yml 讀取。所有值都在 reload() 時讀成欄位，執行期間不再查 YAML，並帶有預設值防呆。
 */
public final class ConfigManager {

    public enum DisplayMode { AUTO, FALLBACK }

    private final JavaPlugin plugin;

    private ZoneId zone = ZoneId.of("Asia/Taipei");
    private boolean ignoreCreative = true;
    private DisplayMode displayMode = DisplayMode.AUTO;
    private boolean fallbackTitle = true;
    private String fallbackSound = "minecraft:ui.toast.challenge_complete";
    private int saveIntervalSeconds = 60;
    private int pendingLimit = 100;
    private int periodicIntervalSeconds = 60;
    private int playersPerTick = 10;
    private int leaderboardRefreshMinutes = 5;
    private int leaderboardSize = 10;
    private boolean itemClaimEnabled = true;
    private int placedBlocksPerChunk = 4096;
    private int chatInputTimeoutSeconds = 60;
    private List<String> cancelKeywords = List.of("取消", "cancel");
    private boolean discordFallbackSync = true;
    private boolean updateCheckOnStartup = true;
    private boolean updateNotifyOnJoin = true;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration c = YamlFiles.loadAndFill(plugin, "config.yml");

        String tz = c.getString("settings.timezone", "Asia/Taipei");
        try {
            zone = ZoneId.of(tz == null ? "Asia/Taipei" : tz);
        } catch (DateTimeException e) {
            plugin.getLogger().warning("settings.timezone 無效：" + tz + "，改用 Asia/Taipei");
            zone = ZoneId.of("Asia/Taipei");
        }
        ignoreCreative = c.getBoolean("settings.ignore-creative", true);

        String mode = c.getString("display.mode", "auto");
        displayMode = "fallback".equalsIgnoreCase(mode) ? DisplayMode.FALLBACK : DisplayMode.AUTO;
        fallbackTitle = c.getBoolean("display.fallback.show-title", true);
        fallbackSound = c.getString("display.fallback.sound", "");

        saveIntervalSeconds = Math.max(10, c.getInt("storage.save-interval-seconds", 60));
        pendingLimit = clamp(c.getInt("rewards.pending-limit", 100), 1, 1000);
        periodicIntervalSeconds = Math.max(10, c.getInt("periodic-check.interval-seconds", 60));
        playersPerTick = clamp(c.getInt("periodic-check.players-per-tick", 10), 1, 200);
        leaderboardRefreshMinutes = Math.max(1, c.getInt("leaderboard.refresh-minutes", 5));
        leaderboardSize = clamp(c.getInt("leaderboard.size", 10), 1, 45);
        itemClaimEnabled = c.getBoolean("item-claim.enabled", true);
        placedBlocksPerChunk = clamp(c.getInt("anti-abuse.placed-blocks-per-chunk", 4096), 64, 65536);
        chatInputTimeoutSeconds = clamp(c.getInt("chat-input.timeout-seconds", 60), 10, 600);
        List<String> kw = new ArrayList<>();
        for (String s : c.getStringList("chat-input.cancel-keywords")) {
            if (s != null && !s.isBlank()) {
                kw.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        cancelKeywords = kw.isEmpty() ? List.of("取消", "cancel") : List.copyOf(kw);
        discordFallbackSync = c.getBoolean("discordsrv.fallback-sync", true);
        updateCheckOnStartup = c.getBoolean("update-checker.check-on-startup", true);
        updateNotifyOnJoin = c.getBoolean("update-checker.notify-on-join", true);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public ZoneId zone() { return zone; }
    public boolean ignoreCreative() { return ignoreCreative; }
    public DisplayMode displayMode() { return displayMode; }
    public boolean fallbackTitle() { return fallbackTitle; }
    public String fallbackSound() { return fallbackSound == null ? "" : fallbackSound; }
    public int saveIntervalSeconds() { return saveIntervalSeconds; }
    public int pendingLimit() { return pendingLimit; }
    public int periodicIntervalSeconds() { return periodicIntervalSeconds; }
    public int playersPerTick() { return playersPerTick; }
    public int leaderboardRefreshMinutes() { return leaderboardRefreshMinutes; }
    public int leaderboardSize() { return leaderboardSize; }
    public boolean itemClaimEnabled() { return itemClaimEnabled; }
    public int placedBlocksPerChunk() { return placedBlocksPerChunk; }
    public int chatInputTimeoutSeconds() { return chatInputTimeoutSeconds; }
    public List<String> cancelKeywords() { return cancelKeywords; }
    public boolean discordFallbackSync() { return discordFallbackSync; }
    public boolean updateCheckOnStartup() { return updateCheckOnStartup; }
    public boolean updateNotifyOnJoin() { return updateNotifyOnJoin; }
}
