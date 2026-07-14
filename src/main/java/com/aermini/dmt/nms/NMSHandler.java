package com.aermini.dmt.nms;

import org.bukkit.entity.Player;

public interface NMSHandler {

    void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut);

    void sendActionBar(Player player, String message);

    void sendPacket(Player player, Object packet) throws Exception;

    boolean isHandRaised(Player player);
}
