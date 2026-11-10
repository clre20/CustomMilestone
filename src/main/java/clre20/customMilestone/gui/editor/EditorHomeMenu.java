package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.gui.Items;
import clre20.customMilestone.gui.MainMenu;
import clre20.customMilestone.milestone.Category;
import clre20.customMilestone.milestone.MilestoneRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * 編輯器首頁：分類列表、新增分類。
 */
public final class EditorHomeMenu extends EditorMenu {

    private final int page;

    public EditorHomeMenu(CustomMilestone plugin, Player viewer, int page) {
        super(plugin, viewer);
        this.page = page;
    }

    @Override
    protected String title() {
        return plugin.messages().raw("gui.editor.home-title");
    }

    @Override
    protected void draw() {
        List<Category> cats = plugin.registry().categories();
        int pages = pages(cats.size(), 45);
        int p = Math.min(page, pages - 1);
        for (int i = 0; i < 45; i++) {
            int idx = p * 45 + i;
            if (idx >= cats.size()) break;
            Category c = cats.get(idx);
            ItemStack item = Items.of(c.icon(), plugin.messages().raw("gui.editor.category-name", "name", c.name()),
                    plugin.messages().list("gui.editor.category-lore", "id", c.id(),
                            "count", String.valueOf(c.milestones().size()), "order", String.valueOf(c.order())));
            String id = c.id();
            set(i, item, act(pl -> new EditorCategoryMenu(plugin, pl, id, 0).open()));
        }
        navigation(p, pages, () -> {
            PlayerData d = plugin.data().get(viewer.getUniqueId());
            if (d != null) new MainMenu(plugin, viewer, d, 0).open();
            else viewer.closeInventory();
        }, np -> new EditorHomeMenu(plugin, viewer, np).open());
        set(47, button(Material.LIME_DYE, "gui.editor.new-category", "gui.editor.new-category-lore"), act(this::newCategory));
        set(51, button(Material.KNOWLEDGE_BOOK, "gui.editor.help", "gui.editor.help-lore"));
    }

    private void newCategory(Player p) {
        Runnable reopen = () -> new EditorHomeMenu(plugin, p, page).open();
        ask(p, "editor.prompt.category-id", 32, text -> {
            String id = text.toLowerCase(Locale.ROOT);
            if (!MilestoneRegistry.CATEGORY_ID.matcher(id).matches()) return "editor.error.bad-id";
            if (plugin.registry().category(id) != null) return "editor.error.category-exists";
            if (plugin.registry().categories().size() >= MilestoneRegistry.MAX_CATEGORIES) return "editor.error.too-many";
            return null;
        }, text -> {
            String id = text.toLowerCase(Locale.ROOT);
            // 第二步：輸入顯示名稱
            ask(p, "editor.prompt.category-name", 64, null, name -> {
                if (plugin.registry().category(id) != null) {
                    plugin.messages().send(p, "editor.error.category-exists");
                    return;
                }
                plugin.editor().createCategory(id, name);
                plugin.messages().send(p, "editor.saved");
            }, () -> new EditorCategoryMenu(plugin, p, id, 0).open(), reopen);
        }, () -> { }, reopen);
    }
}
