package clre20.customMilestone.gui;

import clre20.customMilestone.CustomMilestone;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 編輯器的聊天室輸入：有逾時、取消關鍵字、長度與格式驗證。
 * 聊天事件在非同步執行緒，收到後切回主執行緒處理。
 */
public final class ChatInputManager implements Listener {

    /** validator 回傳 null 代表通過，否則回傳 messages.yml 的錯誤訊息鍵。 */
    public record Request(String promptKey, String[] promptArgs, int maxLength,
                          Function<String, String> validator, Consumer<String> onInput, Runnable onCancel) { }

    private record Session(Request request, BukkitTask timeout) { }

    private final CustomMilestone plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChatInputManager(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    public void request(Player p, Request req) {
        cancel(p.getUniqueId(), false);
        p.closeInventory();
        plugin.messages().send(p, req.promptKey(), req.promptArgs());
        plugin.messages().send(p, "input.hint",
                "keyword", plugin.config().cancelKeywords().getFirst(),
                "seconds", String.valueOf(plugin.config().chatInputTimeoutSeconds()),
                "max", String.valueOf(req.maxLength()));
        start(p.getUniqueId(), req);
    }

    private void start(UUID id, Request req) {
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session s = sessions.remove(id);
            if (s == null) return;
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.messages().send(p, "input.timeout");
            if (s.request().onCancel() != null && p != null) s.request().onCancel().run();
        }, plugin.config().chatInputTimeoutSeconds() * 20L);
        sessions.put(id, new Session(req, t));
    }

    public boolean isWaiting(UUID id) {
        return sessions.containsKey(id);
    }

    /** 取消輸入（離線、重載時）。runCallback = true 會呼叫 onCancel。 */
    public void cancel(UUID id, boolean runCallback) {
        Session s = sessions.remove(id);
        if (s == null) return;
        s.timeout().cancel();
        if (runCallback && s.request().onCancel() != null) s.request().onCancel().run();
    }

    public void clearAll() {
        for (Session s : sessions.values()) s.timeout().cancel();
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!sessions.containsKey(id)) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> handle(id, text));
    }

    private void handle(UUID id, String raw) {
        Session s = sessions.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (s == null || p == null) return;
        s.timeout().cancel();
        Request req = s.request();
        // 移除 § 與控制字元，只保留 & 色碼
        String text = raw.replace('§', '&').replaceAll("\\p{Cntrl}", "").trim();

        if (plugin.config().cancelKeywords().contains(text.toLowerCase(Locale.ROOT))) {
            plugin.messages().send(p, "input.cancelled");
            if (req.onCancel() != null) req.onCancel().run();
            return;
        }
        if (text.isEmpty() || text.length() > req.maxLength()) {
            plugin.messages().send(p, "input.invalid-length", "max", String.valueOf(req.maxLength()));
            start(id, req);
            return;
        }
        String error = req.validator() == null ? null : req.validator().apply(text);
        if (error != null) {
            plugin.messages().send(p, error);
            start(id, req);
            return;
        }
        req.onInput().accept(text);
    }
}
