package clre20.customMilestone.reward;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.Reward;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 發放獎勵。背包放不下的物品存進「待領取獎勵」（SQLite），超過上限才掉在腳邊。
 */
public final class RewardService {

    private final CustomMilestone plugin;

    public RewardService(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    /** 線上玩家領取成就獎勵。 */
    public void give(Player player, PlayerData data, Milestone m) {
        Reward r = m.reward();
        int pendingAdded = 0;
        int dropped = 0;
        for (ItemStack stack : splitStacks(r.buildItems())) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack rest : left.values()) {
                if (addPending(data, rest)) {
                    pendingAdded++;
                } else {
                    player.getWorld().dropItem(player.getLocation(), rest, item -> item.setOwner(player.getUniqueId()));
                    dropped++;
                }
            }
        }
        if (r.exp() > 0) {
            player.giveExp(r.exp());
        }
        runCommands(r, data.name(), data.uuid(), m);
        if (pendingAdded > 0) {
            plugin.messages().send(player, "reward.to-pending", "count", String.valueOf(pendingAdded));
        }
        if (dropped > 0) {
            plugin.messages().send(player, "reward.dropped", "count", String.valueOf(dropped));
        }
    }

    /** 離線玩家（管理員指令給予）：物品全部進待領取，經驗值無法給予。 */
    public void giveOffline(PlayerData data, Milestone m) {
        Reward r = m.reward();
        for (ItemStack stack : splitStacks(r.buildItems())) {
            if (!addPending(data, stack)) {
                plugin.getLogger().warning(data.name() + " 的待領取獎勵已滿，" + m.id() + " 的物品獎勵未發放");
            }
        }
        runCommands(r, data.name(), data.uuid(), m);
    }

    /** 正版／Floodgate 玩家名稱的安全字元；不符合（例如離線模式的 @a）時改用 UUID，避免被當成目標選擇器。 */
    private static final java.util.regex.Pattern SAFE_NAME = java.util.regex.Pattern.compile("^[A-Za-z0-9_.*\\-]{1,20}$");

    private void runCommands(Reward r, String name, UUID uuid, Milestone m) {
        String safeName = name != null && SAFE_NAME.matcher(name).matches() && !name.startsWith("-") ? name : uuid.toString();
        for (String cmd : r.commands()) {
            String line = cmd.replace("%player%", safeName)
                    .replace("%uuid%", uuid.toString())
                    .replace("%milestone%", m.id());
            if (line.startsWith("/")) line = line.substring(1);
            if (line.isBlank()) continue;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "執行獎勵指令失敗（" + m.id() + "）：" + line, e);
            }
        }
    }

    /** 把超過堆疊上限的物品拆成多疊，避免存檔時數量超出範圍。 */
    private static List<ItemStack> splitStacks(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack st : in) {
            int max = Math.max(1, st.getMaxStackSize());
            int amount = st.getAmount();
            while (amount > 0) {
                ItemStack part = st.clone();
                part.setAmount(Math.min(max, amount));
                amount -= part.getAmount();
                out.add(part);
            }
        }
        return out;
    }

    /** 加入待領取；已達上限回傳 false。 */
    public boolean addPending(PlayerData data, ItemStack stack) {
        if (data.pending().size() >= plugin.config().pendingLimit()) {
            return false;
        }
        byte[] bytes;
        try {
            bytes = stack.serializeAsBytes();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "無法序列化獎勵物品", e);
            return false;
        }
        data.addPending(new PlayerData.PendingReward(UUID.randomUUID().toString(), bytes, System.currentTimeMillis()));
        return true;
    }

    public static ItemStack decode(PlayerData.PendingReward r) {
        try {
            return ItemStack.deserializeBytes(r.data());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public enum ClaimResult { OK, NO_SPACE, BROKEN, MISSING }

    /** 領取一份待領取獎勵（主執行緒）。 */
    public ClaimResult claim(Player player, PlayerData data, String id) {
        PlayerData.PendingReward target = null;
        for (PlayerData.PendingReward r : data.pending()) {
            if (r.id().equals(id)) {
                target = r;
                break;
            }
        }
        if (target == null) return ClaimResult.MISSING;
        ItemStack stack = decode(target);
        if (stack == null || stack.getType().isAir()) {
            // 損毀的資料直接移除，避免永遠卡在清單裡
            data.removePending(id);
            return ClaimResult.BROKEN;
        }
        if (!canFit(player.getInventory(), stack)) return ClaimResult.NO_SPACE;
        if (!data.removePending(id)) return ClaimResult.MISSING;
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            // 理論上不會發生（已先檢查空間），保險起見放回待領取
            addPending(data, rest);
        }
        return ClaimResult.OK;
    }

    /** 背包（不含盔甲、副手）是否放得下整疊。 */
    public static boolean canFit(PlayerInventory inv, ItemStack stack) {
        int need = stack.getAmount();
        int max = stack.getMaxStackSize();
        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                need -= max;
            } else if (slot.isSimilar(stack)) {
                need -= Math.max(0, max - slot.getAmount());
            }
            if (need <= 0) return true;
        }
        return false;
    }
}
