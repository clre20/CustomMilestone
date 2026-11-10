package clre20.customMilestone.listener;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.milestone.Trigger;
import clre20.customMilestone.milestone.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 合成、熔煉、附魔、釀造、鐵砧。
 */
public final class CraftingListener implements Listener {

    /** 釀造台 → 最後開啟它的玩家（BrewEvent 沒有玩家資訊）。LRU，上限 1024 筆。 */
    private record BlockRef(UUID world, long key) { }

    private final CustomMilestone plugin;
    private final Map<BlockRef, UUID> brewers = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockRef, UUID> eldest) {
            return size() > 1024;
        }
    };

    public CraftingListener(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !plugin.registry().hasType(TriggerType.CRAFT)) return;
        Material type = e.getRecipe().getResult().getType();
        if (type.isAir()) return;
        // Shift 合成一次會做很多份：以原版「合成次數」統計的差值計算實際數量（下一 tick）
        int before = p.getStatistic(Statistic.CRAFT_ITEM, type);
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null) return;
            int crafted = pl.getStatistic(Statistic.CRAFT_ITEM, type) - before;
            if (crafted > 0) {
                plugin.progress().add(pl, TriggerType.CRAFT, crafted, t -> t.matchesMaterial(type));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent e) {
        if (!plugin.registry().hasType(TriggerType.SMELT) || e.getItemAmount() <= 0) return;
        Material type = e.getItemType();
        plugin.progress().add(e.getPlayer(), TriggerType.SMELT, e.getItemAmount(), t -> t.matchesMaterial(type));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (!plugin.registry().hasType(TriggerType.ENCHANT)) return;
        Material type = e.getItem().getType();
        Map<Enchantment, Integer> added = e.getEnchantsToAdd();
        plugin.progress().add(e.getEnchanter(), TriggerType.ENCHANT, 1,
                t -> t.matchesMaterial(type) && enchantsMatch(t, added));
    }

    private static boolean enchantsMatch(Trigger t, Map<Enchantment, Integer> added) {
        if (t.enchantments().isEmpty()) return true;
        for (Map.Entry<String, Integer> want : t.enchantments().entrySet()) {
            boolean ok = false;
            for (Map.Entry<Enchantment, Integer> have : added.entrySet()) {
                if (have.getKey().getKey().toString().equals(want.getKey()) && have.getValue() >= want.getValue()) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory().getType() != InventoryType.BREWING || !plugin.registry().hasType(TriggerType.BREW)) return;
        org.bukkit.Location loc = e.getInventory().getLocation();
        if (loc == null || loc.getWorld() == null) return;
        brewers.put(new BlockRef(loc.getWorld().getUID(), loc.getBlock().getBlockKey()), e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        if (!plugin.registry().hasType(TriggerType.BREW)) return;
        Block b = e.getBlock();
        UUID id = brewers.get(new BlockRef(b.getWorld().getUID(), b.getBlockKey()));
        Player p = id == null ? null : Bukkit.getPlayer(id);
        if (p == null) return;
        List<ItemStack> results = e.getResults();
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            ItemStack r = results.get(i);
            if (r == null || r.getType().isAir()) continue;
            Material type = r.getType();
            String potion = null;
            if (r.getItemMeta() instanceof PotionMeta pm && pm.getBasePotionType() != null) {
                potion = pm.getBasePotionType().getKey().toString();
            }
            String potionKey = potion;
            plugin.progress().add(p, TriggerType.BREW, 1, t -> t.matchesMaterial(type)
                    && (t.potionTypes().isEmpty() || (potionKey != null && t.potionTypes().contains(potionKey))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvil(InventoryClickEvent e) {
        if (!(e.getView() instanceof AnvilView view) || e.getRawSlot() != 2) return;
        if (!(e.getWhoClicked() instanceof Player p) || !plugin.registry().hasType(TriggerType.ANVIL)) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        // 經驗不足時原版不會讓玩家拿走成品
        if (p.getGameMode() != GameMode.CREATIVE && p.getLevel() < view.getRepairCost()) return;
        Material type = result.getType();
        plugin.progress().add(p, TriggerType.ANVIL, 1, t -> t.matchesMaterial(type));
    }

    public void clear() {
        brewers.clear();
    }
}
