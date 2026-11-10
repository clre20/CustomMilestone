package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import clre20.customMilestone.gui.ChatInputManager;
import clre20.customMilestone.gui.Menu;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 編輯器介面基底：每次操作前重新檢查權限（介面開著時權限可能被拔掉）。
 */
public abstract class EditorMenu extends Menu {

    public static final String PERMISSION = "custommilestone.admin.edit";

    protected EditorMenu(CustomMilestone plugin, Player viewer) {
        super(plugin, viewer);
    }

    protected boolean guard(Player p) {
        if (p.hasPermission(PERMISSION)) return true;
        plugin.messages().send(p, "no-permission");
        p.closeInventory();
        return false;
    }

    /** 包裝動作：先檢查權限。 */
    protected Consumer<Player> act(Consumer<Player> action) {
        return p -> {
            if (guard(p)) action.accept(p);
        };
    }

    /** 聊天室輸入；完成或取消後都回到 reopen 指定的介面。 */
    protected void ask(Player p, String promptKey, int maxLength, Function<String, String> validator,
                       Consumer<String> onInput, Runnable reopen, String... args) {
        ask(p, promptKey, maxLength, validator, onInput, reopen, reopen, args);
    }

    /** 聊天室輸入；完成後執行 afterInput，取消或逾時執行 onCancel。 */
    protected void ask(Player p, String promptKey, int maxLength, Function<String, String> validator,
                       Consumer<String> onInput, Runnable afterInput, Runnable onCancel, String... args) {
        plugin.chatInput().request(p, new ChatInputManager.Request(promptKey, args, maxLength, validator,
                text -> {
                    if (!p.hasPermission(PERMISSION)) {
                        plugin.messages().send(p, "no-permission");
                        return;
                    }
                    onInput.accept(text);
                    afterInput.run();
                }, onCancel));
    }
}
