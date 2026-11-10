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
 * 編輯獎勵：物品（手上的物品完整保留 NBT／元件）、後台指令、經驗值。左鍵點已有的項目即移除。
 */
public final class RewardEditorMenu extends EditorMenu {

    /** 單一獎勵物品序列化後的大小上限（避免塞滿東西的界伏盒讓資料檔暴增）。 */
    private static final int MAX_ITEM_BYTES = 64 * 1024;

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
    protected void draw() {
        Milestone m = plugin.registry().get(id);
        if (m == null) {
            navigation(0, 1, () -> new EditorHomeMenu(plugin, viewer, 0).open(), p -> { });
            return;
        }
        Reward r = m.reward();
        // 物品：0~26（順序：先 YAML 元件字串、再編輯器加入的物品）
        int slot = 0;
        for (int i = 0; i < r.items().size() && slot < 27; i++, slot++) {
            ItemStack st = Reward.parseItem(r.items().get(i));
            if (st != null) st.setAmount(Math.min(st.getMaxStackSize(), Reward.amountOf(r.items().get(i))));
            int index = slot;
            set(slot, st == null
                    ? Items.of(Material.BARRIER, plugin.messages().raw("gui.editor.reward-broken"), List.of(r.items().get(i)))
                    : Items.preview(st, plugin.messages().list("gui.editor.reward-item-lore")),
                    act(p -> removeItem(p, index)));
        }
        for (int i = 0; i < r.storedItems().size() && slot < 27; i++, slot++) {
            ItemStack st = Reward.decode(r.storedItems().get(i));
            int index = slot;
            set(slot, st == null
                    ? Items.of(Material.BARRIER, plugin.messages().raw("gui.editor.reward-broken"), List.of())
                    : Items.preview(st, plugin.messages().list("gui.editor.reward-item-lore")),
                    act(p -> removeItem(p, index)));
        }
        // 指令：27~35
        fillBorder(36, 44);
        for (int i = 0; i < Math.min(9, r.commands().size()); i++) {
            int index = i;
            set(27 + i, Items.of(Material.PAPER, plugin.messages().raw("gui.editor.reward-command-name", "index", String.valueOf(i + 1)),
                    plugin.messages().list("gui.editor.reward-command-lore", "command", r.commands().get(i))),
                    act(p -> removeCommand(p, index)));
        }
        set(38, button(Material.HOPPER, "gui.editor.reward-add-hand", "gui.editor.reward-add-hand-lore"), act(this::addHand));
        set(40, button(Material.COMMAND_BLOCK, "gui.editor.reward-add-command", "gui.editor.reward-add-command-lore",
                "max", String.valueOf(Math.min(9, Reward.MAX_COMMANDS))), act(this::addCommand));
        set(42, button(Material.EXPERIENCE_BOTTLE, "gui.editor.reward-exp", "gui.editor.current",
                "value", String.valueOf(r.exp())), act(this::setExp));
        navigation(0, 1, () -> new MilestoneEditorMenu(plugin, viewer, id).open(), p -> { });
    }

    private void save(Player p, Reward r) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        plugin.editor().saveMilestone(m.withReward(r));
        plugin.messages().send(p, "editor.saved");
    }

    private void removeItem(Player p, int index) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        save(p, m.reward().withoutItem(index));
        refresh();
    }

    private void removeCommand(Player p, int index) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        save(p, m.reward().withoutCommand(index));
        refresh();
    }

    private void addHand(Player p) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            plugin.messages().send(p, "editor.error.empty-hand");
            return;
        }
        if (m.reward().itemCount() >= Reward.MAX_ITEMS) {
            plugin.messages().send(p, "editor.error.too-many");
            return;
        }
        if (hand.serializeAsBytes().length > MAX_ITEM_BYTES) {
            plugin.messages().send(p, "editor.error.item-too-large");
            return;
        }
        save(p, m.reward().withStoredItem(hand.clone()));
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
