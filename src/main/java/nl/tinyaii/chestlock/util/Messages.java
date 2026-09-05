package nl.tinyaii.chestlock.util;

import org.bukkit.ChatColor;

/**
 * 消息工具：颜色转义。
 */
public final class Messages {

    private Messages() {}

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static String prefix() {
        return color("&7[&c锁&7] &r");
    }
}
