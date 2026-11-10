package clre20.customMilestone.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * 讀取 YAML，並以 jar 內的預設檔自動補齊缺少的項目（保留用戶原本的值與註解）。
 */
public final class YamlFiles {

    private YamlFiles() {
    }

    public static YamlConfiguration loadDefaults(JavaPlugin plugin, String resource) {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.options().parseComments(true);
        try (InputStream in = plugin.getResource(resource)) {
            if (in != null) {
                defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "無法讀取內建的 " + resource, e);
        }
        return defaults;
    }

    /**
     * 載入檔案並補齊缺少的項目。檔案格式錯誤時，會先備份壞掉的檔案再用預設值重建，避免插件無法啟動。
     */
    public static YamlConfiguration loadAndFill(JavaPlugin plugin, String resource) {
        File file = new File(plugin.getDataFolder(), resource);
        YamlConfiguration defaults = loadDefaults(plugin, resource);
        if (!file.exists()) {
            plugin.saveResource(resource, false);
        }

        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, resource + " 格式錯誤，已備份為 " + resource + ".broken 並改用預設值：" + e.getMessage());
            try {
                Files.copy(file.toPath(), new File(plugin.getDataFolder(), resource + ".broken").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // 備份失敗也繼續用預設值
            }
            plugin.saveResource(resource, true);
            config = new YamlConfiguration();
            config.options().parseComments(true);
            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException ex) {
                return defaults;
            }
        }

        int added = fill(defaults, config, "");
        if (added > 0) {
            try {
                config.save(file);
                plugin.getLogger().info(resource + " 已自動補齊 " + added + " 個缺少的項目。");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "無法寫回 " + resource, e);
            }
        }
        config.setDefaults(defaults);
        return config;
    }

    private static int fill(ConfigurationSection defaults, ConfigurationSection target, String prefix) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object defValue = defaults.get(key);
            if (defValue instanceof ConfigurationSection defSection) {
                ConfigurationSection section = target.getConfigurationSection(key);
                if (section == null) {
                    if (target.contains(key)) {
                        // 型別不符（用戶把區段改成單一值）就不動它
                        continue;
                    }
                    section = target.createSection(key);
                    copyComments(defaults, target, key);
                }
                added += fill(defSection, section, path);
            } else if (!target.contains(key)) {
                target.set(key, defValue);
                copyComments(defaults, target, key);
                added++;
            }
        }
        return added;
    }

    private static void copyComments(ConfigurationSection from, ConfigurationSection to, String key) {
        try {
            to.setComments(key, from.getComments(key));
            to.setInlineComments(key, from.getInlineComments(key));
        } catch (Throwable ignored) {
            // 舊版 API 沒有註解功能時忽略
        }
    }
}
