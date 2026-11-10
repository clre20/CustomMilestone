package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.gui.Items;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.Trigger;
import clre20.customMilestone.milestone.TriggerOptions;
import clre20.customMilestone.milestone.TriggerType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 編輯單一成就。
 */
public final class MilestoneEditorMenu extends EditorMenu {

    private static final int[] OPTION_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    private final String id;
    /** 正在等待「點下方背包物品」設定物品條件。 */
    private boolean pickingItem;

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

        String typeKey = "trigger." + t.type().messageKey();
        // 觸發說明
        List<String> info = new ArrayList<>(plugin.messages().list(typeKey + ".description"));
        info.add(0, plugin.messages().raw("gui.editor.trigger-info-head", "name", plugin.messages().raw(typeKey + ".name")));
        set(18, Items.of(Material.KNOWLEDGE_BOOK, plugin.messages().raw("gui.editor.trigger-info"), info));
        set(19, button(t.type().icon(), "gui.editor.m-trigger", "gui.editor.m-trigger-lore",
                "value", plugin.messages().raw(typeKey + ".name")),
                act(p -> new TriggerTypeMenu(plugin, p, id).open()));
        // 目標：依觸發類型的目標種類顯示不同說明；不需要目標的類型不顯示
        TriggerType.TargetKind kind = t.type().targetKind();
        if (kind != TriggerType.TargetKind.NONE) {
            String kindKey = "gui.editor.target-kind." + kind.name().toLowerCase(Locale.ROOT);
            set(20, button(Material.TARGET, kindKey + ".name", kindKey + ".lore",
                    "value", t.rawTargets().isEmpty() ? plugin.messages().raw("gui.editor.any") : String.join(", ", t.rawTargets())),
                    act(this::targets));
        }
        set(21, button(Material.REPEATER, "gui.editor.m-amount", "gui.editor.m-amount-lore",
                "value", String.valueOf(t.amount()), "unit", plugin.messages().raw(typeKey + ".amount")), act(this::amount));
        List<String> reqNames = new ArrayList<>(m.requires());
        set(22, button(Material.LEAD, "gui.editor.m-requires", "gui.editor.m-requires-lore",
                "value", reqNames.isEmpty() ? none : String.join(", ", reqNames)), act(this::requires));

        // 進階條件：依觸發類型自動顯示
        List<TriggerOptions.Spec> specs = TriggerOptions.of(t.type());
        if (specs.isEmpty()) {
            set(31, button(Material.GRAY_DYE, "gui.editor.option-none-title", "gui.editor.option-none-lore"));
        }
        for (int i = 0; i < Math.min(OPTION_SLOTS.length, specs.size()); i++) {
            TriggerOptions.Spec spec = specs.get(i);
            set(OPTION_SLOTS[i], optionButton(t, spec), act(p -> editOption(p, spec)));
        }

        set(44, button(Material.LAVA_BUCKET, "gui.editor.m-delete", "gui.editor.m-delete-lore"), act(this::delete));
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

    // ===== 進階條件 =====

    private ItemStack optionButton(Trigger t, TriggerOptions.Spec spec) {
        String base = "option." + spec.key();
        Object raw = t.rawOptions().get(spec.key());
        String value;
        if (raw == null) {
            value = spec.def() instanceof Boolean b
                    ? plugin.messages().raw(b ? "gui.editor.value-true" : "gui.editor.value-false")
                    : plugin.messages().raw("gui.editor.any");
        } else if (raw instanceof Boolean b) {
            value = plugin.messages().raw(b ? "gui.editor.value-true" : "gui.editor.value-false");
        } else if (raw instanceof List<?> l) {
            value = l.isEmpty() ? plugin.messages().raw("gui.editor.any")
                    : String.join(", ", l.stream().map(String::valueOf).toList());
        } else {
            value = String.valueOf(raw);
        }
        List<String> lore = new ArrayList<>(plugin.messages().list(base + ".description"));
        lore.add(plugin.messages().raw("gui.editor.option-value", "value", value));
        lore.add("");
        String actionKey = switch (spec.kind()) {
            case TOGGLE, MERCHANT, LEVEL -> "gui.editor.option-action.cycle";
            case LIST, TEXT -> "gui.editor.option-action.chat";
            case ITEM -> raw == null ? (pickingItem ? "gui.editor.option-action.picking" : "gui.editor.option-action.item")
                    : "gui.editor.option-action.clear";
            case POSITION -> "gui.editor.option-action.position";
            case WORLD -> "gui.editor.option-action.world";
        };
        lore.addAll(plugin.messages().list(actionKey));
        boolean glow = spec.kind() == TriggerOptions.Kind.ITEM && pickingItem;
        return Items.decorate(new ItemStack(spec.icon()),
                plugin.messages().raw("gui.editor.option-name", "name", plugin.messages().raw(base + ".name")), lore, glow);
    }

