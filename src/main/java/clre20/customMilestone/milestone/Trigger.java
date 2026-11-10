package clre20.customMilestone.milestone;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 成就的觸發條件。建立時就把字串解析成 EnumSet / HashSet，事件中只做 O(1) 查詢。
 */
public final class Trigger {

    public enum Source { PICKUP, CONTAINER, CRAFT, SMELT, FISH, TRADE }

    /** 可在 YAML 的 trigger.options 設定的鍵（編輯器沒有提供的進階條件）。 */
    public static final List<String> OPTION_KEYS = List.of(
            "natural-only", "exclude-spawner", "sources", "item", "merchants", "professions", "min-level",
            "costs", "enchantments", "potion-types", "world", "min", "max", "statistic", "sub", "category");

    private final TriggerType type;
    private final List<String> rawTargets;
    private final long amount;
    private final Map<String, Object> rawOptions;

    // 解析後
    private final Set<Material> materials = EnumSet.noneOf(Material.class);
    private final Set<EntityType> entities = EnumSet.noneOf(EntityType.class);
    private final Set<String> keys = new HashSet<>();
    private boolean naturalOnly;
    private boolean excludeSpawner = true;
    private Set<Source> sources = EnumSet.allOf(Source.class);
    private ItemMatcher item;
    private Set<String> merchants = Set.of();
    private Set<String> professions = Set.of();
    private int minLevel;
    private Set<Material> costs = EnumSet.noneOf(Material.class);
    private Map<String, Integer> enchantments = Map.of();
    private Set<String> potionTypes = Set.of();
    private String world;
    private int[] min;
    private int[] max;
    private Statistic statistic;
    private Material statMaterial;
    private EntityType statEntity;
    private String category;

    public Trigger(TriggerType type, List<String> targets, long amount, Map<String, Object> options,
                   Consumer<String> warn) {
        this.type = type;
        this.rawTargets = List.copyOf(targets);
        this.amount = Math.max(1, amount);
        this.rawOptions = options == null ? new LinkedHashMap<>() : new LinkedHashMap<>(options);
        resolveTargets(warn);
        resolveOptions(warn);
    }

    public static Trigger fromSection(ConfigurationSection s, Consumer<String> warn) {
        TriggerType type = TriggerType.parse(s.getString("type"));
        if (type == null) {
            warn.accept("未知的觸發類型：" + s.getString("type"));
            return null;
        }
        List<String> targets = s.isList("targets") ? s.getStringList("targets")
                : (s.isString("targets") ? List.of(s.getString("targets", "")) : List.of());
        long amount = s.getLong("amount", 1);
        Map<String, Object> options = new LinkedHashMap<>();
        ConfigurationSection os = s.getConfigurationSection("options");
        if (os != null) {
            for (String k : os.getKeys(false)) {
                options.put(k, os.get(k));
            }
        }
        return new Trigger(type, targets, amount, options, warn);
    }

    public void write(ConfigurationSection s) {
        s.set("type", type.name());
        s.set("targets", new ArrayList<>(rawTargets));
        s.set("amount", amount);
        if (!rawOptions.isEmpty()) {
            ConfigurationSection os = s.createSection("options");
            for (Map.Entry<String, Object> e : rawOptions.entrySet()) {
                os.set(e.getKey(), e.getValue());
            }
        }
    }

