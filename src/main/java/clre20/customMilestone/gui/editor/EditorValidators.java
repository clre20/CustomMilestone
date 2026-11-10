package clre20.customMilestone.gui.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * 編輯器聊天輸入的格式驗證。回傳 null 代表通過，否則回傳 messages.yml 的錯誤訊息鍵。
 */
public final class EditorValidators {

    public static final int MAX_DESCRIPTION_LINES = 8;
    public static final long MAX_AMOUNT = 1_000_000_000_000L;
    public static final int MAX_EXP = 1_000_000;

    private EditorValidators() {
    }

    /** 說明以 | 分行。 */
    public static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        for (String s : text.split("\\|")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static String description(String text) {
        List<String> lines = splitLines(text);
        if (lines.isEmpty() || lines.size() > MAX_DESCRIPTION_LINES) return "editor.error.description";
        return null;
    }

    public static String integer(String text) {
        try {
            int v = Integer.parseInt(text.trim());
            return Math.abs(v) <= 100000 ? null : "editor.error.number";
        } catch (NumberFormatException e) {
            return "editor.error.number";
        }
    }

    public static String amount(String text) {
        try {
            long v = Long.parseLong(text.trim());
            return v >= 1 && v <= MAX_AMOUNT ? null : "editor.error.number";
        } catch (NumberFormatException e) {
            return "editor.error.number";
        }
    }

    public static String exp(String text) {
        try {
            int v = Integer.parseInt(text.trim());
            return v >= 0 && v <= MAX_EXP ? null : "editor.error.number";
        } catch (NumberFormatException e) {
            return "editor.error.number";
        }
    }

    /** 獎勵指令：不允許換行（已在輸入時移除控制字元），也不允許空白指令。 */
    public static String command(String text) {
        String t = text.startsWith("/") ? text.substring(1) : text;
        return t.isBlank() ? "editor.error.command" : null;
    }
}
