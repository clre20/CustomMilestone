package clre20.customMilestone.listener;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.milestone.TriggerType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * 破壞／放置方塊。高頻事件：最外層先檢查有沒有成就用到這個類型，沒有就直接 return。
 */
public final class BlockListener implements Listener {

    private final CustomMilestone plugin;
    private final PlacedBlockTracker tracker;

    public BlockListener(CustomMilestone plugin, PlacedBlockTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material type = b.getType();
        boolean tracked = plugin.registry().naturalTracked().contains(type);
        if (!tracked && !plugin.registry().hasType(TriggerType.BREAK_BLOCK)) return;
        boolean placed = tracked && tracker.consume(b);
        plugin.progress().add(e.getPlayer(), TriggerType.BREAK_BLOCK, 1,
                t -> t.matchesMaterial(type) && !(placed && t.naturalOnly()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        Material type = b.getType();
        if (plugin.registry().naturalTracked().contains(type)) {
            tracker.markPlaced(b);
        }
        if (!plugin.registry().hasType(TriggerType.PLACE_BLOCK)) return;
        plugin.progress().add(e.getPlayer(), TriggerType.PLACE_BLOCK, 1, t -> t.matchesMaterial(type));
    }
}
