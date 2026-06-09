package com.mrstart.dmt;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemManager {

    private final DrawMyThing instance;

    private ItemStack leaveItem;
    private ItemStack pencilItem;
    private ItemStack bigPencilItem;
    private ItemStack eraserItem;
    private ItemStack colorPickerItem;

    public Inventory cci;

    public ItemManager(DrawMyThing instance) {
        this.instance = instance;
    }

    public void initItems() {
        leaveItem = buildItemFromConfig("item.leave", Material.BED, (short) 14, "&c返回大厅", null);
        pencilItem = buildItemFromConfig("item.pencil", Material.WOOD_SWORD, (short) 0, "&a默认画笔", null);
        bigPencilItem = buildItemFromConfig("item.big-pencil", Material.IRON_SWORD, (short) 0, "&a&l粗画笔", null);
        eraserItem = buildItemFromConfig("item.eraser", Material.TNT, (short) 0, "&c清空画布", null);
        colorPickerItem = buildItemFromConfig("item.color-picker", Material.BUCKET, (short) 0, "&9填充画布", null);
    }

    private ItemStack buildItemFromConfig(String path, Material defMat, short defData, String defName, List<String> defLore) {
        FileConfiguration cfg = instance.getConfig();
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

    public void initColorPicker() {
        cci = Bukkit.createInventory(null, 18,
                ChatUtil.colorize(instance.getConfig().getString("color-picker.title", "选择画笔颜色")));
        initColorItems();
    }

    public void initColorItems() {
        cci.clear();
        List<Map<?, ?>> colorList = instance.getConfig().getMapList("color-picker.colors");
        if (colorList != null && !colorList.isEmpty()) {
            for (Map<?, ?> c : colorList) {
                int woolData = c.containsKey("wool") ? ((Number) c.get("wool")).intValue() : 0;
                String colorName = ChatUtil.colorize(c.containsKey("name") ? c.get("name").toString() : "&f颜色");
                int slot = c.containsKey("slot") ? ((Number) c.get("slot")).intValue() : 0;
                ItemStack item = new ItemStack(Material.WOOL, 1, (short) woolData);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(colorName);
                item.setItemMeta(meta);
                if (slot >= 0 && slot < 18) cci.setItem(slot, item);
            }
        } else {
            String[] names = {"&f&l白色", "&6&l橙色", "&5&l品红色", "&b&l淡蓝色", "&e&l黄色", "&a&l黄绿色",
                    "&d&l粉红色", "&8&l灰色", "&7&l淡灰色", "&3&l青色", "&5&l紫色", "&9&l蓝色", "&6&l棕色", "&2&l绿色", "&c&l红色", "&0&l黑色"};
            for (int i = 0; i < 16; i++) {
                ItemStack item = new ItemStack(Material.WOOL, 1, (short) i);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatUtil.colorize(names[i]));
                item.setItemMeta(meta);
                cci.setItem(i, item);
            }
        }
    }

    public void giveConfigItem(org.bukkit.entity.Player player, String path, ItemStack item) {
        int slot = instance.getConfig().getInt(path + ".slot", 0);
        player.getInventory().setItem(slot, item);
    }

    public ItemStack getLeaveItem() { return leaveItem; }
    public ItemStack getPencilItem() { return pencilItem; }
    public ItemStack getBigPencilItem() { return bigPencilItem; }
    public ItemStack getEraserItem() { return eraserItem; }
    public ItemStack getColorPickerItem() { return colorPickerItem; }

    public static DyeColor woolDataToDyeColor(byte data) {
        DyeColor[] colors = DyeColor.values();
        if (data >= 0 && data < colors.length) return colors[data];
        return DyeColor.WHITE;
    }
}
