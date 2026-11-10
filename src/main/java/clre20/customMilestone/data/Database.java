package clre20.customMilestone.data;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite 存取。所有 SQL 只在單一 I/O 執行緒上執行（executor()），連線不會被多執行緒共用。
 */
public final class Database {

    public record TopEntry(UUID uuid, String name, int value) { }

    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CustomMilestone-IO");
            t.setDaemon(true);
            return t;
        });
    }

    public ExecutorService executor() {
        return executor;
    }

    /** 在 I/O 執行緒上開啟資料庫並建立資料表，等待完成。 */
    public boolean open() {
        try {
            return executor.submit(() -> {
                Class.forName("org.sqlite.JDBC");
                File file = new File(plugin.getDataFolder(), "data.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA synchronous=NORMAL");
                    st.execute("PRAGMA foreign_keys=OFF");
                    st.execute("CREATE TABLE IF NOT EXISTS cm_players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', "
                            + "completed INTEGER NOT NULL DEFAULT 0, vanilla INTEGER NOT NULL DEFAULT 0, "
                            + "login_days INTEGER NOT NULL DEFAULT 0, login_streak INTEGER NOT NULL DEFAULT 0, "
                            + "last_login_day INTEGER NOT NULL DEFAULT 0, updated INTEGER NOT NULL DEFAULT 0)");
                    st.execute("CREATE TABLE IF NOT EXISTS cm_progress (uuid TEXT NOT NULL, milestone TEXT NOT NULL, "
                            + "value INTEGER NOT NULL, PRIMARY KEY (uuid, milestone))");
                    st.execute("CREATE TABLE IF NOT EXISTS cm_completed (uuid TEXT NOT NULL, milestone TEXT NOT NULL, "
                            + "time INTEGER NOT NULL, PRIMARY KEY (uuid, milestone))");
                    st.execute("CREATE TABLE IF NOT EXISTS cm_pending (id TEXT PRIMARY KEY, uuid TEXT NOT NULL, "
                            + "data BLOB NOT NULL, created INTEGER NOT NULL)");
                    st.execute("CREATE INDEX IF NOT EXISTS cm_pending_uuid ON cm_pending (uuid)");
                    st.execute("CREATE INDEX IF NOT EXISTS cm_players_name ON cm_players (name COLLATE NOCASE)");
                }
                return true;
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "無法開啟 SQLite 資料庫", e);
            return false;
        }
    }

    /** 只能在 I/O 執行緒呼叫。 */
    PlayerData load(UUID uuid, String name) throws SQLException {
        PlayerData d = new PlayerData(uuid, name);
        String id = uuid.toString();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, vanilla, login_days, login_streak, last_login_day FROM cm_players WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    d.loadRow(name == null || name.isEmpty() ? rs.getString(1) : name, rs.getInt(2), rs.getInt(3),
                            rs.getInt(4), rs.getLong(5));
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT milestone, value FROM cm_progress WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) d.loadProgress(rs.getString(1), rs.getLong(2));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT milestone, time FROM cm_completed WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) d.loadCompleted(rs.getString(1), rs.getLong(2));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, data, created FROM cm_pending WHERE uuid = ? ORDER BY created")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) d.loadPending(new PlayerData.PendingReward(rs.getString(1), rs.getBytes(2), rs.getLong(3)));
            }
        }
        return d;
    }

    /** 只能在 I/O 執行緒呼叫。一次交易寫入一批快照。 */
    void save(List<PlayerData.Snapshot> batch) {
        if (batch.isEmpty() || connection == null) return;
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement player = connection.prepareStatement(
                    "INSERT INTO cm_players (uuid, name, completed, vanilla, login_days, login_streak, last_login_day, updated) "
                            + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, "
                            + "completed=excluded.completed, vanilla=excluded.vanilla, login_days=excluded.login_days, "
                            + "login_streak=excluded.login_streak, last_login_day=excluded.last_login_day, updated=excluded.updated");
                 PreparedStatement progUp = connection.prepareStatement(
                         "INSERT INTO cm_progress (uuid, milestone, value) VALUES (?,?,?) "
                                 + "ON CONFLICT(uuid, milestone) DO UPDATE SET value=excluded.value");
                 PreparedStatement progDel = connection.prepareStatement("DELETE FROM cm_progress WHERE uuid=? AND milestone=?");
                 PreparedStatement compUp = connection.prepareStatement(
                         "INSERT INTO cm_completed (uuid, milestone, time) VALUES (?,?,?) "
                                 + "ON CONFLICT(uuid, milestone) DO UPDATE SET time=excluded.time");
                 PreparedStatement compDel = connection.prepareStatement("DELETE FROM cm_completed WHERE uuid=? AND milestone=?");
                 PreparedStatement pendIns = connection.prepareStatement(
                         "INSERT OR REPLACE INTO cm_pending (id, uuid, data, created) VALUES (?,?,?,?)");
                 PreparedStatement pendDel = connection.prepareStatement("DELETE FROM cm_pending WHERE id=? AND uuid=?");
                 PreparedStatement wipeProg = connection.prepareStatement("DELETE FROM cm_progress WHERE uuid=?");
                 PreparedStatement wipeComp = connection.prepareStatement("DELETE FROM cm_completed WHERE uuid=?")) {
                long now = System.currentTimeMillis();
                for (PlayerData.Snapshot s : batch) {
                    String id = s.uuid().toString();
                    if (s.wipe()) {
                        wipeProg.setString(1, id);
                        wipeProg.executeUpdate();
                        wipeComp.setString(1, id);
                        wipeComp.executeUpdate();
                    }
                    player.setString(1, id);
                    player.setString(2, s.name());
                    player.setInt(3, s.completedCount());
                    player.setInt(4, s.vanilla());
                    player.setInt(5, s.loginDays());
                    player.setInt(6, s.loginStreak());
                    player.setLong(7, s.lastLoginDay());
                    player.setLong(8, now);
                    player.addBatch();
                    for (Map.Entry<String, Long> e : s.progressUpserts().entrySet()) {
                        progUp.setString(1, id);
                        progUp.setString(2, e.getKey());
                        progUp.setLong(3, e.getValue());
                        progUp.addBatch();
                    }
                    for (String m : s.progressDeletes()) {
                        progDel.setString(1, id);
                        progDel.setString(2, m);
                        progDel.addBatch();
                    }
                    for (Map.Entry<String, Long> e : s.completedUpserts().entrySet()) {
                        compUp.setString(1, id);
                        compUp.setString(2, e.getKey());
                        compUp.setLong(3, e.getValue());
                        compUp.addBatch();
                    }
                    for (String m : s.completedDeletes()) {
                        compDel.setString(1, id);
                        compDel.setString(2, m);
                        compDel.addBatch();
                    }
                    for (PlayerData.PendingReward r : s.pendingInserts()) {
                        pendIns.setString(1, r.id());
                        pendIns.setString(2, id);
                        pendIns.setBytes(3, r.data());
                        pendIns.setLong(4, r.created());
                        pendIns.addBatch();
                    }
                    for (String r : s.pendingDeletes()) {
                        pendDel.setString(1, r);
                        pendDel.setString(2, id);
                        pendDel.addBatch();
                    }
                }
                player.executeBatch();
                progUp.executeBatch();
                progDel.executeBatch();
                compUp.executeBatch();
                compDel.executeBatch();
                pendIns.executeBatch();
                pendDel.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "寫入玩家資料失敗", e);
        }
    }

    /** 只能在 I/O 執行緒呼叫。 */
    List<TopEntry> top(boolean includeVanilla, int limit) {
        List<TopEntry> out = new ArrayList<>();
        if (connection == null) return out;
        String col = includeVanilla ? "(completed + vanilla)" : "completed";
        String sql = "SELECT uuid, name, " + col + " AS v FROM cm_players WHERE " + col
                + " > 0 ORDER BY v DESC, updated ASC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.add(new TopEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3)));
                    } catch (IllegalArgumentException ignored) {
                        // 損毀的 UUID 略過
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "讀取排行榜失敗", e);
        }
        return out;
    }

    /** 只能在 I/O 執行緒呼叫：依名稱找 UUID（離線玩家指令用）。 */
    UUID findByName(String name) {
        if (connection == null) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid FROM cm_players WHERE name = ? COLLATE NOCASE LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString(1));
            }
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "查詢玩家失敗", e);
        }
        return null;
    }

    /** 關閉：等待所有排隊中的寫入完成。 */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("資料庫寫入未在 30 秒內完成，強制關閉");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
            // 關服中，忽略
        }
    }
}
