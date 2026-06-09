package com.mrstart.dmt.game;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.DrawMyThing;
import com.mrstart.dmt.LocationUtil;
import com.mrstart.dmt.cuboid.CuboidZone;
import com.mrstart.dmt.task.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import java.util.*;

public class Game {

    private DrawMyThing instance;
    private CuboidZone buildzone;
    private Location spawn;
    private Location bspawn;
    private Location lobby;
    private Location paintingPos1;
    private Location paintingPos2;
    private String name;
    private String region;

    private Map<Player, Integer> score = new HashMap<Player, Integer>();
    private Map<Player, Boolean> ready = new HashMap<Player, Boolean>();
    private Map<Player, Integer> hasBeenBuilder = new HashMap<Player, Integer>();
    private Map<Player, ItemStack[]> inventories = new HashMap<Player, ItemStack[]>();
    private Map<Player, GameMode> gamemode = new HashMap<Player, GameMode>();
    private Map<Player, Integer> foodLevel = new HashMap<Player, Integer>();
    private List<Player> hasFound = new ArrayList<Player>();

    private Player builder;
    private int players = 0;
    private int minplayers = 2;
    private int maxplayers = 12;
    private int buildPerPlayer = 2;
    private int roundCount = 0;
    private String word;
    private StringBuilder wordf;
    private boolean started = false;
    private boolean waiting = false;
    private boolean wordHasBeenFound = false;
    private int playerFound = 0;
    private long countdownStartTime = 0;
    private int countdownDuration = 0;
    private List<BukkitRunnable> tasks = new ArrayList<BukkitRunnable>();
    private boolean acceptWords = true;
    private List<Block> signs = new ArrayList<Block>();

    private ScoreboardManager manager;
    private Scoreboard board;
    private Objective objective;
    private BukkitTask scoreboardTask = null;

