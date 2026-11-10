package clre20.customMilestone.listener;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.milestone.ItemMatcher;
import clre20.customMilestone.milestone.Milestone;
import clre20.customMilestone.milestone.Trigger;
import clre20.customMilestone.milestone.TriggerType;
import clre20.customMilestone.progress.ProgressService;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 「取得物品」成就與認領記號。
 * <p>
 * 事件發生時只記錄「這位玩家取得了某種物品（來源、取得前的未認領數量）」，下一個 tick 再統一檢查背包：
 * 新增的未認領物品計入進度，並只在「計入進度」的數量上寫入隱藏的認領記號（必要時拆成兩疊）。
 * 已有認領記號的物品不會再被任何人計入。
 */
public final class ItemAcquireListener implements Listener {

    private record Token(Trigger.Source source, int baseline, int fixed) { }

    private static final Set<InventoryType> CONTAINERS = EnumSet.of(
            InventoryType.CHEST, InventoryType.DISPENSER, InventoryType.DROPPER, InventoryType.HOPPER,
            InventoryType.BARREL, InventoryType.SHULKER_BOX, InventoryType.ENDER_CHEST, InventoryType.FURNACE,
            InventoryType.BLAST_FURNACE, InventoryType.SMOKER, InventoryType.BREWING, InventoryType.CRAFTER,
            InventoryType.DECORATED_POT, InventoryType.CHISELED_BOOKSHELF, InventoryType.SHELF, InventoryType.LECTERN);

    private final CustomMilestone plugin;
    private final NamespacedKey claimKey;
    private final NamespacedKey fishedKey;
    private final Map<UUID, Map<Material, Token>> tokens = new HashMap<>();

    public ItemAcquireListener(CustomMilestone plugin) {
        this.plugin = plugin;
        this.claimKey = new NamespacedKey(plugin, "claimed");
        this.fishedKey = new NamespacedKey(plugin, "fished_by");
    }

