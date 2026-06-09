package com.mrstart.dmt;

import com.mrstart.dmt.game.Game;
import com.mrstart.dmt.game.GameListener;
import com.mrstart.dmt.sign.SignListener;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DrawMyThing extends JavaPlugin {

    private List<Game> games = new ArrayList<Game>();
    private List<String> words = new ArrayList<String>();
    public Inventory cci;
    private GameListener gameListener;

    private ItemStack leaveItem;
    private ItemStack pencilItem;
    private ItemStack bigPencilItem;
    private ItemStack eraserItem;
    private ItemStack colorPickerItem;

    private File gamesFile;
    private FileConfiguration gamesConfig;
    private File msgFile;
    private FileConfiguration msgConfig;
    private BukkitRunnable weatherTask = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupGamesFile();
        setupMessageFile();
        ChatUtil.init(this);

        initItems();
        loadWords();
        loadGames();

        if (getConfig().getBoolean("autojoin.bungee", false)) {
            getConfig().set("autojoin.enabled", true);
            getConfig().set("autojoin.randomjoin", true);
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }

        cci = Bukkit.createInventory(null, 18,
                ChatUtil.colorize(getConfig().getString("color-picker.title", "选择画笔颜色")));
        initColorItems();

        PluginManager pm = getServer().getPluginManager();
        gameListener = new GameListener(this);
        pm.registerEvents(gameListener, this);
        pm.registerEvents(new SignListener(this), this);

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

    public FileConfiguration getMsgConfig() { return msgConfig; }
    public FileConfiguration getGamesConfig() { return gamesConfig; }

    private void saveGamesFile() {
        try { gamesConfig.save(gamesFile); }
        catch (IOException e) { getLogger().warning("Could not save games.yml: " + e.getMessage()); }
    }

    private void loadWords() {
        words.clear();
        List<String> wordList = getConfig().getStringList("words");
        if (wordList != null && !wordList.isEmpty()) words.addAll(wordList);
    }

    private void loadGames() {
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

    public String getRandomWord() {
        if (words.isEmpty()) return "unknown";
        return words.get(new Random().nextInt(words.size()));
    }

    public Game getGameByName(String name) {
        for (Game game : games) { if (game.getName().equalsIgnoreCase(name)) return game; }
        return null;
    }

    public List<Game> getGames() { return games; }
    public GameListener getGameListener() { return gameListener; }

    private void initItems() {
        leaveItem = buildItemFromConfig("item.leave", Material.BED, (short) 14, "&c返回大厅", null);
        pencilItem = buildItemFromConfig("item.pencil", Material.WOOD_SWORD, (short) 0, "&a默认画笔", null);
        bigPencilItem = buildItemFromConfig("item.big-pencil", Material.IRON_SWORD, (short) 0, "&a&l粗画笔", null);
        eraserItem = buildItemFromConfig("item.eraser", Material.TNT, (short) 0, "&c清空画布", null);
        colorPickerItem = buildItemFromConfig("item.color-picker", Material.BUCKET, (short) 0, "&9填充画布", null);
    }

    private ItemStack buildItemFromConfig(String path, Material defMat, short defData, String defName, List<String> defLore) {
        FileConfiguration cfg = getConfig();
        Material mat = defMat;
        String matStr = cfg.getString(path + ".material", null);
        if (matStr != null) {
            try { mat = Material.valueOf(matStr.toUpperCase()); }
            catch (IllegalArgumentException e) {
                try { mat = Material.getMaterial(Integer.parseInt(matStr)); } catch (Exception ignored) {}
            }
        }
        short data = defData;
        if (cfg.contains(path + ".data")) data = (short) cfg.getInt(path + ".data");

        ItemStack item = new ItemStack(mat, 1, data);
        ItemMeta meta = item.getItemMeta();
        String name = cfg.getString(path + ".name", null);
        if (name != null) meta.setDisplayName(ChatUtil.colorize(name));
        else if (defName != null) meta.setDisplayName(ChatUtil.colorize(defName));

        List<String> lore = cfg.getStringList(path + ".lore");
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<String>();
            for (String line : lore) coloredLore.add(ChatUtil.colorize(line));
            meta.setLore(coloredLore);
        } else if (defLore != null) meta.setLore(defLore);

        item.setItemMeta(meta);
        return item;
    }

    public void giveConfigItem(Player player, String path, ItemStack item) {
        int slot = getConfig().getInt(path + ".slot", 0);
        player.getInventory().setItem(slot, item);
    }

    public ItemStack getLeaveItem() { return leaveItem; }
    public ItemStack getPencilItem() { return pencilItem; }
    public ItemStack getBigPencilItem() { return bigPencilItem; }
    public ItemStack getEraserItem() { return eraserItem; }
    public ItemStack getColorPickerItem() { return colorPickerItem; }

    private void initColorItems() {
        cci.clear();
        ConfigurationSection colorSection = getConfig().getConfigurationSection("color-picker.colors");
        if (colorSection != null) {
            for (String key : colorSection.getKeys(false)) {
                ConfigurationSection c = colorSection.getConfigurationSection(key);
                if (c == null) continue;
                int woolData = c.getInt("wool", 0);
                String colorName = ChatUtil.colorize(c.getString("name", "&f颜色"));
                int slot = c.getInt("slot", 0);
                ItemStack item = new ItemStack(Material.WOOL, 1, (short) woolData);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(colorName);
                item.setItemMeta(meta);
                if (slot >= 0 && slot < 18) cci.setItem(slot, item);
            }
        } else {
            String[] names = {"&f白色", "&6橙色", "&5品红色", "&b淡蓝色", "&e黄色", "&a黄绿色",
                    "&d粉红色", "&8灰色", "&7淡灰色", "&3青色", "&5紫色", "&9蓝色", "&6棕色", "&2绿色", "&c红色", "&0黑色"};
            for (int i = 0; i < 16; i++) {
                ItemStack item = new ItemStack(Material.WOOL, 1, (short) i);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatUtil.colorize(names[i]));
                item.setItemMeta(meta);
                cci.setItem(i, item);
            }
        }
    }

    public static DyeColor woolDataToDyeColor(byte data) {
        DyeColor[] colors = DyeColor.values();
        if (data >= 0 && data < colors.length) return colors[data];
        return DyeColor.WHITE;
    }

    @Override
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
        if (getGameByName(gameName) != null) { ChatUtil.sendMsg(player, "game-already-exists", "%game%", gameName); return; }

        World world = player.getWorld();
        Game game = Game.createEmpty(gameName, world, this);
        games.add(game);
        game.save(gamesConfig);
        saveGamesFile();
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
        Game game = getGameByName(gameName);
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

        game.save(gamesConfig);
        saveGamesFile();
    }

    /**
     * /dmt setspawn <gameName>       — 设置观战出生点
     * /dmt setbuilderspawn <gameName> — 设置绘画者出生点
     */
    private void handleSetSpawn(Player player, String[] args, String type) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        Game game = getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }

        Location loc = player.getLocation();

        if (type.equals("spawn")) {
            game.setSpawn(loc);
            ChatUtil.sendMsg(player, "spawn-set", "%game%", gameName);
        } else {
            game.setBuilderSpawn(loc);
            ChatUtil.sendMsg(player, "builder-spawn-set", "%game%", gameName);
        }

        game.save(gamesConfig);
        saveGamesFile();
    }

    /**
     * /dmt setlobby <gameName> — 设置等待大厅位置
     */
    private void handleSetLobby(Player player, String[] args) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        Game game = getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }

        Location loc = player.getLocation();
        game.setLobby(loc);
        game.save(gamesConfig);
        saveGamesFile();
        ChatUtil.sendMsg(player, "lobby-set", "%game%", gameName);
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        String gameName = joinArgs(args, 1);
        Game game = getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }
        game.remove(gamesConfig);
        games.remove(game);
        saveGamesFile();
        ChatUtil.sendMsg(player, "game-deleted", "%game%", gameName);
    }

    private void handleSetMax(Player player, String[] args) {
        if (args.length < 3) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        Game game = getGameByName(args[1]);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", args[1]); return; }
        try {
            int max = Integer.parseInt(args[2]);
            game.setMaxPlayers(max);
            game.save(gamesConfig); saveGamesFile();
            ChatUtil.sendMsg(player, "game-set-max", "%game%", args[1], "%value%", String.valueOf(max));
        } catch (NumberFormatException e) { ChatUtil.sendMsg(player, "unknown-command"); }
    }

    private void handleSetMin(Player player, String[] args) {
        if (args.length < 3) { ChatUtil.sendMsg(player, "game-name-required"); return; }
        Game game = getGameByName(args[1]);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", args[1]); return; }
        try {
            int min = Integer.parseInt(args[2]);
            game.setMinPlayers(min);
            game.save(gamesConfig); saveGamesFile();
            ChatUtil.sendMsg(player, "game-set-min", "%game%", args[1], "%value%", String.valueOf(min));
        } catch (NumberFormatException e) { ChatUtil.sendMsg(player, "unknown-command"); }
    }

    private void handleJoin(Player player, String[] args) {
        if (player.hasMetadata("inbmt")) { ChatUtil.sendMsg(player, "already-in-game"); return; }
        if (args.length < 2) {
            if (getConfig().getBoolean("autojoin.enabled", false)) handleAutoJoin(player);
            else ChatUtil.sendMsg(player, "game-name-required");
            return;
        }
        String gameName = joinArgs(args, 1);
        Game game = getGameByName(gameName);
        if (game == null) { ChatUtil.sendMsg(player, "game-not-found", "%game%", gameName); return; }
        game.join(player);
    }

    private void handleAutoJoin(Player player) {
        if (getConfig().getBoolean("autojoin.randomjoin", false)) {
            List<Game> available = new ArrayList<Game>();
            for (Game g : games) { if (!g.isStarted() && g.getPlayerCount() < g.getMaxPlayers()) available.add(g); }
            if (available.isEmpty()) { ChatUtil.sendMsg(player, "auto-join-no-game"); return; }
            available.get(new Random().nextInt(available.size())).join(player);
        } else {
            String target = getConfig().getString("autojoin.joingame", "");
            if (target.isEmpty()) { ChatUtil.sendMsg(player, "auto-join-no-game"); return; }
            Game game = getGameByName(target);
            if (game == null) { ChatUtil.sendMsg(player, "auto-join-failed"); return; }
            game.join(player);
        }
    }

    private void handleLeave(Player player) {
        if (!player.hasMetadata("inbmt")) { ChatUtil.sendMsg(player, "not-in-game"); return; }
        String gameName = ((MetadataValue) player.getMetadata("inbmt").get(0)).asString();
        Game game = getGameByName(gameName);
        if (game != null) game.leave(player);
    }

    private void handleReload(Player player) {
        for (Game game : games) game.stop();
        games.clear();
        reloadConfig();
        msgConfig = YamlConfiguration.loadConfiguration(msgFile);
        gamesConfig = YamlConfiguration.loadConfiguration(gamesFile);
        initItems();
        initColorItems();
        loadWords();
        loadGames();
        if (weatherTask != null) weatherTask.cancel();
        if (getConfig().getBoolean("force-clear-weather", true)) startWeatherLock();
        ChatUtil.sendMsg(player, "game-reloaded");
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatUtil.colorize("&e&lDrawMyThing &r&7for &b&lYanYuTing"));
        if (player.hasPermission("dmt.admin")) {
            player.sendMessage(ChatUtil.colorize("&f/dmt create <gameName> &7- 创建游戏"));
            player.sendMessage(ChatUtil.colorize("&f/dmt delete <gameName> &7- 删除游戏"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setcanvaspos1 <gameName> &7- 设置画布坐标1"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setcanvaspos2 <gameName> &7- 设置画布坐标2"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setspawn <gameName> &7- 设置观战出生点"));
            player.sendMessage(ChatUtil.colorize("&f/dmt setbuilderspawn <gameName> &7- 设置绘画者出生点"));
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