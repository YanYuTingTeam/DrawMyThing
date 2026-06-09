package com.mrstart.dmt.task;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.DrawMyThing;
import com.mrstart.dmt.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskActionBar extends BukkitRunnable {
    private final Game game;
    private final DrawMyThing plugin;

    public TaskActionBar(Game game, DrawMyThing plugin) {
        this.game = game;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!game.isStarted()) { cancel(); return; }

        String template = plugin.getConfig().getString("game.actionbar", "&f&l提示 &r&f- %tip%");

        for (Player p : game.getPlayers()) {
            if (p.isOnline()) {
                String tip = game.getTipText(p);
                String actionBar = template.replace("%tip%", tip);
                ChatUtil.sendActionBar(p, actionBar);
            }
        }
    }
}
