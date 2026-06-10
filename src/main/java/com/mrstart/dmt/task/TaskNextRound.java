package com.mrstart.dmt.task;

import com.mrstart.dmt.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskNextRound extends BukkitRunnable {
    private final Game game;
    public TaskNextRound(Game game) { this.game = game; }

    @Override
    public void run() {
        try {
            if (game.getNextRoundEndTime() > 0) {
                try { game.timesUp(); } catch (Exception ignored) {}
                try { game.startRound(); } catch (Exception ignored) {}
                return;
            }
        } catch (Exception ignored) {}
        try { game.timesUp(); } catch (Exception ignored) {}
        int nextRoundTime = game.getInstance().getConfig().getInt("game.nextroundTime", 5);
        game.setNextRoundEndTime(System.currentTimeMillis() + nextRoundTime * 1000L);
        org.bukkit.scheduler.BukkitRunnable delayedStart = new org.bukkit.scheduler.BukkitRunnable() {
            public void run() { game.startRound(); }
        };
        delayedStart.runTaskLater(game.getInstance(), nextRoundTime * 20L);
        game.addTask(delayedStart);
        try { game.startXpCountdown(); } catch (Exception ignored) {}
    }
}