package com.mrstart.dmt.task;

import com.mrstart.dmt.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskNextRound extends BukkitRunnable {
    private final Game game;
    public TaskNextRound(Game game) { this.game = game; }

    @Override
    public void run() {
        game.timesUp();
        int nextRoundTime = game.getInstance().getConfig().getInt("game.nextroundTime", 5);
        game.setNextRoundEndTime(System.currentTimeMillis() + nextRoundTime * 1000L);
        BukkitRunnable delayedStart = new BukkitRunnable() {
            public void run() { game.startRound(); }
        };
        delayedStart.runTaskLater(game.getInstance(), nextRoundTime * 20L);
        game.addTask(delayedStart);
        TaskXpCountdown xpTask = new TaskXpCountdown(game);
        xpTask.runTaskTimer(game.getInstance(), 0L, 20L);
        game.addTask(xpTask);
    }
}