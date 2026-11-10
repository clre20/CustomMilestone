package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.milestone.Category;
import clre20.customMilestone.milestone.Milestone;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 某個自訂分類的成就列表（每頁 45 個）。
 */
public final class CategoryMenu extends Menu {

    private final PlayerData target;
    private final String categoryId;
    private final int page;

    public CategoryMenu(CustomMilestone plugin, Player viewer, PlayerData target, String categoryId, int page) {
        super(plugin, viewer);
        this.target = target;
        this.categoryId = categoryId;
        this.page = page;
    }

    @Override
    protected String title() {
        Category c = plugin.registry().category(categoryId);
        return plugin.messages().raw("gui.category.title", "name", c == null ? categoryId : c.name(),
                "page", String.valueOf(page + 1));
    }

    @Override
    protected void draw() {
        Category c = plugin.registry().category(categoryId);
        Runnable back = () -> new MainMenu(plugin, viewer, target, 0).open();
        if (c == null) {
            navigation(0, 1, back, p -> { });
            return;
        }
        List<Milestone> list = new ArrayList<>(c.milestones());
        int pages = pages(list.size(), 45);
        int p = Math.min(page, pages - 1);
        for (int i = 0; i < 45; i++) {
            int idx = p * 45 + i;
            if (idx >= list.size()) break;
            set(i, MilestoneView.build(plugin, target, list.get(idx)));
        }
        navigation(p, pages, back, np -> new CategoryMenu(plugin, viewer, target, categoryId, np).open());
    }
}