    public Game(CuboidZone build, Location spawn, Location bspawn, String name, String region, DrawMyThing instance) {
        this.buildzone = build;
        this.spawn = spawn;
        this.bspawn = bspawn;
        this.name = name;
        this.region = region;
        this.instance = instance;

        manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();
        objective = board.registerNewObjective(name + "_points", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(colorize(instance.getMsgConfig().getString("gaming-scoreboard-title", "&6分数")));
    }

    private String colorize(String s) { return ChatUtil.colorize(s); }

    public String getName() { return name; }
    public String getRegion() { return region; }
    public Player getBuilder() { return builder; }
    public CuboidZone getBuildZone() { return buildzone; }
    public String getWord() { return word; }
    public boolean isStarted() { return started; }
    public boolean isWaiting() { return waiting; }
    public int getMinPlayers() { return minplayers; }
    public int getMaxPlayers() { return maxplayers; }
    public int getPlayerCount() { return players; }
    public Location getSpawn() { return spawn; }
    public Location getBuilderSpawn() { return bspawn; }
    public Location getLobby() { return lobby; }

    public void setSpawn(Location loc) { this.spawn = loc; }
    public void setBuilderSpawn(Location loc) { this.bspawn = loc; }
    public void setLobby(Location loc) { this.lobby = loc; }
    public void setMinPlayers(int i) { this.minplayers = i; }
    public void setMaxPlayers(int i) { this.maxplayers = i; }
    public void setRegion(String region) { this.region = region; }
    public void setNotAcceptWords() { this.acceptWords = false; }
    public boolean hasFound(Player player) { return hasFound.contains(player); }

    public int getStartTime() {
        if (!waiting || started) return 0;
        int elapsed = (int) ((System.currentTimeMillis() - countdownStartTime) / 1000);
        int remaining = countdownDuration - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    /**
     * 统一占位符替换（不做着色，着色由调用方处理）
     */
    public String replacePlaceholders(String template, Player player) {
        if (template == null) return "";
        String result = template;
        result = result.replace("%game%", name);
        result = result.replace("%count%", String.valueOf(players));
        result = result.replace("%max%", String.valueOf(maxplayers));
        result = result.replace("%min%", String.valueOf(minplayers));
        result = result.replace("%starttime%", String.valueOf(getStartTime()));
        result = result.replace("%round_now%", String.valueOf(roundCount));
        result = result.replace("%round_all%", String.valueOf(buildPerPlayer * score.size()));
        if (player != null) {
            result = result.replace("%player%", player.getName());
            if (score.containsKey(player)) result = result.replace("%score%", String.valueOf(score.get(player)));
        }
        if (word != null) {
            result = result.replace("%paintname%", word);
            result = result.replace("%tip%", player != null ? getTipText(player) : getTipText(null));
        }
        return result;
    }

    public Location getPaintingPos1() { return paintingPos1; }
    public Location getPaintingPos2() { return paintingPos2; }

    public void setPaintingPos1(Location loc) { this.paintingPos1 = loc; }
    public void setPaintingPos2(Location loc) { this.paintingPos2 = loc; }

    public void applyPaintingZone() {
        if (paintingPos1 != null && paintingPos2 != null) {
            this.buildzone = new CuboidZone(paintingPos1.getBlock(), paintingPos2.getBlock());
        }
    }

    public List<Player> getPlayers() { return new ArrayList<Player>(score.keySet()); }

    public void sendMessage(String message) {
        for (Player p : score.keySet()) ChatUtil.send(p, message);
    }

    public void sendMsg(String key, String... replacements) {
        String msg = ChatUtil.getMessage(key, replacements);
        for (Player p : score.keySet()) p.sendMessage(ChatUtil.getPrefix() + msg);
    }

    public void playSoundForAll(Sound sound, float volume, float pitch) {
        for (Player p : score.keySet()) p.playSound(p.getLocation(), sound, volume, pitch);
    }

    public void sendTitleToAll(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player p : score.keySet()) ChatUtil.sendTitle(p, title, subtitle, fadeIn, stay, fadeOut);
    }

    public void sendActionBarToAll(String message) {
        for (Player p : score.keySet()) ChatUtil.sendActionBar(p, message);
    }

    public String getTipText(Player player) {
        if (word == null) return "";
        String splice = instance.getConfig().getString("tip.splice", " ");
        String hide = instance.getConfig().getString("tip.hide", "_");
        if (builder != null && builder.equals(player)) return joinWithSplice(word, splice);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordf.length(); i++) {
            if (i > 0) sb.append(splice);
            char c = wordf.charAt(i);
            sb.append(c == '_' ? hide : String.valueOf(c));
        }
        return sb.toString();
    }

