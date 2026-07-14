package com.aermini.dmt.nms.v1_8_R3;

import com.aermini.dmt.nms.NMSHandler;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class NMSHandler_v1_8_R3 implements NMSHandler {

    private static final String NMS_VERSION = "v1_8_R3";

    private Class<?> getNMSClass(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + NMS_VERSION + "." + name);
    }

    @Override
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Class<?> packetTitleClass = getNMSClass("PacketPlayOutTitle");
            Class<?> enumTitleAction = getNMSClass("PacketPlayOutTitle$EnumTitleAction");
            Class<?> chatSerializer = getNMSClass("IChatBaseComponent$ChatSerializer");
            Class<?> chatComponent = getNMSClass("IChatBaseComponent");

            Object titleAction = enumTitleAction.getEnumConstants()[0];
            Object subtitleAction = enumTitleAction.getEnumConstants()[1];
            Object timesAction = enumTitleAction.getEnumConstants()[2];

            Object timingPacket = packetTitleClass
                    .getConstructor(enumTitleAction, chatComponent, int.class, int.class, int.class)
                    .newInstance(timesAction, null, fadeIn, stay, fadeOut);
            sendPacket(player, timingPacket);

            if (title != null && !title.isEmpty()) {
                Method aMethod = chatSerializer.getMethod("a", String.class);
                Object titleComponent = aMethod.invoke(null, "{\"text\":\"" + title + "\"}");
                Object titlePacket = packetTitleClass
                        .getConstructor(enumTitleAction, chatComponent)
                        .newInstance(titleAction, titleComponent);
                sendPacket(player, titlePacket);
            }

            if (subtitle != null && !subtitle.isEmpty()) {
                Method aMethod = chatSerializer.getMethod("a", String.class);
                Object subtitleComponent = aMethod.invoke(null, "{\"text\":\"" + subtitle + "\"}");
                Object subtitlePacket = packetTitleClass
                        .getConstructor(enumTitleAction, chatComponent)
                        .newInstance(subtitleAction, subtitleComponent);
                sendPacket(player, subtitlePacket);
            }
        } catch (Exception e) {
            if (title != null && !title.isEmpty()) player.sendMessage(title);
            if (subtitle != null && !subtitle.isEmpty()) player.sendMessage(subtitle);
        }
    }

    @Override
    public void sendActionBar(Player player, String message) {
        try {
            Class<?> chatSerializer = getNMSClass("IChatBaseComponent$ChatSerializer");
            Class<?> packetChatClass = getNMSClass("PacketPlayOutChat");

            Method aMethod = chatSerializer.getMethod("a", String.class);
            Object chatComponent = aMethod.invoke(null, "{\"text\":\"" + message + "\"}");
            Object packet = packetChatClass
                    .getConstructor(getNMSClass("IChatBaseComponent"), byte.class)
                    .newInstance(chatComponent, (byte) 2);
            sendPacket(player, packet);
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }

    @Override
    public void sendPacket(Player player, Object packet) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
        playerConnection.getClass()
                .getMethod("sendPacket", getNMSClass("Packet"))
                .invoke(playerConnection, packet);
    }

    @Override
    public boolean isHandRaised(Player player) {
        try {
            Object nmsEntity = player.getClass().getMethod("getHandle").invoke(player);
            Class<?> clazz = nmsEntity.getClass();
            while (clazz != null && !clazz.getSimpleName().equals("EntityHuman")) {
                clazz = clazz.getSuperclass();
            }
            if (clazz != null) {
                Method m = clazz.getDeclaredMethod("isHandRaised");
                m.setAccessible(true);
                return (boolean) m.invoke(nmsEntity);
            }
        } catch (Exception ignored) {}
        return true;
    }
}
