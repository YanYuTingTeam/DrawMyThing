package com.mrstart.dmt.task;

import com.mrstart.dmt.game.Game;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskRegisterSign extends BukkitRunnable {
    private final Block block;
    private final Game game;
    private final String display;

    public TaskRegisterSign(Block block, Game game, String display) {
        this.block = block;
        this.game = game;
        this.display = display;
    }

    @Override
    public void run() { game.registerSign(block, display); }
}
