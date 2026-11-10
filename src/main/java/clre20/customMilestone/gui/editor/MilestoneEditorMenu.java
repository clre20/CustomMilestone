package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.gui.Items;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.Trigger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 編輯單一成就。
 */
public final class MilestoneEditorMenu extends EditorMenu {

    private final String id;

    public MilestoneEditorMenu(CustomMilestone plugin, Player viewer, String id) {
        super(plugin, viewer);
        this.id = id;
    }

    @Override
    protected String title() {
        return plugin.messages().raw("gui.editor.milestone-title", "id", id);
    }

    private Milestone m() {
        return plugin.registry().get(id);
    }

    @Override
    protected void draw() {
        Milestone m = m();
        if (m == null) {
            navigation(0, 1, () -> new EditorHomeMenu(plugin, viewer, 0).open(), p -> { });
            return;
        }
        fillBorder(0, 44);
        String none = plugin.messages().raw("gui.editor.none");
        Trigger t = m.trigger();

        set(4, Items.of(m.icon(), plugin.messages().raw("gui.editor.milestone-name", "name", m.name()),
                plugin.messages().list("gui.editor.milestone-summary", "id", m.id(), "category", m.categoryId())));

        set(10, button(Material.NAME_TAG, "gui.editor.m-name", "gui.editor.current", "value", m.name()), act(this::name));
        set(11, button(Material.BOOK, "gui.editor.m-description", "gui.editor.current",
                "value", m.description().isEmpty() ? none : String.join("&7 | &f", m.description())), act(this::description));
        set(12, button(m.icon(), "gui.editor.m-icon", "gui.editor.icon-lore"), act(this::icon));
        set(13, button(Material.ITEM_FRAME, "gui.editor.m-frame", "gui.editor.cycle-lore",
                "value", plugin.messages().raw("frame." + m.frame().key())), act(this::frame));
        set(14, button(m.hidden() ? Material.ENDER_EYE : Material.ENDER_PEARL, "gui.editor.m-hidden", "gui.editor.toggle-lore",
                "value", plugin.messages().raw(m.hidden() ? "gui.editor.value-true" : "gui.editor.value-false")), act(this::hidden));
        set(16, button(Material.CHEST, "gui.editor.m-rewards", "gui.editor.m-rewards-lore",
                "exp", String.valueOf(m.reward().exp()), "items", String.valueOf(m.reward().itemCount()),
                "commands", String.valueOf(m.reward().commands().size())),
                act(p -> new RewardEditorMenu(plugin, p, id).open()));

        set(19, button(t.type().icon(), "gui.editor.m-trigger", "gui.editor.m-trigger-lore",
                "value", plugin.messages().raw("trigger." + t.type().messageKey() + ".name")),
                act(p -> new TriggerTypeMenu(plugin, p, id).open()));
        set(20, button(Material.TARGET, "gui.editor.m-targets", "gui.editor.m-targets-lore",
                "value", t.rawTargets().isEmpty() ? plugin.messages().raw("gui.editor.any") : String.join(", ", t.rawTargets())),
                act(this::targets));
        set(21, button(Material.REPEATER, "gui.editor.m-amount", "gui.editor.current",
                "value", String.valueOf(t.amount())), act(this::amount));
        List<String> reqNames = new ArrayList<>();
        for (String r : m.requires()) reqNames.add(r);
        set(22, button(Material.LEAD, "gui.editor.m-requires", "gui.editor.m-requires-lore",
                "value", reqNames.isEmpty() ? none : String.join(", ", reqNames)), act(this::requires));
        List<String> optLines = new ArrayList<>();
        for (Map.Entry<String, Object> e : t.rawOptions().entrySet()) {
            optLines.add(plugin.messages().raw("gui.editor.option-line", "key", e.getKey(), "value", String.valueOf(e.getValue())));
        }
        if (optLines.isEmpty()) optLines.add(plugin.messages().raw("gui.editor.option-none"));
        optLines.addAll(plugin.messages().list("gui.editor.options-hint", "file", m.categoryId() + ".yml"));
        set(23, Items.of(Material.COMMAND_BLOCK, plugin.messages().raw("gui.editor.m-options"), optLines));

        set(34, button(Material.LAVA_BUCKET, "gui.editor.m-delete", "gui.editor.m-delete-lore"), act(this::delete));
        navigation(0, 1, () -> new EditorCategoryMenu(plugin, viewer, m.categoryId(), 0).open(), p -> { });
    }

