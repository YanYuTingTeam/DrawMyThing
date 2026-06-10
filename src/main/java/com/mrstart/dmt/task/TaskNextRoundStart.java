package com.mrstart.dmt.task;

import com.mrstart.dmt.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskNextRoundStart extends BukkitRunnable {
    private final Game game;
    public TaskNextRoundStart(Game game) { this.game = game; }

    @Override
    public void run() {
        game.startRound();
    }
}