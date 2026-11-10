package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.gui.Items;
import clre20.customMilestone.milestone.Category;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.MilestoneRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 編輯一個分類：成就列表（每頁 36 個）、分類設定、新增成就、刪除分類。
 */
public final class EditorCategoryMenu extends EditorMenu {

    private static final int PER_PAGE = 36;

    private final String categoryId;
    private final int page;

    public EditorCategoryMenu(CustomMilestone plugin, Player viewer, String categoryId, int page) {
        super(plugin, viewer);
        this.categoryId = categoryId;
        this.page = page;
    }

    @Override
    protected String title() {
        Category c = plugin.registry().category(categoryId);
        return plugin.messages().raw("gui.editor.category-title", "name", c == null ? categoryId : c.name());
    }

    @Override
    protected void draw() {
        Category c = plugin.registry().category(categoryId);
        Runnable back = () -> new EditorHomeMenu(plugin, viewer, 0).open();
        if (c == null) {
            navigation(0, 1, back, p -> { });
            return;
        }
        List<Milestone> list = new ArrayList<>(c.milestones());
        int pages = pages(list.size(), PER_PAGE);
        int p = Math.min(page, pages - 1);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = p * PER_PAGE + i;
            if (idx >= list.size()) break;
            Milestone m = list.get(idx);
            ItemStack item = Items.of(m.icon(), plugin.messages().raw("gui.editor.milestone-name", "name", m.name()),
                    plugin.messages().list("gui.editor.milestone-lore", "id", m.id(),
                            "trigger", plugin.messages().raw("trigger." + m.trigger().type().messageKey() + ".name"),
                            "amount", String.valueOf(m.target())));
            String id = m.id();
            set(i, item, act(pl -> new MilestoneEditorMenu(plugin, pl, id).open()));
        }

        fillBorder(36, 44);
        set(37, button(Material.NAME_TAG, "gui.editor.cat-rename", "gui.editor.current", "value", c.name()),
                act(this::rename));
        set(38, button(Material.BOOK, "gui.editor.cat-description", "gui.editor.current",
                "value", String.join("&7 | &f", c.description())), act(this::describe));
        set(39, button(c.icon(), "gui.editor.cat-icon", "gui.editor.icon-lore"), act(this::icon));
        set(40, button(Material.COMPARATOR, "gui.editor.cat-order", "gui.editor.current",
                "value", String.valueOf(c.order())), act(this::order));
        set(42, button(Material.LIME_DYE, "gui.editor.new-milestone", "gui.editor.new-milestone-lore"),
                act(this::newMilestone));
        set(44, button(Material.LAVA_BUCKET, "gui.editor.cat-delete", "gui.editor.cat-delete-lore"),
                act(this::delete));
        navigation(p, pages, back, np -> new EditorCategoryMenu(plugin, viewer, categoryId, np).open());
    }

    private Runnable reopen(Player p) {
        return () -> new EditorCategoryMenu(plugin, p, categoryId, page).open();
    }

    private void rename(Player p) {
        ask(p, "editor.prompt.name", 64, null, text -> {
            Category c = plugin.registry().category(categoryId);
            if (c == null) return;
            c.setName(text);
            plugin.editor().saveCategory(c);
            plugin.messages().send(p, "editor.saved");
        }, reopen(p));
    }

    private void describe(Player p) {
        ask(p, "editor.prompt.description", 256, EditorValidators::description, text -> {
            Category c = plugin.registry().category(categoryId);
            if (c == null) return;
            c.setDescription(EditorValidators.splitLines(text));
            plugin.editor().saveCategory(c);
            plugin.messages().send(p, "editor.saved");
        }, reopen(p));
    }

    private void icon(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            plugin.messages().send(p, "editor.error.empty-hand");
            return;
        }
        Category c = plugin.registry().category(categoryId);
        if (c == null) return;
        c.setIcon(hand.getType());
        plugin.editor().saveCategory(c);
        plugin.messages().send(p, "editor.saved");
        refresh();
    }

    private void order(Player p) {
        ask(p, "editor.prompt.order", 6, EditorValidators::integer, text -> {
            Category c = plugin.registry().category(categoryId);
            if (c == null) return;
            c.setOrder(Integer.parseInt(text.trim()));
            plugin.editor().saveCategory(c);
            plugin.messages().send(p, "editor.saved");
        }, reopen(p));
    }

    private void newMilestone(Player p) {
        String[] created = new String[1];
        ask(p, "editor.prompt.milestone-id", 32, text -> {
            String id = text.toLowerCase(Locale.ROOT);
            if (!clre20.customMilestone.milestone.Milestone.ID_PATTERN.matcher(id).matches() || id.equals("root")) {
                return "editor.error.bad-id";
            }
            if (plugin.registry().get(id) != null) return "editor.error.milestone-exists";
            if (plugin.registry().size() >= MilestoneRegistry.MAX_MILESTONES) return "editor.error.too-many";
            return null;
        }, text -> {
            if (plugin.registry().category(categoryId) == null) return;
            String id = text.toLowerCase(Locale.ROOT);
            if (plugin.registry().get(id) != null) {
                plugin.messages().send(p, "editor.error.milestone-exists");
                return;
            }
            plugin.editor().createMilestone(categoryId, id);
            created[0] = id;
            plugin.messages().send(p, "editor.saved");
        }, () -> {
            // 建立後直接進入成就編輯
            if (created[0] != null) new MilestoneEditorMenu(plugin, p, created[0]).open();
            else reopen(p).run();
        }, reopen(p));
    }

    private void delete(Player p) {
        Category c = plugin.registry().category(categoryId);
        if (c == null) return;
        new ConfirmMenu(plugin, p, plugin.messages().raw("gui.editor.cat-delete-question", "name", c.name(),
                "count", String.valueOf(c.milestones().size())), () -> {
            plugin.editor().deleteCategory(categoryId);
            plugin.messages().send(p, "editor.deleted");
            new EditorHomeMenu(plugin, p, 0).open();
        }, reopen(p)).open();
    }
}
