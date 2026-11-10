package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.gui.Items;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.Reward;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 編輯獎勵：
 * <ul>
 *   <li>左鍵點下方背包的任何物品即加入（完整保留 NBT／元件，數量取自那一疊），相同物品自動合併</li>
 *   <li>左鍵點上方的獎勵物品 → 設定數量或移除</li>
 *   <li>後台指令、經驗值</li>
 * </ul>
 */
public final class RewardEditorMenu extends EditorMenu {

    /** 單一獎勵物品序列化後的大小上限（避免塞滿東西的界伏盒讓資料檔暴增）。 */
    static final int MAX_ITEM_BYTES = 64 * 1024;

    private final String id;

    public RewardEditorMenu(CustomMilestone plugin, Player viewer, String id) {
        super(plugin, viewer);
        this.id = id;
    }

    @Override
    protected String title() {
        return plugin.messages().raw("gui.editor.reward-title", "id", id);
    }

    @Override
    protected boolean acceptsBottomClicks() {
        return true;
    }

    @Override
    protected void draw() {
        Milestone m = plugin.registry().get(id);
        if (m == null) {
            navigation(0, 1, () -> new EditorHomeMenu(plugin, viewer, 0).open(), p -> { });
            return;
        }
        Reward r = m.reward();
        // 物品：0~26
        List<Reward.Entry> entries = r.entries();
        for (int i = 0; i < Math.min(27, entries.size()); i++) {
            Reward.Entry e = entries.get(i);
            int index = i;
            ItemStack shown;
            if (e.base() == null) {
                shown = Items.of(Material.BARRIER, plugin.messages().raw("gui.editor.reward-broken"), List.of());
            } else {
                shown = e.base().clone();
                shown.setAmount(Math.min(e.amount(), shown.getMaxStackSize()));
                shown = Items.preview(shown, plugin.messages().list("gui.editor.reward-item-lore",
                        "amount", String.valueOf(e.amount())));
            }
            set(i, shown, act(p -> new RewardItemMenu(plugin, p, id, index).open()));
        }
        // 指令：27~35
        fillBorder(36, 44);
        for (int i = 0; i < Math.min(9, r.commands().size()); i++) {
            int index = i;
            set(27 + i, Items.of(Material.PAPER, plugin.messages().raw("gui.editor.reward-command-name", "index", String.valueOf(i + 1)),
                    plugin.messages().list("gui.editor.reward-command-lore", "command", r.commands().get(i))),
                    act(p -> removeCommand(p, index)));
        }
        set(38, button(Material.HOPPER, "gui.editor.reward-add-inventory", "gui.editor.reward-add-inventory-lore",
                "count", String.valueOf(r.itemCount()), "max", String.valueOf(Reward.MAX_ITEMS)));
        set(40, button(Material.COMMAND_BLOCK, "gui.editor.reward-add-command", "gui.editor.reward-add-command-lore",
                "max", String.valueOf(Math.min(9, Reward.MAX_COMMANDS))), act(this::addCommand));
        set(42, button(Material.EXPERIENCE_BOTTLE, "gui.editor.reward-exp", "gui.editor.current",
                "value", String.valueOf(r.exp())), act(this::setExp));
        navigation(0, 1, () -> new MilestoneEditorMenu(plugin, viewer, id).open(), p -> { });
    }

    /** 左鍵點了下方背包的物品：加入獎勵（相同物品合併數量）。 */
    @Override
    protected void clickBottom(ItemStack item) {
        if (!guard(viewer)) return;
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        if (item.serializeAsBytes().length > MAX_ITEM_BYTES) {
            plugin.messages().send(viewer, "editor.error.item-too-large");
            return;
        }
        Reward next = m.reward().withStoredItem(item);
        if (next == null) {
            plugin.messages().send(viewer, "editor.error.too-many");
            return;
        }
        save(viewer, next);
        refresh();
    }

    private void save(Player p, Reward r) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        plugin.editor().saveMilestone(m.withReward(r));
        plugin.messages().send(p, "editor.saved");
    }

    private void removeCommand(Player p, int index) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        save(p, m.reward().withoutCommand(index));
        refresh();
    }

    private void addCommand(Player p) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        if (m.reward().commands().size() >= Math.min(9, Reward.MAX_COMMANDS)) {
            plugin.messages().send(p, "editor.error.too-many");
            return;
        }
        ask(p, "editor.prompt.command", 256, EditorValidators::command, text -> {
            Milestone cur = plugin.registry().get(id);
            if (cur == null) return;
            String cmd = text.startsWith("/") ? text.substring(1) : text;
            save(p, cur.reward().withCommand(cmd));
        }, () -> new RewardEditorMenu(plugin, p, id).open());
    }

    private void setExp(Player p) {
        ask(p, "editor.prompt.exp", 7, EditorValidators::exp, text -> {
            Milestone cur = plugin.registry().get(id);
            if (cur == null) return;
            save(p, cur.reward().withExp(Integer.parseInt(text.trim())));
        }, () -> new RewardEditorMenu(plugin, p, id).open());
    }
}
