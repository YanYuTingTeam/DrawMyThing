package com.mrstart.dmt.task;

import com.mrstart.dmt.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskNextRound extends BukkitRunnable {
    private final Game game;
    public TaskNextRound(Game game) { this.game = game; }

    @Override
    public void run() {
        game.timesUp();
        game.startRound();
    }
}