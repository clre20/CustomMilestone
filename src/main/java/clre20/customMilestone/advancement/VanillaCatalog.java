package clre20.customMilestone.advancement;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版的 5 個成就分類（主線、地獄、終界、冒險、農牧），啟動時建立一次清單。
 */
public final class VanillaCatalog {

    public record VanillaCategory(String id, Material icon, List<NamespacedKey> advancements) { }

    public static final List<String> IDS = List.of("story", "nether", "end", "adventure", "husbandry");
    private static final Map<String, Material> ICONS = Map.of(
            "story", Material.GRASS_BLOCK, "nether", Material.RED_NETHER_BRICKS, "end", Material.END_STONE,
            "adventure", Material.MAP, "husbandry", Material.HAY_BLOCK);

    private final Map<String, VanillaCategory> categories = new LinkedHashMap<>();
    private final List<NamespacedKey> all = new ArrayList<>();
    private final java.util.Set<NamespacedKey> allSet = new java.util.HashSet<>();

    public void build() {
        categories.clear();
        all.clear();
        allSet.clear();
        Map<String, List<Advancement>> grouped = new LinkedHashMap<>();
        for (String id : IDS) grouped.put(id, new ArrayList<>());
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement a = it.next();
            NamespacedKey key = a.getKey();
            if (!key.getNamespace().equals(NamespacedKey.MINECRAFT) || a.getDisplay() == null) continue;
            String path = key.getKey();
            int slash = path.indexOf('/');
            if (slash < 0) continue;
            List<Advancement> list = grouped.get(path.substring(0, slash));
            if (list != null) list.add(a);
        }
        for (Map.Entry<String, List<Advancement>> e : grouped.entrySet()) {
            List<Advancement> list = e.getValue();
            list.sort(Comparator.comparingInt(VanillaCatalog::depth).thenComparing(a -> a.getKey().getKey()));
            List<NamespacedKey> keys = list.stream().map(Advancement::getKey).toList();
            categories.put(e.getKey(), new VanillaCategory(e.getKey(), ICONS.getOrDefault(e.getKey(), Material.BOOK), keys));
            all.addAll(keys);
            allSet.addAll(keys);
        }
    }

    private static int depth(Advancement a) {
        int d = 0;
        Advancement cur = a.getParent();
        while (cur != null && d < 64) {
            d++;
            cur = cur.getParent();
        }
        return d;
    }

    public List<VanillaCategory> categories() {
        return List.copyOf(categories.values());
    }

    public VanillaCategory category(String id) {
        return categories.get(id);
    }

    public int total() {
        return all.size();
    }

    public boolean isVanilla(NamespacedKey key) {
        return allSet.contains(key);
    }

    /** 計算玩家完成的原版成就數（上線時呼叫一次）。 */
    public int countDone(Player player) {
        int n = 0;
        for (NamespacedKey key : all) {
            Advancement a = Bukkit.getAdvancement(key);
            if (a != null && player.getAdvancementProgress(a).isDone()) n++;
        }
        return n;
    }
}