    /** 套用新的進階條件；新增了錯誤時不儲存並顯示原因。 */
    private boolean setOption(Player p, String key, Object value) {
        Milestone m = m();
        if (m == null) return false;
        Trigger t = m.trigger();
        List<String> before = new ArrayList<>();
        new Trigger(t.type(), t.rawTargets(), t.amount(), t.rawOptions(), before::add);
        Map<String, Object> opts = new LinkedHashMap<>(t.rawOptions());
        if (value == null) opts.remove(key);
        else opts.put(key, value);
        List<String> after = new ArrayList<>();
        Trigger next = new Trigger(t.type(), t.rawTargets(), t.amount(), opts, after::add);
        if (after.size() > before.size()) {
            for (String w : after) if (!before.contains(w)) plugin.messages().send(p, "editor.option-invalid", "message", w);
            return false;
        }
        plugin.editor().saveMilestone(m.withTrigger(next));
        plugin.messages().send(p, "editor.saved");
        return true;
    }

    private void editOption(Player p, TriggerOptions.Spec spec) {
        Milestone m = m();
        if (m == null) return;
        Object raw = m.trigger().rawOptions().get(spec.key());
        switch (spec.kind()) {
            case TOGGLE -> {
                boolean cur = raw instanceof Boolean b ? b : (raw != null ? Boolean.parseBoolean(String.valueOf(raw))
                        : Boolean.TRUE.equals(spec.def()));
                setOption(p, spec.key(), !cur);
                refresh();
            }
            case MERCHANT -> {
                String cur = raw instanceof List<?> l && l.size() == 1 ? String.valueOf(l.getFirst()) : null;
                Object next = cur == null ? List.of("villager")
                        : (cur.equals("villager") ? List.of("wandering_trader") : null);
                setOption(p, spec.key(), next);
                refresh();
            }
            case LEVEL -> {
                int cur = raw instanceof Number n ? n.intValue() : 0;
                setOption(p, spec.key(), cur >= 5 ? null : cur + 1);
                refresh();
            }
            case POSITION -> {
                org.bukkit.Location l = p.getLocation();
                Milestone cur = m();
                if (cur == null) return;
                // 同時把世界設為目前所在的世界
                if (!p.getWorld().getName().equalsIgnoreCase(String.valueOf(cur.trigger().rawOptions().get("world")))) {
                    setOption(p, "world", p.getWorld().getName());
                }
                setOption(p, spec.key(), l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
                refresh();
            }
            case WORLD -> {
                setOption(p, spec.key(), p.getWorld().getName());
                refresh();
            }
            case ITEM -> {
                if (raw != null) {
                    pickingItem = false;
                    setOption(p, spec.key(), null);
                } else {
                    pickingItem = !pickingItem;
                    if (pickingItem) plugin.messages().send(p, "editor.pick-item");
                }
                refresh();
            }
            case LIST, TEXT -> ask(p, "option." + spec.key() + ".prompt", 256, null, text -> {
                String v = text.trim();
                if (isNone(v)) {
                    setOption(p, spec.key(), null);
                } else if (spec.kind() == TriggerOptions.Kind.LIST) {
                    List<String> list = new ArrayList<>();
                    for (String s : v.split("[,，\\s]+")) if (!s.isBlank()) list.add(s.trim().toLowerCase(Locale.ROOT));
                    if (list.size() > 50) {
                        plugin.messages().send(p, "editor.error.too-many");
                        return;
                    }
                    setOption(p, spec.key(), list);
                } else {
                    if (spec.key().equals("category") && plugin.registry().category(v) == null) {
                        plugin.messages().send(p, "editor.option-invalid", "message", v);
                        return;
                    }
                    setOption(p, spec.key(), spec.key().equals("statistic") ? v.toUpperCase(Locale.ROOT) : v.toLowerCase(Locale.ROOT));
                }
            }, () -> new MilestoneEditorMenu(plugin, p, id).open());
        }
    }

    @Override
    protected boolean acceptsBottomClicks() {
        return true;
    }

    /**
     * 點下方背包的物品：
     * 正在選物品條件 → 設為物品條件；目標是物品／方塊的觸發 → 把這種物品加入（或移出）目標清單。
     */
    @Override
    protected void clickBottom(ItemStack item) {
        if (!guard(viewer)) return;
        Milestone m = m();
        if (m == null) return;
        if (pickingItem) {
            pickingItem = false;
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            String components = meta == null ? "" : meta.getAsComponentString();
            String value = item.getType().getKey().toString() + (components == null ? "" : components);
            if (value.length() > 2048) {
                plugin.messages().send(viewer, "editor.error.item-too-large");
            } else {
                setOption(viewer, "item", value);
            }
            refresh();
            return;
        }
        Trigger t = m.trigger();
        if (t.type().targetKind() != TriggerType.TargetKind.MATERIAL) {
            plugin.messages().send(viewer, "editor.pick-not-material");
            return;
        }
        String key = item.getType().getKey().getKey();
        List<String> targets = new ArrayList<>(t.rawTargets());
        boolean removed = targets.remove(key);
        if (!removed) {
            if (targets.size() >= 200) {
                plugin.messages().send(viewer, "editor.error.too-many");
                return;
            }
            targets.add(key);
        }
        plugin.editor().saveMilestone(m.withTrigger(new Trigger(t.type(), targets, t.amount(), t.rawOptions(), s -> { })));
        plugin.messages().send(viewer, removed ? "editor.target-removed" : "editor.target-added", "target", key);
        refresh();
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
