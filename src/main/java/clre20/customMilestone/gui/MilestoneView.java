package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.config.MessageManager;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.Reward;
import clre20.customMilestone.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 成就在箱子 UI 中的顯示（完成、進行中、未解鎖、隱藏）。
 */
public final class MilestoneView {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private MilestoneView() {
    }

    public static String formatTime(CustomMilestone plugin, long millis) {
        return DATE.format(Instant.ofEpochMilli(millis).atZone(plugin.config().zone()));
    }

    public static ItemStack build(CustomMilestone plugin, PlayerData d, Milestone m) {
        MessageManager msg = plugin.messages();
        boolean done = d.isCompleted(m.id());
        boolean unlocked = plugin.progress().isUnlocked(d, m);

        if (!done && m.hidden()) {
            return Items.of(Material.GRAY_STAINED_GLASS_PANE, msg.raw("gui.milestone.hidden-name"),
                    msg.list("gui.milestone.hidden-lore"));
        }

        List<Component> lore = new ArrayList<>();
        for (String line : m.description()) lore.add(Text.item(msg.raw("gui.milestone.description-line", "line", line)));
        lore.add(Component.empty());
        String name;
        Material icon = m.icon();
        boolean glow = false;
        if (done) {
            name = msg.raw("gui.milestone.name-done", "name", m.name());
            Long at = d.completedAt(m.id());
            String time = at == null ? "-" : formatTime(plugin, at);
            lore.addAll(Text.itemLines(msg.list("gui.milestone.lore-done", "time", time)));
            glow = true;
        } else if (!unlocked) {
            name = msg.raw("gui.milestone.name-locked", "name", m.name());
            icon = Material.GRAY_DYE;
            List<String> reqNames = new ArrayList<>();
            for (String r : m.requires()) {
                if (d.isCompleted(r)) continue;
                Milestone rm = plugin.registry().get(r);
                reqNames.add(rm == null ? r : (rm.hidden() ? msg.raw("gui.milestone.hidden-name") : rm.name()));
            }
            lore.addAll(Text.itemLines(msg.list("gui.milestone.lore-locked", "requires", String.join("&7、", reqNames))));
        } else {
            name = msg.raw("gui.milestone.name-progress", "name", m.name());
            long cur = d.progress(m.id());
            long target = m.target();
            int percent = (int) Math.min(100, cur * 100 / Math.max(1, target));
            lore.addAll(Text.itemLines(msg.list("gui.milestone.lore-progress",
                    "current", String.valueOf(cur), "target", String.valueOf(target),
                    "percent", String.valueOf(percent),
                    "bar", Text.progressBar(cur, target, 20, "&a", "&8"))));
        }
        lore.addAll(rewardLines(plugin, m.reward()));
        lore.add(Text.item(msg.raw("gui.milestone.frame-line", "frame", msg.raw("frame." + m.frame().key()))));
        return Items.withComponents(new ItemStack(icon), Text.item(name), lore, glow);
    }

    /** 獎勵摘要（物品名稱用翻譯鍵，玩家看到自己語言的名稱）。 */
    public static List<Component> rewardLines(CustomMilestone plugin, Reward r) {
        MessageManager msg = plugin.messages();
        List<Component> out = new ArrayList<>();
        if (r.isEmpty()) return out;
        out.add(Text.item(msg.raw("gui.milestone.reward-title")));
        if (r.exp() > 0) out.add(Text.item(msg.raw("gui.milestone.reward-exp", "exp", String.valueOf(r.exp()))));
        String template = msg.raw("gui.milestone.reward-item");
        for (ItemStack st : r.buildItems()) {
            out.add(withItemName(template.replace("%amount%", String.valueOf(st.getAmount())), itemName(st)));
        }
        if (!r.commands().isEmpty()) {
            out.add(Text.item(msg.raw("gui.milestone.reward-commands", "count", String.valueOf(r.commands().size()))));
        }
        return out;
    }

    public static Component itemName(ItemStack st) {
        ItemMeta meta = st.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) return meta.displayName();
        if (meta != null && meta.hasItemName()) return meta.itemName();
        return Component.translatable(st.translationKey());
    }

    /** 把模板中的 %item% 換成物品名稱 Component。 */
    public static Component withItemName(String template, Component name) {
        int idx = template.indexOf("%item%");
        if (idx < 0) return Text.item(template);
        Component before = Text.item(template.substring(0, idx));
        Component after = Text.item(template.substring(idx + "%item%".length()));
        return before.append(name.colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.WHITE)).append(after);
    }
}
