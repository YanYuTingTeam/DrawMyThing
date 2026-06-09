package com.mrstart.dmt;

import com.mrstart.dmt.game.Game;
import com.mrstart.dmt.game.GameSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DMTCommandHandler {

    private final DrawMyThing instance;

    public DMTCommandHandler(DrawMyThing instance) {
        this.instance = instance;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("dmt")) return false;
        if (!(sender instanceof Player)) { sender.sendMessage(ChatUtil.getMessage("player-only")); return true; }

        Player player = (Player) sender;
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "setcanvaspos1":
                if (!checkAdmin(player)) return true;
                handleSetPos(player, args, 1);
                break;
            case "setcanvaspos2":
                if (!checkAdmin(player)) return true;
                handleSetPos(player, args, 2);
                break;
            case "setspawn":
                if (!checkAdmin(player)) return true;
                handleSetSpawn(player, args, "spawn");
                break;
            case "setbuilderspawn":
                if (!checkAdmin(player)) return true;
                handleSetSpawn(player, args, "builderspawn");
                break;
            case "setbuildpos1":
                if (!checkAdmin(player)) return true;
                handleSetBuildBoundary(player, args, 1);
                break;
            case "setbuildpos2":
                if (!checkAdmin(player)) return true;
                handleSetBuildBoundary(player, args, 2);
                break;
            case "setlobby":
                if (!checkAdmin(player)) return true;
                handleSetLobby(player, args);
                break;
            case "create":
                if (!checkAdmin(player)) return true;
                handleCreate(player, args);
                break;
            case "delete":
                if (!checkAdmin(player)) return true;
                handleDelete(player, args);
                break;
            case "setmax":
                if (!checkAdmin(player)) return true;
                handleSetMax(player, args);
                break;
            case "setmin":
                if (!checkAdmin(player)) return true;
                handleSetMin(player, args);
                break;
            case "reload":
                if (!checkAdmin(player)) return true;
                handleReload(player);
                break;
            case "join":
                handleJoin(player, args);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "help":
                sendHelp(player);
                break;
            default:
                ChatUtil.sendMsg(player, "unknown-command");
                break;
        }
        return true;
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) { if (i > from) sb.append(" "); sb.append(args[i]); }
        return sb.toString();
    }

    /**
     * /dmt create <gameName>
     * 以玩家当前世界为region，坐标全部留空
     */
    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        if (instance.getGameByName(gameName) != null) { ChatUtil.sendMsg(player, "game-already-exists", "%game%", gameName); return; }

        World world = player.getWorld();
        Game game = GameSerializer.createEmpty(gameName, world, instance);
        instance.getGames().add(game);
        game.save(instance.getGamesConfig());
        instance.saveGamesFile();
        ChatUtil.sendMsg(player, "game-created", "%game%", gameName);
    }

    /**
     * /dmt setcanvaspos1 <gameName>  — 设置画布坐标1
     * /dmt setcanvaspos2 <gameName>  — 设置画布坐标2
     * 两个都设置后自动创建画布区域
     */
    private void handleSetPos(Player player, String[] args, int posIndex) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        Game game = instance.getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }

        Location loc = player.getLocation();

        if (posIndex == 1) {
            game.setPaintingPos1(loc);
            ChatUtil.sendMsg(player, "pos1-set", "%game%", gameName);
        } else {
            game.setPaintingPos2(loc);
            ChatUtil.sendMsg(player, "pos2-set", "%game%", gameName);
        }

        // 两个都设置了，自动创建画布区域
        if (game.getPaintingPos1() != null && game.getPaintingPos2() != null) {
            game.applyPaintingZone();
        }

        game.save(instance.getGamesConfig());
        instance.saveGamesFile();
    }

    /**
     * /dmt setspawn <gameName>       — 设置观战出生点
     * /dmt setbuilderspawn <gameName> — 设置绘画者出生点
     */
    private void handleSetSpawn(Player player, String[] args, String type) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        Game game = instance.getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }

        Location loc = player.getLocation();

        if (type.equals("spawn")) {
            game.setSpawn(loc);
            ChatUtil.sendMsg(player, "spawn-set", "%game%", gameName);
        } else {
            game.setBuilderSpawn(loc);
            ChatUtil.sendMsg(player, "builder-spawn-set", "%game%", gameName);
        }

        game.save(instance.getGamesConfig());
        instance.saveGamesFile();
    }

    /**
     * /dmt setbuildpos1 <gameName>  — 设置绘画者边界坐标1
     * /dmt setbuildpos2 <gameName>  — 设置绘画者边界坐标2
     */
    private void handleSetBuildBoundary(Player player, String[] args, int posIndex) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        Game game = instance.getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }

        Location loc = player.getLocation();
        if (posIndex == 1) {
            game.setBuilderBoundaryPos1(loc);
            ChatUtil.sendMsg(player, "build-boundary-pos1-set", "%game%", gameName);
        } else {
            game.setBuilderBoundaryPos2(loc);
            ChatUtil.sendMsg(player, "build-boundary-pos2-set", "%game%", gameName);
        }

        game.save(instance.getGamesConfig());
        instance.saveGamesFile();
    }

    /**
     * /dmt setlobby <gameName> — 设置等待大厅位置
     */
    private void handleSetLobby(Player player, String[] args) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        Game game = instance.getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }

        Location loc = player.getLocation();
        game.setLobby(loc);
        game.save(instance.getGamesConfig());
        instance.saveGamesFile();
        ChatUtil.sendMsg(player, "lobby-set", "%game%", gameName);
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        Game game = instance.getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }
        game.remove(instance.getGamesConfig());
        instance.getGames().remove(game);
        instance.saveGamesFile();
        ChatUtil.sendMsg(player, "game-deleted", "%game%", gameName);
    }

    private void handleSetMax(Player player, String[] args) {
        if (args.length < 3) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        Game game = instance.getGameByName(args[1]);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", args[1]); return; }
        try {
            int max = Integer.parseInt(args[2]);
            game.setMaxPlayers(max);
            game.save(instance.getGamesConfig()); instance.saveGamesFile();
            ChatUtil.sendMsg(player, "game-set-max", "%game%", args[1], "%value%", String.valueOf(max));
        } catch (NumberFormatException e) { ChatUtil.sendMsg(player, "unknown-command"); }
    }

    private void handleSetMin(Player player, String[] args) {
        if (args.length < 3) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        Game game = instance.getGameByName(args[1]);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", args[1]); return; }
        try {
            int min = Integer.parseInt(args[2]);
            game.setMinPlayers(min);
            game.save(instance.getGamesConfig()); instance.saveGamesFile();
            ChatUtil.sendMsg(player, "game-set-min", "%game%", args[1], "%value%", String.valueOf(min));
        } catch (NumberFormatException e) { ChatUtil.sendMsg(player, "unknown-command"); }
    }

    private void handleJoin(Player player, String[] args) {
        if (player.hasMetadata("inbmt")) { ChatUtil.sendMsg(player, "already-in-game"); return; }
        if (args.length < 2) {
            if (instance.getConfig().getBoolean("autojoin.enabled", false)) handleAutoJoin(player);
            else ChatUtil.sendMsg(player, "game-name-required");
            return;
        }
        String gameName = joinArgs(args, 1);
        Game game = instance.getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }
        game.join(player);
    }

    private void handleAutoJoin(Player player) {
        if (instance.getConfig().getBoolean("autojoin.randomjoin", false)) {
            List<Game> available = new ArrayList<Game>();
            for (Game g : instance.getGames()) { if (!g.isStarted() && g.getPlayerCount() < g.getMaxPlayers()) available.add(g); }
            if (available.isEmpty()) { ChatUtil.sendMsg(player, "auto-join-no-game"); return; }
            available.get(new Random().nextInt(available.size())).join(player);
        } else {
            String target = instance.getConfig().getString("autojoin.joingame", "");
            if (target.isEmpty()) { ChatUtil.sendMsg(player, "auto-join-no-game"); return; }
            Game game = instance.getGameByName(target);
            if (game == null) { ChatUtil.sendMsg(player, "auto-join-failed"); return; }
            game.join(player);
        }
    }

    private void handleLeave(Player player) {
        if (!player.hasMetadata("inbmt")) { ChatUtil.sendMsg(player, "not-in-game"); return; }
        String gameName = ((MetadataValue) player.getMetadata("inbmt").get(0)).asString();
        Game game = instance.getGameByName(gameName);
        if (game != null) game.leave(player);
    }

    private void handleReload(Player player) {
        for (Game game : instance.getGames()) game.stop();
        instance.getGames().clear();
        instance.reloadConfig();
        instance.reloadMsgConfig();
        instance.reloadGamesConfig();
        instance.reloadWordsConfig();
        instance.getItemManager().initItems();
        instance.getItemManager().initColorItems();
        instance.loadWords();
        instance.loadGames();
        instance.restartWeatherLock();
        ChatUtil.sendMsg(player, "game-reloaded");
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatUtil.colorize("&e&lDrawMyThing &r&7for &b&lYanYuTing &8| &fby. &fmrstart, &dAerMini"));
        if (player.hasPermission("dmt.admin")) {
            player.sendMessage(ChatUtil.colorize("&f/dmt create <gameName> &7- 创建游戏"));
            player.sendMessage(ChatUtil.colorize("&f/dmt delete <gameName> &7- 删除游戏"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setcanvaspos1 <gameName> &7- 设置画布坐标1"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setcanvaspos2 <gameName> &7- 设置画布坐标2"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setspawn <gameName> &7- 设置观战出生点"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setbuilderspawn <gameName> &7- 设置绘画者出生点"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setbuildpos1 <gameName> &7- 设置绘画者边界坐标1"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setbuildpos2 <gameName> &7- 设置绘画者边界坐标2"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setlobby <gameName> &7- 设置等待大厅位置"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setmin <gameName> <amount> &7- 设置最少开始人数"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setmax <gameName> <amount> &7- 设置最大人数"));
            player.sendMessage(ChatUtil.colorize("&f/dmt reload &7- 重载插件"));
        }
    }

    private boolean checkAdmin(Player player) {
        if (!player.hasPermission("dmt.admin")) { ChatUtil.sendMsg(player, "no-permission"); return false; }
        return true;
    }
}