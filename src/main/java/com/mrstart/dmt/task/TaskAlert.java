package com.mrstart.dmt.task;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class TaskAlert extends BukkitRunnable {
    private final String message;
    private final List<Player> sendTo;
    private final Game game;

    public TaskAlert(String message, List<Player> players, Game game) {
        this.message = message;
        this.sendTo = new ArrayList<Player>(players);
        this.game = game;
    }

    @Override
    public void run() {
        for (Player p : sendTo) {
            if (p.isOnline()) {
                if (message.contains("\n")) {
                    String[] lines = message.split("\n");
                    p.sendMessage(ChatUtil.getPrefix() + lines[0]);
                    for (int i = 1; i < lines.length; i++) {
                        p.sendMessage(lines[i]);
                    }
                } else {
                    p.sendMessage(ChatUtil.getPrefix() + message);
                }
            }
        }
    }

    public void removePlayer(Player p) { sendTo.remove(p); }
}