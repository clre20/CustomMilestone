package clre20.customMilestone.milestone;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 每種觸發類型可在編輯器設定的進階條件（trigger.options）。
 */
public final class TriggerOptions {

    public enum Kind {
        /** 開關（true/false）。 */
        TOGGLE,
        /** 逗號分隔清單（聊天室輸入）。 */
        LIST,
        /** 單一文字（聊天室輸入）。 */
        TEXT,
        /** 物品細節：點下方背包的物品設定。 */
        ITEM,
        /** 交易對象循環：不限 → 村民 → 流浪商人。 */
        MERCHANT,
        /** 村民等級循環：不限 → 1 ~ 5。 */
        LEVEL,
        /** 座標：設為自己目前的位置。 */
        POSITION,
        /** 世界：設為自己目前所在的世界，或聊天室輸入。 */
        WORLD
    }

    public record Spec(String key, Kind kind, Material icon, Object def) { }

    private static final Map<TriggerType, List<Spec>> SPECS = new EnumMap<>(TriggerType.class);

    static {
        SPECS.put(TriggerType.BREAK_BLOCK, List.of(new Spec("natural-only", Kind.TOGGLE, Material.GRASS_BLOCK, false)));
        SPECS.put(TriggerType.KILL_ENTITY, List.of(new Spec("exclude-spawner", Kind.TOGGLE, Material.SPAWNER, true)));
        SPECS.put(TriggerType.ENCHANT, List.of(new Spec("enchantments", Kind.LIST, Material.ENCHANTED_BOOK, null)));
        SPECS.put(TriggerType.BREW, List.of(new Spec("potion-types", Kind.LIST, Material.POTION, null)));
        SPECS.put(TriggerType.FISH, List.of(new Spec("item", Kind.ITEM, Material.ITEM_FRAME, null)));
        SPECS.put(TriggerType.CONSUME, List.of(new Spec("item", Kind.ITEM, Material.ITEM_FRAME, null)));
        SPECS.put(TriggerType.TRADE, List.of(
                new Spec("item", Kind.ITEM, Material.ITEM_FRAME, null),
                new Spec("merchants", Kind.MERCHANT, Material.VILLAGER_SPAWN_EGG, null),
                new Spec("professions", Kind.LIST, Material.LECTERN, null),
                new Spec("min-level", Kind.LEVEL, Material.EMERALD_BLOCK, null),
                new Spec("costs", Kind.LIST, Material.EMERALD, null)));
        SPECS.put(TriggerType.ACQUIRE_ITEM, List.of(
                new Spec("sources", Kind.LIST, Material.HOPPER, null),
                new Spec("item", Kind.ITEM, Material.ITEM_FRAME, null)));
        SPECS.put(TriggerType.REACH_REGION, List.of(
                new Spec("world", Kind.WORLD, Material.GRASS_BLOCK, null),
                new Spec("min", Kind.POSITION, Material.RED_BANNER, null),
                new Spec("max", Kind.POSITION, Material.BLUE_BANNER, null)));
        SPECS.put(TriggerType.STATISTIC, List.of(
                new Spec("statistic", Kind.TEXT, Material.WRITABLE_BOOK, null),
                new Spec("sub", Kind.TEXT, Material.NAME_TAG, null)));
        SPECS.put(TriggerType.MILESTONE_COUNT, List.of(new Spec("category", Kind.TEXT, Material.BOOKSHELF, null)));
    }

    private TriggerOptions() {
    }

    public static List<Spec> of(TriggerType type) {
        return SPECS.getOrDefault(type, List.of());
    }
}
