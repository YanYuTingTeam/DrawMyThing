package com.aermini.dmt.cuboid;

import com.aermini.dmt.util.DyeColorCompat;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class CuboidZone {

    private Block corner1;
    private Block corner2;
    private World world;

    public CuboidZone(Block corner1, Block corner2) {
        if (corner1.getWorld().equals(corner2.getWorld())) {
            this.corner1 = corner1;
            this.corner2 = corner2;
            this.world = corner1.getWorld();
        } else {
            throw new IllegalArgumentException("All cuboid blocks aren't in the same world!");
        }
    }

    public boolean contains(Block b) {
        return toArray().contains(b);
    }

    public List<Block> toArray() {
        List<Block> result = new ArrayList<Block>();
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    result.add(world.getBlockAt(new Location(world, x, y, z)));
                }
            }
        }
        return result;
    }

    public Block getCorner1() { return corner1; }
    public Block getCorner2() { return corner2; }

    public void clear() {
        for (Block b : toArray()) {
            b.setType(Material.AIR);
        }
    }

    public void setWool(DyeColor dc) {
        for (Block b : toArray()) {
            byte data = DyeColorCompat.getData(dc);
            b.setTypeIdAndData(Material.WOOL.getId(), data, true);
        }
    }
}
