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
 * 單一獎勵物品：調整數量（-64 -10 -1 +1 +10 +64、聊天室輸入）或移除。
 */
public final class RewardItemMenu extends EditorMenu {

    private static final int[] STEPS = {-64, -10, -1, 1, 10, 64};
    private static final int[] STEP_SLOTS = {10, 11, 12, 14, 15, 16};

    private final String id;
    private final int index;

    public RewardItemMenu(CustomMilestone plugin, Player viewer, String id, int index) {
        super(plugin, viewer);
        this.id = id;
        this.index = index;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String title() {
        return plugin.messages().raw("gui.editor.reward-item-title", "id", id);
    }

    private Reward.Entry entry() {
        Milestone m = plugin.registry().get(id);
        if (m == null || index < 0 || index >= m.reward().entries().size()) return null;
        return m.reward().entries().get(index);
    }

    @Override
    protected void draw() {
        fillBorder(0, 26);
        Reward.Entry e = entry();
        Runnable back = () -> new RewardEditorMenu(plugin, viewer, id).open();
        set(22, button(Material.OAK_DOOR, "gui.common.back", null), p -> back.run());
        if (e == null) return;

        ItemStack shown = e.base() == null ? new ItemStack(Material.BARRIER) : e.base().clone();
        shown.setAmount(Math.min(e.amount(), shown.getMaxStackSize()));
        set(13, Items.preview(shown, plugin.messages().list("gui.editor.reward-amount-lore",
                "amount", String.valueOf(e.amount()), "max", String.valueOf(Reward.MAX_AMOUNT))), act(this::askAmount));
        for (int i = 0; i < STEPS.length; i++) {
            int step = STEPS[i];
            Material mat = step < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
            set(STEP_SLOTS[i], Items.of(mat, plugin.messages().raw("gui.editor.reward-step",
                    "step", (step > 0 ? "+" : "") + step), List.of()), act(p -> change(p, e.amount() + step)));
        }
        set(18, button(Material.LAVA_BUCKET, "gui.editor.reward-remove", "gui.editor.reward-remove-lore"), act(this::remove));
    }

    private void change(Player p, int amount) {
        Milestone m = plugin.registry().get(id);
        if (m == null || entry() == null) return;
        plugin.editor().saveMilestone(m.withReward(m.reward().withItemAmount(index, Reward.clampAmount(amount))));
        refresh();
    }

    private void askAmount(Player p) {
        ask(p, "editor.prompt.reward-amount", 4, text -> {
            try {
                int v = Integer.parseInt(text.trim());
                return v >= 1 && v <= Reward.MAX_AMOUNT ? null : "editor.error.number";
            } catch (NumberFormatException ex) {
                return "editor.error.number";
            }
        }, text -> change(p, Integer.parseInt(text.trim())), () -> new RewardItemMenu(plugin, p, id, index).open(),
                "max", String.valueOf(Reward.MAX_AMOUNT));
    }

    private void remove(Player p) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        plugin.editor().saveMilestone(m.withReward(m.reward().withoutItem(index)));
        plugin.messages().send(p, "editor.deleted");
        new RewardEditorMenu(plugin, p, id).open();
    }
}
