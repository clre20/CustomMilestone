package clre20.customMilestone.progress;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.advancement.AdvancementBridge;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.Trigger;
import clre20.customMilestone.milestone.TriggerType;
import clre20.customMilestone.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * 進度核心：累加／設定進度、檢查前置成就、完成成就（原版成就或降級廣播）、發放獎勵。只在主執行緒執行。
 */
public final class ProgressService {

    /** 回傳此值代表這個成就不受這次事件影響。 */
    public static final long SKIP = -1;

    private final CustomMilestone plugin;
    private int cascadeDepth;

    public ProgressService(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    /** 這位玩家目前是否能累積進度（創造／旁觀模式依設定排除）。 */
    public boolean canProgress(Player p) {
        if (!plugin.config().ignoreCreative()) return true;
        GameMode gm = p.getGameMode();
        return gm != GameMode.CREATIVE && gm != GameMode.SPECTATOR;
    }

    public boolean isUnlocked(PlayerData d, Milestone m) {
        for (String r : m.requires()) {
            if (!d.isCompleted(r)) return false;
        }
        return true;
    }

    /**
     * 處理一個觸發事件。valueFn 對 ADD 類型回傳增加量（≤0 或 SKIP 表示不相符），
     * 對 SET 類型回傳目前數值（SKIP 表示不相符）。
     */
    public void handle(Player p, TriggerType type, ToLongFunction<Milestone> valueFn) {
        List<Milestone> list = plugin.registry().byType(type);
        if (list.isEmpty()) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        boolean dbg = !debug.isEmpty() && debug.contains(p.getUniqueId());
        // 定時檢查的類型每分鐘都會跑，除錯時只回報有變化的結果，避免洗版
        boolean verbose = dbg && !type.periodic();
        String typeName = dbg ? plugin.messages().raw("trigger." + type.messageKey() + ".name") : null;
        if (!canProgress(p)) {
            if (verbose) plugin.messages().send(p, "debug.gamemode", "type", typeName);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Milestone m = list.get(i);
            if (d.isCompleted(m.id())) continue;
            if (!isUnlocked(d, m)) {
                if (verbose) plugin.messages().send(p, "debug.locked", "type", typeName, "name", m.name(), "id", m.id());
                continue;
            }
            long v = valueFn.applyAsLong(m);
            if (v == SKIP || (type.mode() == TriggerType.Mode.ADD && v <= 0)) {
                if (verbose) plugin.messages().send(p, "debug.no-match", "type", typeName, "name", m.name(), "id", m.id());
                continue;
            }
            long before = d.progress(m.id());
            apply(p, d, m, v);
            if (dbg) {
                long after = d.isCompleted(m.id()) ? m.target() : d.progress(m.id());
                if (after != before || verbose) {
                    plugin.messages().send(p, "debug.progress", "type", typeName, "name", m.name(), "id", m.id(),
                            "before", String.valueOf(before), "after", String.valueOf(after),
                            "target", String.valueOf(m.target()));
                }
            }
        }
    }

    // ===== 除錯模式（/cm debug） =====

    private final java.util.Set<java.util.UUID> debug = new java.util.HashSet<>();

    /** 切換除錯模式，回傳切換後是否開啟。 */
    public boolean toggleDebug(java.util.UUID id) {
        if (debug.remove(id)) return false;
        debug.add(id);
        return true;
    }

    public void clearDebug(java.util.UUID id) {
        debug.remove(id);
    }

    /** 增加量版本的簡便寫法：符合條件就加 amount。 */
    public void add(Player p, TriggerType type, long amount, java.util.function.Predicate<Trigger> filter) {
        if (amount <= 0) return;
        handle(p, type, m -> filter.test(m.trigger()) ? amount : SKIP);
    }

    private void apply(Player p, PlayerData d, Milestone m, long v) {
        long target = m.target();
        long cur = d.progress(m.id());
        long next;
        if (m.trigger().type().mode() == TriggerType.Mode.SET) {
            next = Math.min(target, Math.max(cur, v));
        } else {
            if (v <= 0) return;
            next = Math.min(target, cur + v);
        }
        if (next == cur && next < target) return;
        if (next >= target) {
            complete(p, d, m);
        } else {
            d.setProgress(m.id(), next);
        }
    }

    /** 管理員直接設定進度（不檢查觸發條件；鎖定中的成就只記錄進度）。 */
    public void adminSetProgress(Player online, PlayerData d, Milestone m, long value) {
        if (d.isCompleted(m.id())) return;
        long v = Math.max(0, Math.min(m.target(), value));
        if (v >= m.target() && isUnlocked(d, m)) {
            complete(online, d, m);
        } else {
            d.setProgress(m.id(), v);
        }
    }

    /** 完成成就。online 為 null 代表離線玩家（管理員指令）。 */
    public void complete(Player online, PlayerData d, Milestone m) {
        if (d.isCompleted(m.id())) return;
        d.setCompleted(m.id(), System.currentTimeMillis());
        if (online != null) {
            if (plugin.advancements().isActive()) {
                plugin.advancements().award(online, m, false);
            } else {
                announceFallback(online, m);
            }
            plugin.rewards().give(online, d, m);
        } else {
            plugin.rewards().giveOffline(d, m);
            // 原版成就在玩家上線時靜默補發
        }
        cascade(online, d, m);
    }

    /** 完成後：重新檢查「完成數量」類與以它為前置的成就（限制遞迴深度）。 */
    private void cascade(Player online, PlayerData d, Milestone done) {
        if (online == null || cascadeDepth > 16) return;
        cascadeDepth++;
        try {
            if (plugin.registry().hasType(TriggerType.MILESTONE_COUNT)) {
                handle(online, TriggerType.MILESTONE_COUNT, m -> milestoneCount(d, m));
            }
            for (Milestone dep : plugin.registry().dependents(done.id())) {
                if (d.isCompleted(dep.id()) || !isUnlocked(d, dep)) continue;
                // 剛解鎖：周期型條件立刻檢查一次，已有足夠進度則直接完成
                if (dep.trigger().type().periodic()) {
                    long v = periodicValue(online, d, dep);
                    if (v != SKIP) apply(online, d, dep, v);
                } else if (d.progress(dep.id()) >= dep.target()) {
                    complete(online, d, dep);
                }
            }
        } finally {
            cascadeDepth--;
        }
    }

    public void revoke(Player online, PlayerData d, Milestone m) {
        d.removeCompleted(m.id());
        d.setProgress(m.id(), 0);
        if (online != null) plugin.advancements().revoke(online, m);
    }

    public void resetAll(Player online, PlayerData d) {
        if (online != null) {
            for (String id : d.completedMap().keySet()) {
                Milestone m = plugin.registry().get(id);
                if (m != null) plugin.advancements().revoke(online, m);
            }
        }
        d.wipeAll();
    }

    // ===== 周期型條件 =====

    /** 計算周期型觸發的目前數值；不相符時回傳 SKIP。 */
    public long periodicValue(Player p, PlayerData d, Milestone m) {
        Trigger t = m.trigger();
        return switch (t.type()) {
            case LEVEL -> p.getLevel();
            case PLAYTIME -> p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200L;
            case LOGIN_DAYS -> d.loginDays();
            case LOGIN_STREAK -> d.loginStreak();
            case ENTER_WORLD -> t.matchesKey(p.getWorld().getName()) ? m.target() : SKIP;
            case ENTER_BIOME -> {
                Location loc = p.getLocation();
                String biome = p.getWorld().getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).key().asString();
                yield t.matchesKey(biome) ? m.target() : SKIP;
            }
            case REACH_REGION -> inRegion(p, t) ? m.target() : SKIP;
            case STATISTIC -> statistic(p, t);
            case ADVANCEMENT -> advancementCount(p, t);
            case MILESTONE_COUNT -> milestoneCount(d, m);
            default -> SKIP;
        };
    }

