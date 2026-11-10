package clre20.customMilestone.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 一位玩家的成就資料（記憶體快取）。只在主執行緒修改；存檔時建立快照交給 I/O 執行緒。
 */
public final class PlayerData {

    public record PendingReward(String id, byte[] data, long created) { }

    public record Snapshot(UUID uuid, String name, int completedCount, int vanilla, int loginDays, int loginStreak,
                           long lastLoginDay, boolean wipe, Map<String, Long> progressUpserts,
                           Set<String> progressDeletes, Map<String, Long> completedUpserts,
                           Set<String> completedDeletes, List<PendingReward> pendingInserts,
                           Set<String> pendingDeletes) { }

    private final UUID uuid;
    private String name;
    private final Map<String, Long> progress = new HashMap<>();
    private final Map<String, Long> completed = new HashMap<>();
    private final List<PendingReward> pending = new ArrayList<>();
    private int vanillaCompleted;
    private int loginDays;
    private int loginStreak;
    private long lastLoginDay;

    // 髒資料標記
    private boolean rowDirty;
    private boolean wipe;
    private final Set<String> dirtyProgress = new HashSet<>();
    private final Set<String> dirtyCompleted = new HashSet<>();
    private final List<PendingReward> newPending = new ArrayList<>();
    private final Set<String> removedPending = new HashSet<>();
    private long lastAccess = System.currentTimeMillis();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    // ===== 載入（I/O 執行緒，物件尚未公開） =====
    void loadRow(String name, int vanilla, int loginDays, int loginStreak, long lastLoginDay) {
        if (name != null && !name.isEmpty()) this.name = name;
        this.vanillaCompleted = vanilla;
        this.loginDays = loginDays;
        this.loginStreak = loginStreak;
        this.lastLoginDay = lastLoginDay;
    }
    void loadProgress(String id, long value) { progress.put(id, value); }
    void loadCompleted(String id, long time) { completed.put(id, time); }
    void loadPending(PendingReward r) { pending.add(r); }

    // ===== 查詢 =====
    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public long progress(String id) { return progress.getOrDefault(id, 0L); }
    public boolean isCompleted(String id) { return completed.containsKey(id); }
    public Long completedAt(String id) { return completed.get(id); }
    public Map<String, Long> completedMap() { return Collections.unmodifiableMap(completed); }
    public int completedCount(Predicate<String> exists) {
        int n = 0;
        for (String id : completed.keySet()) if (exists.test(id)) n++;
        return n;
    }
    public List<PendingReward> pending() { return Collections.unmodifiableList(pending); }
    public int vanillaCompleted() { return vanillaCompleted; }
    public int loginDays() { return loginDays; }
    public int loginStreak() { return loginStreak; }
    public long lastLoginDay() { return lastLoginDay; }
    public long lastAccess() { return lastAccess; }
    public void touch() { lastAccess = System.currentTimeMillis(); }

    public boolean isDirty() {
        return rowDirty || wipe || !dirtyProgress.isEmpty() || !dirtyCompleted.isEmpty()
                || !newPending.isEmpty() || !removedPending.isEmpty();
    }

    // ===== 修改（主執行緒） =====
    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            rowDirty = true;
        }
    }

    public void setProgress(String id, long value) {
        if (value <= 0) {
            if (progress.remove(id) != null) dirtyProgress.add(id);
            return;
        }
        Long old = progress.put(id, value);
        if (old == null || old != value) dirtyProgress.add(id);
    }

    public void setCompleted(String id, long time) {
        completed.put(id, time);
        dirtyCompleted.add(id);
        rowDirty = true;
        // 完成後進度不再需要，刪掉以節省空間
        setProgress(id, 0);
    }

    public boolean removeCompleted(String id) {
        if (completed.remove(id) != null) {
            dirtyCompleted.add(id);
            rowDirty = true;
            return true;
        }
        return false;
    }

    public void setVanillaCompleted(int v) {
        if (v != vanillaCompleted) {
            vanillaCompleted = v;
            rowDirty = true;
        }
    }

    public void setLogin(int days, int streak, long lastDay) {
        loginDays = days;
        loginStreak = streak;
        lastLoginDay = lastDay;
        rowDirty = true;
    }

    public void addPending(PendingReward r) {
        pending.add(r);
        newPending.add(r);
    }

    public boolean removePending(String id) {
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).id().equals(id)) {
                PendingReward r = pending.remove(i);
                if (!newPending.remove(r)) removedPending.add(id);
                return true;
            }
        }
        return false;
    }

    /** 清除全部成就進度與完成紀錄（/cm reset）。待領取獎勵與登入統計保留。 */
    public void wipeAll() {
        progress.clear();
        completed.clear();
        dirtyProgress.clear();
        dirtyCompleted.clear();
        wipe = true;
        rowDirty = true;
    }

    /** 建立存檔快照並清除髒標記。 */
    public Snapshot snapshot(int completedCount) {
        Map<String, Long> pu = new HashMap<>();
        Set<String> pd = new HashSet<>();
        for (String id : dirtyProgress) {
            Long v = progress.get(id);
            if (v == null) pd.add(id); else pu.put(id, v);
        }
        Map<String, Long> cu = new HashMap<>();
        Set<String> cd = new HashSet<>();
        for (String id : dirtyCompleted) {
            Long v = completed.get(id);
            if (v == null) cd.add(id); else cu.put(id, v);
        }
        Snapshot s;
        if (wipe) {
            // 重置：先刪除全部，再寫入目前記憶體中的內容
            s = new Snapshot(uuid, name, completedCount, vanillaCompleted, loginDays, loginStreak, lastLoginDay,
                    true, new HashMap<>(progress), Set.of(), new HashMap<>(completed), Set.of(),
                    List.copyOf(newPending), Set.copyOf(removedPending));
        } else {
            s = new Snapshot(uuid, name, completedCount, vanillaCompleted, loginDays, loginStreak, lastLoginDay,
                    false, pu, pd, cu, cd, List.copyOf(newPending), Set.copyOf(removedPending));
        }
        rowDirty = false;
        wipe = false;
        dirtyProgress.clear();
        dirtyCompleted.clear();
        newPending.clear();
        removedPending.clear();
        return s;
    }
}
