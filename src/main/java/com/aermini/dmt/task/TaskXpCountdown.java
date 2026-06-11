package com.aermini.dmt.task;

import com.aermini.dmt.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskXpCountdown extends BukkitRunnable {
    private final Game game;
    private Integer lastSet = null;
    private Integer lastRemaining = null;
    public TaskXpCountdown(Game game) {
        this.game = game;
    }

    @Override
    public void run() {
        try {
            if (game.getXpTask() != this) {
                try { cancel(); } catch (Exception ignored) {}
                game.clearXpTask(this);
                return;
            }
        } catch (Exception ignored) {}
        long now = System.currentTimeMillis();
        int remaining;
        boolean usedAfter = false, usedNext = false, usedRound = false, usedStart = false;
        if (game.isAftergameRunning()) {
            remaining = game.getAftergameRemaining();
            usedAfter = true;
        } else if (game.getNextRoundEndTime() > 0) {
            remaining = (int) ((game.getNextRoundEndTime() - now) / 1000);
            usedNext = true;
        } else if (game.getRoundEndTime() > 0) {
            remaining = (int) ((game.getRoundEndTime() - now) / 1000);
            usedRound = true;
        } else if (game.isWaiting() && !game.isStarted()) {
            remaining = game.tickStartCountdown();
            usedStart = true;
        } else {
            cancel();
            setLevel(0);
            return;
        }
        if (remaining <= 0) {
            if (lastSet == null || lastSet != 0) {
                setLevel(0);
                lastSet = 0;
            }
            try { cancel(); } catch (Exception ignored) {}
            game.clearXpTask(this);
            return;
        }
        int setVal;
        if (usedNext) {
            setVal = remaining;
        } else if (usedStart) {
            setVal = Math.max(0, remaining - 1);
        } else {
            setVal = remaining;
        }
        if (lastSet == null || lastSet.intValue() != setVal || lastRemaining == null || lastRemaining.intValue() != remaining) {
            setLevel(setVal);
            lastSet = setVal;
            lastRemaining = remaining;
        }
    }

    private void setLevel(int level) {
        for (Player p : game.getPlayers()) {
            if (p.isOnline()) p.setLevel(level);
        }
    }
}