    private static boolean inRegion(Player p, Trigger t) {
        if (t.world() == null || t.min() == null || t.max() == null) return false;
        if (!p.getWorld().getName().equalsIgnoreCase(t.world())) return false;
        Location l = p.getLocation();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        int[] a = t.min(), b = t.max();
        return x >= a[0] && x <= b[0] && y >= a[1] && y <= b[1] && z >= a[2] && z <= b[2];
    }

    private static long statistic(Player p, Trigger t) {
        Statistic s = t.statistic();
        if (s == null) return SKIP;
        try {
            return switch (s.getType()) {
                case UNTYPED -> p.getStatistic(s);
                case ENTITY -> t.statEntity() == null ? SKIP : p.getStatistic(s, t.statEntity());
                default -> t.statMaterial() == null ? SKIP : p.getStatistic(s, t.statMaterial());
            };
        } catch (IllegalArgumentException e) {
            return SKIP;
        }
    }

    private static long advancementCount(Player p, Trigger t) {
        if (t.keys().isEmpty()) return SKIP;
        int n = 0;
        for (String k : t.keys()) {
            NamespacedKey key = NamespacedKey.fromString(k);
            Advancement a = key == null ? null : Bukkit.getAdvancement(key);
            if (a != null && p.getAdvancementProgress(a).isDone()) n++;
        }
        return n;
    }

