package com.aermini.dmt.game;

import com.aermini.dmt.DrawMyThing;
import com.aermini.dmt.util.LocationUtil;
import com.aermini.dmt.cuboid.CuboidZone;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class GameSerializer {

    public static void save(Game game, FileConfiguration file) {
        String path = "games." + game.getName();
        file.set(path + ".region", game.getRegion());
        file.set(path + ".builderspawn", game.getBuilderSpawn() != null ? LocationUtil.LocationToString(game.getBuilderSpawn()) : "");
        file.set(path + ".spawn", game.getSpawn() != null ? LocationUtil.LocationToString(game.getSpawn()) : "");
        file.set(path + ".lobby", game.getLobby() != null ? LocationUtil.LocationToString(game.getLobby()) : "");
        if (game.getPaintingPos1() != null) file.set(path + ".painting.pos1", LocationUtil.LocationToString(game.getPaintingPos1()));
        else if (game.getBuildZone() != null) file.set(path + ".painting.pos1", LocationUtil.LocationToString(game.getBuildZone().getCorner1().getLocation()));
        else file.set(path + ".painting.pos1", "");
        if (game.getPaintingPos2() != null) file.set(path + ".painting.pos2", LocationUtil.LocationToString(game.getPaintingPos2()));
        else if (game.getBuildZone() != null) file.set(path + ".painting.pos2", LocationUtil.LocationToString(game.getBuildZone().getCorner2().getLocation()));
        else file.set(path + ".painting.pos2", "");
        file.set(path + ".min-player", game.getMinPlayers());
        file.set(path + ".max-player", game.getMaxPlayers());
        file.set(path + ".builder-boundary.pos1", game.getBuilderBoundaryPos1() != null ? LocationUtil.LocationToString(game.getBuilderBoundaryPos1()) : "");
        file.set(path + ".builder-boundary.pos2", game.getBuilderBoundaryPos2() != null ? LocationUtil.LocationToString(game.getBuilderBoundaryPos2()) : "");
    }

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
        String bbp1 = file.getString(path + ".builder-boundary.pos1", "");
        String bbp2 = file.getString(path + ".builder-boundary.pos2", "");
        if (bbp1 != null && !bbp1.isEmpty()) game.setBuilderBoundaryPos1(LocationUtil.StringToLoc(bbp1));
        if (bbp2 != null && !bbp2.isEmpty()) game.setBuilderBoundaryPos2(LocationUtil.StringToLoc(bbp2));
        return game;
    }

    public static Game createEmpty(String name, World world, DrawMyThing instance) {
        return new Game(null, null, null, name, world.getName(), instance);
    }
}
