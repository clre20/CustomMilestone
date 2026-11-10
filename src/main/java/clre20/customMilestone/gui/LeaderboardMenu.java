package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.Database;
import clre20.customMilestone.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 排行榜（完成數）。可切換「僅自訂」與「含原版」。資料每隔數分鐘更新一次。
 */
public final class LeaderboardMenu extends Menu {

    private final boolean vanilla;

    public LeaderboardMenu(CustomMilestone plugin, Player viewer, boolean vanilla) {
        super(plugin, viewer);
        this.vanilla = vanilla;
    }

    @Override
    protected String title() {
        return plugin.messages().raw(vanilla ? "gui.leaderboard.title-vanilla" : "gui.leaderboard.title-custom");
    }

    @Override
    protected void draw() {
        List<Database.TopEntry> top = plugin.leaderboard().top(vanilla);
        for (int i = 0; i < Math.min(45, top.size()); i++) {
            Database.TopEntry e = top.get(i);
            set(i, Items.head(e.uuid(), e.name(),
                    plugin.messages().raw("gui.leaderboard.entry-name", "rank", String.valueOf(i + 1), "player", e.name()),
                    plugin.messages().list("gui.leaderboard.entry-lore", "value", String.valueOf(e.value()))));
        }
        if (top.isEmpty()) {
            set(22, button(Material.BARRIER, "gui.leaderboard.empty", null));
        }
        Runnable back = () -> {
            PlayerData d = plugin.data().get(viewer.getUniqueId());
            if (d != null) new MainMenu(plugin, viewer, d, 0).open();
            else viewer.closeInventory();
        };
        navigation(0, 1, back, p -> { });
        set(47, button(vanilla ? Material.GRASS_BLOCK : Material.NETHER_STAR,
                vanilla ? "gui.leaderboard.switch-custom" : "gui.leaderboard.switch-vanilla", "gui.leaderboard.switch-lore"),
                pl -> new LeaderboardMenu(plugin, pl, !vanilla).open());
        long at = plugin.leaderboard().updatedAt();
        set(51, button(Material.CLOCK, "gui.leaderboard.updated", "gui.leaderboard.updated-lore",
                "time", at == 0 ? "-" : MilestoneView.formatTime(plugin, at),
                "minutes", String.valueOf(plugin.config().leaderboardRefreshMinutes())));
    }
}
