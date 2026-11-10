package clre20.customMilestone.milestone;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * 所有分類與成就的定義（來自 milestones/*.yml）。只在主執行緒讀寫。
 */
public final class MilestoneRegistry {

    public static final Pattern CATEGORY_ID = Pattern.compile("^[a-z0-9_]{1,32}$");
    public static final int MAX_CATEGORIES = 100;
    public static final int MAX_MILESTONES = 2000;

    private final JavaPlugin plugin;
    private final File folder;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, Milestone> milestones = new HashMap<>();
    private final Map<TriggerType, List<Milestone>> byType = new EnumMap<>(TriggerType.class);
    private final Map<String, List<Milestone>> dependents = new HashMap<>();
    private final Set<Material> naturalTracked = EnumSet.noneOf(Material.class);
    private List<Category> ordered = List.of();

    public MilestoneRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "milestones");
    }

    public void load() {
        categories.clear();
        milestones.clear();
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                plugin.saveResource("milestones/basics.yml", false);
                plugin.saveResource("milestones/explore.yml", false);
            }
        }
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) files = new File[0];
        java.util.Arrays.sort(files);
        for (File f : files) {
            String catId = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (!CATEGORY_ID.matcher(catId).matches()) {
                plugin.getLogger().warning("略過檔案 " + f.getName() + "：檔名只能用小寫英數與底線（1~32 字）");
                continue;
            }
            if (categories.size() >= MAX_CATEGORIES) {
                plugin.getLogger().warning("分類數量超過上限 " + MAX_CATEGORIES + "，略過 " + f.getName());
                continue;
            }
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.load(f);
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().severe("讀取 " + f.getName() + " 失敗（格式錯誤）：" + e.getMessage());
                continue;
            }
            Category cat = Category.fromSection(catId, y.getConfigurationSection("category"));
            ConfigurationSection ms = y.getConfigurationSection("milestones");
            if (ms != null) {
                for (String rawId : ms.getKeys(false)) {
                    String id = rawId.toLowerCase(Locale.ROOT);
                    String where = f.getName() + " > " + rawId;
                    if (!Milestone.ID_PATTERN.matcher(id).matches() || id.equals("root")) {
                        plugin.getLogger().warning(where + "：ID 只能用小寫英數與底線（1~32 字），且不能叫 root");
                        continue;
                    }
                    if (milestones.containsKey(id)) {
                        plugin.getLogger().warning(where + "：ID 與其他成就重複，已略過");
                        continue;
                    }
                    if (milestones.size() >= MAX_MILESTONES) {
                        plugin.getLogger().warning("成就數量超過上限 " + MAX_MILESTONES + "，略過 " + where);
                        continue;
                    }
                    ConfigurationSection sec = ms.getConfigurationSection(rawId);
                    if (sec == null) continue;
                    Milestone m = Milestone.fromSection(id, catId, sec,
                            msg -> plugin.getLogger().warning(where + "：" + msg));
                    if (m == null) continue;
                    cat.put(m);
                    milestones.put(id, m);
                }
            }
            categories.put(catId, cat);
        }
        validateRequires();
        rebuildIndexes();
        plugin.getLogger().info("已載入 " + categories.size() + " 個分類、" + milestones.size() + " 個成就。");
    }

    /** 移除不存在的前置成就，並打斷循環依賴。 */
    private void validateRequires() {
        for (Milestone m : new ArrayList<>(milestones.values())) {
            List<String> ok = new ArrayList<>();
            for (String r : m.requires()) {
                if (r.equals(m.id())) continue;
                if (!milestones.containsKey(r)) {
                    plugin.getLogger().warning("成就 " + m.id() + " 的前置成就 " + r + " 不存在，已忽略");
                    continue;
                }
                if (!ok.contains(r)) ok.add(r);
            }
            if (ok.size() != m.requires().size()) replaceInternal(m.withRequires(ok));
        }
        for (Milestone m : new ArrayList<>(milestones.values())) {
            if (hasCycle(m.id(), new HashSet<>(), new HashSet<>())) {
                plugin.getLogger().warning("成就 " + m.id() + " 的前置成就形成循環，已清除它的前置設定");
                replaceInternal(milestones.get(m.id()).withRequires(List.of()));
            }
        }
    }

    private boolean hasCycle(String id, Set<String> visiting, Set<String> done) {
        if (done.contains(id)) return false;
        if (!visiting.add(id)) return true;
        Milestone m = milestones.get(id);
        if (m != null) {
            for (String r : m.requires()) {
                if (hasCycle(r, visiting, done)) return true;
            }
        }
        visiting.remove(id);
        done.add(id);
        return false;
    }

    private void replaceInternal(Milestone m) {
        milestones.put(m.id(), m);
        Category c = categories.get(m.categoryId());
        if (c != null) c.put(m);
    }

    private void rebuildIndexes() {
        byType.clear();
        dependents.clear();
        naturalTracked.clear();
        for (Milestone m : milestones.values()) {
            byType.computeIfAbsent(m.trigger().type(), k -> new ArrayList<>()).add(m);
            for (String r : m.requires()) {
                dependents.computeIfAbsent(r, k -> new ArrayList<>()).add(m);
            }
            if (m.trigger().type() == TriggerType.BREAK_BLOCK && m.trigger().naturalOnly()) {
                if (m.trigger().rawTargets().isEmpty()) {
                    for (Material mat : Material.values()) if (mat.isBlock() && !mat.isLegacy()) naturalTracked.add(mat);
                } else {
                    naturalTracked.addAll(m.trigger().materials());
                }
            }
        }
        List<Category> list = new ArrayList<>(categories.values());
        list.sort(Comparator.comparingInt(Category::order).thenComparing(Category::id));
        ordered = List.copyOf(list);
    }

    // ===== 查詢 =====

    public List<Milestone> byType(TriggerType type) {
        List<Milestone> l = byType.get(type);
        return l == null ? List.of() : l;
    }

    public boolean hasType(TriggerType type) {
        List<Milestone> l = byType.get(type);
        return l != null && !l.isEmpty();
    }

    public List<Milestone> dependents(String id) {
        List<Milestone> l = dependents.get(id);
        return l == null ? List.of() : l;
    }

    public Milestone get(String id) {
        return id == null ? null : milestones.get(id.toLowerCase(Locale.ROOT));
    }

    public Category category(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Category> categories() { return ordered; }
    public Collection<Milestone> all() { return Collections.unmodifiableCollection(milestones.values()); }
    public int size() { return milestones.size(); }
    public Set<Material> naturalTracked() { return naturalTracked; }

    /** 原版成就畫面的父節點：同分類中的第一個前置成就，沒有就是分類根節點（回傳 null）。 */
    public Milestone parentOf(Milestone m) {
        for (String r : m.requires()) {
            Milestone p = milestones.get(r);
            if (p != null && p.categoryId().equals(m.categoryId())) return p;
        }
        return null;
    }

    /** 依父子深度排序（父節點在前），註冊原版成就時使用。 */
    public List<Milestone> orderedByDepth(Category c) {
        List<Milestone> list = new ArrayList<>(c.milestones());
        Map<String, Integer> depth = new HashMap<>();
        for (Milestone m : list) depth.put(m.id(), depthOf(m, 0));
        list.sort(Comparator.comparingInt((Milestone m) -> depth.get(m.id())));
        return list;
    }

    private int depthOf(Milestone m, int guard) {
        Milestone p = parentOf(m);
        if (p == null || guard > 64) return 0;
        return 1 + depthOf(p, guard + 1);
    }

    // ===== 編輯器修改（主執行緒） =====

    public Category createCategory(String id, String name) {
        Category c = new Category(id, name, List.of(), Material.BOOK, Category.DEFAULT_BACKGROUND, categories.size() + 1);
        categories.put(id, c);
        rebuildIndexes();
        return c;
    }

    public void removeCategory(String id) {
        Category c = categories.remove(id);
        if (c == null) return;
        for (Milestone m : c.milestones()) milestones.remove(m.id());
        // 其他成就若以被刪除的成就為前置，前置條件一併移除
        cleanupRequires();
        rebuildIndexes();
    }

    public void put(Milestone m) {
        Category c = categories.get(m.categoryId());
        if (c == null) return;
        c.put(m);
        milestones.put(m.id(), m);
        validateRequires();
        rebuildIndexes();
    }

    public void removeMilestone(String id) {
        Milestone m = milestones.remove(id);
        if (m == null) return;
        Category c = categories.get(m.categoryId());
        if (c != null) c.remove(id);
        cleanupRequires();
        rebuildIndexes();
    }

    /** 分類資訊（名稱、圖示…）變更後重新排序。 */
    public void touch() {
        rebuildIndexes();
    }

    private void cleanupRequires() {
        for (Milestone m : new ArrayList<>(milestones.values())) {
            List<String> ok = m.requires().stream().filter(milestones::containsKey).toList();
            if (ok.size() != m.requires().size()) replaceInternal(m.withRequires(ok));
        }
    }

    // ===== 存檔 =====

    /** 在主執行緒序列化成字串，再交給 I/O 執行緒寫檔（先寫暫存檔再取代，避免寫一半壞檔）。 */
    public void saveAsync(Category c, Executor io) {
        YamlConfiguration y = new YamlConfiguration();
        c.write(y.createSection("category"));
        ConfigurationSection ms = y.createSection("milestones");
        for (Milestone m : c.milestones()) {
            m.write(ms.createSection(m.id()));
        }
        String content = y.saveToString();
        Path target = new File(folder, c.id() + ".yml").toPath();
        io.execute(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                Files.writeString(tmp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "無法儲存分類 " + c.id(), e);
            }
        });
    }

    public void deleteFileAsync(String categoryId, Executor io) {
        if (!CATEGORY_ID.matcher(categoryId).matches()) return;
        Path target = new File(folder, categoryId + ".yml").toPath();
        io.execute(() -> {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "無法刪除分類檔案 " + categoryId, e);
            }
        });
    }
}
