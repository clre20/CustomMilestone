package clre20.customMilestone.milestone;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 成就獎勵：物品（元件字串或完整序列化）、後台指令、經驗值。
 * <p>
 * stored-items 格式為「數量|Base64」，Base64 內的物品固定 1 個，數量另外記錄（可超過堆疊上限，最多 6400）。
 * 舊格式（只有 Base64）仍可讀取。
 */
public final class Reward {

    /** 「diamond_sword[enchantments={sharpness:5}] x2」的數量後綴。 */
    private static final Pattern AMOUNT_SUFFIX = Pattern.compile("^(.*?)\\s+x(\\d{1,4})$");
    public static final int MAX_COMMANDS = 20;
    public static final int MAX_ITEMS = 27;
    public static final int MAX_AMOUNT = 6400;

    /** 介面顯示用的一項物品獎勵（base 為 null 代表無法解析）。 */
    public record Entry(ItemStack base, int amount, boolean stored) { }

    private final List<String> items;        // 元件字串（可帶「 x數量」）
    private final List<String> storedItems;  // 數量|Base64
    private final List<String> commands;
    private final int exp;
    private final List<Entry> entries = new ArrayList<>();

    public Reward(List<String> items, List<String> storedItems, List<String> commands, int exp,
                  Consumer<String> warn) {
        this.items = new ArrayList<>(items);
        this.storedItems = new ArrayList<>(storedItems);
        this.commands = new ArrayList<>(commands);
        this.exp = Math.max(0, exp);
        for (String s : this.items) {
            ItemStack st = parseItem(s);
            if (st == null) warn.accept("無法解析獎勵物品：" + s);
            entries.add(new Entry(st, amountOf(s), false));
        }
        // 編輯器加入的物品：相同物品合併數量（也會整理舊版本分開存的重複物品），並統一成「數量|Base64」格式
        List<String> normalized = new ArrayList<>();
        List<Entry> stored = new ArrayList<>();
        for (String b : this.storedItems) {
            ItemStack st = decodeStored(b);
            if (st == null) {
                warn.accept("無法還原編輯器設定的獎勵物品（資料損毀或版本不相容）");
                stored.add(new Entry(null, 1, true));
                normalized.add(b);
                continue;
            }
            int amount = storedAmount(b, st);
            int merged = -1;
            for (int i = 0; i < stored.size(); i++) {
                Entry e = stored.get(i);
                if (e.base() != null && e.base().isSimilar(st)) {
                    merged = i;
                    break;
                }
            }
            if (merged >= 0) {
                int total = clampAmount(stored.get(merged).amount() + amount);
                stored.set(merged, new Entry(stored.get(merged).base(), total, true));
                normalized.set(merged, encodeStored(st, total));
            } else {
                stored.add(new Entry(st, amount, true));
                normalized.add(b.indexOf('|') > 0 ? b : encodeStored(st, amount));
            }
        }
        this.storedItems.clear();
        this.storedItems.addAll(normalized);
        entries.addAll(stored);
    }

    public static Reward empty() {
        return new Reward(List.of(), List.of(), List.of(), 0, s -> { });
    }

    public static Reward fromSection(ConfigurationSection s, Consumer<String> warn) {
        if (s == null) return empty();
        return new Reward(s.getStringList("items"), s.getStringList("stored-items"),
                s.getStringList("commands"), s.getInt("exp", 0), warn);
    }

    public void write(ConfigurationSection s) {
        s.set("exp", exp);
        s.set("items", new ArrayList<>(items));
        s.set("stored-items", new ArrayList<>(storedItems));
        s.set("commands", new ArrayList<>(commands));
    }

    // ===== 解析 =====

    /** 「 x數量」後綴的數量（1 ~ 6400），沒有後綴為 1。 */
    public static int amountOf(String input) {
        if (input == null) return 1;
        Matcher m = AMOUNT_SUFFIX.matcher(input.trim());
        return m.matches() ? clampAmount(Integer.parseInt(m.group(2))) : 1;
    }

    private static String stripAmount(String input) {
        String s = input.trim();
        Matcher m = AMOUNT_SUFFIX.matcher(s);
        return m.matches() ? m.group(1).trim() : s;
    }

    public static int clampAmount(int v) {
        return Math.max(1, Math.min(MAX_AMOUNT, v));
    }