    // ===== 事件：只做快速過濾＋記錄 =====

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !plugin.registry().hasType(TriggerType.ACQUIRE_ITEM)) return;
        Item item = e.getItem();
        ItemStack stack = item.getItemStack();
        if (isClaimed(stack)) return;
        String fishedBy = item.getPersistentDataContainer().get(fishedKey, PersistentDataType.STRING);
        Trigger.Source src = p.getUniqueId().toString().equals(fishedBy) ? Trigger.Source.FISH : Trigger.Source.PICKUP;
        record(p, stack.getType(), src, -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item item)) return;
        if (!plugin.registry().hasType(TriggerType.ACQUIRE_ITEM)) return;
        // 標記在掉落物實體上（不是物品本身），撿起時判斷來源為釣魚
        item.getPersistentDataContainer().set(fishedKey, PersistentDataType.STRING, e.getPlayer().getUniqueId().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !plugin.registry().hasType(TriggerType.ACQUIRE_ITEM)) return;
        ItemStack result = e.getRecipe().getResult();
        record(p, result.getType(), Trigger.Source.CRAFT, -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent e) {
        if (!plugin.registry().hasType(TriggerType.ACQUIRE_ITEM)) return;
        record(e.getPlayer(), e.getItemType(), Trigger.Source.SMELT, e.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent e) {
        if (!plugin.registry().hasType(TriggerType.ACQUIRE_ITEM)) return;
        ItemStack result = e.getTrade().getResult();
        record(e.getPlayer(), result.getType(), Trigger.Source.TRADE, result.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerTake(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !plugin.registry().hasType(TriggerType.ACQUIRE_ITEM)) return;
        Inventory top = e.getView().getTopInventory();
        if (!CONTAINERS.contains(top.getType())) return;
        // 熔爐成品格由 FurnaceExtractEvent 處理（來源算「熔煉」）
        if (e.getSlotType() == InventoryType.SlotType.RESULT) return;
        InventoryAction action = e.getAction();
        ItemStack moved;
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            moved = e.getCursor();
        } else {
            if (e.getClickedInventory() != top) return;
            switch (action) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY,
                     HOTBAR_SWAP, SWAP_WITH_CURSOR -> moved = e.getCurrentItem();
                default -> { return; }
            }
        }
        if (moved == null || moved.getType().isAir() || isClaimed(moved)) return;
        record(p, moved.getType(), Trigger.Source.CONTAINER, -1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        tokens.remove(e.getPlayer().getUniqueId());
    }

    // ===== 記錄與下一 tick 檢查 =====

    /** fixed ≥ 0：事件已知取得數量（取得時物品可能已經進背包）；否則以「取得前的未認領數量」做差值。 */
    private void record(Player p, Material type, Trigger.Source src, int fixed) {
        if (type == null || type.isAir() || !relevant(p, type, src)) return;
        Map<Material, Token> map = tokens.get(p.getUniqueId());
        boolean schedule = map == null;
        if (map == null) {
            map = new EnumMap<>(Material.class);
            tokens.put(p.getUniqueId(), map);
        }
        Token old = map.get(type);
        if (old == null) {
            map.put(type, new Token(src, fixed >= 0 ? 0 : countUnclaimed(p, type, null), fixed));
        } else if (fixed >= 0 && old.fixed() >= 0) {
            map.put(type, new Token(old.source(), 0, old.fixed() + fixed));
        }
        if (schedule) {
            UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> scan(id));
        }
    }

    private boolean relevant(Player p, Material type, Trigger.Source src) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null || !plugin.progress().canProgress(p)) return false;
        for (Milestone m : plugin.registry().byType(TriggerType.ACQUIRE_ITEM)) {
            Trigger t = m.trigger();
            if (d.isCompleted(m.id()) || !t.sources().contains(src) || !t.matchesMaterial(type)) continue;
            if (plugin.progress().isUnlocked(d, m)) return true;
        }
        return false;
    }

    private void scan(UUID uuid) {
        Map<Material, Token> map = tokens.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (map == null || p == null) return;
        PlayerData d = plugin.data().get(uuid);
        if (d == null) return;
        boolean claim = plugin.config().itemClaimEnabled();

        for (Map.Entry<Material, Token> e : map.entrySet()) {
            Material type = e.getKey();
            Token token = e.getValue();
            int unclaimed = countUnclaimed(p, type, null);
            int gained = token.fixed() >= 0 ? Math.min(token.fixed(), unclaimed) : unclaimed - token.baseline();
            if (gained <= 0) continue;

            Map<String, Long> adds = new HashMap<>();
            List<ItemMatcher> matchers = new ArrayList<>();
            int mark = 0;
            for (Milestone m : plugin.registry().byType(TriggerType.ACQUIRE_ITEM)) {
                Trigger t = m.trigger();
                if (d.isCompleted(m.id()) || !t.sources().contains(token.source()) || !t.matchesMaterial(type)) continue;
                if (!plugin.progress().isUnlocked(d, m)) continue;
                int eligible = gained;
                if (t.item() != null) {
                    eligible = Math.min(gained, countUnclaimed(p, type, t.item()));
                    matchers.add(t.item());
                }
                long add = Math.min(m.target() - d.progress(m.id()), eligible);
                if (add <= 0) continue;
                adds.put(m.id(), add);
                mark = (int) Math.max(mark, add);
            }
            if (adds.isEmpty()) continue;
            if (claim && mark > 0) markClaimed(p, type, mark, matchers);
            plugin.progress().handle(p, TriggerType.ACQUIRE_ITEM,
                    m -> adds.getOrDefault(m.id(), ProgressService.SKIP));
        }
    }

    // ===== 背包操作 =====

    public boolean isClaimed(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getPersistentDataContainer().has(claimKey);
    }

    private int countUnclaimed(Player p, Material type, ItemMatcher matcher) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        ItemStack[] storage = inv.getStorageContents();
        for (ItemStack st : storage) n += unclaimedAmount(st, type, matcher);
        n += unclaimedAmount(inv.getItemInOffHand(), type, matcher);
        n += unclaimedAmount(p.getItemOnCursor(), type, matcher);
        return n;
    }

    private int unclaimedAmount(ItemStack st, Material type, ItemMatcher matcher) {
        if (st == null || st.getType() != type || isClaimed(st)) return 0;
        if (matcher != null && !matcher.matches(st)) return 0;
        return st.getAmount();
    }

    /** 在 count 個未認領物品上寫入認領記號；優先標記符合物品條件的那幾疊。 */
    private void markClaimed(Player p, Material type, int count, List<ItemMatcher> matchers) {
        int left = count;
        if (!matchers.isEmpty()) left = markPass(p, type, left, matchers, true);
        if (left > 0) markPass(p, type, left, matchers, false);
    }

    private int markPass(Player p, Material type, int left, List<ItemMatcher> matchers, boolean requireMatch) {
        PlayerInventory inv = p.getInventory();
        String owner = p.getUniqueId().toString();
        // 背包格（0~35）
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack st = inv.getItem(slot);
            if (!candidate(st, type, matchers, requireMatch)) continue;
            final int s = slot;
            left = markStack(inv, st, left, owner, marked -> inv.setItem(s, marked));
        }
        // 副手
        if (left > 0) {
            ItemStack off = inv.getItemInOffHand();
            if (candidate(off, type, matchers, requireMatch)) {
                left = markStack(inv, off, left, owner, inv::setItemInOffHand);
            }
        }
        // 游標
        if (left > 0) {
            ItemStack cursor = p.getItemOnCursor();
            if (candidate(cursor, type, matchers, requireMatch)) {
                left = markStack(inv, cursor, left, owner, p::setItemOnCursor);
            }
        }
        return left;
    }

    private boolean candidate(ItemStack st, Material type, List<ItemMatcher> matchers, boolean requireMatch) {
        if (st == null || st.getType() != type || isClaimed(st)) return false;
        if (!requireMatch) return true;
        for (ItemMatcher m : matchers) if (m.matches(st)) return true;
        return false;
    }

    /**
     * 標記一疊物品。需要的數量比整疊少時拆疊：原位置保留未標記的部分，標記的部分放進空格；
     * 沒有空格時整疊都標記（會多標記幾個，但不會遺失物品）。
     */
    private int markStack(PlayerInventory inv, ItemStack st, int left, String owner,
                          java.util.function.Consumer<ItemStack> writeBack) {
        int amount = st.getAmount();
        if (amount <= left) {
            ItemStack marked = st.clone();
            marked.editPersistentDataContainer(pdc -> pdc.set(claimKey, PersistentDataType.STRING, owner));
            writeBack.accept(marked);
            return left - amount;
        }
        int empty = inv.firstEmpty();
        ItemStack marked = st.clone();
        marked.editPersistentDataContainer(pdc -> pdc.set(claimKey, PersistentDataType.STRING, owner));
        if (empty >= 0 && empty < 36) {
            marked.setAmount(left);
            ItemStack rest = st.clone();
            rest.setAmount(amount - left);
            writeBack.accept(rest);
            inv.setItem(empty, marked);
        } else {
            writeBack.accept(marked);
        }
        return 0;
    }

    public void clear() {
        tokens.clear();
    }
}
