package clre20.customMilestone.compatibility;

import clre20.customMilestone.CustomMilestone;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * DiscordSRV 串接（反射，不需要編譯依賴）。
 * 原版成就模式下 DiscordSRV 會自己監聽成就事件，這裡只在降級模式時把達成訊息送到主頻道。
 */
public final class DiscordSRVHook {

    private final CustomMilestone plugin;
    private Object mainChannelGetterTarget;
    private Method getMainTextChannel;
    private Method sendMessage;
    private boolean available;

    public DiscordSRVHook(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        available = false;
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") == null) return;
        try {
            Class<?> main = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            mainChannelGetterTarget = main.getMethod("getPlugin").invoke(null);
            getMainTextChannel = main.getMethod("getMainTextChannel");
            Class<?> util = Class.forName("github.scarsz.discordsrv.util.DiscordUtil");
            Class<?> textChannel = Class.forName("github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel");
            sendMessage = util.getMethod("sendMessage", textChannel, String.class);
            available = true;
            plugin.getLogger().info("已偵測到 DiscordSRV。");
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().info("偵測到 DiscordSRV，但版本不相容，降級模式時不會直接同步：" + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** 降級模式的達成訊息（主執行緒呼叫；DiscordSRV 內部會非同步送出）。 */
    public void sendFallback(String plainText) {
        if (!available || !plugin.config().discordFallbackSync() || plugin.advancements().isActive()) return;
        try {
            Object channel = getMainTextChannel.invoke(mainChannelGetterTarget);
            if (channel == null) return;
            // 避免成就名稱觸發 @everyone / @here 提及
            sendMessage.invoke(null, channel, plainText.replace("@", "@​"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "DiscordSRV 傳送失敗", e);
        }
    }
}
