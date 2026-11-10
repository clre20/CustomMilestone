package clre20.customMilestone.milestone;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成就分類（對應 milestones/ 資料夾裡的一個檔案，以及原版成就畫面的一個分頁）。
 */
public final class Category {

    public static final String DEFAULT_BACKGROUND = "minecraft:gui/advancements/backgrounds/stone";

    private final String id;
    private String name;
    private List<String> description;
    private Material icon;
    private String background;
    private int order;
    private final Map<String, Milestone> milestones = new LinkedHashMap<>();

    public Category(String id, String name, List<String> description, Material icon, String background, int order) {
        this.id = id;
        this.name = name;
        this.description = List.copyOf(description);
        this.icon = icon == null || !icon.isItem() || icon.isAir() ? Material.BOOK : icon;
        this.background = background == null || background.isBlank() ? DEFAULT_BACKGROUND : background;
        this.order = order;
    }

    public static Category fromSection(String id, ConfigurationSection s) {
        if (s == null) {
            return new Category(id, id, List.of(), Material.BOOK, DEFAULT_BACKGROUND, 0);
        }
        List<String> desc = s.isList("description") ? s.getStringList("description")
                : (s.isString("description") ? List.of(s.getString("description", "")) : List.of());
        return new Category(id, s.getString("name", id), desc,
                Material.matchMaterial(s.getString("icon", "book")),
                s.getString("background", DEFAULT_BACKGROUND), s.getInt("order", 0));
    }

    public void write(ConfigurationSection s) {
        s.set("name", name);
        s.set("description", new ArrayList<>(description));
        s.set("icon", icon.getKey().getKey());
        s.set("background", background);
        s.set("order", order);
    }

    public NamespacedKey rootKey(String namespace) {
        return new NamespacedKey(namespace, id + "/root");
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<String> description() { return description; }
    public Material icon() { return icon; }
    public String background() { return background; }
    public int order() { return order; }
    public Collection<Milestone> milestones() { return Collections.unmodifiableCollection(milestones.values()); }
    public Milestone milestone(String id) { return milestones.get(id); }

    void put(Milestone m) { milestones.put(m.id(), m); }
    void remove(String id) { milestones.remove(id); }

    public void setName(String name) { this.name = name; }
    public void setDescription(List<String> description) { this.description = List.copyOf(description); }
    public void setIcon(Material icon) { if (icon != null && icon.isItem() && !icon.isAir()) this.icon = icon; }
    public void setOrder(int order) { this.order = order; }
}
