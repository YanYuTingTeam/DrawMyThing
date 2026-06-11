package com.aermini.dmt;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
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

    private static String nmsVersion = null;

    private static String getNmsVersion() {
        if (nmsVersion == null) {
            String name = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            nmsVersion = name.substring(name.lastIndexOf('.') + 1);
        }
        return nmsVersion;
    }

    private static Class<?> getNMSClass(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + getNmsVersion() + "." + name);
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Class<?> packetTitleClass = getNMSClass("PacketPlayOutTitle");
            Class<?> enumTitleAction = getNMSClass("PacketPlayOutTitle$EnumTitleAction");
            Class<?> chatSerializer = getNMSClass("IChatBaseComponent$ChatSerializer");
            Class<?> chatComponent = getNMSClass("IChatBaseComponent");

            Object timingPacket = packetTitleClass.getConstructor(enumTitleAction, chatComponent, int.class, int.class, int.class)
                    .newInstance(enumTitleAction.getEnumConstants()[2], null, fadeIn, stay, fadeOut);
            sendPacket(player, timingPacket);

            if (title != null && !title.isEmpty()) {
                Method aMethod = chatSerializer.getMethod("a", String.class);
                Object titleComponent = aMethod.invoke(null, "{\"text\":\"" + colorize(title) + "\"}");
                Object titlePacket = packetTitleClass.getConstructor(enumTitleAction, chatComponent)
                        .newInstance(enumTitleAction.getEnumConstants()[0], titleComponent);
                sendPacket(player, titlePacket);
            }

            if (subtitle != null && !subtitle.isEmpty()) {
                Method aMethod = chatSerializer.getMethod("a", String.class);
                Object subtitleComponent = aMethod.invoke(null, "{\"text\":\"" + colorize(subtitle) + "\"}");
                Object subtitlePacket = packetTitleClass.getConstructor(enumTitleAction, chatComponent)
                        .newInstance(enumTitleAction.getEnumConstants()[1], subtitleComponent);
                sendPacket(player, subtitlePacket);
            }
        } catch (Exception e) {
            if (title != null && !title.isEmpty()) {
                player.sendMessage(colorize(title));
            }
            if (subtitle != null && !subtitle.isEmpty()) {
                player.sendMessage(colorize(subtitle));
            }
        }
    }

    public static void sendActionBar(Player player, String message) {
        try {
            Class<?> chatSerializer = getNMSClass("IChatBaseComponent$ChatSerializer");
            Class<?> packetChatClass = getNMSClass("PacketPlayOutChat");

            Method aMethod = chatSerializer.getMethod("a", String.class);
            Object chatComponent = aMethod.invoke(null, "{\"text\":\"" + colorize(message) + "\"}");
            Object packet = packetChatClass.getConstructor(
                    getNMSClass("IChatBaseComponent"), byte.class)
                    .newInstance(chatComponent, (byte) 2);

            sendPacket(player, packet);
        } catch (Exception e) {
            player.sendMessage(colorize(message));
        }
    }

    private static void sendPacket(Player player, Object packet) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
        playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet"))
                .invoke(playerConnection, packet);
    }
}