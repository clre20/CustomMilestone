package clre20.customMilestone.listener;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.milestone.TriggerType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * 擊殺生物、擊殺玩家、死亡。
 */
public final class CombatListener implements Listener {

    private final CustomMilestone plugin;

    public CombatListener(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();

        if (dead instanceof Player victim) {
            if (plugin.registry().hasType(TriggerType.DEATH)) {
                EntityDamageEvent last = victim.getLastDamageCause();
                String cause = last == null ? "CUSTOM" : last.getCause().name();
                plugin.progress().add(victim, TriggerType.DEATH, 1, t -> t.matchesKey(cause));
            }
            if (killer != null && killer != victim && plugin.registry().hasType(TriggerType.KILL_PLAYER)) {
                plugin.progress().add(killer, TriggerType.KILL_PLAYER, 1, t -> true);
            }
            return;
        }

        if (killer == null || !plugin.registry().hasType(TriggerType.KILL_ENTITY)) return;
        EntityType type = dead.getType();
        CreatureSpawnEvent.SpawnReason reason = dead.getEntitySpawnReason();
        boolean fromSpawner = reason == CreatureSpawnEvent.SpawnReason.SPAWNER
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG;
        plugin.progress().add(killer, TriggerType.KILL_ENTITY, 1,
                t -> t.matchesEntity(type) && !(fromSpawner && t.excludeSpawner()));
    }
}
