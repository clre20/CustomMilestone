package clre20.customMilestone;

import clre20.customMilestone.advancement.AdvancementBridge;
import clre20.customMilestone.advancement.VanillaCatalog;
import clre20.customMilestone.command.MilestoneCommand;
import clre20.customMilestone.compatibility.DiscordSRVHook;
import clre20.customMilestone.compatibility.PlaceholderHook;
import clre20.customMilestone.config.ConfigManager;
import clre20.customMilestone.config.MessageManager;
import clre20.customMilestone.data.Database;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.data.PlayerDataManager;
import clre20.customMilestone.gui.ChatInputManager;
import clre20.customMilestone.gui.GuiListener;
import clre20.customMilestone.gui.editor.EditorService;
import clre20.customMilestone.leaderboard.LeaderboardService;
import clre20.customMilestone.listener.AdvancementListener;
import clre20.customMilestone.listener.BlockListener;
import clre20.customMilestone.listener.CombatListener;
import clre20.customMilestone.listener.CraftingListener;
import clre20.customMilestone.listener.ItemAcquireListener;
import clre20.customMilestone.listener.LifeListener;
import clre20.customMilestone.listener.PlacedBlockTracker;
import clre20.customMilestone.listener.PlayerConnectionListener;
import clre20.customMilestone.milestone.MilestoneRegistry;
import clre20.customMilestone.progress.ProgressService;
import clre20.customMilestone.reward.RewardService;
import clre20.customMilestone.task.PeriodicCheckTask;
import clre20.customMilestone.update.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomMilestone extends JavaPlugin {

    private ConfigManager config;
    private MessageManager messages;
    private Database database;
    private PlayerDataManager data;
    private MilestoneRegistry registry;
    private AdvancementBridge advancements;
    private VanillaCatalog vanilla;
    private ProgressService progress;
    private RewardService rewards;
    private LeaderboardService leaderboard;
    private PeriodicCheckTask periodic;
    private ChatInputManager chatInput;
    private EditorService editor;
    private DiscordSRVHook discord;
    private UpdateChecker updates;
    private PlayerConnectionListener connectionListener;
    private ItemAcquireListener acquireListener;
    private CraftingListener craftingListener;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("無法建立資料夾 " + getDataFolder());
        }
        config = new ConfigManager(this);
        config.reload();
        messages = new MessageManager(this);
        messages.reload();

        database = new Database(this);
        if (!database.open()) {
            getLogger().severe("資料庫無法開啟，插件停用。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        data = new PlayerDataManager(this, database);
        registry = new MilestoneRegistry(this);
        registry.load();
        vanilla = new VanillaCatalog();
        vanilla.build();
        advancements = new AdvancementBridge(this);
        advancements.sync(registry);
        getLogger().info(advancements.isActive() ? "顯示模式：原版成就" : "顯示模式：降級模式（聊天室廣播）");

        progress = new ProgressService(this);
        rewards = new RewardService(this);
        leaderboard = new LeaderboardService(this);
        periodic = new PeriodicCheckTask(this);
        chatInput = new ChatInputManager(this);
        editor = new EditorService(this);
        discord = new DiscordSRVHook(this);
        discord.hook();
        updates = new UpdateChecker(this);

        PluginManager pm = Bukkit.getPluginManager();
        connectionListener = new PlayerConnectionListener(this);
        acquireListener = new ItemAcquireListener(this);
        craftingListener = new CraftingListener(this);
        pm.registerEvents(connectionListener, this);
        pm.registerEvents(new BlockListener(this, new PlacedBlockTracker(this)), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(craftingListener, this);
        pm.registerEvents(new LifeListener(this), this);
        pm.registerEvents(new AdvancementListener(this), this);
        pm.registerEvents(acquireListener, this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(chatInput, this);

        PluginCommand cmd = getCommand("custommilestone");
        if (cmd != null) {
            MilestoneCommand executor = new MilestoneCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        data.start(config.saveIntervalSeconds());
        periodic.start();
        leaderboard.start();

        if (pm.getPlugin("PlaceholderAPI") != null) {
            placeholderHook = new PlaceholderHook(this);
            if (placeholderHook.register()) getLogger().info("已註冊 PlaceholderAPI 變數 %custommilestone_...%");
        }

        // 插件重載時已在線上的玩家
        for (Player p : Bukkit.getOnlinePlayers()) attachOnline(p);

        if (config.updateCheckOnStartup()) updates.check(null);
    }

    private void attachOnline(Player p) {
        PlayerData d = data.attach(p.getUniqueId(), p.getName(),
                loaded -> connectionListener.ready(p.getUniqueId(), loaded));
        if (d != null) connectionListener.ready(p.getUniqueId(), d);
    }

    @Override
    public void onDisable() {
        GuiListener.closeAll();
        if (chatInput != null) chatInput.clearAll();
        if (periodic != null) periodic.stop();
        if (leaderboard != null) leaderboard.stop();
        if (acquireListener != null) acquireListener.clear();
        if (craftingListener != null) craftingListener.clear();
        if (placeholderHook != null) {
            try {
                placeholderHook.unregister();
            } catch (Throwable ignored) {
                // PlaceholderAPI 可能已先關閉
            }
        }
        if (data != null) data.shutdown();
        if (database != null) database.close();
        Bukkit.getScheduler().cancelTasks(this);
    }

    /** /cm reload：重讀設定、訊息、成就定義，只重新註冊有變更的原版成就。 */
    public void reload() {
        GuiListener.closeAll();
        chatInput.clearAll();
        config.reload();
        messages.reload();
        data.flush();
        registry.load();
        vanilla.build();
        advancements.sync(registry);
        data.start(config.saveIntervalSeconds());
        periodic.start();
        leaderboard.start();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data.get(p.getUniqueId());
            if (d == null) continue;
            progress.syncAdvancements(p, d);
            progress.checkReached(p, d);
        }
    }

    public ConfigManager config() { return config; }
    public MessageManager messages() { return messages; }
    public Database database() { return database; }
    public PlayerDataManager data() { return data; }
    public MilestoneRegistry registry() { return registry; }
    public AdvancementBridge advancements() { return advancements; }
    public VanillaCatalog vanilla() { return vanilla; }
    public ProgressService progress() { return progress; }
    public RewardService rewards() { return rewards; }
    public LeaderboardService leaderboard() { return leaderboard; }
    public ChatInputManager chatInput() { return chatInput; }
    public EditorService editor() { return editor; }
    public DiscordSRVHook discord() { return discord; }
    public UpdateChecker updates() { return updates; }
}
