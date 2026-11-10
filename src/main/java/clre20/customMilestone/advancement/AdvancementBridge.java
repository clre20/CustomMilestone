package clre20.customMilestone.advancement;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.config.ConfigManager;
import clre20.customMilestone.milestone.Category;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.MilestoneRegistry;
import clre20.customMilestone.util.Text;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * 把自訂成就註冊成「真正的原版成就」（Bukkit UnsafeValues#loadAdvancement）。
 * 只重新載入有變更的成就，避免每次重載都讓線上玩家重讀整份成就資料。
 * 伺服器不支援時切換到降級模式（active = false）。
 */
public final class AdvancementBridge {

    public static final String NAMESPACE = "custommilestone";
    private static final String CRITERION = "done";

    private final CustomMilestone plugin;
    private final File cacheFile;
    private final Map<String, String> hashes = new HashMap<>();
    private final Set<String> silent = new HashSet<>();
    private boolean active;
    private int pendingRestart;

    public AdvancementBridge(CustomMilestone plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "advancement-cache.yml");
    }

    public boolean isActive() {
        return active;
    }

    /** 已變更或刪除、但要重啟伺服器才會更新原版畫面的成就數。 */
    public int pendingRestart() {
        return active ? pendingRestart : 0;
    }

    // ===== 註冊 =====

    /** 依目前的成就定義同步原版成就（主執行緒）。 */
    @SuppressWarnings("deprecation")
    public void sync(MilestoneRegistry registry) {
        if (plugin.config().displayMode() == ConfigManager.DisplayMode.FALLBACK) {
            if (active) {
                plugin.getLogger().info("display.mode 為 fallback，已停用原版成就顯示");
            }
            active = false;
            return;
        }
        loadCache();

        // 1. 目標狀態（父節點在前）
        LinkedHashMap<NamespacedKey, String> desired = new LinkedHashMap<>();
        Map<NamespacedKey, NamespacedKey> parents = new HashMap<>();
        for (Category c : registry.categories()) {
            NamespacedKey root = c.rootKey(NAMESPACE);
            desired.put(root, rootJson(c));
            for (Milestone m : registry.orderedByDepth(c)) {
                Milestone p = registry.parentOf(m);
                NamespacedKey parent = p == null ? root : p.advancementKey(NAMESPACE);
                NamespacedKey key = m.advancementKey(NAMESPACE);
                desired.put(key, milestoneJson(m, parent));
                parents.put(key, parent);
            }
        }

        try {
            // 2. 目前已存在的自訂成就
            Map<NamespacedKey, Advancement> existing = new HashMap<>();
            for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
                Advancement a = it.next();
                if (a.getKey().getNamespace().equals(NAMESPACE)) existing.put(a.getKey(), a);
            }

            // 3. 分類：不存在 → 直接載入；內容變了或已刪除 → 嘗試移除
            //    （Paper 26.1 的 removeAdvancement 只刪 datapack 檔案、不會從記憶體移除，
            //     這種情況下舊版本會保留到重啟，重啟時一律以最新定義重新註冊）
            Set<NamespacedKey> changed = new HashSet<>();
            for (Map.Entry<NamespacedKey, String> e : desired.entrySet()) {
                NamespacedKey key = e.getKey();
                if (existing.containsKey(key) && !hash(e.getValue()).equals(hashes.get(key.toString()))) {
                    changed.add(key);
                }
            }
            Set<NamespacedKey> remove = new HashSet<>(changed);
            for (NamespacedKey key : existing.keySet()) {
                if (!desired.containsKey(key)) remove.add(key);
            }

            List<NamespacedKey> toLoad = new ArrayList<>();
            for (NamespacedKey key : desired.keySet()) {
                if (!existing.containsKey(key)) toLoad.add(key);
            }
            if (toLoad.isEmpty() && remove.isEmpty()) {
                pendingRestart = 0;
                active = true;
                return;
            }

            // 4. 移除（子節點優先）；真的從記憶體移除的，才可以立即以新內容載入
            List<NamespacedKey> removeOrder = new ArrayList<>(remove);
            removeOrder.sort((x, y) -> Integer.compare(depth(existing.get(y)), depth(existing.get(x))));
            int stale = 0;
            for (NamespacedKey key : removeOrder) {
                Bukkit.getUnsafe().removeAdvancement(key);
                if (Bukkit.getAdvancement(key) == null) {
                    hashes.remove(key.toString());
                    if (desired.containsKey(key)) toLoad.add(key);
                } else {
                    stale++;
                }
            }
            pendingRestart = stale;

            // 依父節點在前的順序
            Set<NamespacedKey> loadSet = new HashSet<>(toLoad);
            List<NamespacedKey> loadOrder = new ArrayList<>();
            for (NamespacedKey key : desired.keySet()) if (loadSet.contains(key)) loadOrder.add(key);

            if (!loadOrder.isEmpty() && !Bukkit.getOnlinePlayers().isEmpty()) {
                // 載入成就時伺服器會讓線上玩家從硬碟重讀成就進度：先存檔，避免遺失尚未寫入的原版進度
                Bukkit.savePlayers();
            }

            // 5. 載入
            int loaded = 0;
            int failed = 0;
            Throwable lastError = null;
            for (NamespacedKey key : loadOrder) {
                String json = desired.get(key);
                try {
                    Advancement adv = Bukkit.getUnsafe().loadAdvancement(key, json);
                    if (adv == null) throw new IllegalStateException("loadAdvancement 回傳 null");
                    hashes.put(key.toString(), hash(json));
                    loaded++;
                } catch (UnsupportedOperationException | LinkageError ex) {
                    throw ex; // 伺服器不支援，整體改用降級模式
                } catch (Exception ex) {
                    // 單一成就的定義有問題：略過它，不影響其他成就
                    failed++;
                    lastError = ex;
                    plugin.getLogger().warning("無法註冊原版成就 " + key + "：" + ex.getMessage());
                }
            }
            saveCache();
            if (loaded == 0 && failed > 0 && existing.isEmpty()) {
                throw new IllegalStateException("所有成就都註冊失敗", lastError);
            }
            active = true;
            if (loaded > 0) resyncOnline();
            plugin.getLogger().info("原版成就同步完成：載入 " + loaded + " 個"
                    + (stale > 0 ? "，" + stale + " 個已變更或刪除的成就會在伺服器重啟後更新原版畫面" : "") + "。");
        } catch (Throwable t) {
            active = false;
            plugin.getLogger().log(Level.WARNING, "無法註冊原版成就，改用降級模式（聊天室廣播＋標題）。原因：" + t, t);
        }
    }

    private static final java.util.regex.Pattern RESOURCE_ID = java.util.regex.Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private String validBackground(String bg) {
        if (bg != null && RESOURCE_ID.matcher(bg).matches()) return bg;
        plugin.getLogger().warning("分類背景 ID 格式錯誤：" + bg + "，改用預設背景");
        return Category.DEFAULT_BACKGROUND;
    }

    /** 重新載入後，讓線上玩家的自訂成就狀態與資料庫一致（靜默補發）。 */
    private void resyncOnline() {
        if (plugin.progress() == null || plugin.data() == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            clre20.customMilestone.data.PlayerData d = plugin.data().get(p.getUniqueId());
            if (d != null) plugin.progress().syncAdvancements(p, d);
        }
    }

    private static int depth(Advancement a) {
        int d = 0;
        Advancement cur = a == null ? null : a.getParent();
        while (cur != null && d < 64) {
            d++;
            cur = cur.getParent();
        }
        return d;
    }

    private String rootJson(Category c) {
        JsonObject o = new JsonObject();
        JsonObject display = new JsonObject();
        display.add("icon", icon(c.icon().getKey().toString()));
        display.add("title", component(Text.color(c.name())));
        display.add("description", component(Text.color(String.join("\n", c.description()))));
        display.addProperty("background", validBackground(c.background()));
        display.addProperty("frame", "task");
        display.addProperty("show_toast", false);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", false);
        o.add("display", display);
        JsonObject criteria = new JsonObject();
        JsonObject tick = new JsonObject();
        tick.addProperty("trigger", "minecraft:tick");
        criteria.add("tick", tick);
        o.add("criteria", criteria);
        o.add("requirements", requirements("tick"));
        return o.toString();
    }

    private String milestoneJson(Milestone m, NamespacedKey parent) {
        JsonObject o = new JsonObject();
        o.addProperty("parent", parent.toString());
        JsonObject display = new JsonObject();
        display.add("icon", icon(m.icon().getKey().toString()));
        display.add("title", component(Text.color(m.name())));
        display.add("description", component(Text.color(String.join("\n", m.description()))));
        display.addProperty("frame", m.frame().key());
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
        display.addProperty("hidden", m.hidden());
        o.add("display", display);
        JsonObject criteria = new JsonObject();
        JsonObject done = new JsonObject();
        done.addProperty("trigger", "minecraft:impossible");
        criteria.add(CRITERION, done);
        o.add("criteria", criteria);
        o.add("requirements", requirements(CRITERION));
        return o.toString();
    }

    private static JsonObject icon(String id) {
        JsonObject icon = new JsonObject();
        icon.addProperty("id", id);
        return icon;
    }

    private static JsonElement component(Component c) {
        return GsonComponentSerializer.gson().serializeToTree(c);
    }

    private static JsonArray requirements(String criterion) {
        JsonArray outer = new JsonArray();
        JsonArray inner = new JsonArray();
        inner.add(criterion);
        outer.add(inner);
        return outer;
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private void loadCache() {
        hashes.clear();
        if (!cacheFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(cacheFile);
        for (String k : y.getKeys(false)) {
            String v = y.getString(k);
            if (v != null) hashes.put(k.replace('|', ':').replace('>', '/'), v);
        }
    }

    private void saveCache() {
        YamlConfiguration y = new YamlConfiguration();
        // YAML 鍵不能含 . ，以替代字元儲存
        for (Map.Entry<String, String> e : hashes.entrySet()) {
            y.set(e.getKey().replace(':', '|').replace('/', '>').replace('.', '_'), e.getValue());
        }
        String content = y.saveToString();
        plugin.database().executor().execute(() -> {
            try {
                java.nio.file.Files.writeString(cacheFile.toPath(), content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "無法寫入 advancement-cache.yml", e);
            }
        });
    }

    // ===== 給予／撤銷 =====

    public Advancement advancement(Milestone m) {
        return Bukkit.getAdvancement(m.advancementKey(NAMESPACE));
    }

    /** 給予原版成就。silent = true 時不廣播聊天室（上線補發用）。 */
    public void award(Player player, Milestone m, boolean silent) {
        if (!active) return;
        Advancement adv = advancement(m);
        if (adv == null) return;
        AdvancementProgress p = player.getAdvancementProgress(adv);
        if (p.isDone()) return;
        if (silent) this.silent.add(player.getUniqueId() + "|" + adv.getKey());
        try {
            p.awardCriteria(CRITERION);
        } finally {
            this.silent.remove(player.getUniqueId() + "|" + adv.getKey());
        }
    }

    public void revoke(Player player, Milestone m) {
        if (!active) return;
        Advancement adv = advancement(m);
        if (adv == null) return;
        AdvancementProgress p = player.getAdvancementProgress(adv);
        for (String c : p.getAwardedCriteria()) p.revokeCriteria(c);
    }

    public boolean isDone(Player player, Milestone m) {
        Advancement adv = advancement(m);
        return adv != null && player.getAdvancementProgress(adv).isDone();
    }

    /** AdvancementListener 用：這次達成是否為靜默補發。 */
    public boolean isSilent(Player player, Advancement adv) {
        return silent.contains(player.getUniqueId() + "|" + adv.getKey());
    }
}
