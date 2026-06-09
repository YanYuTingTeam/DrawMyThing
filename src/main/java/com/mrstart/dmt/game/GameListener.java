package com.mrstart.dmt.game;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.DrawMyThing;
import com.mrstart.dmt.cuboid.CuboidZone;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class GameListener implements Listener {
    private DyeColor pencilColor = DyeColor.WHITE;
    private DrawMyThing instance;
    private Map<Player, BukkitTask> paintTasks = new HashMap<Player, BukkitTask>();
    private Map<Player, Block> lastPaintedBlock = new HashMap<Player, Block>();

    public GameListener(DrawMyThing instance) {
        this.instance = instance;
    }

    @EventHandler
    public void onPlayerLogOut(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelPaintTask(player);
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
            playItemSound(game, player, "item.eraser", Sound.EXPLODE);
        } else if (isColorPickerItem(item)) {
            CuboidZone zone = game.getBuildZone();
            if (zone != null) zone.setWool(pencilColor);
            playItemSound(game, player, "item.color-picker", Sound.SPLASH);
        }
    }

    @EventHandler
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        if (isInGame(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isInGame(player)) return;
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
            }return;
        }

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (isPencilItem(item) || isBigPencilItem(item) || isColorPickerItem(item)) {
                player.openInventory(instance.cci);
            } else {
                cancelPaintTask(player);
            }
            return;
        }

        if (isPencilItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            cancelPaintTask(player);
            Block b = player.getTargetBlock((HashSet<Byte>) null, 100);
            if (b.getType() != Material.WOOL) return;
            b.setTypeIdAndData(Material.WOOL.getId(), pencilColor.getData(), true);
            lastPaintedBlock.put(player, b);
            playItemSound(game, player, "item.pencil", Sound.FIZZ);
            startPaintTask(player, game, false);
            return;
        }

        if (isBigPencilItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            cancelPaintTask(player);
            Block b = player.getTargetBlock((HashSet<Byte>) null, 100);
            if (b.getType() != Material.WOOL) return;
            CuboidZone zone = game.getBuildZone();
            if (zone != null && !zone.contains(b)) return;
            byte data = pencilColor.getData();
            b.setTypeIdAndData(Material.WOOL.getId(), data, true);
            BlockFace face = event.getBlockFace();
            Block h1, h2, v1, v2;
            if (face == BlockFace.UP || face == BlockFace.DOWN) {
                BlockFace dir = getPlayerFacing(player);
                h1 = b.getRelative(dir.getModX(), 0, dir.getModZ());
                h2 = b.getRelative(-dir.getModX(), 0, -dir.getModZ());
                BlockFace perp = getPerpendicularFace(dir);
                v1 = b.getRelative(perp.getModX(), 0, perp.getModZ());
                v2 = b.getRelative(-perp.getModX(), 0, -perp.getModZ());
            } else {
                h1 = b.getRelative(BlockFace.UP);
                h2 = b.getRelative(BlockFace.DOWN);
                BlockFace dir = getPlayerFacing(player);
                if (face == dir || face == dir.getOppositeFace()) {
                    BlockFace perp = getPerpendicularFace(dir);
                    v1 = b.getRelative(perp.getModX(), 0, perp.getModZ());
                    v2 = b.getRelative(-perp.getModX(), 0, -perp.getModZ());
                } else {
                    v1 = b.getRelative(dir.getModX(), 0, dir.getModZ());
                    v2 = b.getRelative(-dir.getModX(), 0, -dir.getModZ());
                }
            }
            if (zone == null || zone.contains(h1)) h1.setTypeIdAndData(Material.WOOL.getId(), data, true);
            if (zone == null || zone.contains(h2)) h2.setTypeIdAndData(Material.WOOL.getId(), data, true);
            if (zone == null || zone.contains(v1)) v1.setTypeIdAndData(Material.WOOL.getId(), data, true);
            if (zone == null || zone.contains(v2)) v2.setTypeIdAndData(Material.WOOL.getId(), data, true);
            lastPaintedBlock.put(player, b);
            playItemSound(game, player, "item.big-pencil", Sound.FIZZ);
            startPaintTask(player, game, true);
            return;
        }

        if (isEraserItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            CuboidZone zone = game.getBuildZone();
            if (zone != null) zone.setWool(DyeColor.WHITE);
            cancelPaintTask(player);
            playItemSound(game, player, "item.eraser", Sound.EXPLODE);
            return;
        }

        if (isColorPickerItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            CuboidZone zone = game.getBuildZone();
            if (zone != null) zone.setWool(pencilColor);
            playItemSound(game, player, "item.color-picker", Sound.SPLASH);
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
        if (word != null && event.getMessage().toLowerCase().contains(word.toLowerCase())) {
            game.wordFoundBy(player);
        } else {
            game.sendMessage(ChatColor.BOLD + player.getName() + ": " + ChatColor.RESET + event.getMessage().toLowerCase());
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
        if (event.getInventory().equals(instance.cci)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked.getType() == Material.WOOL) {
                byte woolData = (byte)clicked.getDurability();
                pencilColor = DrawMyThing.woolDataToDyeColor(woolData);
                player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1.0f, 1.0f);
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
        BukkitTask task = new BukkitRunnable() {
            public void run() {
                if (!player.isOnline() || !isInGame(player)) { cancelPaintTask(player); return; }
                ItemStack hand = player.getItemInHand();
                if (hand == null || (!isPencilItem(hand) && !isBigPencilItem(hand))) { cancelPaintTask(player); return; }
                if (hand.getType().name().endsWith("_SWORD") && !player.isBlocking()) { cancelPaintTask(player); return; }
                Block b = player.getTargetBlock((HashSet<Byte>) null, 100);
                if (b.getType() != Material.WOOL) return;
                CuboidZone zone = game.getBuildZone();
                if (zone != null && !zone.contains(b)) return;
                byte data = pencilColor.getData();
                Block last = lastPaintedBlock.get(player);
                if (last != null && !last.equals(b)) {
                    paintLine(last, b, data, zone, big, player);
                } else {
                    b.setTypeIdAndData(Material.WOOL.getId(), data, true);
                    if (big) {
                        BlockFace dir = getPlayerFacing(player);
                        float pitch = player.getLocation().getPitch();
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
                }
                lastPaintedBlock.put(player, b);
            }
        }.runTaskTimer(instance, 1L, 1L);
        paintTasks.put(player, task);
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
                sound = Sound.valueOf(parts[0].trim());
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
    public void resetColor() { this.pencilColor = DyeColor.WHITE; }
}