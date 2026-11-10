package clre20.customMilestone.milestone;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 一個自訂成就（不可變；編輯器修改時建立新的物件再替換）。
 */
public final class Milestone {

    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]{1,32}$");

    public enum Frame { TASK, GOAL, CHALLENGE;
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public static Frame parse(String s) {
            try {
                return s == null ? TASK : valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return TASK;
            }
        }
        public Frame next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private final String id;
    private final String categoryId;
    private final String name;
    private final List<String> description;
    private final Material icon;
    private final Frame frame;
    private final boolean hidden;
    private final Trigger trigger;
    private final List<String> requires;
    private final Reward reward;

    public Milestone(String id, String categoryId, String name, List<String> description, Material icon,
                     Frame frame, boolean hidden, Trigger trigger, List<String> requires, Reward reward) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.description = List.copyOf(description);
        this.icon = icon == null || !icon.isItem() || icon.isAir() ? Material.PAPER : icon;
        this.frame = frame;
        this.hidden = hidden;
        this.trigger = trigger;
        this.requires = List.copyOf(requires);
        this.reward = reward;
    }

    public static Milestone fromSection(String id, String categoryId, ConfigurationSection s, Consumer<String> warn) {
        ConfigurationSection ts = s.getConfigurationSection("trigger");
        if (ts == null) {
            warn.accept("缺少 trigger 區段");
            return null;
        }
        Trigger trigger = Trigger.fromSection(ts, warn);
        if (trigger == null) return null;
        Material icon = Material.matchMaterial(s.getString("icon", "paper"));
        if (icon == null) warn.accept("未知的圖示：" + s.getString("icon"));
        List<String> desc = s.isList("description") ? s.getStringList("description")
                : (s.isString("description") ? List.of(s.getString("description", "")) : List.of());
        List<String> req = new ArrayList<>();
        for (String r : s.getStringList("requires")) req.add(r.trim().toLowerCase(Locale.ROOT));
        return new Milestone(id, categoryId, s.getString("name", id), desc, icon,
                Frame.parse(s.getString("frame")), s.getBoolean("hidden", false), trigger, req,
                Reward.fromSection(s.getConfigurationSection("rewards"), warn));
    }

    public void write(ConfigurationSection s) {
        s.set("name", name);
        s.set("description", new ArrayList<>(description));
        s.set("icon", icon.getKey().getKey());
        s.set("frame", frame.key());
        s.set("hidden", hidden);
        trigger.write(s.createSection("trigger"));
        s.set("requires", new ArrayList<>(requires));
        reward.write(s.createSection("rewards"));
    }

    /** 原版成就鍵：custommilestone:分類/成就ID */
    public NamespacedKey advancementKey(String namespace) {
        return new NamespacedKey(namespace, categoryId + "/" + id);
    }

    public String id() { return id; }
    public String categoryId() { return categoryId; }
    public String name() { return name; }
    public List<String> description() { return description; }
    public Material icon() { return icon; }
    public Frame frame() { return frame; }
    public boolean hidden() { return hidden; }
    public Trigger trigger() { return trigger; }
    public List<String> requires() { return requires; }
    public Reward reward() { return reward; }
    public long target() { return trigger.amount(); }

    // ===== 編輯器用 =====
    public Milestone withName(String v) { return new Milestone(id, categoryId, v, description, icon, frame, hidden, trigger, requires, reward); }
    public Milestone withDescription(List<String> v) { return new Milestone(id, categoryId, name, v, icon, frame, hidden, trigger, requires, reward); }
    public Milestone withIcon(Material v) { return new Milestone(id, categoryId, name, description, v, frame, hidden, trigger, requires, reward); }
    public Milestone withFrame(Frame v) { return new Milestone(id, categoryId, name, description, icon, v, hidden, trigger, requires, reward); }
    public Milestone withHidden(boolean v) { return new Milestone(id, categoryId, name, description, icon, frame, v, trigger, requires, reward); }
    public Milestone withTrigger(Trigger v) { return new Milestone(id, categoryId, name, description, icon, frame, hidden, v, requires, reward); }
    public Milestone withRequires(List<String> v) { return new Milestone(id, categoryId, name, description, icon, frame, hidden, trigger, v, reward); }
    public Milestone withReward(Reward v) { return new Milestone(id, categoryId, name, description, icon, frame, hidden, trigger, requires, v); }
}
