package clre20.customMilestone.milestone;

import org.bukkit.Material;

import java.util.Locale;

/**
 * 觸發類型。
 * ADD：事件發生時累加；SET：取目前數值（例如等級、遊玩時間），只會往上升。
 */
public enum TriggerType {

    BREAK_BLOCK(TargetKind.MATERIAL, Mode.ADD, false, Material.IRON_PICKAXE),
    PLACE_BLOCK(TargetKind.MATERIAL, Mode.ADD, false, Material.BRICKS),
    KILL_ENTITY(TargetKind.ENTITY, Mode.ADD, false, Material.IRON_SWORD),
    KILL_PLAYER(TargetKind.NONE, Mode.ADD, false, Material.NETHERITE_SWORD),
    DEATH(TargetKind.DAMAGE_CAUSE, Mode.ADD, false, Material.SKELETON_SKULL),
    CRAFT(TargetKind.MATERIAL, Mode.ADD, false, Material.CRAFTING_TABLE),
    SMELT(TargetKind.MATERIAL, Mode.ADD, false, Material.FURNACE),
    ENCHANT(TargetKind.MATERIAL, Mode.ADD, false, Material.ENCHANTING_TABLE),
    BREW(TargetKind.MATERIAL, Mode.ADD, false, Material.BREWING_STAND),
    ANVIL(TargetKind.MATERIAL, Mode.ADD, false, Material.ANVIL),
    FISH(TargetKind.MATERIAL, Mode.ADD, false, Material.FISHING_ROD),
    BREED(TargetKind.ENTITY, Mode.ADD, false, Material.WHEAT),
    TAME(TargetKind.ENTITY, Mode.ADD, false, Material.BONE),
    SHEAR(TargetKind.ENTITY, Mode.ADD, false, Material.SHEARS),
    CONSUME(TargetKind.MATERIAL, Mode.ADD, false, Material.COOKED_BEEF),
    TRADE(TargetKind.MATERIAL, Mode.ADD, false, Material.EMERALD),
    ACQUIRE_ITEM(TargetKind.MATERIAL, Mode.ADD, false, Material.CHEST),
    LEVEL(TargetKind.NONE, Mode.SET, true, Material.EXPERIENCE_BOTTLE),
    PLAYTIME(TargetKind.NONE, Mode.SET, true, Material.CLOCK),
    LOGIN_DAYS(TargetKind.NONE, Mode.SET, true, Material.OAK_DOOR),
    LOGIN_STREAK(TargetKind.NONE, Mode.SET, true, Material.CAMPFIRE),
    ENTER_WORLD(TargetKind.WORLD, Mode.SET, true, Material.END_PORTAL_FRAME),
    ENTER_BIOME(TargetKind.BIOME, Mode.SET, true, Material.GRASS_BLOCK),
    REACH_REGION(TargetKind.NONE, Mode.SET, true, Material.COMPASS),
    STATISTIC(TargetKind.NONE, Mode.SET, true, Material.WRITABLE_BOOK),
    ADVANCEMENT(TargetKind.ADVANCEMENT, Mode.SET, true, Material.KNOWLEDGE_BOOK),
    MILESTONE_COUNT(TargetKind.NONE, Mode.SET, true, Material.NETHER_STAR),
    MANUAL(TargetKind.NONE, Mode.ADD, false, Material.COMMAND_BLOCK);

    public enum TargetKind { NONE, MATERIAL, ENTITY, DAMAGE_CAUSE, WORLD, BIOME, ADVANCEMENT }

    public enum Mode { ADD, SET }

    private final TargetKind targetKind;
    private final Mode mode;
    private final boolean periodic;
    private final Material icon;

    TriggerType(TargetKind targetKind, Mode mode, boolean periodic, Material icon) {
        this.targetKind = targetKind;
        this.mode = mode;
        this.periodic = periodic;
        this.icon = icon;
    }

    public TargetKind targetKind() { return targetKind; }
    public Mode mode() { return mode; }
    /** 由定時檢查負責更新的類型。 */
    public boolean periodic() { return periodic; }
    public Material icon() { return icon; }

    /** messages.yml 用的鍵名，例如 break-block。 */
    public String messageKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static TriggerType parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
