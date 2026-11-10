package clre20.customMilestone.command;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.data.PlayerData;
import clre20.customMilestone.gui.LeaderboardMenu;
import clre20.customMilestone.gui.MainMenu;
import clre20.customMilestone.gui.editor.EditorHomeMenu;
import clre20.customMilestone.milestone.Milestone;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * /custommilestone（別名 /cm）。每個子指令都檢查自己的權限節點。
 */
public final class MilestoneCommand implements TabExecutor {

    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_.*\\-]{1,20}$");
    private static final long MAX_PROGRESS = 1_000_000_000_000L;

    private record Sub(String name, String permission) { }

    private static final List<Sub> SUBS = List.of(
            new Sub("help", "custommilestone.help"),
            new Sub("top", "custommilestone.top"),
            new Sub("view", "custommilestone.view.others"),
            new Sub("edit", "custommilestone.admin.edit"),
            new Sub("grant", "custommilestone.admin.grant"),
            new Sub("revoke", "custommilestone.admin.grant"),
            new Sub("progress", "custommilestone.admin.progress"),
            new Sub("reset", "custommilestone.admin.reset"),
            new Sub("reload", "custommilestone.admin.reload"),
            new Sub("version", "custommilestone.admin.version"));

    private final CustomMilestone plugin;

    public MilestoneCommand(CustomMilestone plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            openMenu(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Sub def = SUBS.stream().filter(s -> s.name().equals(sub)).findFirst().orElse(null);
        if (def == null) {
            plugin.messages().send(sender, "command.unknown", "label", label);
            return true;
        }
        if (!sender.hasPermission(def.permission())) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "help" -> help(sender, label);
            case "top" -> {
                if (sender instanceof Player p) new LeaderboardMenu(plugin, p, false).open();
                else plugin.messages().send(sender, "command.player-only");
            }
            case "view" -> view(sender, args, label);
            case "edit" -> {
                if (sender instanceof Player p) new EditorHomeMenu(plugin, p, 0).open();
                else plugin.messages().send(sender, "command.player-only");
            }
            case "grant" -> grant(sender, args, label, true);
            case "revoke" -> grant(sender, args, label, false);
            case "progress" -> progress(sender, args, label);
            case "reset" -> reset(sender, args, label);
            case "reload" -> {
                plugin.reload();
                plugin.messages().send(sender, "command.reloaded",
                        "categories", String.valueOf(plugin.registry().categories().size()),
                        "milestones", String.valueOf(plugin.registry().size()),
                        "mode", plugin.messages().raw(plugin.advancements().isActive() ? "mode.vanilla" : "mode.fallback"));
                int pending = plugin.advancements().pendingRestart();
                if (pending > 0) {
                    plugin.messages().send(sender, "editor.restart-needed", "count", String.valueOf(pending));
                }
            }
            case "version" -> {
                plugin.messages().send(sender, "update.current", "current", plugin.updates().current());
                plugin.updates().check(sender);
            }
            default -> { }
        }
        return true;
    }

    private void openMenu(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        if (!p.hasPermission("custommilestone.use")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) {
            plugin.messages().send(p, "command.data-loading");
            return;
        }
        new MainMenu(plugin, p, d, 0).open();
    }

    private void help(CommandSender sender, String label) {
        for (String line : plugin.messages().list("command.help", "label", label)) {
            plugin.messages().sendRaw(sender, line);
        }
    }

    // ===== 目標玩家（線上或離線） =====

    private void withTarget(CommandSender sender, String name, BiConsumer<PlayerData, Player> action) {
        if (!PLAYER_NAME.matcher(name).matches()) {
            plugin.messages().send(sender, "command.player-not-found", "player", name);
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerData d = plugin.data().get(online.getUniqueId());
            if (d == null) plugin.messages().send(sender, "command.data-loading");
            else action.accept(d, online);
            return;
        }
        plugin.data().findUuid(name, uuid -> {
            if (uuid == null) {
                plugin.messages().send(sender, "command.player-not-found", "player", name);
                return;
            }
            loadAndRun(sender, uuid, name, action);
        });
    }

    private void loadAndRun(CommandSender sender, UUID uuid, String name, BiConsumer<PlayerData, Player> action) {
        plugin.data().loadAsync(uuid, name, d -> {
            Player now = Bukkit.getPlayer(uuid);
            if (now != null) {
                PlayerData live = plugin.data().get(uuid);
                if (live == null) {
                    plugin.messages().send(sender, "command.data-loading");
                    return;
                }
                action.accept(live, now);
            } else {
                action.accept(d, null);
            }
        });
    }

    private void view(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "command.usage.view", "label", label);
            return;
        }
        withTarget(sender, args[1], (d, online) -> {
            if (p.isOnline()) new MainMenu(plugin, p, d, 0).open();
        });
    }

    private Milestone milestoneArg(CommandSender sender, String id) {
        Milestone m = plugin.registry().get(id);
        if (m == null) plugin.messages().send(sender, "command.milestone-not-found", "id", id);
        return m;
    }

    private void grant(CommandSender sender, String[] args, String label, boolean grant) {
        if (args.length < 3) {
            plugin.messages().send(sender, grant ? "command.usage.grant" : "command.usage.revoke", "label", label);
            return;
        }
        Milestone m = milestoneArg(sender, args[2]);
        if (m == null) return;
        withTarget(sender, args[1], (d, online) -> {
            Milestone cur = plugin.registry().get(m.id());
            if (cur == null) return;
            if (grant) {
                if (d.isCompleted(cur.id())) {
                    plugin.messages().send(sender, "command.already-done", "player", d.name(), "name", cur.name());
                    return;
                }
                plugin.progress().complete(online, d, cur);
                plugin.messages().send(sender, "command.granted", "player", d.name(), "name", cur.name());
            } else {
                if (!d.isCompleted(cur.id()) && d.progress(cur.id()) == 0) {
                    plugin.messages().send(sender, "command.not-done", "player", d.name(), "name", cur.name());
                    return;
                }
                plugin.progress().revoke(online, d, cur);
                plugin.messages().send(sender, "command.revoked", "player", d.name(), "name", cur.name());
            }
            if (online == null) plugin.data().saveNow(d);
        });
    }

    private void progress(CommandSender sender, String[] args, String label) {
        if (args.length < 5) {
            plugin.messages().send(sender, "command.usage.progress", "label", label);
            return;
        }
        Milestone m = milestoneArg(sender, args[2]);
        if (m == null) return;
        String mode = args[3].toLowerCase(Locale.ROOT);
        if (!mode.equals("add") && !mode.equals("set")) {
            plugin.messages().send(sender, "command.usage.progress", "label", label);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[4]);
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "command.bad-number", "value", args[4]);
            return;
        }
        if (amount < 0 || amount > MAX_PROGRESS) {
            plugin.messages().send(sender, "command.bad-number", "value", args[4]);
            return;
        }
        long finalAmount = amount;
        withTarget(sender, args[1], (d, online) -> {
            Milestone cur = plugin.registry().get(m.id());
            if (cur == null) return;
            if (d.isCompleted(cur.id())) {
                plugin.messages().send(sender, "command.already-done", "player", d.name(), "name", cur.name());
                return;
            }
            long value = mode.equals("add") ? Math.min(MAX_PROGRESS, d.progress(cur.id()) + finalAmount) : finalAmount;
            plugin.progress().adminSetProgress(online, d, cur, value);
            plugin.messages().send(sender, "command.progress-set", "player", d.name(), "name", cur.name(),
                    "current", String.valueOf(d.isCompleted(cur.id()) ? cur.target() : d.progress(cur.id())),
                    "target", String.valueOf(cur.target()));
            if (online == null) plugin.data().saveNow(d);
        });
    }

    private void reset(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            plugin.messages().send(sender, "command.usage.reset", "label", label);
            return;
        }
        if (args.length >= 3) {
            Milestone m = milestoneArg(sender, args[2]);
            if (m == null) return;
            withTarget(sender, args[1], (d, online) -> {
                plugin.progress().revoke(online, d, m);
                plugin.messages().send(sender, "command.reset-one", "player", d.name(), "name", m.name());
                if (online == null) plugin.data().saveNow(d);
            });
            return;
        }
        withTarget(sender, args[1], (d, online) -> {
            plugin.progress().resetAll(online, d);
            plugin.messages().send(sender, "command.reset-all", "player", d.name());
            if (online == null) plugin.data().saveNow(d);
        });
    }

    // ===== Tab 補全 =====

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Sub s : SUBS) if (sender.hasPermission(s.permission())) out.add(s.name());
            return filter(out, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Sub def = SUBS.stream().filter(s -> s.name().equals(sub)).findFirst().orElse(null);
        if (def == null || !sender.hasPermission(def.permission())) return out;
        switch (sub) {
            case "view" -> {
                if (args.length == 2) out.addAll(onlineNames());
            }
            case "grant", "revoke", "reset", "progress" -> {
                if (args.length == 2) out.addAll(onlineNames());
                else if (args.length == 3) {
                    for (Milestone m : plugin.registry().all()) out.add(m.id());
                } else if (sub.equals("progress") && args.length == 4) {
                    out.add("add");
                    out.add("set");
                } else if (sub.equals("progress") && args.length == 5) {
                    Milestone m = plugin.registry().get(args[2]);
                    out.add("1");
                    if (m != null) out.add(String.valueOf(m.target()));
                }
            }
            default -> { }
        }
        List<String> filtered = filter(out, args[args.length - 1]);
        return filtered.size() > 100 ? filtered.subList(0, 100) : filtered;
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    private static List<String> filter(List<String> list, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
