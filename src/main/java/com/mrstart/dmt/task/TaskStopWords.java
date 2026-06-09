package com.mrstart.dmt.task;

import com.mrstart.dmt.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskStopWords extends BukkitRunnable {
    private final Game game;
    public TaskStopWords(Game game) { this.game = game; }

    @Override
    public void run() { game.setNotAcceptWords(); }
}
