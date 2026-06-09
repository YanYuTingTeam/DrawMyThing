package com.mrstart.dmt.game;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.DrawMyThing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public class GameScoreboard {

    private final Game game;
    private final DrawMyThing plugin;
    private final ScoreboardManager manager;
    private final Scoreboard board;
    private Objective objective;
    private BukkitTask scoreboardTask;
    private int objectiveIndex;

    public GameScoreboard(Game game, DrawMyThing plugin) {
        this.game = game;
        this.plugin = plugin;
        this.manager = Bukkit.getScoreboardManager();
        this.board = manager.getNewScoreboard();
        this.objective = board.registerNewObjective(game.getName() + "_points", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatUtil.colorize(plugin.getConfig().getString("scoreboard.gaming.title", "&6分数")));
    }

    public Scoreboard getBoard() { return board; }

    private void clearScoreboardTeams() {
        for (Team t : board.getTeams()) {
            for (String e : t.getEntries()) board.resetScores(e);
            t.unregister();
        }
    }

    private void addTeamEntry(String display, int scoreVal) {
        String invisible = "§" + (char) ('0' + (objectiveIndex % 10));
        objectiveIndex++;
        Team t = board.registerNewTeam("t" + objectiveIndex);
        if (display.length() <= 16) {
            t.setPrefix(display);
            t.setSuffix("");
        } else {
            int split = 16;
            if (split > 0 && display.charAt(split - 1) == '§') split--;
            t.setPrefix(display.substring(0, split));
            t.setSuffix(display.substring(split));
        }
        t.addEntry(invisible);
        objective.getScore(invisible).setScore(scoreVal);
    }

    public void startWaitingScoreboard() {
        stopScoreboardTask();
        if (objective != null) objective.unregister();
        objectiveIndex = 0;
        objective = board.registerNewObjective(game.getName() + "_sb", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatUtil.colorize(plugin.getConfig().getString("scoreboard.waiting.title", "&6等待中")));
        int refresh = plugin.getConfig().getInt("scoreboard.waiting.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (game.isStarted()) { cancel(); return; } updateWaitingScoreboard(); }
        }.runTaskTimer(plugin, 5L, Math.max(refresh, 1));
    }

    public void startStartingScoreboard() {
        stopScoreboardTask();
        if (objective != null) objective.unregister();
        objectiveIndex = 0;
        objective = board.registerNewObjective(game.getName() + "_sb", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatUtil.colorize(plugin.getConfig().getString("scoreboard.starting.title", "&6开始中")));
        int refresh = plugin.getConfig().getInt("scoreboard.starting.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (!game.isWaiting() || game.isStarted()) { cancel(); return; } updateStartingScoreboard(); }
        }.runTaskTimer(plugin, 5L, Math.max(refresh, 1));
    }

    public void startGamingScoreboard() {
        stopScoreboardTask();
        if (objective != null) objective.unregister();
        objectiveIndex = 0;
        objective = board.registerNewObjective(game.getName() + "_sb", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatUtil.colorize(plugin.getConfig().getString("scoreboard.gaming.title", "&6分数")));
        int refresh = plugin.getConfig().getInt("scoreboard.gaming.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (!game.isStarted()) { cancel(); return; } updateGamingScoreboard(); }
        }.runTaskTimer(plugin, 5L, Math.max(refresh, 1));
    }

    public void startEndingScoreboard() {
        stopScoreboardTask();
        if (objective != null) objective.unregister();
        objectiveIndex = 0;
        objective = board.registerNewObjective(game.getName() + "_sb", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatUtil.colorize(plugin.getConfig().getString("scoreboard.ending.title", "&c&l游戏结束")));
        int refresh = plugin.getConfig().getInt("scoreboard.ending.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (game.getEndTime() <= 0) { cancel(); return; } updateEndingScoreboard(); }
        }.runTaskTimer(plugin, 5L, Math.max(refresh, 1));
    }

    public void stopScoreboardTask() {
        if (scoreboardTask != null) { scoreboardTask.cancel(); scoreboardTask = null; }
    }

    private void updateWaitingScoreboard() {
        clearScoreboardTeams();
        objective.setDisplayName(ChatUtil.colorize(game.replacePlaceholders(plugin.getConfig().getString("scoreboard.waiting.title", "&6等待中"), null)));
        List<String> content = plugin.getConfig().getStringList("scoreboard.waiting.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&7玩家: &f%count%/%max%", "", "&7状态: &a等待开始", "");
        int scoreVal = content.size();
        for (String line : content) {
            line = ChatUtil.colorize(game.replacePlaceholders(line, null));
            addTeamEntry(line, scoreVal--);
        }
    }

    private void updateStartingScoreboard() {
        clearScoreboardTeams();
        objective.setDisplayName(ChatUtil.colorize(game.replacePlaceholders(plugin.getConfig().getString("scoreboard.starting.title", "&6开始中"), null)));
        List<String> content = plugin.getConfig().getStringList("scoreboard.starting.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&7玩家: &f%count%/%max%", "", "&7状态: &a即将开始", "");
        int scoreVal = content.size();
        for (String line : content) {
            line = ChatUtil.colorize(game.replacePlaceholders(line, null));
            addTeamEntry(line, scoreVal--);
        }
    }

    private void updateGamingScoreboard() {
        clearScoreboardTeams();
        objective.setDisplayName(ChatUtil.colorize(game.replacePlaceholders(plugin.getConfig().getString("scoreboard.gaming.title", "&6分数"), null)));
        List<String> content = plugin.getConfig().getStringList("scoreboard.gaming.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&a%player%: &e%score%", "");
        Map<Player, Integer> scores = game.getScoreMap();
        List<Map.Entry<Player, Integer>> sorted = new ArrayList<Map.Entry<Player, Integer>>(scores.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<Player, Integer>>() {
            public int compare(Map.Entry<Player, Integer> a, Map.Entry<Player, Integer> b) { return b.getValue() - a.getValue(); }
        });
        int scoreVal = content.size() * 10;
        for (String line : content) {
            if (line.contains("%player%") && !line.contains("%builder_player%") && line.contains("%score%")) {
                for (Map.Entry<Player, Integer> e : sorted) {
                    String pl = ChatUtil.colorize(game.replacePlaceholders(line, e.getKey()));
                    addTeamEntry(pl, scoreVal--);
                }
            } else {
                String f = ChatUtil.colorize(game.replacePlaceholders(line, null));
                addTeamEntry(f, scoreVal--);
            }
        }
    }

    private void updateEndingScoreboard() {
        clearScoreboardTeams();
        objective.setDisplayName(ChatUtil.colorize(game.replacePlaceholders(plugin.getConfig().getString("scoreboard.ending.title", "&c&l游戏结束"), null)));
        List<String> content = plugin.getConfig().getStringList("scoreboard.ending.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&7即将结束", "&f%endtime%秒", "");
        int scoreVal = content.size();
        for (String line : content) {
            line = ChatUtil.colorize(game.replacePlaceholders(line, null));
            addTeamEntry(line, scoreVal--);
        }
    }

    public void clearScoreboard() {
        stopScoreboardTask();
        if (objective != null) { objective.unregister(); objective = null; }
        clearScoreboardTeams();
    }

    public void resetPlayerScore(Player player) {
        board.resetScores(player);
    }

    public void setPlayerScoreboard(Player player) {
        player.setScoreboard(board);
    }

    public void resetPlayerScoreboard(Player player) {
        player.setScoreboard(manager.getMainScoreboard());
    }
}