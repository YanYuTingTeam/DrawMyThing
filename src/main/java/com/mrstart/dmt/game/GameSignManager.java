package com.mrstart.dmt.game;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.DrawMyThing;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.List;

public class GameSignManager {

    private final Game game;
    private final DrawMyThing instance;
    private final List<Block> signs = new ArrayList<Block>();

    public GameSignManager(Game game, DrawMyThing instance) {
        this.game = game;
        this.instance = instance;
    }

    public void registerSign(Block block, String display) {
        if (block.getState() instanceof Sign) {
            signs.add(block);
            block.setMetadata("bmtjoinsign", new FixedMetadataValue(instance, game.getName()));
            updateSigns();
        }
    }

    public void removeSign(Block block) {
        if (block.getState() instanceof Sign) {
            signs.remove(block);
            if (block.hasMetadata("bmtjoinsign")) block.removeMetadata("bmtjoinsign", instance);
        }
    }

    public void updateSigns() {
        for (Block b : signs) {
            if (b.getState() instanceof Sign) {
                Sign s = (Sign) b.getState();
                s.setLine(0, ChatUtil.colorize(instance.getMsgConfig().getString("sign-line-0", "&6[DMT]")));
                s.setLine(1, ChatUtil.colorize(instance.getMsgConfig().getString("sign-join-line-1", "&e%game%").replace("%game%", game.getName())));
                s.setLine(2, ChatUtil.colorize(instance.getMsgConfig().getString("sign-join-line-2", "&7%count%/%max%").replace("%count%", String.valueOf(game.getPlayerCount())).replace("%max%", String.valueOf(game.getMaxPlayers()))));
                s.setLine(3, game.isStarted() ? ChatUtil.colorize(instance.getMsgConfig().getString("sign-join-line-3-ingame", "&c游戏中")) : ChatUtil.colorize(instance.getMsgConfig().getString("sign-join-line-3-join", "&a加入")));
                s.update();
            }
        }
    }
}
