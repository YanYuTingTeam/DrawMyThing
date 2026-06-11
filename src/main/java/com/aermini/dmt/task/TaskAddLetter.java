package com.aermini.dmt.task;

import com.aermini.dmt.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskAddLetter extends BukkitRunnable {
    private final Game game;
    public TaskAddLetter(Game game) { this.game = game; }

    @Override
    public void run() { game.addRandomLetter(); }
}
