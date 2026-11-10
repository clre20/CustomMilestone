package clre20.customMilestone.milestone;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 成就獎勵：物品（元件字串或完整序列化）、後台指令、經驗值。
 */
public final class Reward {

    /** 「diamond_sword[enchantments={sharpness:5}] x2」的數量後綴。 */
    private static final Pattern AMOUNT_SUFFIX = Pattern.compile("^(.*?)\\s+x(\\d{1,4})$");
    public static final int MAX_COMMANDS = 20;
    public static final int MAX_ITEMS = 27;

    private final List<String> items;        // 元件字串
    private final List<String> storedItems;  // Base64(ItemStack#serializeAsBytes)
    private final List<String> commands;
    private final int exp;
    private record Entry(ItemStack base, int amount) { }

    private final List<Entry> parsed = new ArrayList<>();

    public Reward(List<String> items, List<String> storedItems, List<String> commands, int exp,
                  Consumer<String> warn) {
        this.items = new ArrayList<>(items);
        this.storedItems = new ArrayList<>(storedItems);
        this.commands = new ArrayList<>(commands);
        this.exp = Math.max(0, exp);
        for (String s : this.items) {
            int amount = amountOf(s);
            ItemStack st = parseItem(s);
            if (st == null) warn.accept("無法解析獎勵物品：" + s);
            else parsed.add(new Entry(st, amount));
        }
        for (String b : this.storedItems) {
            ItemStack st = decode(b);
            if (st == null) warn.accept("無法還原編輯器設定的獎勵物品（資料損毀或版本不相容）");
            else parsed.add(new Entry(st, st.getAmount()));
        }
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

    /** 「 x數量」後綴的數量（1 ~ 6400），沒有後綴為 1。 */
    public static int amountOf(String input) {
        if (input == null) return 1;
        Matcher m = AMOUNT_SUFFIX.matcher(input.trim());
        return m.matches() ? Math.max(1, Math.min(6400, Integer.parseInt(m.group(2)))) : 1;
    }

    /** 解析元件字串（不含數量，回傳 1 個）。 */
    public static ItemStack parseItem(String input) {
        if (input == null || input.isBlank()) return null;
        String s = input.trim();
        Matcher m = AMOUNT_SUFFIX.matcher(s);
        if (m.matches()) s = m.group(1).trim();
        try {
            ItemStack stack = Bukkit.getItemFactory().createItemStack(s);
            if (stack.getType().isAir()) return null;
            stack.setAmount(1);
            return stack;
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    /** 取得要發放的物品（每次回傳新的複本；超過堆疊上限的已拆成多疊）。 */
    public List<ItemStack> buildItems() {
        List<ItemStack> out = new ArrayList<>(parsed.size());
        for (Entry e : parsed) {
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

    public boolean isEmpty() {
        return parsed.isEmpty() && commands.isEmpty() && exp <= 0;
    }

    public List<String> items() { return items; }
    public List<String> storedItems() { return storedItems; }
    public List<String> commands() { return commands; }
    public int exp() { return exp; }

    // ===== 編輯器用：回傳新的 Reward =====

    public Reward withExp(int newExp) {
        return new Reward(items, storedItems, commands, newExp, s -> { });
    }

    public Reward withStoredItem(ItemStack stack) {
        List<String> list = new ArrayList<>(storedItems);
        list.add(encode(stack));
        return new Reward(items, list, commands, exp, s -> { });
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

    public int itemCount() {
        return items.size() + storedItems.size();
    }
}
