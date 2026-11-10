package clre20.customMilestone.listener;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 登入預載、上線同步、離線存檔。
 */
public final class PlayerConnectionListener implements Listener {

    private final CustomMilestone plugin;

    public PlayerConnectionListener(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    /** 非同步執行緒：先把資料讀好，進服時不必等。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.data().preload(e.getUniqueId(), e.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().attach(p.getUniqueId(), p.getName(), loaded -> ready(p.getUniqueId(), loaded));
        if (d != null) ready(p.getUniqueId(), d);
    }

    /** 資料就緒後（主執行緒）。 */
    public void ready(UUID uuid, PlayerData d) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;

        // 登入天數／連續登入
        long today = LocalDate.now(plugin.config().zone()).toEpochDay();
        if (d.lastLoginDay() != today) {
            int streak = d.lastLoginDay() == today - 1 ? d.loginStreak() + 1 : 1;
            d.setLogin(d.loginDays() + 1, streak, today);
        }
        d.setVanillaCompleted(plugin.vanilla().countDone(p));

        plugin.progress().syncAdvancements(p, d);
        plugin.progress().checkReached(p, d);
        plugin.progress().checkPeriodic(p);

        if (!d.pending().isEmpty()) {
            plugin.messages().send(p, "reward.pending-reminder", "count", String.valueOf(d.pending().size()));
        }
        if (plugin.config().updateNotifyOnJoin() && p.hasPermission("custommilestone.admin.version")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player pl = Bukkit.getPlayer(uuid);
                if (pl != null) plugin.updates().notifyIfOutdated(pl);
            }, 60L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        plugin.chatInput().cancel(id, false);
        plugin.data().detach(id);
    }
}
