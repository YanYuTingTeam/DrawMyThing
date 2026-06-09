package com.mrstart.dmt.task;

import com.mrstart.dmt.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskXpCountdown extends BukkitRunnable {
    private final Game game;
    public TaskXpCountdown(Game game) {
        this.game = game;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        int remaining;
        if (game.isAftergameRunning()) {
            remaining = game.getAftergameRemaining();
        } else if (game.getRoundEndTime() > 0) {
            remaining = (int) ((game.getRoundEndTime() - now) / 1000);
        } else if (game.isWaiting() && !game.isStarted()) {
            remaining = game.getStartTime();
        } else {
            cancel();
            setLevel(0);
            return;}
        if (remaining <= 0) {
            setLevel(0);
            return;}
        setLevel(remaining);
    }

    private void setLevel(int level) {
        for (Player p : game.getPlayers()) {
            if (p.isOnline()) p.setLevel(level);
        }
    }
}