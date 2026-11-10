package clre20.customMilestone.update;

import clre20.customMilestone.CustomMilestone;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 透過 GitHub Releases 檢查更新。結果快取 10 分鐘、不定時輪詢，避免撞到 GitHub 每小時 60 次的限制。
 */
public final class UpdateChecker {

    public enum Status { UNKNOWN, LATEST, OUTDATED, DEV, NO_RELEASE, ERROR }

    public static final String REPO = "clre20/CustomMilestone";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long CACHE_MS = TimeUnit.MINUTES.toMillis(10);

    private final CustomMilestone plugin;
    private final HttpClient client;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Status status = Status.UNKNOWN;
    private volatile String latest = "";
    private volatile String url = "https://github.com/" + REPO + "/releases";
    private volatile long checkedAt;

    public UpdateChecker(CustomMilestone plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public String current() {
        return plugin.getPluginMeta().getVersion();
    }

    /** 檢查更新並把結果回報給 sender（null = 只寫後台）。 */
    public void check(CommandSender sender) {
        if (System.currentTimeMillis() - checkedAt < CACHE_MS && status != Status.UNKNOWN && status != Status.ERROR) {
            report(sender);
            return;
        }
        UUID requester = sender instanceof Player p ? p.getUniqueId() : null;
        boolean console = sender == null || !(sender instanceof Player);
        if (sender != null) plugin.messages().send(sender, "update.checking");
        if (!running.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                fetch();
            } finally {
                running.set(false);
            }
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (requester != null) {
                    Player p = Bukkit.getPlayer(requester);
                    if (p != null) report(p);
                } else if (console) {
                    report(sender == null ? Bukkit.getConsoleSender() : sender);
                }
            });
        });
    }

    private void fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "CustomMilestone-UpdateChecker")
                    .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            checkedAt = System.currentTimeMillis();
            if (res.statusCode() == 404) {
                status = Status.NO_RELEASE;
                return;
            }
            if (res.statusCode() != 200) {
                status = Status.ERROR;
                return;
            }
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonElement tag = o.get("tag_name");
            JsonElement html = o.get("html_url");
            if (tag == null || tag.isJsonNull()) {
                status = Status.ERROR;
                return;
            }
            latest = strip(tag.getAsString());
            if (html != null && !html.isJsonNull() && html.getAsString().startsWith("https://github.com/")) {
                url = html.getAsString();
            }
            int cmp = compare(strip(current()), latest);
            status = cmp < 0 ? Status.OUTDATED : (cmp > 0 ? Status.DEV : Status.LATEST);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = Status.ERROR;
        } catch (Exception e) {
            status = Status.ERROR;
        }
    }

    private void report(CommandSender sender) {
        String key = switch (status) {
            case LATEST -> "update.latest";
            case OUTDATED -> "update.outdated";
            case DEV -> "update.dev";
            case NO_RELEASE -> "update.no-release";
            default -> "update.failed";
        };
        plugin.messages().send(sender, key, "current", current(), "latest", latest, "url", url);
    }

    /** 登入提醒：只在已知有新版本時通知，不會為此重新查詢。 */
    public void notifyIfOutdated(Player p) {
        if (status == Status.OUTDATED) {
            plugin.messages().send(p, "update.join-notify", "current", current(), "latest", latest, "url", url);
        }
    }

    public static String strip(String v) {
        String s = v == null ? "" : v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        return s;
    }

    /** 比較 x.y.z（忽略 -SNAPSHOT 等後綴）。 */
    public static int compare(String a, String b) {
        String[] pa = a.split("[.\\-+]");
        String[] pb = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? num(pa[i]) : 0;
            int y = i < pb.length ? num(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
