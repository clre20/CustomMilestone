package clre20.customMilestone.config;

import clre20.customMilestone.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * messages.yml：所有顯示文字。reload 時把字串讀進記憶體，之後只查快取。
 */
public final class MessageManager {

    private final JavaPlugin plugin;
    private final Map<String, String> strings = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private String prefix = "";

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration c = YamlFiles.loadAndFill(plugin, "messages.yml");
        strings.clear();
        lists.clear();
        for (String key : c.getKeys(true)) {
            if (c.isConfigurationSection(key)) {
                continue;
            }
            if (c.isList(key)) {
                lists.put(key, List.copyOf(c.getStringList(key)));
            } else {
                String v = c.getString(key);
                if (v != null) {
                    strings.put(key, v);
                }
            }
        }
        prefix = strings.getOrDefault("prefix", "");
    }

    /** 取得原始字串（含 &amp; 色碼），找不到時回傳路徑本身，避免 NPE。 */
    public String raw(String path) {
        String v = strings.get(path);
        if (v == null) {
            List<String> l = lists.get(path);
            return l != null ? String.join("\n", l) : path;
        }
        return v;
    }

    public String raw(String path, String... kv) {
        return Text.replace(raw(path), map(kv));
    }

    public List<String> list(String path, String... kv) {
        List<String> l = lists.get(path);
        if (l == null) {
            String s = strings.get(path);
            l = s == null ? List.of() : List.of(s);
        }
        if (kv.length == 0) {
            return l;
        }
        Map<String, String> m = map(kv);
        return l.stream().map(line -> Text.replace(line, m)).toList();
    }

    public Component get(String path, String... kv) {
        return Text.color(raw(path, kv));
    }

    public void send(CommandSender sender, String path, String... kv) {
        String text = raw(path, kv);
        if (text.isEmpty()) {
            return;
        }
        sender.sendMessage(Text.color(prefix + text));
    }

    public void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(Text.color(prefix + text));
    }

    public static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
