package com.aermini.dmt.game;

import com.aermini.dmt.util.ChatUtil;
import com.aermini.dmt.DrawMyThing;
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
    private final List<String> entryKeys = new ArrayList<String>();
    private static final int MAX_SIDEBAR_ENTRIES = 15;

    public GameScoreboard(Game game, DrawMyThing plugin) {
        this.game = game;
        this.plugin = plugin;
        this.manager = Bukkit.getScoreboardManager();
        this.board = manager.getNewScoreboard();
        this.objective = board.registerNewObjective(game.getName() + "_points", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public Scoreboard getBoard() { return board; }

    private void clearAllEntries() {
        for (String key : entryKeys) {
            Team t = board.getTeam("sb_" + key);
            if (t != null) {
                board.resetScores(key);
                t.unregister();
            }
        }
        entryKeys.clear();
    }

    private String generateInvisibleKey(int index) {
        String key = "";
        int i = index;
        do {
            key = "\u00A7" + (char) ('0' + (i % 16)) + key;
            i = i / 16 - 1;
        } while (i >= 0);
        return key;
    }

    private void ensureEntries(int count) {
        count = Math.min(count, MAX_SIDEBAR_ENTRIES);
        while (entryKeys.size() > count) {
            String key = entryKeys.remove(entryKeys.size() - 1);
            Team t = board.getTeam("sb_" + key);
            if (t != null) {
                board.resetScores(key);
                t.unregister();
            }
        }
        while (entryKeys.size() < count) {
            String key = generateInvisibleKey(entryKeys.size());
            Team t = board.registerNewTeam("sb_" + key);
            t.addEntry(key);
            entryKeys.add(key);
        }
    }

    private String getLastColorCode(String text) {
        String last = "";
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '\u00A7') {
                char c = text.charAt(i + 1);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                    last = "\u00A7" + c;
                }
            }
        }
        return last;
    }

    private void updateTeamDisplay(int index, String display) {
        if (index >= entryKeys.size()) return;
        String key = entryKeys.get(index);
        Team t = board.getTeam("sb_" + key);
        if (t == null) return;
        if (display.length() <= 16) {
            t.setPrefix(display);
            t.setSuffix("");
        } else {
            int split = 16;
            if (split > 0 && display.charAt(split - 1) == '\u00A7') split--;
            String prefix = display.substring(0, split);
            String suffix = display.substring(split);
            String lastColor = getLastColorCode(prefix);
            if (!lastColor.isEmpty() && (suffix.isEmpty() || suffix.charAt(0) != '\u00A7')) {
                suffix = lastColor + suffix;
            }
            t.setPrefix(prefix);
            t.setSuffix(suffix.length() > 16 ? suffix.substring(0, 16) : suffix);
        }
        objective.getScore(key).setScore(entryKeys.size() - index);
    }

    private void prepareObjective() {
        stopScoreboardTask();
        clearAllEntries();
        if (objective != null) objective.unregister();
        objective = board.registerNewObjective(game.getName() + "_sb", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void startWaitingScoreboard() {
        prepareObjective();
        int refresh = plugin.getConfig().getInt("scoreboard.waiting.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (game.isStarted()) { cancel(); return; } updateWaitingScoreboard(); }
        }.runTaskTimer(plugin, 0L, Math.max(refresh, 1));
    }

    public void startStartingScoreboard() {
        prepareObjective();
        int refresh = plugin.getConfig().getInt("scoreboard.starting.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (!game.isWaiting() || game.isStarted()) { cancel(); return; } updateStartingScoreboard(); }
        }.runTaskTimer(plugin, 0L, Math.max(refresh, 1));
    }

    public void startGamingScoreboard() {
        prepareObjective();
        int refresh = plugin.getConfig().getInt("scoreboard.gaming.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (!game.isStarted()) { cancel(); return; } updateGamingScoreboard(); }
        }.runTaskTimer(plugin, 0L, Math.max(refresh, 1));
    }

    public void startEndingScoreboard() {
        prepareObjective();
        int refresh = plugin.getConfig().getInt("scoreboard.ending.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (game.getEndTime() <= 0) { cancel(); return; } updateEndingScoreboard(); }
        }.runTaskTimer(plugin, 0L, Math.max(refresh, 1));
    }

    public void stopScoreboardTask() {
        if (scoreboardTask != null) { scoreboardTask.cancel(); scoreboardTask = null; }
    }

    private void updateDisplayLines(List<String> lines) {
        int count = Math.min(lines.size(), MAX_SIDEBAR_ENTRIES);
        ensureEntries(count);
        for (int i = 0; i < count; i++) {
            updateTeamDisplay(i, lines.get(i));
        }
    }

    private void updateWaitingScoreboard() {
        objective.setDisplayName(ChatUtil.colorize(game.getMessenger().replacePlaceholders(plugin.getConfig().getString("scoreboard.waiting.title", "&6等待中"), null)));
        List<String> content = plugin.getConfig().getStringList("scoreboard.waiting.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&7玩家: &f%count%/%max%", "", "&7状态: &a等待开始", "");
        List<String> lines = new ArrayList<String>();
        for (String line : content) lines.add(ChatUtil.colorize(game.getMessenger().replacePlaceholders(line, null)));
        updateDisplayLines(lines);
    }

    private void updateStartingScoreboard() {
        objective.setDisplayName(ChatUtil.colorize(game.getMessenger().replacePlaceholders(plugin.getConfig().getString("scoreboard.starting.title", "&6开始中"), null)));
        List<String> content = plugin.getConfig().getStringList("scoreboard.starting.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&7玩家: &f%count%/%max%", "", "&7状态: &a即将开始", "");
        List<String> lines = new ArrayList<String>();
        for (String line : content) lines.add(ChatUtil.colorize(game.getMessenger().replacePlaceholders(line, null)));
        updateDisplayLines(lines);
    }

    private void updateGamingScoreboard() {
        objective.setDisplayName(ChatUtil.colorize(game.getMessenger().replacePlaceholders(plugin.getConfig().getString("scoreboard.gaming.title", "&6分数"), null)));
        List<String> content = plugin.getConfig().getStringList("scoreboard.gaming.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&a%player%: &e%score%", "");
        Map<Player, Integer> scores = game.getScoreMap();
        List<Map.Entry<Player, Integer>> sorted = new ArrayList<Map.Entry<Player, Integer>>(scores.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<Player, Integer>>() {
            public int compare(Map.Entry<Player, Integer> a, Map.Entry<Player, Integer> b) { return b.getValue() - a.getValue(); }
        });
        List<String> lines = new ArrayList<String>();
        for (String line : content) {
            if (line.contains("%player%") && !line.contains("%builder_player%") && line.contains("%score%")) {
                for (Map.Entry<Player, Integer> e : sorted) {
                    lines.add(ChatUtil.colorize(game.getMessenger().replacePlaceholders(line, e.getKey())));
                    if (lines.size() >= MAX_SIDEBAR_ENTRIES) break;
                }
            } else {
                lines.add(ChatUtil.colorize(game.getMessenger().replacePlaceholders(line, null)));
            }
            if (lines.size() >= MAX_SIDEBAR_ENTRIES) break;
        }
        updateDisplayLines(lines);
    }

    private void updateEndingScoreboard() {
        objective.setDisplayName(ChatUtil.colorize(game.getMessenger().replacePlaceholders(plugin.getConfig().getString("scoreboard.ending.title", "&c&l游戏结束"), null)));
        List<String> content = plugin.getConfig().getStringList("scoreboard.ending.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&7即将结束", "&f%endtime%秒", "");
        List<String> lines = new ArrayList<String>();
        for (String line : content) lines.add(ChatUtil.colorize(game.getMessenger().replacePlaceholders(line, null)));
        updateDisplayLines(lines);
    }

    public void clearScoreboard() {
        stopScoreboardTask();
        clearAllEntries();
        if (objective != null) { objective.unregister(); objective = null; }
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