    private void resolveTargets(Consumer<String> warn) {
        for (String raw : rawTargets) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String t = raw.trim().toLowerCase(Locale.ROOT);
            switch (type.targetKind()) {
                case MATERIAL -> {
                    if (t.startsWith("#")) {
                        NamespacedKey key = NamespacedKey.fromString(t.substring(1));
                        boolean found = false;
                        if (key != null) {
                            Tag<Material> blocks = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
                            Tag<Material> items = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
                            if (blocks != null) { materials.addAll(blocks.getValues()); found = true; }
                            if (items != null) { materials.addAll(items.getValues()); found = true; }
                        }
                        if (!found) warn.accept("找不到物品／方塊標籤：" + raw);
                    } else {
                        Material m = Material.matchMaterial(t);
                        if (m == null) warn.accept("未知的物品／方塊：" + raw);
                        else materials.add(m);
                    }
                }
                case ENTITY -> {
                    if (t.startsWith("#")) {
                        NamespacedKey key = NamespacedKey.fromString(t.substring(1));
                        Tag<EntityType> tag = key == null ? null
                                : Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key, EntityType.class);
                        if (tag == null) warn.accept("找不到生物標籤：" + raw);
                        else entities.addAll(tag.getValues());
                    } else {
                        EntityType et = parseEntity(t);
                        if (et == null) warn.accept("未知的生物：" + raw);
                        else entities.add(et);
                    }
                }
                case DAMAGE_CAUSE -> {
                    try {
                        keys.add(EntityDamageEvent.DamageCause.valueOf(t.toUpperCase(Locale.ROOT)).name().toLowerCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        warn.accept("未知的死亡原因：" + raw);
                    }
                }
                case WORLD -> keys.add(t);
                case BIOME, ADVANCEMENT -> keys.add(t.contains(":") ? t : "minecraft:" + t);
                case NONE -> { }
            }
        }
    }

    private void resolveOptions(Consumer<String> warn) {
        naturalOnly = bool("natural-only", false);
        excludeSpawner = bool("exclude-spawner", true);
        List<String> src = strings("sources");
        if (!src.isEmpty()) {
            Set<Source> set = EnumSet.noneOf(Source.class);
            for (String s : src) {
                try {
                    set.add(Source.valueOf(s.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    warn.accept("未知的取得來源：" + s);
                }
            }
            if (!set.isEmpty()) sources = set;
        }
        Object itemRaw = rawOptions.get("item");
        if (itemRaw != null) {
            item = ItemMatcher.parse(String.valueOf(itemRaw));
            if (item == null) warn.accept("無法解析物品條件：" + itemRaw);
        }
        merchants = lowerSet("merchants");
        professions = lowerSet("professions");
        for (String k : professions) {
            if (!registryHas(io.papermc.paper.registry.RegistryKey.VILLAGER_PROFESSION, "minecraft:" + k)) {
                warn.accept("未知的村民職業：" + k);
            }
        }
        for (String k : merchants) {
            if (!k.equals("villager") && !k.equals("wandering_trader")) warn.accept("交易對象只能是 villager 或 wandering_trader：" + k);
        }
        minLevel = intOpt("min-level", 0);
        if (minLevel < 0 || minLevel > 5) warn.accept("村民等級只能是 1~5：" + minLevel);
        for (String c : strings("costs")) {
            Material m = Material.matchMaterial(c.trim());
            if (m == null) warn.accept("未知的交易付出物品：" + c);
            else costs.add(m);
        }
        Map<String, Integer> ench = new java.util.HashMap<>();
        for (String e : strings("enchantments")) {
            // 格式：sharpness、sharpness:5、minecraft:sharpness、minecraft:sharpness:5
            String s = e.trim().toLowerCase(Locale.ROOT);
            int idx = s.lastIndexOf(':');
            String name = s;
            String lvl = "1";
            if (idx > 0 && idx < s.length() - 1 && s.substring(idx + 1).chars().allMatch(Character::isDigit)) {
                name = s.substring(0, idx);
                lvl = s.substring(idx + 1);
            }
            if (!name.contains(":")) name = "minecraft:" + name;
            try {
                ench.put(name, Math.max(1, Integer.parseInt(lvl)));
            } catch (NumberFormatException ex) {
                warn.accept("附魔條件格式錯誤：" + e);
            }
        }
        for (String k : ench.keySet()) {
            if (!registryHas(io.papermc.paper.registry.RegistryKey.ENCHANTMENT, k)) warn.accept("未知的附魔：" + k);
        }
        enchantments = Collections.unmodifiableMap(ench);
        Set<String> potions = new HashSet<>();
        for (String p : strings("potion-types")) {
            String k = p.trim().toLowerCase(Locale.ROOT);
            potions.add(k.contains(":") ? k : "minecraft:" + k);
        }
        for (String k : potions) {
            if (!registryHas(io.papermc.paper.registry.RegistryKey.POTION, k)) warn.accept("未知的藥水種類：" + k);
        }
        potionTypes = Set.copyOf(potions);
        Object w = rawOptions.get("world");
        world = w == null ? null : String.valueOf(w).toLowerCase(Locale.ROOT);
        min = coords("min", warn);
        max = coords("max", warn);
        if (min != null && max != null) {
            for (int i = 0; i < 3; i++) {
                int lo = Math.min(min[i], max[i]);
                int hi = Math.max(min[i], max[i]);
                min[i] = lo;
                max[i] = hi;
            }
        }
        Object st = rawOptions.get("statistic");
        if (st != null) {
            try {
                statistic = Statistic.valueOf(String.valueOf(st).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warn.accept("未知的原版統計：" + st);
            }
        }
        Object sub = rawOptions.get("sub");
        if (statistic != null && statistic.getType() != Statistic.Type.UNTYPED) {
            if (sub == null) {
                warn.accept("統計 " + statistic + " 需要 sub（方塊、物品或生物）");
                statistic = null;
            } else if (statistic.getType() == Statistic.Type.ENTITY) {
                statEntity = parseEntity(String.valueOf(sub).trim().toLowerCase(Locale.ROOT));
                if (statEntity == null) { warn.accept("未知的生物：" + sub); statistic = null; }
            } else {
                statMaterial = Material.matchMaterial(String.valueOf(sub).trim());
                if (statMaterial == null) { warn.accept("未知的物品／方塊：" + sub); statistic = null; }
            }
        }
        if (type == TriggerType.STATISTIC && statistic == null) {
            warn.accept("STATISTIC 觸發缺少有效的 options.statistic");
        }
        if (type == TriggerType.REACH_REGION && (world == null || min == null || max == null)) {
            warn.accept("REACH_REGION 觸發需要 options.world、options.min、options.max");
        }
        Object cat = rawOptions.get("category");
        category = cat == null ? null : String.valueOf(cat).toLowerCase(Locale.ROOT);
    }

    private static <T extends org.bukkit.Keyed> boolean registryHas(io.papermc.paper.registry.RegistryKey<T> reg, String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        if (k == null) return false;
        try {
            return io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(reg).get(k) != null;
        } catch (RuntimeException e) {
            return true; // 無法查詢時不阻擋
        }
    }

    private static EntityType parseEntity(String t) {
        NamespacedKey key = NamespacedKey.fromString(t);
        if (key != null) {
            EntityType et = org.bukkit.Registry.ENTITY_TYPE.get(key);
            if (et != null) return et;
        }
        try {
            return EntityType.valueOf(t.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean bool(String key, boolean def) {
        Object o = rawOptions.get(key);
        if (o instanceof Boolean b) return b;
        if (o != null) return Boolean.parseBoolean(String.valueOf(o));
        return def;
    }

    private int intOpt(String key, int def) {
        Object o = rawOptions.get(key);
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private List<String> strings(String key) {
        Object o = rawOptions.get(key);
        if (o instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object x : l) if (x != null) out.add(String.valueOf(x));
            return out;
        }
        if (o != null) {
            List<String> out = new ArrayList<>();
            for (String part : String.valueOf(o).split(",")) if (!part.isBlank()) out.add(part.trim());
            return out;
        }
        return List.of();
    }

    private Set<String> lowerSet(String key) {
        Set<String> out = new HashSet<>();
        for (String s : strings(key)) {
            String k = s.trim().toLowerCase(Locale.ROOT);
            int idx = k.indexOf(':');
            out.add(idx >= 0 ? k.substring(idx + 1) : k);
        }
        return Set.copyOf(out);
    }

    private int[] coords(String key, Consumer<String> warn) {
        Object o = rawOptions.get(key);
        if (o == null) return null;
        String[] parts = String.valueOf(o).split("[,\\s]+");
        if (parts.length != 3) {
            warn.accept(key + " 座標格式應為 x,y,z：" + o);
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())};
        } catch (NumberFormatException e) {
            warn.accept(key + " 座標格式錯誤：" + o);
            return null;
        }
    }

    // ===== 查詢 =====

    public TriggerType type() { return type; }
    public List<String> rawTargets() { return rawTargets; }
    public long amount() { return amount; }
    public Map<String, Object> rawOptions() { return Collections.unmodifiableMap(rawOptions); }

    public boolean matchesMaterial(Material m) {
        return rawTargets.isEmpty() || materials.contains(m);
    }

    public boolean matchesEntity(EntityType e) {
        return rawTargets.isEmpty() || entities.contains(e);
    }

    public boolean matchesKey(String key) {
        return rawTargets.isEmpty() || keys.contains(key.toLowerCase(Locale.ROOT));
    }

    public Set<Material> materials() { return materials; }
    public Set<String> keys() { return keys; }
    public boolean naturalOnly() { return naturalOnly; }
    public boolean excludeSpawner() { return excludeSpawner; }
    public Set<Source> sources() { return sources; }
    public ItemMatcher item() { return item; }
    public Set<String> merchants() { return merchants; }
    public Set<String> professions() { return professions; }
    public int minLevel() { return minLevel; }
    public Set<Material> costs() { return costs; }
    public Map<String, Integer> enchantments() { return enchantments; }
    public Set<String> potionTypes() { return potionTypes; }
    public String world() { return world; }
    public int[] min() { return min; }
    public int[] max() { return max; }
    public Statistic statistic() { return statistic; }
    public Material statMaterial() { return statMaterial; }
    public EntityType statEntity() { return statEntity; }
    public String category() { return category; }
}
