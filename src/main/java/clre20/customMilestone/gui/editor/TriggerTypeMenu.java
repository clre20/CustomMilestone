package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.gui.Items;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.Trigger;
import clre20.customMilestone.milestone.TriggerType;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 選擇觸發類型。換成不同類型時，目標清單（如果目標種類不同）與進階條件會清空。
 */
public final class TriggerTypeMenu extends EditorMenu {

    private final String id;

    public TriggerTypeMenu(CustomMilestone plugin, Player viewer, String id) {
        super(plugin, viewer);
        this.id = id;
    }

    @Override
    protected String title() {
        return plugin.messages().raw("gui.editor.trigger-title");
    }

    @Override
    protected void draw() {
        Milestone m = plugin.registry().get(id);
        TriggerType[] types = TriggerType.values();
        for (int i = 0; i < Math.min(45, types.length); i++) {
            TriggerType type = types[i];
            String key = "trigger." + type.messageKey();
            boolean current = m != null && m.trigger().type() == type;
            List<String> lore = new java.util.ArrayList<>(plugin.messages().list(key + ".description"));
            if (current) lore.add(plugin.messages().raw("gui.editor.current-type"));
            set(i, Items.decorate(new org.bukkit.inventory.ItemStack(type.icon()),
                    plugin.messages().raw("gui.editor.trigger-name", "name", plugin.messages().raw(key + ".name")),
                    lore, current), act(p -> choose(p, type)));
        }
        navigation(0, 1, () -> new MilestoneEditorMenu(plugin, viewer, id).open(), p -> { });
    }

    private void choose(Player p, TriggerType type) {
        Milestone m = plugin.registry().get(id);
        if (m == null) return;
        Trigger old = m.trigger();
        if (old.type() != type) {
            List<String> targets = old.type().targetKind() == type.targetKind() ? old.rawTargets() : List.of();
            Trigger next = new Trigger(type, targets, old.amount(), null, s -> { });
            plugin.editor().saveMilestone(m.withTrigger(next));
            plugin.messages().send(p, "editor.saved");
        }
        new MilestoneEditorMenu(plugin, p, id).open();
    }
}
