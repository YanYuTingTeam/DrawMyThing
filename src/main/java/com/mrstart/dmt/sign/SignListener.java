package com.mrstart.dmt.sign;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.DrawMyThing;
import com.mrstart.dmt.game.Game;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.MetadataValue;

public class SignListener implements Listener {

    private final DrawMyThing plugin;

    public SignListener(DrawMyThing plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getLine(0) == null) return;

        if (event.getLine(0).startsWith("[joindmt]")) {
            if (!player.hasPermission("dmt.admin")) return;

            String gameName = event.getLine(1);
            if (gameName == null || gameName.isEmpty()) return;

            Game game = plugin.getGameByName(gameName);
            if (game == null) {
                ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName);
                return;
            }

            event.setLine(0, ChatUtil.colorize(plugin.getMsgConfig().getString("sign-line-0", "&6[DMT]")));
            event.setLine(1, ChatUtil.colorize(plugin.getMsgConfig().getString("sign-join-line-1", "&e%game%").replace("%game%", gameName)));
            event.setLine(2, ChatUtil.colorize(plugin.getMsgConfig().getString("sign-join-line-2", "&7%count%/%max%")
                    .replace("%count%", String.valueOf(game.getPlayerCount()))
                    .replace("%max%", String.valueOf(game.getMaxPlayers()))));
            event.setLine(3, ChatUtil.colorize(plugin.getMsgConfig().getString("sign-join-line-3-join", "&a加入")));

            final Block block = event.getBlock();
            final String display = event.getLine(2) != null ? event.getLine(2) : "none";
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                public void run() {
                    if (block.getState() instanceof Sign) {
                        game.registerSign(block, display);
                    }
                }
            }, 2L);

            ChatUtil.sendMsg(player, "sign-created");
        }

        if (event.getLine(0).startsWith("[leavedmt]")) {
            if (!player.hasPermission("dmt.admin")) return;
            event.setLine(0, ChatUtil.colorize(plugin.getMsgConfig().getString("sign-leave-line-0", "&6[DMT]")));
            event.setLine(1, ChatUtil.colorize(plugin.getMsgConfig().getString("sign-leave-line-1", "&c离开")));
            event.setLine(2, "");
            event.setLine(3, "");
        }
    }

    @EventHandler
    public void onBlockBroken(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (!(b.getState() instanceof Sign)) return;

        if (b.hasMetadata("bmtjoinsign")) {
            if (event.getPlayer().hasPermission("dmt.admin")) {
                String gameName = ((MetadataValue) b.getMetadata("bmtjoinsign").get(0)).asString();
                Game game = plugin.getGameByName(gameName);
                if (game != null) {
                    game.removeSign(b);
                    ChatUtil.sendMsg(event.getPlayer(), "sign-removed");
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock().getState() instanceof Sign)) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block.hasMetadata("bmtjoinsign")) {
            String gameName = ((MetadataValue) block.getMetadata("bmtjoinsign").get(0)).asString();
            Game game = plugin.getGameByName(gameName);
            if (game != null && player.hasPermission("dmt.default")) {
                game.join(player);
            }
            return;
        }

        Sign sign = (Sign) block.getState();
        String leaveLine0 = ChatUtil.colorize(plugin.getMsgConfig().getString("sign-leave-line-0", "&6[DMT]"));
        String leaveLine1 = ChatUtil.colorize(plugin.getMsgConfig().getString("sign-leave-line-1", "&c离开"));

        if (sign.getLine(0) != null && sign.getLine(0).equals(leaveLine0) &&
            sign.getLine(1) != null && sign.getLine(1).equals(leaveLine1)) {

            if (player.hasPermission("dmt.default") && player.hasMetadata("inbmt")) {
                String gameName = ((MetadataValue) player.getMetadata("inbmt").get(0)).asString();
                Game game = plugin.getGameByName(gameName);
                if (game != null) game.leave(player);
            }
        }
    }
}
