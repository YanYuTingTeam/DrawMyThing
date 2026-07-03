package com.aermini.dmt;

import com.aermini.dmt.game.Game;
import com.aermini.dmt.game.GameListener;
import com.aermini.dmt.util.ChatUtil;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DrawMyThing extends JavaPlugin {

    private List<Game> games = new ArrayList<Game>();
    private List<List<String>> words = new ArrayList<List<String>>();
    private GameListener gameListener;

    private File gamesFile;
    private FileConfiguration gamesConfig;
    private File msgFile;
    private FileConfiguration msgConfig;
    private File wordsFile;
    private FileConfiguration wordsConfig;
    private BukkitRunnable weatherTask = null;

    private ItemManager itemManager;
    private DMTCommandHandler commandHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupGamesFile();
        setupMessageFile();
        setupWordsFile();
        ChatUtil.init(this);

        itemManager = new ItemManager(this);
        itemManager.initItems();
        loadWords();
        loadGames();

        if (getConfig().getBoolean("autojoin.bungee", false)) {
            getConfig().set("autojoin.enabled", true);
            getConfig().set("autojoin.randomjoin", true);
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }

        itemManager.initColorPicker();

        commandHandler = new DMTCommandHandler(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(commandHandler, this);
        gameListener = new GameListener(this);
        pm.registerEvents(gameListener, this);

        if (getConfig().getBoolean("force-clear-weather", true)) startWeatherLock();

        getLogger().info("DrawMyThing v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        saveAllGames();
        if (weatherTask != null) weatherTask.cancel();
        getLogger().info("DrawMyThing has been disabled!");
    }

    private void setupGamesFile() {
        gamesFile = new File(getDataFolder(), "games.yml");
        if (!gamesFile.exists()) { gamesFile.getParentFile().mkdirs(); saveResource("games.yml", false); }
        gamesConfig = YamlConfiguration.loadConfiguration(gamesFile);
    }

    private void setupMessageFile() {
        msgFile = new File(getDataFolder(), "message.yml");
        if (!msgFile.exists()) { msgFile.getParentFile().mkdirs(); saveResource("message.yml", false); }
        msgConfig = YamlConfiguration.loadConfiguration(msgFile);
    }

    private void setupWordsFile() {
        wordsFile = new File(getDataFolder(), "words.yml");
        if (!wordsFile.exists()) { wordsFile.getParentFile().mkdirs(); saveResource("words.yml", false); }
        wordsConfig = YamlConfiguration.loadConfiguration(wordsFile);
    }

    public FileConfiguration getMsgConfig() { return msgConfig; }
    public FileConfiguration getGamesConfig() { return gamesConfig; }

    public void reloadMsgConfig() { msgConfig = YamlConfiguration.loadConfiguration(msgFile); }
    public void reloadGamesConfig() { gamesConfig = YamlConfiguration.loadConfiguration(gamesFile); }
    public void reloadWordsConfig() { wordsConfig = YamlConfiguration.loadConfiguration(wordsFile); }

    public void saveGamesFile() {
        try { gamesConfig.save(gamesFile); }
        catch (IOException e) { getLogger().warning("Could not save games.yml: " + e.getMessage()); }
    }

    public void loadWords() {
        words.clear();
        List<?> raw = wordsConfig.getList("words");
        if (raw == null) return;
        for (Object o : raw) {
            if (o instanceof String) {
                words.add(Collections.singletonList((String) o));
            } else if (o instanceof List) {
                List<?> inner = (List<?>) o;
                List<String> group = new ArrayList<String>();
                for (Object item : inner) {
                    if (item != null) group.add(String.valueOf(item));
                }
                if (!group.isEmpty()) words.add(group);
            }
        }
    }

    public void loadGames() {
        games.clear();
        if (gamesConfig.getConfigurationSection("games") == null) return;
        for (String name : gamesConfig.getConfigurationSection("games").getKeys(false)) {
            Game game = Game.load(gamesConfig, name, this);
            if (game != null) { games.add(game); getLogger().info("Loaded game: " + name); }
        }
    }

    private void saveAllGames() {
        gamesConfig.set("games", null);
        for (Game game : games) game.save(gamesConfig);
        saveGamesFile();
    }

    public List<String> getWords() {
        List<String> flat = new ArrayList<String>();
        for (List<String> g : words) flat.addAll(g);
        return flat;
    }

    public String getRandomWord() {
        if (words.isEmpty()) return "unknown";
        List<String> group = words.get(new Random().nextInt(words.size()));
        if (group.isEmpty()) return "unknown";
        return group.get(new Random().nextInt(group.size()));
    }

    public List<List<String>> getWordGroups() { return words; }
    public Game getGameByName(String name) {
        for (Game game : games) { if (game.getName().equalsIgnoreCase(name)) return game; }
        return null;
    }

    public List<Game> getGames() { return games; }
    public GameListener getGameListener() { return gameListener; }
    public ItemManager getItemManager() { return itemManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return commandHandler.onCommand(sender, cmd, label, args);
    }

    private void startWeatherLock() {
        weatherTask = new BukkitRunnable() {
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    if (world.hasStorm()) { world.setStorm(false); world.setThundering(false); world.setWeatherDuration(999999); }
                    if (world.isThundering()) world.setThundering(false);
                }
            }
        };
        weatherTask.runTaskTimer(this, 100L, 200L);
    }

    public void restartWeatherLock() {
        if (weatherTask != null) weatherTask.cancel();
        if (getConfig().getBoolean("force-clear-weather", true)) startWeatherLock();
    }

    public void sendToServer(Player player, String server) {
        try {
            com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
        } catch (Exception e) {
            getLogger().warning("无法将玩家 " + player.getName() + " 传送至子服 " + server + ": " + e.getMessage());
        }
    }

    public boolean isBungeeMode() {
        return getConfig().getBoolean("autojoin.bungee", false);
    }

    public String getBungeeLobby() {
        return getConfig().getString("autojoin.lobby", "lobby");
    }
}