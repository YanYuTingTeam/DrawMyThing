package com.mrstart.dmt.game;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.DrawMyThing;
import com.mrstart.dmt.LocationUtil;
import com.mrstart.dmt.cuboid.CuboidZone;
import com.mrstart.dmt.task.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
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
    private Map<Player, Integer> hasBeenBuilder = new HashMap<Player, Integer>();
    private Map<Player, ItemStack[]> inventories = new HashMap<Player, ItemStack[]>();
    private Map<Player, GameMode> gamemode = new HashMap<Player, GameMode>();
    private Map<Player, Integer> foodLevel = new HashMap<Player, Integer>();
    private List<Player> hasFound = new ArrayList<Player>();
    private Map<Player, Integer> roundGainedScore = new HashMap<Player, Integer>();

    private Player builder;
    private int players = 0;
    private int minplayers = 2;
    private int maxplayers = 12;
    private int round = 2;
    private int roundCount = 0;
    private String word;
    private StringBuilder wordf;
    private boolean started = false;
    private boolean waiting = false;
    private boolean wordHasBeenFound = false;
    private int playerFound = 0;
    private int hintCount = 0;
    private long countdownStartTime = 0;
    private int countdownDuration = 0;
    private boolean aftergameRunning = false;
    private long aftergameStartTime = 0;
    private int aftergameDuration = 0;
    private long roundEndTime = 0;
    private long builderFreezeEndTime = 0;
    private Location builderBoundaryPos1;
    private Location builderBoundaryPos2;
    private List<BukkitRunnable> tasks = new ArrayList<BukkitRunnable>();
    private boolean acceptWords = true;

    private GameScoreboard scoreboard;
    private GameMessenger messenger;
    private GameSignManager signManager;

    public Game(CuboidZone build, Location spawn, Location bspawn, String name, String region, DrawMyThing instance) {
        this.buildzone = build;
        this.spawn = spawn;
        this.bspawn = bspawn;
        this.name = name;
        this.region = region;
        this.instance = instance;
        this.scoreboard = new GameScoreboard(this, instance);
        this.messenger = new GameMessenger(this, instance);
        this.signManager = new GameSignManager(this, instance);
    }

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
    public int getRound() { return round; }
    public int getRoundCount() { return roundCount; }
    public StringBuilder getWordFormatted() { return wordf; }
    public Map<Player, Integer> getRoundGainedScore() { return roundGainedScore; }

    public void setSpawn(Location loc) { this.spawn = loc; }
    public void setBuilderSpawn(Location loc) { this.bspawn = loc; }
    public void setLobby(Location loc) { this.lobby = loc; }
    public void setMinPlayers(int i) { this.minplayers = i; }
    public void setMaxPlayers(int i) { this.maxplayers = i; }
    public void setRegion(String region) { this.region = region; }
    public void setNotAcceptWords() { this.acceptWords = false; }
    public boolean hasFound(Player player) { return hasFound.contains(player); }

    public Map<Player, Integer> getScoreMap() { return score; }
    public GameMessenger getMessenger() { return messenger; }
    public GameSignManager getSignManager() { return signManager; }

    public int getStartTime() {
        if (!waiting || started) return 0;
        int elapsed = (int) ((System.currentTimeMillis() - countdownStartTime) / 1000);
        int remaining = countdownDuration - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    public int getEndTime() {
        if (!aftergameRunning) return 0;
        int elapsed = (int) ((System.currentTimeMillis() - aftergameStartTime) / 1000);
        int remaining = aftergameDuration - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    public boolean isAftergameRunning() { return aftergameRunning; }
    public int getAftergameRemaining() { return getEndTime(); }
    public long getRoundEndTime() { return roundEndTime; }
    public long getBuilderFreezeEndTime() { return builderFreezeEndTime; }

    public Location getBuilderBoundaryPos1() { return builderBoundaryPos1; }
    public Location getBuilderBoundaryPos2() { return builderBoundaryPos2; }
    public void setBuilderBoundaryPos1(Location loc) { this.builderBoundaryPos1 = loc; }
    public void setBuilderBoundaryPos2(Location loc) { this.builderBoundaryPos2 = loc; }

    public boolean isInBuilderBoundary(Location loc) {
        if (builderBoundaryPos1 == null || builderBoundaryPos2 == null || loc == null) return true;
        int minX = Math.min(builderBoundaryPos1.getBlockX(), builderBoundaryPos2.getBlockX());
        int minY = Math.min(builderBoundaryPos1.getBlockY(), builderBoundaryPos2.getBlockY());
        int minZ = Math.min(builderBoundaryPos1.getBlockZ(), builderBoundaryPos2.getBlockZ());
        int maxX = Math.max(builderBoundaryPos1.getBlockX(), builderBoundaryPos2.getBlockX());
        int maxY = Math.max(builderBoundaryPos1.getBlockY(), builderBoundaryPos2.getBlockY());
        int maxZ = Math.max(builderBoundaryPos1.getBlockZ(), builderBoundaryPos2.getBlockZ());
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
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

    private void giveWaitingItems(Player player) {
        PlayerInventory inv = player.getInventory();
        int leaveSlot = instance.getConfig().getInt("item.leave.slot", 8);
        inv.setItem(leaveSlot, instance.getItemManager().getLeaveItem());
    }

    private void giveBuilderItems(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(instance.getConfig().getInt("item.pencil.slot", 0), instance.getItemManager().getPencilItem());
        inv.setItem(instance.getConfig().getInt("item.big-pencil.slot", 1), instance.getItemManager().getBigPencilItem());
        inv.setItem(instance.getConfig().getInt("item.eraser.slot", 2), instance.getItemManager().getEraserItem());
        inv.setItem(instance.getConfig().getInt("item.color-picker.slot", 3), instance.getItemManager().getColorPickerItem());
    }

    public void join(Player player) {
        if (started || players >= maxplayers) {
            player.setGameMode(GameMode.SPECTATOR);
            return;
        }
        if (aftergameRunning || score.containsKey(player)) return;
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

        String joinTitle = messenger.replacePlaceholders(instance.getConfig().getString("join.title", "&a&l你画我猜"), player);
        String joinSub = messenger.replacePlaceholders(instance.getConfig().getString("join.subtitle", "&7你已加入 &e%game%"), player);
        int fadeIn = instance.getConfig().getInt("join.fade-in", 10);
        int stay = instance.getConfig().getInt("join.stay", 40);
        int fadeOut = instance.getConfig().getInt("join.fade-out", 10);
        ChatUtil.sendTitle(player, joinTitle, joinSub, fadeIn, stay, fadeOut);

        player.setMetadata("inbmt", new org.bukkit.metadata.FixedMetadataValue(instance, getName()));
        scoreboard.setPlayerScoreboard(player);

        score.put(player, 0);
        players++;

        for (Player p : score.keySet()) {
            ChatUtil.sendMsg(p, "player-joined", "%player%", player.getName(), "%count%", String.valueOf(players), "%max%", String.valueOf(maxplayers));
        }

        scoreboard.startWaitingScoreboard();
        if (players >= minplayers && !waiting) {
            waiting = true;
            countdownDuration = instance.getConfig().getInt("beforestart.time", 10);
            countdownStartTime = System.currentTimeMillis();
            scoreboard.startStartingScoreboard();
            messenger.sendMsg("game-starting", "%time%", String.valueOf(countdownDuration));
            TaskStart startTask = new TaskStart(this);
            startTask.runTaskLater(instance, countdownDuration * 20L);
            tasks.add(startTask);
            TaskXpCountdown xpTask = new TaskXpCountdown(this);
            xpTask.runTaskTimer(instance, 20L, 20L); tasks.add(xpTask);
            messenger.startCountdownBroadcast(countdownDuration);
        }
        signManager.updateSigns();
    }

    public void leave(Player player) {
        if (!score.containsKey(player)) return;
        if (!started && players == maxplayers) { cancelTasks(); waiting = false; countdownStartTime = 0; }
        player.setFoodLevel(foodLevel.containsKey(player) ? foodLevel.get(player) : 20);
        foodLevel.remove(player);
        score.remove(player);
        hasBeenBuilder.remove(player);
        hasFound.remove(player);

        if (inventories.get(player) != null) player.getInventory().setContents(inventories.get(player));
        inventories.remove(player);
        player.setLevel(0);

        player.setDisplayName(player.getName());
        player.setGameMode(gamemode.containsKey(player) ? gamemode.get(player) : GameMode.SURVIVAL);
        gamemode.remove(player);

        scoreboard.resetPlayerScore(player);
        scoreboard.resetPlayerScoreboard(player);
        players--;
        if (instance.isBungeeMode()) {
            instance.sendToServer(player, instance.getBungeeLobby());
            player.removeMetadata("oldLoc", instance);
        } else if (player.hasMetadata("oldLoc")) {
            Location oldLoc = LocationUtil.StringToLoc(((MetadataValue) player.getMetadata("oldLoc").get(0)).asString());
            if (oldLoc != null) player.teleport(oldLoc);
            player.removeMetadata("oldLoc", instance);
        }
        player.removeMetadata("inbmt", instance);
        signManager.updateSigns();

        if (players > 0) messenger.sendMsg("player-left", "%player%", player.getName(), "%count%", String.valueOf(players), "%max%", String.valueOf(maxplayers));
        if (started && !aftergameRunning && builder != null && builder.equals(player)) {
            builder = null;
            cancelTasks();
            if (players <= 1) { stop(); return; }
            getNextBuilder();
            return;
        }
        if (started && players <= 1 && !aftergameRunning) { cancelTasks(); stop(); }
        if (players <= 0) {
            waiting = false; countdownStartTime = 0; scoreboard.stopScoreboardTask();
            if (instance.isBungeeMode() && instance.getConfig().getBoolean("autojoin.full-restart", false)) {
                Bukkit.getServer().shutdown();
            }
        }
        else if (waiting && !started && players < minplayers) { cancelTasks(); waiting = false; countdownStartTime = 0; for (Player p : score.keySet()) p.setLevel(0); scoreboard.startWaitingScoreboard(); }
    }

    public void start() {
        if (started) return;
        cancelTasks();
        started = true;
        waiting = false;
        countdownStartTime = 0;
        word = null;
        round = instance.getConfig().getInt("game.round", 2);
        if (players < 3) round = Math.max(round, 3);
        roundCount = 0;
        hasBeenBuilder.clear();
        for (Player p : score.keySet()) hasBeenBuilder.put(p, 0);
        messenger.sendStartedBroadcast();
        startRound();
        signManager.updateSigns();
        scoreboard.startGamingScoreboard();
    }

    public void startRound() {
        cancelTasks();
        wordHasBeenFound = false;
        playerFound = 0;
        hintCount = 0;
        hasFound.clear();
        roundGainedScore.clear();
        acceptWords = true;
        word = getNewWord();
        wordf = new StringBuilder(word.replaceAll(".", "_"));

        if (buildzone != null) {
            buildzone.clear();
            buildzone.setWool(DyeColor.WHITE);
        }
        if (instance.getGameListener() != null) instance.getGameListener().resetColor();

        for (Player p : score.keySet()) {
            p.getInventory().clear();
            if (builder == null || !builder.equals(p)) giveWaitingItems(p);
        }

        getNextBuilder();

        if (builder == null) return;

        messenger.playSoundsForAll(instance.getConfig().getStringList("game.newround.sounds"));

        int roundTime = instance.getConfig().getInt("game.round-time", 75);
        List<Integer> gameBroadcastShowtime = instance.getConfig().getIntegerList("game.broadcast.showtime");
        if (gameBroadcastShowtime != null && !gameBroadcastShowtime.isEmpty()) {
            List<String> gameSoundList = instance.getConfig().getStringList("game.broadcast.sound");
            messenger.runCountdownBroadcast(roundTime, gameBroadcastShowtime, gameSoundList, null, null, 0, 0, 0, "%time%", false);
        }

        List<Integer> hints = instance.getConfig().getIntegerList("game.hints");
        if (hints == null || hints.isEmpty()) hints = Arrays.asList(40, 50, 55);
        for (int h : hints) { TaskAddLetter t = new TaskAddLetter(this); t.runTaskLater(instance, h * 20L); tasks.add(t); }
        roundEndTime = System.currentTimeMillis() + roundTime * 1000L;
        TaskNextRound endRound = new TaskNextRound(this);
        endRound.runTaskLater(instance, roundTime * 20L); tasks.add(endRound);
        TaskXpCountdown xpTask = new TaskXpCountdown(this);
        xpTask.runTaskTimer(instance, 20L, 20L); tasks.add(xpTask);
        TaskAlert endMsg = new TaskAlert(messenger.formatMsg("time-up", null, "%round_now%", String.valueOf(roundCount), "%paintname%", word), getPlayers(), this);
        endMsg.runTaskLater(instance, roundTime * 20L); tasks.add(endMsg);
        int refresh = instance.getConfig().getInt("scoreboard.gaming.refresh", 20);
        TaskActionBar abTask = new TaskActionBar(this, instance);
        abTask.runTaskTimer(instance, 20L, Math.max(refresh, 10)); tasks.add(abTask);
        if (instance.getConfig().getBoolean("force-clear-weather", true)) clearWeather();
    }

    private String getNewWord() { return instance.getRandomWord(); }

    public void addRandomLetter() {
        if (word == null || wordf == null) return;
        int len = word.length();
        int maxHints;
        if (len <= 2) return;
        else if (len == 3) maxHints = 1;
        else if (len <= 6) maxHints = 2;
        else maxHints = 3;
        if (hintCount >= maxHints) return;
        hintCount++;
        Random r = new Random();
        int idx = r.nextInt(word.length());
        wordf.setCharAt(idx, word.charAt(idx));
    }

    public void wordFoundBy(Player player) {
        if (!acceptWords || hasFound.contains(player)) {
            if (hasFound.contains(player)) ChatUtil.sendMsg(player, "already-found");
            return;
        }
        hasFound.add(player);
        messenger.playSoundForAll(Sound.NOTE_PIANO, 1.0f, 1.0f);
        int gained = 0;
        if (!wordHasBeenFound) {
            messenger.sendMsg("first-found", "%player%", player.getName());
            if (builder != null) ChatUtil.sendMsg(builder, "builder-found-notify");
            increaseScore(player, 3);
            gained = 3;
            if (builder != null) increaseScore(builder, 2);
            wordHasBeenFound = true;
        } else {
            messenger.sendMsg("other-found", "%player%", player.getName());
            increaseScore(player, 1);
            gained = 1;
        }
        roundGainedScore.put(player, roundGainedScore.containsKey(player) ? roundGainedScore.get(player) + gained : gained);
        messenger.sendGuessedTitle(player);
        playerFound++;
        int allFoundDelay = instance.getConfig().getInt("game.all-found-delay", 5);
        if (playerFound >= players - 1) {
            messenger.sendMsg("all-found-next", "%round_now%", String.valueOf(roundCount), "%paintname%", word);
            cancelTasks();
            roundEndTime = System.currentTimeMillis() + allFoundDelay * 1000L;
            TaskNextRound endRound = new TaskNextRound(this);
            endRound.runTaskLater(instance, allFoundDelay * 20L); tasks.add(endRound);
            TaskXpCountdown xpTask = new TaskXpCountdown(this);
            xpTask.runTaskTimer(instance, 20L, 20L); tasks.add(xpTask);
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
        for (int i = 0; i < round; i++) {
            for (Player p : hasBeenBuilder.keySet()) {
                if (hasBeenBuilder.get(p) <= i) {
                    roundCount++;
                    setBuilder(p);
                    return;
                }
            }
        }

        List<Player> sorted = new ArrayList<Player>(score.keySet());
        java.util.Collections.sort(sorted, new java.util.Comparator<Player>() {
            public int compare(Player a, Player b) { return score.get(b) - score.get(a); }
        });
        String t1 = sorted.size() > 0 ? sorted.get(0).getName() : "-";
        String t2 = sorted.size() > 1 ? sorted.get(1).getName() : "-";
        String t3 = sorted.size() > 2 ? sorted.get(2).getName() : "-";
        messenger.sendMsg("end-top", "%t1player%", t1, "%t2player%", t2, "%t3player%", t3);
        startAftergame();
    }

    private void setBuilder(Player p) {
        builder = p;
        hasBeenBuilder.put(p, hasBeenBuilder.get(p) + 1);
        final Player fp = p;
        int delay = instance.getConfig().getInt("game.builder-teleport-delay", 1);
        int freezeTime = instance.getConfig().getInt("game.builderToPlanter.time", 0);
        builderFreezeEndTime = freezeTime > 0 ? System.currentTimeMillis() + (delay + freezeTime) * 1000L : 0;
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
        for (Player other : score.keySet()) {
            if (!other.equals(p)) {
                giveWaitingItems(other);
                if (spawn != null) other.teleport(spawn);
            }
        }
        messenger.sendMsg("round-started", "%builder_player%", p.getName());
        String showMsg = messenger.formatMsg("show-paintname", builder, "%paintname%", word);
        if (showMsg.contains("\n")) {
            for (String line : showMsg.split("\n")) builder.sendMessage(line);
        } else {
            builder.sendMessage(showMsg);
        }
    }

    public void increaseScore(Player p, int value) {
        if (score.containsKey(p)) {
            score.put(p, score.get(p) + value);
        }
    }

    private void startAftergame() {
        aftergameDuration = instance.getConfig().getInt("aftergame.time", 10);
        if (aftergameDuration <= 0) { stop(); return; }
        aftergameRunning = true;
        aftergameStartTime = System.currentTimeMillis();
        acceptWords = false;
        roundEndTime = 0;
        scoreboard.startEndingScoreboard();
        List<Integer> showtime = instance.getConfig().getIntegerList("aftergame.showtime");
        List<String> soundList = instance.getConfig().getStringList("aftergame.broadcast.sound");
        String titleStr = instance.getConfig().getString("aftergame.broadcast.title", "&c%endtime%");
        String subtitleStr = instance.getConfig().getString("aftergame.broadcast.subtitle", null);
        int fadeIn = instance.getConfig().getInt("aftergame.broadcast.fade-in", 5);
        int stay = instance.getConfig().getInt("aftergame.broadcast.stay", 10);
        int fadeOut = instance.getConfig().getInt("aftergame.broadcast.fade-out", 5);
        messenger.runCountdownBroadcast(aftergameDuration, showtime, soundList, titleStr, subtitleStr, fadeIn, stay, fadeOut, "%endtime%", true);
        new BukkitRunnable() {
            public void run() { stopAftergame(); }
        }.runTaskLater(instance, aftergameDuration * 20L);
    }

    private void stopAftergame() {
        if (!aftergameRunning) return;
        aftergameRunning = false;
        aftergameStartTime = 0;
        stop();
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
        started = false; waiting = false; countdownStartTime = 0; aftergameRunning = false; aftergameStartTime = 0; roundEndTime = 0; builderFreezeEndTime = 0; cancelTasks(); scoreboard.clearScoreboard();
        builder = null; word = null; wordf = null;
        hasFound.clear(); roundGainedScore.clear(); hasBeenBuilder.clear(); hintCount = 0;
        if (buildzone != null) { buildzone.clear(); buildzone.setWool(DyeColor.WHITE); }
        List<Player> toKick = new ArrayList<Player>(score.keySet());
        for (Player p : toKick) leave(p);
    }

    public void sendMessage(String message) { messenger.sendMessage(message); }
    public void sendMsg(String key, String... replacements) { messenger.sendMsg(key, replacements); }

    public void registerSign(Block block, String display) { signManager.registerSign(block, display); }
    public void removeSign(Block block) { signManager.removeSign(block); }
    public void updateSigns() { signManager.updateSigns(); }

    public void save(FileConfiguration file) { GameSerializer.save(this, file); }
    public void remove(FileConfiguration file) { stop(); file.set("games." + getName(), null); }

    public static Game load(FileConfiguration file, String name, DrawMyThing instance) { return GameSerializer.load(file, name, instance); }
    public static Game createEmpty(String name, World world, DrawMyThing instance) { return GameSerializer.createEmpty(name, world, instance); }
}