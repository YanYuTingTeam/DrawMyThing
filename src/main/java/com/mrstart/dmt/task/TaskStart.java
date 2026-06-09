package com.mrstart.dmt.task;

import com.mrstart.dmt.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskStart extends BukkitRunnable {
    private final Game game;
    public TaskStart(Game game) { this.game = game; }

    @Override
    public void run() { game.start(); }
}
