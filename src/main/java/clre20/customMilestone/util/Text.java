package clre20.customMilestone.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文字工具：支援 &amp; 色碼與 &amp;#RRGGBB 十六進位色碼。
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /** 轉換 &amp; 色碼文字為 Component。 */
    public static Component color(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(text);
    }

    /** 物品名稱與 Lore 用：預設取消斜體。 */
    public static Component item(String text) {
        return color(text).decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                .colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.WHITE);
    }

    public static List<Component> itemLines(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line));
        }
        return out;
    }

    /** 替換 %key% 變數。 */
    public static String replace(String text, Map<String, String> placeholders) {
        if (text == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty() || text.indexOf('%') < 0) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** 移除色碼，得到純文字。 */
    public static String plain(String text) {
        return PlainTextComponentSerializer.plainText().serialize(color(text));
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** 進度條。 */
    public static String progressBar(long current, long target, int length, String done, String todo) {
        if (target <= 0) {
            target = 1;
        }
        int filled = (int) Math.min(length, Math.max(0, (current * length) / target));
        return done + "|".repeat(filled) + todo + "|".repeat(length - filled);
    }
}
