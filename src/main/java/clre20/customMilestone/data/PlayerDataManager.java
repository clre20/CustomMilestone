package clre20.customMilestone.data;

import clre20.customMilestone.CustomMilestone;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * 玩家資料快取：登入前非同步預載、定時批次寫入（髒資料標記）、離線時存檔並移出記憶體。
 */
public final class PlayerDataManager {

    /** 離線玩家資料（管理員指令、查看他人）在記憶體保留的時間。 */
    private static final long OFFLINE_KEEP_MS = TimeUnit.MINUTES.toMillis(10);

    private final CustomMilestone plugin;
    private final Database db;
    private final Map<UUID, PlayerData> online = new HashMap<>();
    private final Map<UUID, PlayerData> offline = new HashMap<>();
    private record Preloaded(PlayerData data, long time) { }
    private final Map<UUID, Preloaded> preloaded = new ConcurrentHashMap<>();
    private BukkitTask flushTask;

    public PlayerDataManager(CustomMilestone plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start(int intervalSeconds) {
        if (flushTask != null) flushTask.cancel();
        long ticks = intervalSeconds * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flush, ticks, ticks);
    }

    // ===== 載入 =====

    /** AsyncPlayerPreLoginEvent（非同步執行緒）呼叫：在 I/O 執行緒載入並等待。 */
    public void preload(UUID uuid, String name) {
        try {
            PlayerData d = db.executor().submit(() -> db.load(uuid, name)).get(10, TimeUnit.SECONDS);
            preloaded.put(uuid, new Preloaded(d, System.currentTimeMillis()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "預載玩家資料失敗：" + name, e);
        }
    }

    /** 玩家加入（主執行緒）。回傳 null 代表需要等待非同步載入，載入後會呼叫 ready。 */
    public PlayerData attach(UUID uuid, String name, Consumer<PlayerData> ready) {
        PlayerData d = offline.remove(uuid);
        Preloaded pre = preloaded.remove(uuid);
        if (d == null && pre != null) d = pre.data();
        if (d != null) {
            d.setName(name);
            online.put(uuid, d);
            return d;
        }
        loadAsync(uuid, name, loaded -> {
            if (Bukkit.getPlayer(uuid) == null) {
                return;
            }
            PlayerData existing = online.get(uuid);
            if (existing != null) {
                ready.accept(existing);
                return;
            }
            loaded.setName(name);
            online.put(uuid, loaded);
            ready.accept(loaded);
        });
        return null;
    }

    /** 非同步載入任意玩家（離線也可），結果在主執行緒回呼。已在記憶體的直接回傳。 */
    public void loadAsync(UUID uuid, String name, Consumer<PlayerData> callback) {
        PlayerData d = online.get(uuid);
        if (d == null) d = offline.get(uuid);
        if (d != null) {
            d.touch();
            callback.accept(d);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return db.load(uuid, name);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, db.executor()).whenComplete((loaded, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "讀取玩家資料失敗：" + uuid, err);
                return;
            }
            PlayerData cur = online.get(uuid);
            if (cur == null) cur = offline.get(uuid);
            if (cur == null) {
                cur = loaded;
                if (Bukkit.getPlayer(uuid) == null) offline.put(uuid, cur);
            }
            cur.touch();
            callback.accept(cur);
        }));
    }

    /** 依名稱找 UUID：先找快取，再查資料庫。結果在主執行緒回呼（找不到為 null）。 */
    public void findUuid(String name, Consumer<UUID> callback) {
        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            callback.accept(cached.getUniqueId());
            return;
        }
        CompletableFuture.supplyAsync(() -> db.findByName(name), db.executor())
                .whenComplete((uuid, err) -> Bukkit.getScheduler().runTask(plugin, () -> callback.accept(err == null ? uuid : null)));
    }

    // ===== 查詢 =====

    public PlayerData get(UUID uuid) {
        return online.get(uuid);
    }

    /** 線上或已載入的離線資料。 */
    public PlayerData getLoaded(UUID uuid) {
        PlayerData d = online.get(uuid);
        return d != null ? d : offline.get(uuid);
    }

    public boolean isOnline(PlayerData d) {
        return online.get(d.uuid()) == d;
    }

    // ===== 存檔 =====

    /** 玩家離線：立即存檔並移出記憶體。 */
    public void detach(UUID uuid) {
        preloaded.remove(uuid);
        PlayerData d = online.remove(uuid);
        if (d != null && d.isDirty()) {
            submit(List.of(snapshot(d)));
        }
    }

    /** 管理員修改離線玩家資料後立即存檔。 */
    public void saveNow(PlayerData d) {
        if (d.isDirty()) submit(List.of(snapshot(d)));
    }

    private PlayerData.Snapshot snapshot(PlayerData d) {
        return d.snapshot(d.completedCount(id -> plugin.registry().get(id) != null));
    }

    /** 定時批次寫入（主執行緒建立快照，I/O 執行緒寫入）。 */
    public void flush() {
        List<PlayerData.Snapshot> batch = new ArrayList<>();
        for (PlayerData d : online.values()) {
            if (d.isDirty()) batch.add(snapshot(d));
        }
        long now = System.currentTimeMillis();
        for (Iterator<PlayerData> it = offline.values().iterator(); it.hasNext(); ) {
            PlayerData d = it.next();
            if (d.isDirty()) batch.add(snapshot(d));
            if (now - d.lastAccess() > OFFLINE_KEEP_MS) it.remove();
        }
        // 預載後沒有進服的資料（例如登入被其他插件拒絕）也清掉，避免洩漏
        preloaded.values().removeIf(p -> now - p.time() > 60_000);
        if (!batch.isEmpty()) submit(batch);
    }

    private void submit(List<PlayerData.Snapshot> batch) {
        try {
            db.executor().execute(() -> db.save(batch));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            plugin.getLogger().warning("I/O 執行緒已關閉，無法寫入 " + batch.size() + " 筆資料");
        }
    }

    /** 關服：所有資料存檔（排入 I/O 佇列，之後 Database.close() 會等待完成）。 */
    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        List<PlayerData.Snapshot> batch = new ArrayList<>();
        for (PlayerData d : online.values()) if (d.isDirty()) batch.add(snapshot(d));
        for (PlayerData d : offline.values()) if (d.isDirty()) batch.add(snapshot(d));
        if (!batch.isEmpty()) submit(batch);
        online.clear();
        offline.clear();
        preloaded.clear();
    }

    /** 排行榜查詢（I/O 執行緒）。 */
    public CompletableFuture<List<Database.TopEntry>> topAsync(boolean vanilla, int limit) {
        return CompletableFuture.supplyAsync(() -> db.top(vanilla, limit), db.executor());
    }

    public java.util.Collection<PlayerData> onlineData() {
        return online.values();
    }
}
