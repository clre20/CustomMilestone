package clre20.customMilestone.listener;

import clre20.customMilestone.CustomMilestone;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 記錄玩家放置的方塊（存在區塊的 PersistentDataContainer，跟著世界存檔，不佔插件記憶體）。
 * 只記錄「設定了 natural-only 的破壞方塊成就」會用到的方塊種類，且每個區塊有數量上限。
 */
public final class PlacedBlockTracker {

    private final CustomMilestone plugin;
    private final NamespacedKey key;

    public PlacedBlockTracker(CustomMilestone plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "placed");
    }

    private static long pack(Block b) {
        return ((long) (b.getY() & 0xFFFFF) << 8) | ((long) (b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    public void markPlaced(Block b) {
        Chunk chunk = b.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] arr = pdc.get(key, PersistentDataType.LONG_ARRAY);
        long v = pack(b);
        if (arr != null) {
            for (long x : arr) if (x == v) return;
        }
        int limit = plugin.config().placedBlocksPerChunk();
        long[] next;
        if (arr == null) {
            next = new long[]{v};
        } else if (arr.length >= limit) {
            // 超過上限：丟掉最舊的紀錄
            next = new long[limit];
            System.arraycopy(arr, arr.length - limit + 1, next, 0, limit - 1);
            next[limit - 1] = v;
        } else {
            next = new long[arr.length + 1];
            System.arraycopy(arr, 0, next, 0, arr.length);
            next[arr.length] = v;
        }
        pdc.set(key, PersistentDataType.LONG_ARRAY, next);
    }

    /** 檢查並移除紀錄；回傳這個方塊是否為玩家放置。 */
    public boolean consume(Block b) {
        Chunk chunk = b.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] arr = pdc.get(key, PersistentDataType.LONG_ARRAY);
        if (arr == null) return false;
        long v = pack(b);
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == v) {
                if (arr.length == 1) {
                    pdc.remove(key);
                } else {
                    long[] next = new long[arr.length - 1];
                    System.arraycopy(arr, 0, next, 0, i);
                    System.arraycopy(arr, i + 1, next, i, arr.length - i - 1);
                    pdc.set(key, PersistentDataType.LONG_ARRAY, next);
                }
                return true;
            }
        }
        return false;
    }
}