    /** 解析元件字串（不含數量，回傳 1 個）。 */
    public static ItemStack parseItem(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            ItemStack stack = Bukkit.getItemFactory().createItemStack(stripAmount(input));
            if (stack.getType().isAir()) return null;
            stack.setAmount(1);
            return stack;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ItemStack decodeStored(String raw) {
        int bar = raw.indexOf('|');
        ItemStack st = decode(bar >= 0 ? raw.substring(bar + 1) : raw);
        if (st == null) return null;
        st = st.clone();
        st.setAmount(1);
        return st;
    }

    private static int storedAmount(String raw, ItemStack decoded) {
        int bar = raw.indexOf('|');
        if (bar > 0) {
            try {
                return clampAmount(Integer.parseInt(raw.substring(0, bar).trim()));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        // 舊格式：數量存在物品本身
        ItemStack old = decode(raw);
        return old == null ? 1 : clampAmount(old.getAmount());
    }

    public static ItemStack decode(String base64) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String encode(ItemStack stack) {
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    private static String encodeStored(ItemStack single, int amount) {
        ItemStack one = single.clone();
        one.setAmount(1);
        return clampAmount(amount) + "|" + encode(one);
    }

    // ===== 查詢 =====

    /** 取得要發放的物品（每次回傳新的複本；超過堆疊上限的已拆成多疊）。 */
    public List<ItemStack> buildItems() {
        List<ItemStack> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (e.base() == null) continue;
            int max = Math.max(1, e.base().getMaxStackSize());
            int left = e.amount();
            while (left > 0) {
                ItemStack part = e.base().clone();
                part.setAmount(Math.min(max, left));
                left -= part.getAmount();
                out.add(part);
            }
        }
        return out;
    }

    /** 介面顯示順序：先 YAML 元件字串，再編輯器加入的物品。 */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return buildItems().isEmpty() && commands.isEmpty() && exp <= 0;
    }

    public List<String> items() { return items; }
    public List<String> storedItems() { return storedItems; }
    public List<String> commands() { return commands; }
    public int exp() { return exp; }

    public int itemCount() {
        return items.size() + storedItems.size();
    }

    // ===== 編輯器用：回傳新的 Reward =====

    public Reward withExp(int newExp) {
        return new Reward(items, storedItems, commands, newExp, s -> { });
    }

    /**
     * 加入物品（數量取自這疊物品）。和已有的獎勵物品完全相同（不計數量）時合併數量。
     * 回傳 null 代表種類數已達上限。
     */
    public Reward withStoredItem(ItemStack stack) {
        List<String> list = new ArrayList<>(storedItems);
        int offset = items.size();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.base() != null && e.base().isSimilar(stack)) {
                int amount = clampAmount(e.amount() + stack.getAmount());
                if (e.stored()) {
                    list.set(i - offset, encodeStored(e.base(), amount));
                    return new Reward(items, list, commands, exp, s -> { });
                }
                return withItemAmount(i, amount);
            }
        }
        if (itemCount() >= MAX_ITEMS) return null;
        list.add(encodeStored(stack, stack.getAmount()));
        return new Reward(items, list, commands, exp, s -> { });
    }

    /** 設定第 index 項物品的數量。 */
    public Reward withItemAmount(int index, int amount) {
        int a = clampAmount(amount);
        List<String> it = new ArrayList<>(items);
        List<String> st = new ArrayList<>(storedItems);
        if (index >= 0 && index < it.size()) {
            String base = stripAmount(it.get(index));
            it.set(index, a == 1 ? base : base + " x" + a);
        } else {
            int j = index - it.size();
            if (j < 0 || j >= st.size()) return this;
            ItemStack base = entries.get(index).base();
            if (base == null) return this;
            st.set(j, encodeStored(base, a));
        }
        return new Reward(it, st, commands, exp, s -> { });
    }

    public Reward withCommand(String cmd) {
        List<String> list = new ArrayList<>(commands);
        list.add(cmd);
        return new Reward(items, storedItems, list, exp, s -> { });
    }

    /** 依顯示順序（先元件字串、再編輯器物品）移除第 index 個物品。 */
    public Reward withoutItem(int index) {
        List<String> a = new ArrayList<>(items);
        List<String> b = new ArrayList<>(storedItems);
        if (index < a.size()) a.remove(index);
        else if (index - a.size() < b.size()) b.remove(index - a.size());
        return new Reward(a, b, commands, exp, s -> { });
    }

    public Reward withoutCommand(int index) {
        List<String> list = new ArrayList<>(commands);
        if (index >= 0 && index < list.size()) list.remove(index);
        return new Reward(items, storedItems, list, exp, s -> { });
    }
}