    private long milestoneCount(PlayerData d, Milestone self) {
        String cat = self.trigger().category();
        int n = 0;
        for (String id : d.completedMap().keySet()) {
            if (id.equals(self.id())) continue;
            Milestone m = plugin.registry().get(id);
            if (m == null) continue;
            if (cat == null || cat.equals(m.categoryId())) n++;
        }
        return n;
    }

    /** 對一位玩家檢查所有周期型成就（定時檢查、上線、解鎖時）。 */
    public void checkPeriodic(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null || !canProgress(p)) return;
        for (TriggerType type : TriggerType.values()) {
            if (!type.periodic() || !plugin.registry().hasType(type)) continue;
            handle(p, type, m -> periodicValue(p, d, m));
        }
    }

    /** 上線時：進度已達標但尚未完成的成就（例如管理員調低了目標）直接完成。 */
    public void checkReached(Player p, PlayerData d) {
        for (Milestone m : List.copyOf(plugin.registry().all())) {
            if (d.isCompleted(m.id()) || !isUnlocked(d, m)) continue;
            if (d.progress(m.id()) >= m.target()) complete(p, d, m);
        }
    }

    /** 上線時：讓原版成就狀態與資料庫一致（資料庫為準），補發時不廣播。 */
    public void syncAdvancements(Player p, PlayerData d) {
        AdvancementBridge bridge = plugin.advancements();
        if (!bridge.isActive()) return;
        for (Milestone m : plugin.registry().all()) {
            boolean done = d.isCompleted(m.id());
            boolean shown = bridge.isDone(p, m);
            if (done && !shown) bridge.award(p, m, true);
            else if (!done && shown) bridge.revoke(p, m);
        }
    }

    // ===== 降級模式 =====

    private void announceFallback(Player p, Milestone m) {
        String frame = m.frame().key();
        Component title = Text.color(plugin.messages().raw("announce.title-" + frame, "name", m.name()));
        Component hover = Text.color(m.name());
        if (!m.description().isEmpty()) {
            hover = hover.append(Component.newline()).append(Text.color(String.join("\n", m.description())));
        }
        Component titleWithHover = title.hoverEvent(HoverEvent.showText(hover));
        Component msg = Text.color(plugin.messages().raw("announce." + frame, "player", p.getName()))
                .replaceText(TextReplacementConfig.builder().matchLiteral("%title%").replacement(titleWithHover).build());

        Boolean announce = p.getWorld().getGameRuleValue(GameRule.ANNOUNCE_ADVANCEMENTS);
        if (announce == null || announce) {
            for (Player other : Bukkit.getOnlinePlayers()) other.sendMessage(msg);
            Bukkit.getConsoleSender().sendMessage(msg);
            plugin.discord().sendFallback(Text.plain(msg));
        } else {
            p.sendMessage(msg);
        }
        if (plugin.config().fallbackTitle()) {
            p.showTitle(Title.title(
                    Text.color(plugin.messages().raw("announce.screen-title." + frame)),
                    Text.color(m.name()),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        String sound = plugin.config().fallbackSound();
        if (!sound.isBlank()) {
            try {
                p.playSound(Sound.sound(Key.key(sound.trim()), Sound.Source.MASTER, 1f, 1f));
            } catch (Exception ignored) {
                // 音效 ID 格式錯誤時忽略
            }
        }
    }
}
