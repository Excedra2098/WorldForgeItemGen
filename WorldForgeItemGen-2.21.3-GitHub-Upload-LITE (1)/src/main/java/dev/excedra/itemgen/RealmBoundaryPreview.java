package dev.excedra.itemgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Staff-only, read-only boundary calibration. Never edits blocks or island records. */
final class RealmBoundaryPreview {
    private static final List<String> PROFILES = List.of(
        "01_terraced_rune_garden.schem", "02_sunrise_lily_basin.schem",
        "03_blossom_pergola_sanctuary.schem", "04_riverbridge_rune_garden.schem");
    private final JavaPlugin plugin;
    private final IslandBridge islands;
    RealmBoundaryPreview(JavaPlugin plugin, IslandBridge islands) {this.plugin=plugin;this.islands=islands;}

    void profiles(Player p) {
        var realm=islands.describe(p.getWorld());
        p.sendMessage("§dRealm template identity: §f"+String.join(", ",islands.templateKeys(realm)));
        Path folder=Path.of(plugin.getConfig().getString("realm-boundaries.starter-islands.schematic-folder", "plugins/WorldForge/schematics/starter-islands")).normalize();
        if(!folder.isAbsolute())folder=Path.of(".").toAbsolutePath().resolve(folder).normalize();
        p.sendMessage("§7Read-only schematic folder: "+folder);
        for(String name:PROFILES)p.sendMessage((Files.isRegularFile(folder.resolve(name))?"§aFOUND ":"§cMISSING ")+"§f"+name);
    }

    void preview(Player p,int radius) {
        islands.describe(p.getWorld()); // verifies Worldforge Realm is ready
        int r=Math.max(8,Math.min(32,radius));
        Location origin=p.getLocation();World world=p.getWorld();int cx=origin.getBlockX(),cz=origin.getBlockZ();
        int base=origin.getBlockY();
        int[] count={0};
        // Scan only loaded chunks and in a small vertical band around the staff position.
        Bukkit.getScheduler().runTask(plugin,()->{
            for(int x=cx-r;x<=cx+r;x+=2)for(int z=cz-r;z<=cz+r;z+=2){
                if(!world.isChunkLoaded(x>>4,z>>4))continue;
                if(Math.abs(x-cx)<5&&Math.abs(z-cz)<5)continue; // exclude central Core footprint
                for(int y=Math.min(world.getMaxHeight()-2,base+6);y>=Math.max(world.getMinHeight()+2,base-16);y--){
                    if(world.getBlockAt(x,y,z).getType().isAir()||!world.getBlockAt(x,y+1,z).getType().isAir())continue;
                    boolean edge=false;
                    for(int[] d:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){
                        int xx=x+d[0],zz=z+d[1];
                        if(!world.isChunkLoaded(xx>>4,zz>>4))continue;
                        if(world.getBlockAt(xx,y,zz).getType().isAir()&&world.getBlockAt(xx,y-1,zz).getType().isAir())edge=true;
                    }
                    if(edge){p.spawnParticle(Particle.PORTAL,x+.5,y+1.15,z+.5,3,.22,.25,.22,.02);
                        if(count[0]%4==0)p.spawnParticle(Particle.END_ROD,x+.5,y+1.25,z+.5,1,0,.12,0,0);count[0]++;}
                    break;
                }
            }
            p.sendMessage("§dBoundary calibration: §f"+count[0]+" candidate edge samples (§7not confirmed island perimeter§f). No blocks changed.");
        });
    }
}