    private Runnable reopen(Player p) {
        return () -> new MilestoneEditorMenu(plugin, p, id).open();
    }

    private void update(Player p, java.util.function.UnaryOperator<Milestone> change) {
        Milestone m = m();
        if (m == null) return;
        plugin.editor().saveMilestone(change.apply(m));
        plugin.messages().send(p, "editor.saved");
    }

    private void name(Player p) {
        ask(p, "editor.prompt.name", 64, null, text -> update(p, m -> m.withName(text)), reopen(p));
    }

    private void description(Player p) {
        ask(p, "editor.prompt.description", 256, EditorValidators::description,
                text -> update(p, m -> m.withDescription(EditorValidators.splitLines(text))), reopen(p));
    }

    private void icon(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            plugin.messages().send(p, "editor.error.empty-hand");
            return;
        }
        Material type = hand.getType();
        update(p, m -> m.withIcon(type));
        refresh();
    }

    private void frame(Player p) {
        update(p, m -> m.withFrame(m.frame().next()));
        refresh();
    }

    private void hidden(Player p) {
        update(p, m -> m.withHidden(!m.hidden()));
        refresh();
    }

    private void targets(Player p) {
        ask(p, "editor.prompt.targets", 512, null, text -> {
            Milestone m = m();
            if (m == null) return;
            List<String> targets = new ArrayList<>();
            boolean any = text.equalsIgnoreCase("any") || text.equals("任意");
            if (!any) {
                for (String s : text.split("[,，\\s]+")) {
                    if (!s.isBlank()) targets.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
            if (targets.size() > 200) {
                plugin.messages().send(p, "editor.error.too-many");
                return;
            }
            List<String> warnings = new ArrayList<>();
            Trigger t = m.trigger();
            Trigger next = new Trigger(t.type(), targets, t.amount(), t.rawOptions(), warnings::add);
            for (String w : warnings) plugin.messages().send(p, "editor.warning", "message", w);
            plugin.editor().saveMilestone(m.withTrigger(next));
            plugin.messages().send(p, "editor.saved");
        }, reopen(p));
    }

    private void amount(Player p) {
        ask(p, "editor.prompt.amount", 13, EditorValidators::amount, text -> {
            Milestone m = m();
            if (m == null) return;
            Trigger t = m.trigger();
            Trigger next = new Trigger(t.type(), t.rawTargets(), Long.parseLong(text.trim()), t.rawOptions(), s -> { });
            plugin.editor().saveMilestone(m.withTrigger(next));
            plugin.messages().send(p, "editor.saved");
        }, reopen(p));
    }

    private void requires(Player p) {
        ask(p, "editor.prompt.requires", 512, text -> {
            if (isNone(text)) return null;
            for (String s : text.split("[,，\\s]+")) {
                if (s.isBlank()) continue;
                String r = s.trim().toLowerCase(Locale.ROOT);
                if (r.equals(id) || plugin.registry().get(r) == null) return "editor.error.unknown-milestone";
            }
            return null;
        }, text -> {
            List<String> req = new ArrayList<>();
            if (!isNone(text)) {
                for (String s : text.split("[,，\\s]+")) {
                    String r = s.trim().toLowerCase(Locale.ROOT);
                    if (!r.isEmpty() && !req.contains(r)) req.add(r);
                }
            }
            update(p, m -> m.withRequires(req));
            Milestone after = m();
            if (after != null && after.requires().size() != req.size()) {
                plugin.messages().send(p, "editor.error.cycle");
            }
        }, reopen(p));
    }

    private static boolean isNone(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        return t.equals("none") || t.equals("無") || t.equals("清除");
    }

    private void delete(Player p) {
        Milestone m = m();
        if (m == null) return;
        String cat = m.categoryId();
        new ConfirmMenu(plugin, p, plugin.messages().raw("gui.editor.m-delete-question", "name", m.name()), () -> {
            plugin.editor().deleteMilestone(id);
            plugin.messages().send(p, "editor.deleted");
            new EditorCategoryMenu(plugin, p, cat, 0).open();
        }, reopen(p)).open();
    }
}
