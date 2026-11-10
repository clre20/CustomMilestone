package clre20.customMilestone.listener;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.milestone.TriggerType;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * 原版成就達成：靜默補發不廣播、統計原版完成數、「完成指定原版成就」觸發。
 */
public final class AdvancementListener implements Listener {

    private final CustomMilestone plugin;

    public AdvancementListener(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    /** 最先執行：上線補發的自訂成就不在聊天室廣播（DiscordSRV 也會看到 message 為空）。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSilent(PlayerAdvancementDoneEvent e) {
        if (plugin.advancements().isSilent(e.getPlayer(), e.getAdvancement())) {
            e.message(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDone(PlayerAdvancementDoneEvent e) {
        Advancement adv = e.getAdvancement();
        if (!plugin.vanilla().isVanilla(adv.getKey())) return;
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d != null) d.setVanillaCompleted(d.vanillaCompleted() + 1);
        if (plugin.registry().hasType(TriggerType.ADVANCEMENT)) {
            plugin.progress().handle(p, TriggerType.ADVANCEMENT, m -> plugin.progress().periodicValue(p, d, m));
        }
    }
}
