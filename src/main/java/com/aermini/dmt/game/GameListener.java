package com.aermini.dmt.game;

import com.aermini.dmt.util.ChatUtil;
import com.aermini.dmt.DrawMyThing;
import com.aermini.dmt.ItemManager;
import com.aermini.dmt.cuboid.CuboidZone;
import com.aermini.dmt.util.SoundCompat;
import com.aermini.dmt.util.DyeColorCompat;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameListener implements Listener {
    private DyeColor pencilColor = DyeColor.BLACK;
    private DrawMyThing instance;
    private Map<Player, BukkitTask> paintTasks = new HashMap<Player, BukkitTask>();
    private Map<Player, Block> lastPaintedBlock = new HashMap<Player, Block>();
    private Map<Player, Long> freezeSoundCooldown = new HashMap<Player, Long>();
    private Map<Player, Long> paintTaskStartTime = new HashMap<Player, Long>();
    private Map<Player, Long> lastPaintPacketAt = new HashMap<Player, Long>();
    private static final long PAINT_PACKET_TIMEOUT = 300L;

    public GameListener(DrawMyThing instance) {
        this.instance = instance;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!instance.isBungeeMode()) return;
        List<Game> games = instance.getGames();
        if (games.isEmpty()) return;
        Game game = games.get(0);
        if (event.getPlayer().hasPermission("dmt.spec")) return;
        if (game.isStarted()) {
            String msg;
            if (game.isAftergameRunning()) {
                msg = instance.getConfig().getString("kick.ending", "&c游戏正在结束, 无法加入");
            } else {
                msg = instance.getConfig().getString("kick.start", "&c游戏已开始, 无法加入");
            }
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatUtil.colorize(msg));
        } else if (game.getPlayerCount() >= game.getMaxPlayers()) {
            String msg = instance.getConfig().getString("kick.full", "&c房间已满, 无法加入");
            event.disallow(PlayerLoginEvent.Result.KICK_FULL, ChatUtil.colorize(msg));
        }
    }


    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        if (!instance.isBungeeMode()) return;
        List<Game> games = instance.getGames();
        if (games.isEmpty()) return;
        Game game = games.get(0);
        event.setMaxPlayers(game.getMaxPlayers());
        String motdWait = instance.getConfig().getString("motd.wait", "");
        String motdFull = instance.getConfig().getString("motd.full", "");
        String motdStart = instance.getConfig().getString("motd.start", "");
        String motdEnd = instance.getConfig().getString("motd.ending", "");
        String motd;
        if (game.isStarted() && game.isAftergameRunning()) motd = motdEnd;
        else if (game.isStarted()) motd = motdStart;
        else if (game.getPlayerCount() >= game.getMaxPlayers()) motd = motdFull;
        else motd = motdWait;

        event.setMotd(motd);
    }

    @EventHandler
    public void onPlayerLogOut(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelPaintTask(player);
        freezeSoundCooldown.remove(player);
        if (player.hasMetadata("inbmt")) {
            String gameName = getMeta(player, "inbmt");
            Game game = instance.getGameByName(gameName);
            if (game != null) game.leave(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!instance.getConfig().getBoolean("autojoin.enabled", false)) return;
        final Player player = event.getPlayer();
        new BukkitRunnable() {
            public void run() {
                if (!player.isOnline() || player.hasMetadata("inbmt")) return;
                if (instance.getConfig().getBoolean("autojoin.randomjoin", false)) {
                    java.util.List<Game> available = new java.util.ArrayList<Game>();
                    for (Game g : instance.getGames()) { if (!g.isStarted() && g.getPlayerCount() < g.getMaxPlayers()) available.add(g); }
                    if (!available.isEmpty()) available.get(new java.util.Random().nextInt(available.size())).join(player);
                } else {
                    String target = instance.getConfig().getString("autojoin.joingame", "");
                    if (target.isEmpty()) return;
                    Game game = instance.getGameByName(target);
                    if (game != null) game.join(player);
                }
            }
        }.runTaskLater(instance, 5L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isInGame(player)) return;
        Game game = getGame(player);
        if (game == null || !game.isStarted()) return;
        if (game.getBuilder() == null || !game.getBuilder().equals(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        long freezeEnd = game.getBuilderFreezeEndTime();
        boolean frozen = freezeEnd > 0 && System.currentTimeMillis() < freezeEnd;
        boolean outOfBounds = !game.isInBuilderBoundary(to);

        if (frozen) {
            Location bspawn = game.getBuilderSpawn();
            if (bspawn != null) player.teleport(bspawn);
            long now = System.currentTimeMillis();
            Long last = freezeSoundCooldown.get(player);
            if (last == null || now - last >= 500) {
                freezeSoundCooldown.put(player, now);
                String soundStr = instance.getConfig().getString("game.builderToPlanter.sound", "NOTE_BASS,1,1");
                try {
                    String[] sp = soundStr.split(",");
                    Sound sound = SoundCompat.getSound(sp[0].trim());
                    if (sound == null) return;
                    float volume = sp.length > 1 ? Float.parseFloat(sp[1].trim()) : 1.0f;
                    float pitch = sp.length > 2 ? Float.parseFloat(sp[2].trim()) : 1.0f;
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception ignored) {}
            }
        } else if (outOfBounds) {
            Location bspawn = game.getBuilderSpawn();
            if (bspawn != null) player.teleport(bspawn);
        }
    }

    @EventHandler
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isInGame(player)) return;
        event.setCancelled(true);
        Game game = getGame(player);
        if (game == null || !game.getBuilder().equals(player)) return;
        ItemStack item = event.getItemInHand();
        if (isEraserItem(item)) {
            CuboidZone zone = game.getBuildZone();
            if (zone != null) zone.setWool(DyeColor.WHITE);
            cancelPaintTask(player);
            playItemSound(game, player, "item.eraser", SoundCompat.getSound("EXPLODE"));
        } else if (isColorPickerItem(item)) {
            CuboidZone zone = game.getBuildZone();
            if (zone != null) zone.setWool(pencilColor);
            playItemSound(game, player, "item.color-picker", SoundCompat.getSound("SPLASH"));
        }
    }

    @EventHandler
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        if (isInGame(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPistonPush(BlockPistonExtendEvent event) {
        for (Game game : instance.getGames()) {
            if (!game.isStarted()) continue;
            CuboidZone zone = game.getBuildZone();
            if (zone == null) continue;
            for (Block block : event.getBlocks()) {
                if (zone.contains(block)) { event.setCancelled(true); return; }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isInGame(player)) return;
        Game game = getGame(player);
        if (game == null) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            if (game.backToSpawn(player)) {
                player.setFallDistance(0.0f);
                event.setCancelled(true);
            }
            return;
        }
        if (instance.getConfig().getBoolean("no-damage", true)) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        if (isInGame(attacker)) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerHungerChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (isInGame(player)) {
            if (instance.getConfig().getBoolean("no-hunger", true)) {
                event.setCancelled(true);
                player.setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isInGame(player)) return;
        Game game = getGame(player);
        if (game == null) return;
        ItemStack item = player.getItemInHand();
        if (item == null) return;
        Action action = event.getAction();
        if (game.getBuilder() == null || !game.getBuilder().equals(player)) {
            if (isLeaveItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
                game.leave(player);
                return;
            }
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            return;
        }

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            cancelPaintTask(player);
            if (isPencilItem(item) || isBigPencilItem(item) || isColorPickerItem(item)) {
                player.openInventory(instance.getItemManager().cci);
            }
            return;
        }

        if (isPencilItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            startPaintTask(player, game, false);
            lastPaintPacketAt.put(player, System.currentTimeMillis());
            Block b = rayTraceWool(player, 100);
            if (b == null || b.getType() != Material.WOOL) { lastPaintedBlock.remove(player); return; }
            CuboidZone zone = game.getBuildZone();
            if (zone != null && !zone.contains(b)) { lastPaintedBlock.remove(player); return; }
            byte data = DyeColorCompat.getData(pencilColor);
            Block last = lastPaintedBlock.get(player);
            if (last == null) {
                b.setTypeIdAndData(Material.WOOL.getId(), data, true);
                playPencilSound(player);
            } else if (!last.equals(b)) {
                paintLine(last, b, data, zone, false, player);
            }
            lastPaintedBlock.put(player, b);
            return;
        }

        if (isBigPencilItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            startPaintTask(player, game, true);
            lastPaintPacketAt.put(player, System.currentTimeMillis());
            Block b = rayTraceWool(player, 100);
            if (b == null || b.getType() != Material.WOOL) { lastPaintedBlock.remove(player); return; }
            CuboidZone zone = game.getBuildZone();
            if (zone != null && !zone.contains(b)) { lastPaintedBlock.remove(player); return; }
            byte data = DyeColorCompat.getData(pencilColor);
            Block last = lastPaintedBlock.get(player);
            if (last == null) {
                b.setTypeIdAndData(Material.WOOL.getId(), data, true);
                paintBigNeighbors(b, data, zone, getPlayerFacing(player), player.getLocation().getPitch());
                playPencilSound(player);
            } else if (!last.equals(b)) {
                paintLine(last, b, data, zone, true, player);
            }
            lastPaintedBlock.put(player, b);
            return;
        }

        if (isEraserItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            CuboidZone zone = game.getBuildZone();
            if (zone != null) zone.setWool(DyeColor.WHITE);
            cancelPaintTask(player);
            playItemSound(game, player, "item.eraser", SoundCompat.getSound("EXPLODE"));
            return;
        }

        if (isColorPickerItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            CuboidZone zone = game.getBuildZone();
            if (zone != null) zone.setWool(pencilColor);
            playItemSound(game, player, "item.color-picker", SoundCompat.getSound("SPLASH"));
            return;
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isInGame(player)) return;
        Game game = getGame(player);
        if (game == null || !game.isStarted()) return;
        event.setCancelled(true);
        if (game.getBuilder() != null && game.getBuilder().equals(player)) {
            ChatUtil.sendMsg(player, "drawer-no-chat");
            return;
        }
        if (game.hasFound(player)) {
            ChatUtil.sendMsg(player, "already-found");
            return;
        }
        String word = game.getWord();
        if (word != null && game.isAcceptWords() && event.getMessage().toLowerCase().contains(word.toLowerCase())) {
            game.wordFoundBy(player);
        } else {
            String chatFormat = instance.getConfig().getString("chat.format", "&l%player%: &r%message%");
            String chatMsg = ChatUtil.colorize(game.getMessenger().replacePlaceholders(chatFormat, player)).replace("%message%", event.getMessage().toLowerCase());
            game.sendMessage(chatMsg);
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isInGame(player) && !event.getMessage().startsWith("/dmt") && !player.hasPermission("dmt.admin")) {
            ChatUtil.sendMsg(player, "no-permission");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isInGame(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!isInGame(player)) return;
        if (event.getInventory().equals(instance.getItemManager().cci)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked.getType() == Material.WOOL) {
                byte woolData = (byte)clicked.getDurability();
                pencilColor = ItemManager.woolDataToDyeColor(woolData);
                player.playSound(player.getLocation(), SoundCompat.getSound("ORB_PICKUP"), 1.0f, 1.0f);
            }
            player.closeInventory();
        } else {
            event.setCancelled(true);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onPlayerItemHeldChange(PlayerItemHeldEvent event) {
        cancelPaintTask(event.getPlayer());
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) cancelPaintTask(event.getPlayer());
    }

    private void startPaintTask(final Player player, final Game game, final boolean big) {
        if (paintTasks.containsKey(player)) return;
        final boolean[] blockingSeen = { false };
        final long[] lastSoundAt = { 0L };
        BukkitTask task = new BukkitRunnable() {
            public void run() {
                if (!player.isOnline() || !isInGame(player)) { cancelPaintTask(player); return; }
                ItemStack hand = player.getItemInHand();
                if (hand == null || (!isPencilItem(hand) && !isBigPencilItem(hand))) { cancelPaintTask(player); return; }

                long now = System.currentTimeMillis();
                boolean blocking = player.isBlocking();
                if (blocking) blockingSeen[0] = true;
                Long lastPacket = lastPaintPacketAt.get(player);
                if (!blocking && (blockingSeen[0] || lastPacket == null || now - lastPacket > PAINT_PACKET_TIMEOUT)) { cancelPaintTask(player); return; }
                Block b = rayTraceWool(player, 100);
                if (b == null || b.getType() != Material.WOOL) { lastPaintedBlock.remove(player); return; }
                CuboidZone zone = game.getBuildZone();
                if (zone != null && !zone.contains(b)) return;
                byte data = DyeColorCompat.getData(pencilColor);
                Block last = lastPaintedBlock.get(player);
                if (last == null) {
                    b.setTypeIdAndData(Material.WOOL.getId(), data, true);
                    if (big) paintBigNeighbors(b, data, zone, getPlayerFacing(player), player.getLocation().getPitch());
                } else if (!last.equals(b)) {
                    paintLine(last, b, data, zone, big, player);
                    if (now - lastSoundAt[0] >= 200L) { playPencilSound(player); lastSoundAt[0] = now; }
                }
                lastPaintedBlock.put(player, b);
            }
        }.runTaskTimer(instance, 1L, 1L);
        paintTasks.put(player, task);
        paintTaskStartTime.put(player, System.currentTimeMillis());
    }

    private void paintLine(Block from, Block to, byte data, CuboidZone zone, boolean big, Player player) {
        int x0 = from.getX(), y0 = from.getY(), z0 = from.getZ();
        int x1 = to.getX(), y1 = to.getY(), z1 = to.getZ();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        BlockFace dir = big ? getPlayerFacing(player) : null;
        float pitch = big ? player.getLocation().getPitch() : 0;
        if (dx >= dy && dx >= dz) {
            int err1 = 2 * dy - dx, err2 = 2 * dz - dx;
            for (int i = 0; i <= dx; i++) {
                Block blk = from.getWorld().getBlockAt(x0, y0, z0);
                if (blk.getType() == Material.WOOL && (zone == null || zone.contains(blk))) {
                    blk.setTypeIdAndData(Material.WOOL.getId(), data, true);
                    if (big) paintBigNeighbors(blk, data, zone, dir, pitch);
                }
                if (x0 == x1 && y0 == y1 && z0 == z1) break;
                x0 += sx; err1 += 2 * dy; err2 += 2 * dz;
                if (err1 >= dx) { err1 -= 2 * dx; y0 += sy; }
                if (err2 >= dx) { err2 -= 2 * dx; z0 += sz; }
            }
        } else if (dy >= dx && dy >= dz) {
            int err1 = 2 * dx - dy, err2 = 2 * dz - dy;
            for (int i = 0; i <= dy; i++) {
                Block blk = from.getWorld().getBlockAt(x0, y0, z0);
                if (blk.getType() == Material.WOOL && (zone == null || zone.contains(blk))) {
                    blk.setTypeIdAndData(Material.WOOL.getId(), data, true);
                    if (big) paintBigNeighbors(blk, data, zone, dir, pitch);
                }
                if (x0 == x1 && y0 == y1 && z0 == z1) break;
                y0 += sy; err1 += 2 * dx; err2 += 2 * dz;
                if (err1 >= dy) { err1 -= 2 * dy; x0 += sx; }
                if (err2 >= dy) { err2 -= 2 * dy; z0 += sz; }
            }
        } else {
            int err1 = 2 * dy - dz, err2 = 2 * dx - dz;
            for (int i = 0; i <= dz; i++) {
                Block blk = from.getWorld().getBlockAt(x0, y0, z0);
                if (blk.getType() == Material.WOOL && (zone == null || zone.contains(blk))) {
                    blk.setTypeIdAndData(Material.WOOL.getId(), data, true);
                    if (big) paintBigNeighbors(blk, data, zone, dir, pitch);
                }
                if (x0 == x1 && y0 == y1 && z0 == z1) break;
                z0 += sz; err1 += 2 * dy; err2 += 2 * dx;
                if (err1 >= dz) { err1 -= 2 * dz; y0 += sy; }
                if (err2 >= dz) { err2 -= 2 * dz; x0 += sx; }
            }
        }
    }

    private void paintBigNeighbors(Block b, byte data, CuboidZone zone, BlockFace dir, float pitch) {
        Block h1, h2, v1, v2;
        if (pitch > 45 || pitch < -45) {
            h1 = b.getRelative(dir.getModX(), 0, dir.getModZ());
            h2 = b.getRelative(-dir.getModX(), 0, -dir.getModZ());
            BlockFace perp = getPerpendicularFace(dir);
            v1 = b.getRelative(perp.getModX(), 0, perp.getModZ());
            v2 = b.getRelative(-perp.getModX(), 0, -perp.getModZ());
        } else {
            h1 = b.getRelative(BlockFace.UP);
            h2 = b.getRelative(BlockFace.DOWN);
            BlockFace perp = getPerpendicularFace(dir);
            v1 = b.getRelative(perp.getModX(), 0, perp.getModZ());
            v2 = b.getRelative(-perp.getModX(), 0, -perp.getModZ());
        }
        if (zone == null || zone.contains(h1)) h1.setTypeIdAndData(Material.WOOL.getId(), data, true);
        if (zone == null || zone.contains(h2)) h2.setTypeIdAndData(Material.WOOL.getId(), data, true);
        if (zone == null || zone.contains(v1)) v1.setTypeIdAndData(Material.WOOL.getId(), data, true);
        if (zone == null || zone.contains(v2)) v2.setTypeIdAndData(Material.WOOL.getId(), data, true);
    }

    private void cancelPaintTask(Player player) {
        BukkitTask task = paintTasks.remove(player);
        if (task != null) task.cancel();
        lastPaintedBlock.remove(player);
        paintTaskStartTime.remove(player);
        lastPaintPacketAt.remove(player);
    }

    public void stopPlayerPaintTask(Player player) {
        cancelPaintTask(player);
    }

    public void stopAllPaintTasks() {
        for (Player p : new java.util.ArrayList<Player>(paintTasks.keySet())) cancelPaintTask(p);
        lastPaintedBlock.clear();
        freezeSoundCooldown.clear();
        paintTaskStartTime.clear();
        lastPaintPacketAt.clear();
    }

    private boolean matchItem(ItemStack item, String configPath, Material defaultMat, short defaultData) {
        if (item == null || item.getType() == Material.AIR) return false;
        String matStr = instance.getConfig().getString(configPath + ".material", null);
        Material mat = defaultMat;
        if (matStr != null) {
            try { mat = Material.valueOf(matStr.toUpperCase()); }
            catch (IllegalArgumentException e) {
                try { mat = Material.getMaterial(Integer.parseInt(matStr)); }
                catch (Exception ignored) {}
            }
        }

        short data = defaultData;
        if (instance.getConfig().contains(configPath + ".data")) {
            data = (short) instance.getConfig().getInt(configPath + ".data");
        }
        return item.getType() == mat && item.getDurability() == data;
    }

    private boolean isLeaveItem(ItemStack item) { return matchItem(item, "item.leave", Material.BED, (short) 14); }
    private boolean isPencilItem(ItemStack item) { return matchItem(item, "item.pencil", Material.STICK, (short) 0); }
    private boolean isBigPencilItem(ItemStack item) { return matchItem(item, "item.big-pencil", Material.BLAZE_ROD, (short) 0); }
    private boolean isEraserItem(ItemStack item) { return matchItem(item, "item.eraser", Material.TNT, (short) 0); }
    private boolean isColorPickerItem(ItemStack item) { return matchItem(item, "item.color-picker", Material.COMPASS, (short) 0); }
    private boolean isInGame(Player player) { return player.hasMetadata("inbmt"); }

    private Block rayTraceWool(Player player, int maxDistance) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().normalize();
        double step = 0.5;
        for (double d = 0; d < maxDistance; d += step) {
            Location point = eye.clone().add(dir.clone().multiply(d));
            Block b = point.getBlock();
            if (b.getType() == Material.WOOL) return b;
            if (b.getType() != Material.AIR) return null;
        }
        return null;
    }

    private Game getGame(Player player) {
        String name = getMeta(player, "inbmt");
        return name != null ? instance.getGameByName(name) : null;
    }

    private String getMeta(Player player, String key) {
        if (!player.hasMetadata(key)) return null;
        List<MetadataValue> values = player.getMetadata(key);
        return (values != null && !values.isEmpty()) ? values.get(0).asString() : null;
    }

    private BlockFace getPlayerFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        else if (yaw < 135) return BlockFace.WEST;
        else if (yaw < 225) return BlockFace.NORTH;
        else return BlockFace.EAST;
    }

    private BlockFace getPerpendicularFace(BlockFace face) {
        switch (face) {
            case NORTH: case SOUTH: return BlockFace.EAST;
            case EAST: case WEST: return BlockFace.NORTH;
            default: return BlockFace.EAST;
        }
    }

    private void playItemSound(Game game, Player player, String configPath, Sound defaultSound) {
        if (!instance.getConfig().getBoolean("tool-sound", true)) return;
        String soundStr = instance.getConfig().getString(configPath + ".sound", null);
        Sound sound = defaultSound;
        float volume = 1.0f;
        float pitch = 1.0f;
        if (soundStr != null && !soundStr.isEmpty()) {
            try {
                String[] parts = soundStr.split(",");
                Sound parsed = SoundCompat.getSound(parts[0].trim());
                if (parsed != null) sound = parsed;
                if (parts.length > 1) volume = Float.parseFloat(parts[1].trim());
                if (parts.length > 2) pitch = Float.parseFloat(parts[2].trim());
            } catch (Exception ignored) {}
        }
        for (Player p : game.getPlayers()) {
            p.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public void setPencilColor(DyeColor color) { this.pencilColor = color; }
    public DyeColor getColor() { return pencilColor; }
    public void resetColor() { this.pencilColor = DyeColor.BLACK; }

    private void playPencilSound(Player player) {
        if (!instance.getConfig().getBoolean("tool-sound", true)) return;
        ItemStack hand = player.getItemInHand();
        if (hand == null) return;
        String configPath = isBigPencilItem(hand) ? "item.big-pencil" : "item.pencil";
        Sound sound = SoundCompat.getSound("FIZZ");
        if (sound == null) return;
        float volume = 1.0f;
        float pitch = 1.0f;
        String soundStr = instance.getConfig().getString(configPath + ".sound", null);
        if (soundStr != null && !soundStr.isEmpty()) {
            try {
                String[] parts = soundStr.split(",");
                Sound parsed = SoundCompat.getSound(parts[0].trim());
                if (parsed != null) sound = parsed;
                if (parts.length > 1) volume = Float.parseFloat(parts[1].trim());
                if (parts.length > 2) pitch = Float.parseFloat(parts[2].trim());
            } catch (Exception ignored) {}
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}