package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.advancement.VanillaCatalog;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.gui.editor.EditorHomeMenu;
import clre20.customMilestone.milestone.Category;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 主選單：自訂分類、原版分類、排行榜、待領取獎勵、編輯器。
 */
public final class MainMenu extends Menu {

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int[] VANILLA_SLOTS = {29, 30, 31, 32, 33};

    private final PlayerData target;
    private final int page;

    public MainMenu(CustomMilestone plugin, Player viewer, PlayerData target, int page) {
        super(plugin, viewer);
        this.target = target;
        this.page = page;
    }

    private boolean self() {
        return target.uuid().equals(viewer.getUniqueId());
    }

    @Override
    protected String title() {
        return self() ? plugin.messages().raw("gui.main.title")
                : plugin.messages().raw("gui.main.title-other", "player", target.name());
    }

    @Override
    protected void draw() {
        fillBorder(0, 8);
        fillBorder(36, 44);

        // 玩家資訊
        int total = plugin.registry().size();
        int done = target.completedCount(id -> plugin.registry().get(id) != null);
        int percent = total == 0 ? 0 : done * 100 / total;
        set(4, Items.head(target.uuid(), target.name(),
                plugin.messages().raw("gui.main.info-name", "player", target.name()),
                plugin.messages().list("gui.main.info-lore",
                        "completed", String.valueOf(done), "total", String.valueOf(total),
                        "percent", String.valueOf(percent),
                        "vanilla", String.valueOf(target.vanillaCompleted()),
                        "vanilla_total", String.valueOf(plugin.vanilla().total()),
                        "login_days", String.valueOf(target.loginDays()),
                        "streak", String.valueOf(target.loginStreak()))));

        // 自訂分類
        List<Category> cats = plugin.registry().categories();
        int per = CATEGORY_SLOTS.length;
        int pages = pages(cats.size(), per);
        int p = Math.min(page, pages - 1);
        for (int i = 0; i < per; i++) {
            int idx = p * per + i;
            if (idx >= cats.size()) break;
            Category c = cats.get(idx);
            int cTotal = c.milestones().size();
            int cDone = 0;
            for (Milestone m : c.milestones()) if (target.isCompleted(m.id())) cDone++;
            List<String> lore = new ArrayList<>();
            for (String line : c.description()) lore.add(plugin.messages().raw("gui.milestone.description-line", "line", line));
            lore.addAll(plugin.messages().list("gui.main.category-lore",
                    "completed", String.valueOf(cDone), "total", String.valueOf(cTotal),
                    "bar", Text.progressBar(cDone, Math.max(1, cTotal), 20, "&a", "&8")));
            ItemStack item = Items.decorate(new ItemStack(c.icon()),
                    plugin.messages().raw("gui.main.category-name", "name", c.name()), lore, cTotal > 0 && cDone == cTotal);
            String catId = c.id();
            set(CATEGORY_SLOTS[i], item, pl -> new CategoryMenu(plugin, pl, target, catId, 0).open());
        }
        if (cats.isEmpty()) {
            set(13, button(Material.BARRIER, "gui.main.no-category", "gui.main.no-category-lore"));
        }

        // 原版分類
        Player targetOnline = Bukkit.getPlayer(target.uuid());
        List<VanillaCatalog.VanillaCategory> vcats = plugin.vanilla().categories();
        for (int i = 0; i < Math.min(VANILLA_SLOTS.length, vcats.size()); i++) {
            VanillaCatalog.VanillaCategory vc = vcats.get(i);
            List<String> lore;
            if (targetOnline != null) {
                int vDone = 0;
                for (NamespacedKey k : vc.advancements()) {
                    Advancement a = Bukkit.getAdvancement(k);
                    if (a != null && targetOnline.getAdvancementProgress(a).isDone()) vDone++;
                }
                lore = plugin.messages().list("gui.main.vanilla-lore",
                        "completed", String.valueOf(vDone), "total", String.valueOf(vc.advancements().size()),
                        "bar", Text.progressBar(vDone, Math.max(1, vc.advancements().size()), 20, "&a", "&8"));
            } else {
                lore = plugin.messages().list("gui.main.vanilla-offline-lore");
            }
            String vid = vc.id();
            set(VANILLA_SLOTS[i], Items.of(vc.icon(),
                    plugin.messages().raw("gui.main.vanilla-name", "name", plugin.messages().raw("vanilla." + vid)), lore),
                    targetOnline == null ? null : pl -> new VanillaMenu(plugin, pl, target, vid, 0).open());
        }

        navigation(p, pages, null, np -> new MainMenu(plugin, viewer, target, np).open());

        if (viewer.hasPermission("custommilestone.top")) {
            set(47, button(Material.GOLD_INGOT, "gui.main.leaderboard", "gui.main.leaderboard-lore"),
                    pl -> new LeaderboardMenu(plugin, pl, false).open());
        }
        if (self()) {
            int pending = target.pending().size();
            set(51, button(pending > 0 ? Material.CHEST_MINECART : Material.MINECART, "gui.main.pending",
                    "gui.main.pending-lore", "count", String.valueOf(pending)),
                    pl -> new PendingMenu(plugin, pl, 0).open());
            if (viewer.hasPermission("custommilestone.admin.edit")) {
                set(46, button(Material.WRITABLE_BOOK, "gui.main.editor", "gui.main.editor-lore"),
                        pl -> new EditorHomeMenu(plugin, pl, 0).open());
            }
        }
    }
}
