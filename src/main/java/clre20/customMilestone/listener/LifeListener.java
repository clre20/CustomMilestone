package clre20.customMilestone.listener;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.milestone.Trigger;
import clre20.customMilestone.milestone.TriggerType;
import clre20.customMilestone.progress.ProgressService;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Material;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

/**
 * 釣魚、繁殖、馴服、剪毛、食用、村民交易、等級、切換世界。
 */
public final class LifeListener implements Listener {

    private final CustomMilestone plugin;

    public LifeListener(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !plugin.registry().hasType(TriggerType.FISH)) return;
        if (!(e.getCaught() instanceof Item item)) return;
        ItemStack stack = item.getItemStack();
        Material type = stack.getType();
        plugin.progress().add(e.getPlayer(), TriggerType.FISH, stack.getAmount(),
                t -> t.matchesMaterial(type) && (t.item() == null || t.item().matches(stack)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p) || !plugin.registry().hasType(TriggerType.BREED)) return;
        EntityType type = e.getEntity().getType();
        plugin.progress().add(p, TriggerType.BREED, 1, t -> t.matchesEntity(type));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (!(e.getOwner() instanceof Player p) || !plugin.registry().hasType(TriggerType.TAME)) return;
        EntityType type = e.getEntity().getType();
        plugin.progress().add(p, TriggerType.TAME, 1, t -> t.matchesEntity(type));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        if (!plugin.registry().hasType(TriggerType.SHEAR)) return;
        EntityType type = e.getEntity().getType();
        plugin.progress().add(e.getPlayer(), TriggerType.SHEAR, 1, t -> t.matchesEntity(type));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!plugin.registry().hasType(TriggerType.CONSUME)) return;
        ItemStack stack = e.getItem();
        Material type = stack.getType();
        plugin.progress().add(e.getPlayer(), TriggerType.CONSUME, 1,
                t -> t.matchesMaterial(type) && (t.item() == null || t.item().matches(stack)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent e) {
        if (!plugin.registry().hasType(TriggerType.TRADE)) return;
        MerchantRecipe recipe = e.getTrade();
        ItemStack result = recipe.getResult();
        AbstractVillager merchant = e.getVillager();
        String merchantType = merchant instanceof WanderingTrader ? "wandering_trader" : "villager";
        String profession = null;
        int level = 0;
        if (merchant instanceof Villager v) {
            profession = v.getProfession().key().value();
            level = v.getVillagerLevel();
        }
        String prof = profession;
        int lvl = level;
        plugin.progress().add(e.getPlayer(), TriggerType.TRADE, 1, t -> tradeMatches(t, recipe, result,
                merchantType, prof, lvl));
    }

    private static boolean tradeMatches(Trigger t, MerchantRecipe recipe, ItemStack result, String merchant,
                                        String profession, int level) {
        if (!t.matchesMaterial(result.getType())) return false;
        if (t.item() != null && !t.item().matches(result)) return false;
        if (!t.merchants().isEmpty() && !t.merchants().contains(merchant)) return false;
        if (!t.professions().isEmpty() && (profession == null || !t.professions().contains(profession))) return false;
        if (t.minLevel() > 0 && level < t.minLevel()) return false;
        if (!t.costs().isEmpty()) {
            boolean any = false;
            for (ItemStack in : recipe.getIngredients()) {
                if (in != null && t.costs().contains(in.getType())) {
                    any = true;
                    break;
                }
            }
            return any;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent e) {
        if (!plugin.registry().hasType(TriggerType.LEVEL) || e.getNewLevel() <= e.getOldLevel()) return;
        int level = e.getNewLevel();
        plugin.progress().handle(e.getPlayer(), TriggerType.LEVEL, m -> level);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        if (!plugin.registry().hasType(TriggerType.ENTER_WORLD)) return;
        Player p = e.getPlayer();
        String world = p.getWorld().getName();
        plugin.progress().handle(p, TriggerType.ENTER_WORLD,
                m -> m.trigger().matchesKey(world) ? m.target() : ProgressService.SKIP);
    }
}
