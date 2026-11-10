package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.reward.RewardService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 待領取獎勵：左鍵領取單一物品，或一次全部領取（放得下的才會領）。
 */
public final class PendingMenu extends Menu {

    private final int page;

    public PendingMenu(CustomMilestone plugin, Player viewer, int page) {
        super(plugin, viewer);
        this.page = page;
    }

    @Override
    protected String title() {
        return plugin.messages().raw("gui.pending.title");
    }

    @Override
    protected void draw() {
        PlayerData d = plugin.data().get(viewer.getUniqueId());
        if (d == null) {
            navigation(0, 1, null, p -> { });
            return;
        }
        List<PlayerData.PendingReward> list = new ArrayList<>(d.pending());
        int pages = pages(list.size(), 45);
        int p = Math.min(page, pages - 1);
        for (int i = 0; i < 45; i++) {
            int idx = p * 45 + i;
            if (idx >= list.size()) break;
            PlayerData.PendingReward r = list.get(idx);
            ItemStack stack = RewardService.decode(r);
            ItemStack shown = stack == null
                    ? Items.of(Material.BARRIER, plugin.messages().raw("gui.pending.broken"), List.of())
                    : Items.preview(stack, plugin.messages().list("gui.pending.item-lore",
                    "time", MilestoneView.formatTime(plugin, r.created())));
            String id = r.id();
            set(i, shown, pl -> claim(pl, id));
        }
        if (list.isEmpty()) {
            set(22, button(Material.MINECART, "gui.pending.empty", null));
        }
        navigation(p, pages, () -> new MainMenu(plugin, viewer, d, 0).open(),
                np -> new PendingMenu(plugin, viewer, np).open());
        if (!list.isEmpty()) {
            set(47, button(Material.HOPPER, "gui.pending.claim-all", "gui.pending.claim-all-lore"), this::claimAll);
        }
    }

    private void claim(Player pl, String id) {
        PlayerData d = plugin.data().get(pl.getUniqueId());
        if (d == null) return;
        switch (plugin.rewards().claim(pl, d, id)) {
            case OK -> plugin.messages().send(pl, "reward.claimed");
            case NO_SPACE -> plugin.messages().send(pl, "reward.no-space");
            case BROKEN -> plugin.messages().send(pl, "reward.broken");
            case MISSING -> { }
        }
        refresh();
    }

    private void claimAll(Player pl) {
        PlayerData d = plugin.data().get(pl.getUniqueId());
        if (d == null) return;
        int ok = 0;
        boolean full = false;
        for (PlayerData.PendingReward r : new ArrayList<>(d.pending())) {
            RewardService.ClaimResult res = plugin.rewards().claim(pl, d, r.id());
            if (res == RewardService.ClaimResult.OK) ok++;
            else if (res == RewardService.ClaimResult.NO_SPACE) full = true;
        }
        plugin.messages().send(pl, full ? "reward.claimed-some" : "reward.claimed-all", "count", String.valueOf(ok));
        refresh();
    }
}
