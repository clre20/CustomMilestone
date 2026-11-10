package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.advancement.VanillaCatalog;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.util.Text;
import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版成就分類列表（名稱與說明使用翻譯鍵，玩家看到自己語言的文字）。
 */
public final class VanillaMenu extends Menu {

    private final PlayerData target;
    private final String categoryId;
    private final int page;

    public VanillaMenu(CustomMilestone plugin, Player viewer, PlayerData target, String categoryId, int page) {
        super(plugin, viewer);
        this.target = target;
        this.categoryId = categoryId;
        this.page = page;
    }

    @Override
    protected String title() {
        return plugin.messages().raw("gui.vanilla.title", "name", plugin.messages().raw("vanilla." + categoryId),
                "page", String.valueOf(page + 1));
    }

    @Override
    protected void draw() {
        Runnable back = () -> new MainMenu(plugin, viewer, target, 0).open();
        VanillaCatalog.VanillaCategory vc = plugin.vanilla().category(categoryId);
        Player owner = Bukkit.getPlayer(target.uuid());
        if (vc == null || owner == null) {
            navigation(0, 1, back, p -> { });
            return;
        }
        List<NamespacedKey> keys = vc.advancements();
        int pages = pages(keys.size(), 45);
        int p = Math.min(page, pages - 1);
        for (int i = 0; i < 45; i++) {
            int idx = p * 45 + i;
            if (idx >= keys.size()) break;
            Advancement a = Bukkit.getAdvancement(keys.get(idx));
            if (a == null || a.getDisplay() == null) continue;
            set(i, build(owner, a));
        }
        navigation(p, pages, back, np -> new VanillaMenu(plugin, viewer, target, categoryId, np).open());
    }

    private ItemStack build(Player owner, Advancement a) {
        AdvancementDisplay display = a.getDisplay();
        AdvancementProgress progress = owner.getAdvancementProgress(a);
        boolean done = progress.isDone();
        if (!done && display.isHidden()) {
            return Items.of(Material.GRAY_STAINED_GLASS_PANE, plugin.messages().raw("gui.milestone.hidden-name"),
                    plugin.messages().list("gui.milestone.hidden-lore"));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(display.description().colorIfAbsent(NamedTextColor.GRAY));
        lore.add(Component.empty());
        int awarded = progress.getAwardedCriteria().size();
        int totalCriteria = awarded + progress.getRemainingCriteria().size();
        if (done) {
            lore.addAll(Text.itemLines(plugin.messages().list("gui.vanilla.lore-done")));
        } else if (totalCriteria > 1) {
            lore.addAll(Text.itemLines(plugin.messages().list("gui.vanilla.lore-progress",
                    "current", String.valueOf(awarded), "target", String.valueOf(totalCriteria),
                    "bar", Text.progressBar(awarded, totalCriteria, 20, "&a", "&8"))));
        } else {
            lore.addAll(Text.itemLines(plugin.messages().list("gui.vanilla.lore-todo")));
        }
        lore.add(Text.item(plugin.messages().raw("gui.milestone.frame-line",
                "frame", plugin.messages().raw("frame." + display.frame().name().toLowerCase(java.util.Locale.ROOT)))));
        ItemStack icon = display.icon().clone();
        if (icon.getType().isAir()) icon = new ItemStack(Material.PAPER);
        icon.setAmount(1);
        Component title = done
                ? Text.color(plugin.messages().raw("gui.vanilla.name-done")).append(display.title().colorIfAbsent(NamedTextColor.GREEN))
                : Text.color(plugin.messages().raw("gui.vanilla.name-todo")).append(display.title().colorIfAbsent(NamedTextColor.YELLOW));
        return Items.withComponents(icon, title, lore, done);
    }
}
