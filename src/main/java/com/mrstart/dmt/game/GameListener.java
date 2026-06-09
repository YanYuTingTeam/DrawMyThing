package com.mrstart.dmt.game;

import com.mrstart.dmt.ChatUtil;
import com.mrstart.dmt.DrawMyThing;
import com.mrstart.dmt.cuboid.CuboidZone;
import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;

import java.util.HashSet;
import java.util.List;

public class GameListener implements Listener {
    private DyeColor pencilColor = DyeColor.BLACK;
    private DrawMyThing instance;

    public GameListener(DrawMyThing instance) {
        this.instance = instance;
    }

    @EventHandler
    public void onPlayerLogOut(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("inbmt")) {
            String gameName = getMeta(player, "inbmt");
            Game game = instance.getGameByName(gameName);
            if (game != null) game.leave(player);
        }
    }

    @EventHandler
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        if (isInGame(event.getPlayer())) event.setCancelled(true);
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

        if (isPencilItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            Block b = player.getTargetBlock((HashSet<Byte>) null, 100);
            if (b.getType() != Material.WOOL) return;
            b.setTypeIdAndData(Material.WOOL.getId(), pencilColor.getData(), true);
            playToolSound(player);
            return;
        }

        if (isBigPencilItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            Block b = player.getTargetBlock((HashSet<Byte>) null, 100);
            if (b.getType() != Material.WOOL) return;
            byte data = pencilColor.getData();
            b.setTypeIdAndData(Material.WOOL.getId(), data, true);
            b.getRelative(1, 0, 0).setTypeIdAndData(Material.WOOL.getId(), data, true);
            b.getRelative(-1, 0, 0).setTypeIdAndData(Material.WOOL.getId(), data, true);
            b.getRelative(0, 1, 0).setTypeIdAndData(Material.WOOL.getId(), data, true);
            b.getRelative(0, -1, 0).setTypeIdAndData(Material.WOOL.getId(), data, true);
            playToolSound(player);
            return;
        }

        if (isEraserItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            Block b = player.getTargetBlock((HashSet<Byte>) null, 100);
            if (b.getType() != Material.WOOL) return;
            b.setTypeIdAndData(Material.WOOL.getId(), DyeColor.WHITE.getData(), true);
            if (instance.getConfig().getBoolean("tool-sound", true)) {
                player.playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1.0f, 1.0f);
            }
            return;
        }

        if (isColorPickerItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            player.openInventory(instance.cci);
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
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isInGame(player)) return;
        Game game = getGame(player);
        if (game == null || !game.isStarted()) return;
        if (game.getBuilder() != null && game.getBuilder().equals(player)) {
            if (event.getTo() == null) return;
            if (event.getTo().getX() == event.getFrom().getX() &&
                event.getTo().getY() == event.getFrom().getY() &&
                event.getTo().getZ() == event.getFrom().getZ()) return;
            event.setTo(event.getFrom());
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
    private boolean isEraserItem(ItemStack item) { return matchItem(item, "item.eraser", Material.WOOL, (short) 14); }
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

    private void playToolSound(Player player) {
        if (instance.getConfig().getBoolean("tool-sound", true)) {
            player.playSound(player.getLocation(), Sound.FIZZ, 1.0f, 1.0f);
        }
    }

    public void setPencilColor(DyeColor color) { this.pencilColor = color; }
    public DyeColor getColor() { return pencilColor; }
}
