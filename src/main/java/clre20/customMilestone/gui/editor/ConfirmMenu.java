package clre20.customMilestone.gui.editor;

import clre20.customMilestone.CustomMilestone;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * 確認介面（刪除等無法復原的操作）。
 */
public final class ConfirmMenu extends EditorMenu {

    private final String question;
    private final Runnable onYes;
    private final Runnable onNo;

    public ConfirmMenu(CustomMilestone plugin, Player viewer, String question, Runnable onYes, Runnable onNo) {
        super(plugin, viewer);
        this.question = question;
        this.onYes = onYes;
        this.onNo = onNo;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String title() {
        return plugin.messages().raw("gui.confirm.title");
    }

    @Override
    protected void draw() {
        fillBorder(0, 26);
        set(13, button(Material.PAPER, "gui.confirm.question", null, "question", question));
        set(11, button(Material.LIME_CONCRETE, "gui.confirm.accept", "gui.confirm.accept-lore"), act(p -> onYes.run()));
        set(15, button(Material.RED_CONCRETE, "gui.confirm.deny", null), p -> onNo.run());
    }
}
