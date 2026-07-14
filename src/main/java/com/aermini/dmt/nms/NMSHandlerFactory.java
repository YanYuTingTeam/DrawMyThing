package com.aermini.dmt.nms;

import org.bukkit.Bukkit;

public class NMSHandlerFactory {

    private static NMSHandler handler;

    public static NMSHandler getHandler() {
        if (handler != null) return handler;
        String version = getNMSVersion();
        if (version == null) {
            Bukkit.getLogger().warning("无法获取NMS");
            return null;
        }
        try {
            String className = NMSHandlerFactory.class.getPackage().getName()
                    + "." + version + ".NMSHandler_" + version;
            handler = (NMSHandler) Class.forName(className).newInstance();
            Bukkit.getLogger().info("已加载NMS " + version);
        } catch (Exception e) {
            Bukkit.getLogger().warning("NMS不支持 " + version);
        }
        return handler;
    }

    private static String getNMSVersion() {
        try {
            String name = Bukkit.getServer().getClass().getPackage().getName();
            return name.substring(name.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return null;
        }
    }
}