    private String joinWithSplice(String word, String splice) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) { if (i > 0) sb.append(splice); sb.append(word.charAt(i)); }
        return sb.toString();
    }

    private void giveWaitingItems(Player player) {
        PlayerInventory inv = player.getInventory();
        int leaveSlot = instance.getConfig().getInt("item.leave.slot", 8);
        inv.setItem(leaveSlot, instance.getLeaveItem());
    }

    private void giveBuilderItems(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(instance.getConfig().getInt("item.pencil.slot", 0), instance.getPencilItem());
        inv.setItem(instance.getConfig().getInt("item.big-pencil.slot", 1), instance.getBigPencilItem());
        inv.setItem(instance.getConfig().getInt("item.eraser.slot", 2), instance.getEraserItem());
        inv.setItem(instance.getConfig().getInt("item.color-picker.slot", 3), instance.getColorPickerItem());
    }

    public void join(Player player) {
        if (started) { ChatUtil.sendMsg(player, "game-already-started"); return; }
        if (score.containsKey(player)) { ChatUtil.sendMsg(player, "already-in-game"); return; }
        if (players >= maxplayers) { ChatUtil.sendMsg(player, "game-full"); return; }
        player.setMetadata("oldLoc", new org.bukkit.metadata.FixedMetadataValue(instance, LocationUtil.LocationToString(player.getLocation())));
        Location teleportTarget = lobby != null ? lobby : spawn;
        if (teleportTarget != null) player.teleport(teleportTarget);
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] saved = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) { if (contents[i] != null) saved[i] = contents[i].clone(); }
        inventories.put(player, saved);
        foodLevel.put(player, player.getFoodLevel());

        player.setFoodLevel(20);
        gamemode.put(player, player.getGameMode());
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        giveWaitingItems(player);

        player.setMetadata("inbmt", new org.bukkit.metadata.FixedMetadataValue(instance, getName()));
        player.setScoreboard(board);

        score.put(player, 0);
        ready.put(player, false);
        players++;

        for (Player p : score.keySet()) {
            ChatUtil.sendMsg(p, "player-joined", "%player%", player.getName(), "%count%", String.valueOf(players), "%max%", String.valueOf(maxplayers));
        }

        startWaitingScoreboard();
        if (players >= minplayers && !waiting) {
            waiting = true;
            countdownDuration = instance.getConfig().getInt("beforestart.time", 10);
            countdownStartTime = System.currentTimeMillis();
            startStartingScoreboard();
            sendMsg("game-starting", "%time%", String.valueOf(countdownDuration));
            TaskStart startTask = new TaskStart(this);
            startTask.runTaskLater(instance, countdownDuration * 20L);
            tasks.add(startTask);
            startCountdownBroadcast(countdownDuration);
        }
        updateSigns();
    }

    public void leave(Player player) {
        if (!score.containsKey(player)) return;
        if (!started && players == maxplayers) { cancelTasks(); waiting = false; countdownStartTime = 0; }
        player.setFoodLevel(foodLevel.containsKey(player) ? foodLevel.get(player) : 20);
        foodLevel.remove(player);
        score.remove(player);
        ready.remove(player);
        hasBeenBuilder.remove(player);
        hasFound.remove(player);

        if (inventories.get(player) != null) player.getInventory().setContents(inventories.get(player));
        inventories.remove(player);

        player.setDisplayName(player.getName());
        player.setGameMode(gamemode.containsKey(player) ? gamemode.get(player) : GameMode.SURVIVAL);
        gamemode.remove(player);

        board.resetScores(player);
        player.setScoreboard(manager.getMainScoreboard());
        players--;
        if (player.hasMetadata("oldLoc")) {
            Location oldLoc = LocationUtil.StringToLoc(((MetadataValue) player.getMetadata("oldLoc").get(0)).asString());
            if (oldLoc != null) player.teleport(oldLoc);
            player.removeMetadata("oldLoc", instance);
        }
        player.removeMetadata("inbmt", instance);
        updateSigns();

        if (players > 0) sendMsg("player-left", "%player%", player.getName(), "%count%", String.valueOf(players), "%max%", String.valueOf(maxplayers));
        if (started && players <= 1) { cancelTasks(); stop(); }
        if (players <= 0) { waiting = false; countdownStartTime = 0; stopScoreboardTask(); }
        else if (waiting && !started && players < minplayers) { cancelTasks(); waiting = false; countdownStartTime = 0; startWaitingScoreboard(); }
    }

    public void start() {
        if (started) return;
        cancelTasks();
        started = true;
        waiting = false;
        countdownStartTime = 0;
        word = null;
        buildPerPlayer = instance.getConfig().getInt("game.build-per-player", 2);
        if (players < 3) buildPerPlayer = Math.max(buildPerPlayer, 3);
        roundCount = 0;
        hasBeenBuilder.clear();
        for (Player p : score.keySet()) hasBeenBuilder.put(p, 0);
        playSoundForAll(Sound.NOTE_PIANO, 1.0f, 1.0f);
        startRound();
        updateSigns();
        startGamingScoreboard();
    }

    public void startRound() {
        roundCount++;
        if (word != null) sendMsg("word-was", "%word%", word);
        cancelTasks();
        wordHasBeenFound = false;
        playerFound = 0;
        hasFound.clear();
        acceptWords = true;
        word = getNewWord();
        wordf = new StringBuilder(word.replaceAll("[a-zA-Z]", "_"));

        if (buildzone != null) {
            buildzone.clear();
            buildzone.setWool(DyeColor.WHITE);
        }

        for (Player p : score.keySet()) {
            p.getInventory().clear();
            if (builder == null || !builder.equals(p)) giveWaitingItems(p);
        }

        getNextBuilder();
        List<Integer> hints = instance.getConfig().getIntegerList("game.hints");
        if (hints == null || hints.isEmpty()) hints = Arrays.asList(40, 50, 55);
        for (int h : hints) { TaskAddLetter t = new TaskAddLetter(this); t.runTaskLater(instance, h * 20L); tasks.add(t); }
        int roundTime = instance.getConfig().getInt("game.round-time", 75);
        TaskAlert a30 = new TaskAlert(ChatUtil.getMessage("time-warning-30"), getPlayers(), this);
        a30.runTaskLater(instance, 30 * 20L); tasks.add(a30);
        TaskAlert a10 = new TaskAlert(ChatUtil.getMessage("time-warning-10"), getPlayers(), this);
        a10.runTaskLater(instance, (roundTime - 20) * 20L); tasks.add(a10);
        TaskNextRound endRound = new TaskNextRound(this);
        endRound.runTaskLater(instance, roundTime * 20L); tasks.add(endRound);
        TaskAlert endMsg = new TaskAlert(ChatUtil.getMessage("time-up"), getPlayers(), this);
        endMsg.runTaskLater(instance, (roundTime - 5) * 20L); tasks.add(endMsg);
        int refresh = instance.getConfig().getInt("scoreboard.gaming.refresh", 20);
        TaskActionBar abTask = new TaskActionBar(this, instance);
        abTask.runTaskTimer(instance, 20L, Math.max(refresh, 10)); tasks.add(abTask);
        if (instance.getConfig().getBoolean("force-clear-weather", true)) clearWeather();
    }

    private String getNewWord() { return instance.getRandomWord(); }

    public void addRandomLetter() {
        if (word == null || wordf == null) return;
        Random r = new Random();
        int idx = r.nextInt(word.length());
        wordf.setCharAt(idx, word.charAt(idx));
        sendMsg("word-hint", "%tip%", getTipText(null));
        playSoundForAll(Sound.ITEM_PICKUP, 1.0f, 1.0f);
        if (builder != null) ChatUtil.sendMsg(builder, "your-word", "%word%", word);
    }

    public void wordFoundBy(Player player) {
        if (!acceptWords || hasFound.contains(player)) {
            if (hasFound.contains(player)) ChatUtil.sendMsg(player, "already-found");
            return;
        }
        hasFound.add(player);
        playSoundForAll(Sound.NOTE_PIANO, 1.0f, 1.0f);
        if (!wordHasBeenFound) {
            sendMsg("first-found", "%player%", player.getName());
            if (builder != null) ChatUtil.sendMsg(builder, "builder-found-notify");
            increaseScore(player, 3);
            if (builder != null) increaseScore(builder, 2);
            wordHasBeenFound = true;
        } else {
            sendMsg("other-found", "%player%", player.getName());
            increaseScore(player, 1);
        }
        playerFound++;
        if (playerFound >= players - 1) {
            sendMsg("all-found-next");
            cancelTasks();
            TaskNextRound endRound = new TaskNextRound(this);
            endRound.runTaskLater(instance, 100L); tasks.add(endRound);
        }
    }

    private void getNextBuilder() {
        if (getBuilder() != null) {
            builder.setGameMode(GameMode.ADVENTURE);
            builder.setFlying(false);
            if (spawn != null) builder.teleport(spawn);
            builder.setDisplayName(builder.getName());
            builder.getInventory().clear();
            giveWaitingItems(builder);
            builder = null;
        }
        for (int i = 0; i < buildPerPlayer; i++) {
            for (Player p : hasBeenBuilder.keySet()) {
                if (hasBeenBuilder.get(p) <= i) { setBuilder(p); return; }
            }
        }

        sendMessage(ChatUtil.getMessage("game-over"));
        Player winner = null;
        for (Player p : score.keySet()) { if (winner == null || score.get(p) > score.get(winner)) winner = p; }
        if (winner != null) {
            sendMessage(ChatUtil.getMessage("scores-label"));
            for (Player p : score.keySet()) sendMessage(ChatUtil.getMessage("score-line", "%player%", p.getName(), "%score%", String.valueOf(score.get(p))));
            sendMessage(ChatUtil.getMessage("winner", "%player%", winner.getName()));
        }
        stop();
    }

    private void setBuilder(Player p) {
        builder = p;
        hasBeenBuilder.put(p, hasBeenBuilder.get(p) + 1);
        final Player fp = p;
        int delay = instance.getConfig().getInt("game.builder-teleport-delay", 1);
        new BukkitRunnable() {
            public void run() {
                if (bspawn != null) fp.teleport(bspawn);
                fp.setGameMode(GameMode.CREATIVE);
                fp.setFlying(true);
                fp.getInventory().clear();
                giveBuilderItems(fp);
                fp.setDisplayName(ChatColor.RED + "[BUILDER] " + fp.getName());
            }
        }.runTaskLater(instance, delay * 20L);
        for (Player other : score.keySet()) { if (!other.equals(p)) giveWaitingItems(other); }
        sendMsg("round-started", "%player%", p.getName());
        sendMsg("word-hint", "%tip%", getTipText(null));
        ChatUtil.sendMsg(builder, "your-word", "%word%", word);
    }

    public void increaseScore(Player p, int value) {
        if (score.containsKey(p)) {
            score.put(p, score.get(p) + value);
            objective.getScore(p).setScore(score.get(p));
        }
    }

    private void startWaitingScoreboard() {
        stopScoreboardTask();
        objective.unregister();
        objective = board.registerNewObjective(name + "_points", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(colorize(instance.getMsgConfig().getString("waiting-scoreboard-title", "&6等待中")));
        int refresh = instance.getConfig().getInt("scoreboard.waiting.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (waiting || started) { cancel(); return; } updateWaitingScoreboard(); }
        }.runTaskTimer(instance, 5L, Math.max(refresh, 1));
    }

    private void startStartingScoreboard() {
        stopScoreboardTask();
        objective.unregister();
        objective = board.registerNewObjective(name + "_points", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(colorize(instance.getMsgConfig().getString("starting-scoreboard-title", "&6开始中")));
        int refresh = instance.getConfig().getInt("scoreboard.starting.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (!waiting || started) { cancel(); return; } updateStartingScoreboard(); }
        }.runTaskTimer(instance, 5L, Math.max(refresh, 1));
    }

    private void startGamingScoreboard() {
        stopScoreboardTask();
        objective.unregister();
        objective = board.registerNewObjective(name + "_points", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(colorize(instance.getMsgConfig().getString("gaming-scoreboard-title", "&6分数")));
        int refresh = instance.getConfig().getInt("scoreboard.gaming.refresh", 20);
        scoreboardTask = new BukkitRunnable() {
            public void run() { if (!started) { cancel(); return; } updateGamingScoreboard(); }
        }.runTaskTimer(instance, 5L, Math.max(refresh, 1));
    }

    private void stopScoreboardTask() { if (scoreboardTask != null) { scoreboardTask.cancel(); scoreboardTask = null; } }

    private void updateWaitingScoreboard() {
        for (String entry : board.getEntries()) board.resetScores(entry);
        objective.setDisplayName(colorize(replacePlaceholders(instance.getConfig().getString("scoreboard.waiting.title", "&6等待中"), null)));
        List<String> content = instance.getConfig().getStringList("scoreboard.waiting.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&7玩家: &f%count%/%max%", "", "&7状态: &a等待开始", "");
        int scoreVal = content.size();
        for (String line : content) {
            line = colorize(replacePlaceholders(line, null));
            String entry = line.length() > 16 ? line.substring(0, 16) : line;
            objective.getScore(entry).setScore(scoreVal--);
        }
    }

    private void updateStartingScoreboard() {
        for (String entry : board.getEntries()) board.resetScores(entry);
        objective.setDisplayName(colorize(replacePlaceholders(instance.getConfig().getString("scoreboard.starting.title", "&6开始中"), null)));
        List<String> content = instance.getConfig().getStringList("scoreboard.starting.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&7玩家: &f%count%/%max%", "", "&7状态: &a即将开始", "");
        int scoreVal = content.size();
        for (String line : content) {
            line = colorize(replacePlaceholders(line, null));
            String entry = line.length() > 16 ? line.substring(0, 16) : line;
            objective.getScore(entry).setScore(scoreVal--);
        }
    }

    private void updateGamingScoreboard() {
        for (String entry : board.getEntries()) board.resetScores(entry);
        objective.setDisplayName(colorize(replacePlaceholders(instance.getConfig().getString("scoreboard.gaming.title", "&6分数"), null)));
        List<String> content = instance.getConfig().getStringList("scoreboard.gaming.content");
        if (content == null || content.isEmpty()) content = Arrays.asList("", "&a%player%: &e%score%", "");
        List<Map.Entry<Player, Integer>> sorted = new ArrayList<Map.Entry<Player, Integer>>(score.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<Player, Integer>>() {
            public int compare(Map.Entry<Player, Integer> a, Map.Entry<Player, Integer> b) { return b.getValue() - a.getValue(); }
        });
        int scoreVal = content.size() * 10;
        for (String line : content) {
            if (line.contains("%player%") && line.contains("%score%")) {
                for (Map.Entry<Player, Integer> e : sorted) {
                    String pl = colorize(replacePlaceholders(line, e.getKey()));
                    String key = pl.length() > 16 ? pl.substring(0, 16) : pl;
                    objective.getScore(key).setScore(scoreVal--);
                }
            } else {
                String f = colorize(replacePlaceholders(line, null));
                String key = f.length() > 16 ? f.substring(0, 16) : f;
                objective.getScore(key).setScore(scoreVal--);
            }
        }
    }

    private void startCountdownBroadcast(int totalTime) {
        List<Integer> showtime = instance.getConfig().getIntegerList("beforestart.showtime");
        if (showtime == null || showtime.isEmpty()) showtime = Arrays.asList(10, 5, 4, 3, 2, 1);
        final String soundStr = instance.getConfig().getString("beforestart.broadcast.sound", "ORB_PICKUP,1,1");
        final String titleStr = instance.getConfig().getString("beforestart.broadcast.title", "&6&l游戏将在 %time% 秒后开始,20,40,20");
        for (int t : showtime) {
            if (t > totalTime) continue;
            final int timeLeft = t;
            new BukkitRunnable() {
                public void run() { if (!waiting) { cancel(); return; } broadcastCountdown(timeLeft, soundStr, titleStr); }
            }.runTaskLater(instance, (totalTime - timeLeft) * 20L);
        }
    }

    private void broadcastCountdown(int timeLeft, String soundStr, String titleStr) {
        try {
            String[] sp = soundStr.split(",");
            playSoundForAll(Sound.valueOf(sp[0].trim()), sp.length > 1 ? Float.parseFloat(sp[1].trim()) : 1.0f, sp.length > 2 ? Float.parseFloat(sp[2].trim()) : 1.0f);
        } catch (Exception ignored) {}
        try {
            String[] tp = titleStr.split(",");
            sendTitleToAll(colorize(tp[0].replace("%time%", String.valueOf(timeLeft))), "",
                    tp.length > 1 ? Integer.parseInt(tp[1].trim()) : 20, tp.length > 2 ? Integer.parseInt(tp[2].trim()) : 40, tp.length > 3 ? Integer.parseInt(tp[3].trim()) : 20);
        } catch (Exception ignored) {}
    }

    private void clearWeather() {
        World world = buildzone != null ? buildzone.getCorner1().getWorld() : Bukkit.getWorld(region);
        if (world != null) { world.setStorm(false); world.setThundering(false); world.setWeatherDuration(999999); }
    }

    public void cancelTasks() { for (BukkitRunnable r : tasks) { try { r.cancel(); } catch (Exception ignored) {} } tasks.clear(); }

    public void removePlayerFromAlerts(Player p) {
        for (BukkitRunnable task : tasks) { if (task instanceof TaskAlert) ((TaskAlert) task).removePlayer(p); }
    }

    public void stop() {
        started = false; waiting = false; countdownStartTime = 0; cancelTasks(); stopScoreboardTask();
        if (buildzone != null) { buildzone.clear(); buildzone.setWool(DyeColor.WHITE); }
        List<Player> toKick = new ArrayList<Player>(score.keySet());
        for (Player p : toKick) leave(p);
    }

    public void registerSign(Block block, String display) {
        if (block.getState() instanceof Sign) { signs.add(block); block.setMetadata("bmtjoinsign", new org.bukkit.metadata.FixedMetadataValue(instance, getName())); updateSigns(); }
    }

    public void removeSign(Block block) {
        if (block.getState() instanceof Sign) { signs.remove(block); if (block.hasMetadata("bmtjoinsign")) block.removeMetadata("bmtjoinsign", instance); }
    }

    public void updateSigns() {
        for (Block b : signs) {
            if (b.getState() instanceof Sign) {
                Sign s = (Sign) b.getState();
                s.setLine(0, colorize(instance.getMsgConfig().getString("sign-line-0", "&6[DMT]")));
                s.setLine(1, colorize(instance.getMsgConfig().getString("sign-join-line-1", "&e%game%").replace("%game%", getName())));
                s.setLine(2, colorize(instance.getMsgConfig().getString("sign-join-line-2", "&7%count%/%max%").replace("%count%", String.valueOf(players)).replace("%max%", String.valueOf(maxplayers))));
                s.setLine(3, started ? colorize(instance.getMsgConfig().getString("sign-join-line-3-ingame", "&c游戏中")) : colorize(instance.getMsgConfig().getString("sign-join-line-3-join", "&a加入")));
                s.update();
            }
        }
    }

    public void save(FileConfiguration file) {
        String path = "games." + getName();
        file.set(path + ".region", region);
        file.set(path + ".builderspawn", bspawn != null ? LocationUtil.LocationToString(bspawn) : "");
        file.set(path + ".spawn", spawn != null ? LocationUtil.LocationToString(spawn) : "");
        file.set(path + ".lobby", lobby != null ? LocationUtil.LocationToString(lobby) : "");
        if (paintingPos1 != null) file.set(path + ".painting.pos1", LocationUtil.LocationToString(paintingPos1));
        else if (buildzone != null) file.set(path + ".painting.pos1", LocationUtil.LocationToString(buildzone.getCorner1().getLocation()));
        else file.set(path + ".painting.pos1", "");
        if (paintingPos2 != null) file.set(path + ".painting.pos2", LocationUtil.LocationToString(paintingPos2));
        else if (buildzone != null) file.set(path + ".painting.pos2", LocationUtil.LocationToString(buildzone.getCorner2().getLocation()));
        else file.set(path + ".painting.pos2", "");
        file.set(path + ".min-player", minplayers);
        file.set(path + ".max-player", maxplayers);
    }

    public void remove(FileConfiguration file) { stop(); file.set("games." + getName(), null); }

    public static Game load(FileConfiguration file, String name, DrawMyThing instance) {
        String path = "games." + name;
        if (file.getString(path + ".region") == null) return null;
        String region = file.getString(path + ".region", "world");
        Location spawn = LocationUtil.StringToLoc(file.getString(path + ".spawn", ""));
        Location bspawn = LocationUtil.StringToLoc(file.getString(path + ".builderspawn", ""));
        Location lobby = LocationUtil.StringToLoc(file.getString(path + ".lobby", ""));

        CuboidZone zone = null;
        Location pos1 = null, pos2 = null;
        String pos1Str = file.getString(path + ".painting.pos1", "");
        String pos2Str = file.getString(path + ".painting.pos2", "");
        if (pos1Str != null && !pos1Str.isEmpty()) pos1 = LocationUtil.StringToLoc(pos1Str);
        if (pos2Str != null && !pos2Str.isEmpty()) pos2 = LocationUtil.StringToLoc(pos2Str);
        if (pos1 != null && pos2 != null) zone = new CuboidZone(pos1.getBlock(), pos2.getBlock());

        Game game = new Game(zone, spawn, bspawn, name, region, instance);
        if (lobby != null) game.setLobby(lobby);
        if (pos1 != null) game.setPaintingPos1(pos1);
        if (pos2 != null) game.setPaintingPos2(pos2);
        game.setMinPlayers(file.getInt(path + ".min-player", 2));
        game.setMaxPlayers(file.getInt(path + ".max-player", 12));
        return game;
    }

    public static Game createEmpty(String name, World world, DrawMyThing instance) {
        return new Game(null, null, null, name, world.getName(), instance);
    }
}