package clre20.customMilestone.compatibility;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.Database;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.milestone.Milestone;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * PlaceholderAPI：%custommilestone_...%
 * <ul>
 *   <li>completed / total / percent / vanilla_completed / vanilla_total / pending / login_days / login_streak</li>
 *   <li>progress_&lt;id&gt; / target_&lt;id&gt; / done_&lt;id&gt;</li>
 *   <li>top_&lt;名次&gt;_name / top_&lt;名次&gt;_value（僅自訂）、top_vanilla_&lt;名次&gt;_name / _value（含原版）</li>
 * </ul>
 * 只讀取記憶體快取；離線玩家回傳空值，不會查資料庫。
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private final CustomMilestone plugin;

    public PlaceholderHook(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "custommilestone";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Clre20";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        try {
            String p = params.toLowerCase(java.util.Locale.ROOT);
            if (p.startsWith("top_")) return top(p);
            switch (p) {
                case "total": return String.valueOf(plugin.registry().size());
                case "vanilla_total": return String.valueOf(plugin.vanilla().total());
                default: break;
            }
            if (player == null) return "";
            PlayerData d = plugin.data().get(player.getUniqueId());
            if (d == null) return "";
            switch (p) {
                case "completed": return String.valueOf(d.completedCount(id -> plugin.registry().get(id) != null));
                case "percent": {
                    int total = plugin.registry().size();
                    int done = d.completedCount(id -> plugin.registry().get(id) != null);
                    return String.valueOf(total == 0 ? 0 : done * 100 / total);
                }
                case "vanilla_completed": return String.valueOf(d.vanillaCompleted());
                case "pending": return String.valueOf(d.pending().size());
                case "login_days": return String.valueOf(d.loginDays());
                case "login_streak": return String.valueOf(d.loginStreak());
                default: break;
            }
            if (p.startsWith("progress_")) {
                Milestone m = plugin.registry().get(p.substring(9));
                if (m == null) return "";
                return String.valueOf(d.isCompleted(m.id()) ? m.target() : d.progress(m.id()));
            }
            if (p.startsWith("target_")) {
                Milestone m = plugin.registry().get(p.substring(7));
                return m == null ? "" : String.valueOf(m.target());
            }
            if (p.startsWith("done_")) {
                Milestone m = plugin.registry().get(p.substring(5));
                return m == null ? "" : String.valueOf(d.isCompleted(m.id()));
            }
            return null;
        } catch (RuntimeException e) {
            // 其他插件可能在非同步執行緒呼叫，讀到正在修改的資料時回傳空值
            return "";
        }
    }

    private String top(String p) {
        boolean vanilla = p.startsWith("top_vanilla_");
        String rest = p.substring(vanilla ? 12 : 4);
        int idx = rest.indexOf('_');
        if (idx <= 0) return null;
        int rank;
        try {
            rank = Integer.parseInt(rest.substring(0, idx));
        } catch (NumberFormatException e) {
            return null;
        }
        String field = rest.substring(idx + 1);
        List<Database.TopEntry> list = plugin.leaderboard().top(vanilla);
        if (rank < 1 || rank > list.size()) return field.equals("value") ? "0" : "-";
        Database.TopEntry e = list.get(rank - 1);
        return switch (field) {
            case "name" -> e.name();
            case "value" -> String.valueOf(e.value());
            default -> null;
        };
    }
}
