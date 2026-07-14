package com.aermini.dmt.util;

import com.aermini.dmt.DrawMyThing;
import com.aermini.dmt.nms.NMSHandler;
import com.aermini.dmt.nms.NMSHandlerFactory;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

public class ChatUtil {

    private static DrawMyThing plugin;

    public static void init(DrawMyThing instance) {
        plugin = instance;
    }

    public static String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String getPrefix() {
        if (plugin != null) {
            return colorize(plugin.getMsgConfig().getString("prefix", "&b[DrawMyThing] &f"));
        }
        return ChatColor.BLUE + "[DrawMyThing] " + ChatColor.WHITE;
    }

    public static void send(Player player, String message) {
        String colored = colorize(message);
        if (colored.contains("\n")) {
            String[] lines = colored.split("\n");
            player.sendMessage(getPrefix() + lines[0]);
            for (int i = 1; i < lines.length; i++) {
                player.sendMessage(lines[i]);
            }
        } else {
            player.sendMessage(getPrefix() + colored);
        }
    }

    public static String getMessage(String key, String... replacements) {
        if (plugin == null) return key;
        Object raw = plugin.getMsgConfig().get(key);
        String msg;
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(list.get(i).toString());
            }
            msg = colorize(sb.toString());
        } else {
            msg = colorize(raw != null ? raw.toString() : key);
        }
        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public static void sendMsg(Player player, String key, String... replacements) {
        String msg = getMessage(key, replacements);
        if (msg.contains("\n")) {
            String[] lines = msg.split("\n");
            player.sendMessage(getPrefix() + lines[0]);
            for (int i = 1; i < lines.length; i++) {
                player.sendMessage(lines[i]);
            }
        } else {
            player.sendMessage(getPrefix() + msg);
        }
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        NMSHandler nms = NMSHandlerFactory.getHandler();
        if (nms != null) {
            nms.sendTitle(player, colorize(title), colorize(subtitle), fadeIn, stay, fadeOut);
        } else {
            if (title != null && !title.isEmpty()) player.sendMessage(colorize(title));
            if (subtitle != null && !subtitle.isEmpty()) player.sendMessage(colorize(subtitle));
        }
    }

    public static void sendActionBar(Player player, String message) {
        NMSHandler nms = NMSHandlerFactory.getHandler();
        if (nms != null) {
            nms.sendActionBar(player, colorize(message));
        } else {
            player.sendMessage(colorize(message));
        }
    }
}