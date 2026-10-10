package dev.excedra.itemgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Staff-only read-only calibration: no world modifications and no island writes. */
final class RealmBoundaryPreview {
    private static final List<String> PROFILES = List.of(
        "01_terraced_rune_garden.schem", "02_sunrise_lily_basin.schem",
        "03_blossom_pergola_sanctuary.schem", "04_riverbridge_rune_garden.schem");
    private static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(170, 70, 255), 1.8f);
    private final JavaPlugin plugin;
    private final IslandBridge islands;
    private final Map<UUID, BukkitTask> previews = new HashMap<>();
    RealmBoundaryPreview(JavaPlugin plugin, IslandBridge islands) { this.plugin = plugin; this.islands = islands; }

    void profiles(Player p) {
        var realm = islands.describe(p.getWorld());
        p.sendMessage("§dRealm template identity: §f" + String.join(", ", islands.templateKeys(realm)));
        Path folder = Path.of(plugin.getConfig().getString("realm-boundaries.starter-islands.schematic-folder",
            "plugins/WorldForge/schematics/starter-islands")).normalize();
        if (!folder.isAbsolute()) folder = Path.of(".").toAbsolutePath().resolve(folder).normalize();
        p.sendMessage("§7Read-only schematic folder: " + folder);
        for (String name : PROFILES)
            p.sendMessage((Files.isRegularFile(folder.resolve(name)) ? "§aFOUND " : "§cMISSING ") + "§f" + name);
    }

    void stop(Player p) {
        BukkitTask prior = previews.remove(p.getUniqueId());
        if (prior != null) prior.cancel();
        p.sendMessage("§7Boundary particle preview stopped.");
    }

    void test(Player p) {
        cancel(p);
        final World world = p.getWorld();
        final Location marker = p.getLocation().clone().add(0, 2.3, 0);
        p.sendMessage("§dParticle diagnostic: §f30 seconds, two blocks above your starting position.");
        animate(p, world, List.of(marker), true);
    }

    void preview(Player p, int radius) {
        islands.describe(p.getWorld());
        cancel(p);
        int r = Math.max(8, Math.min(32, radius));
        Location origin = p.getLocation();
        World world = p.getWorld();
        int cx = origin.getBlockX(), cz = origin.getBlockZ(), base = origin.getBlockY();
        List<Location> samples = new ArrayList<>();
        int candidates = 0;
        for (int x = cx-r; x <= cx+r; x += 2) for (int z = cz-r; z <= cz+r; z += 2) {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            if (Math.abs(x-cx)<5 && Math.abs(z-cz)<5) continue;
            for (int y = Math.min(world.getMaxHeight()-2, base+6); y >= Math.max(world.getMinHeight()+2, base-16); y--) {
                if (world.getBlockAt(x,y,z).getType().isAir() || !world.getBlockAt(x,y+1,z).getType().isAir()) continue;
                boolean edge = false;
                for (int[] d : new int[][]{{2,0},{-2,0},{0,2},{0,-2}}) {
                    int xx=x+d[0], zz=z+d[1];
                    if (!world.isChunkLoaded(xx>>4,zz>>4)) continue;
                    if (world.getBlockAt(xx,y,zz).getType().isAir() && world.getBlockAt(xx,y-1,zz).getType().isAir()) edge=true;
                }
                if (edge) {
                    candidates++;
                    if (samples.size() < 80) samples.add(new Location(world,x+.5,y+1.15,z+.5));
                }
                break;
            }
        }
        p.sendMessage("§dBoundary calibration: §f"+candidates+" candidate samples; showing "+samples.size()+" markers for 30 seconds. §7Not confirmed island perimeter; no blocks changed.");
        if (samples.isEmpty()) p.sendMessage("§eNo candidate positions. Run /realmshards boundary test to verify plugin particles.");
        else p.sendMessage("§7First marker: §f"+samples.get(0).getBlockX()+", "+samples.get(0).getBlockY()+", "+samples.get(0).getBlockZ());
        animate(p, world, samples, false);
    }


    /** Experimental footprint outline. Only exterior air connected to scan border counts;
     * interior ponds/core voids never qualify as external gaps. No blocks are changed. */
    void perimeter(Player p, int radius) {
        islands.describe(p.getWorld());
        cancel(p);
        int r = Math.max(12, Math.min(32, radius));
        World w = p.getWorld();
        int cx=p.getLocation().getBlockX(), cz=p.getLocation().getBlockZ();
        int base=p.getLocation().getBlockY();
        int size=2*r+1;
        boolean[][] land=new boolean[size][size];
        boolean[][] outside=new boolean[size][size];
        boolean[][] connected=new boolean[size][size];
        int minY=Math.max(w.getMinHeight()+1,base-16);
        int maxY=Math.min(w.getMaxHeight()-2,base+3);
        int columns=0;
        for(int i=0;i<size;i++)for(int j=0;j<size;j++) {
            int x=cx-r+i,z=cz-r+j;
            if(!w.isChunkLoaded(x>>4,z>>4))continue;
            for(int y=maxY;y>=minY;y--) {
                org.bukkit.Material m=w.getBlockAt(x,y,z).getType();
                // Require two adjacent solid structural blocks to avoid leaves,
                // fences, flowers, decorative pillars and water.
                if(m.isSolid() && w.getBlockAt(x,y-1,z).getType().isSolid()
                    && !m.name().contains("LEAVES") && !m.name().contains("LOG")
                    && !m.name().contains("FENCE") && !m.name().contains("WALL")) {
                    land[i][j]=true;columns++;break;
                }
            }
        }
        java.util.ArrayDeque<int[]> queue=new java.util.ArrayDeque<>();
        for(int i=0;i<size;i++)for(int j=0;j<size;j++)if(i==0||j==0||i==size-1||j==size-1)
            if(!land[i][j]&&!outside[i][j]){outside[i][j]=true;queue.add(new int[]{i,j});}
        int[][] dirs={{1,0},{-1,0},{0,1},{0,-1}};
        while(!queue.isEmpty()){
            int[] n=queue.removeFirst();
            for(int[] d:dirs){int x=n[0]+d[0],z=n[1]+d[1];
                if(x>=0&&z>=0&&x<size&&z<size&&!land[x][z]&&!outside[x][z]){
                    outside[x][z]=true;queue.addLast(new int[]{x,z});
                }
            }
        }
        // Follow only the connected terrain component nearest the player.
        int seedX=-1,seedZ=-1,best=Integer.MAX_VALUE;
        for(int i=0;i<size;i++)for(int j=0;j<size;j++)if(land[i][j]){
            int distance=(i-r)*(i-r)+(j-r)*(j-r);
            if(distance<best){best=distance;seedX=i;seedZ=j;}
        }
        if(seedX<0){p.sendMessage("§eNo structural terrain found within scan radius.");return;}
        connected[seedX][seedZ]=true;queue.add(new int[]{seedX,seedZ});
        while(!queue.isEmpty()){
            int[] n=queue.removeFirst();
            for(int[] d:dirs){int x=n[0]+d[0],z=n[1]+d[1];
                if(x>=0&&z>=0&&x<size&&z<size&&land[x][z]&&!connected[x][z]){
                    connected[x][z]=true;queue.addLast(new int[]{x,z});
                }
            }
        }
        List<Location> points=new ArrayList<>();
        int edges=0;
        boolean clipped=false;
        for(int i=1;i<size-1;i++)for(int j=1;j<size-1;j++)if(connected[i][j]){
            boolean external=false;
            for(int[] d:dirs)if(outside[i+d[0]][j+d[1]])external=true;
            if(!external)continue;
            edges++;
            int x=cx-r+i,z=cz-r+j;
            for(int y=maxY;y>=minY;y--){
                if(w.getBlockAt(x,y,z).getType().isSolid()
                    && w.getBlockAt(x,y-1,z).getType().isSolid()){
                    points.add(new Location(w,x+.5,y+1.1,z+.5));
                    break;
                }
            }
        }
        for(int i=0;i<size;i++)if(connected[i][0]||connected[i][size-1]||connected[0][i]||connected[size-1][i])clipped=true;
        // Evenly sample the entire perimeter in angular order, rather than stopping
        // after the first 100 positions (which favored one corner).
        points.sort(java.util.Comparator.comparingDouble(loc -> {
            double a=Math.atan2(loc.getZ()-(cz+.5),loc.getX()-(cx+.5));
            return a<0?a+Math.PI*2:a;
        }));
        final int maxMarkers=100;
        if(points.size()>maxMarkers){
            List<Location> evenlySpaced=new ArrayList<>(maxMarkers);
            for(int i=0;i<maxMarkers;i++)
                evenlySpaced.add(points.get((int)((long)i*points.size()/maxMarkers)));
            points=evenlySpaced;
        }
        p.sendMessage("§dExperimental exterior preview: §f"+edges+" perimeter columns, "+points.size()+" evenly distributed markers for 30 seconds.");
        p.sendMessage("§7Structural columns: "+columns+"; scan radius: "+r+"; touches scan limit: "+clipped+". No blocks changed.");
        if(clipped)p.sendMessage("§eSome land reaches the scan boundary. Stand nearer the center; the outline may be incomplete.");
        if(points.isEmpty())p.sendMessage("§eNo exterior positions detected; the terrain may fill the scan or chunks may be unloaded.");
        else animate(p,w,points,false);
    }

    private void cancel(Player p) {
        BukkitTask old = previews.remove(p.getUniqueId());
        if (old != null) old.cancel();
    }

    private void animate(Player p, World world, List<Location> points, boolean diagnostic) {
        final UUID id = p.getUniqueId();
        final int[] ticks = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || p.getWorld() != world || ticks[0]++ >= 60) {
                BukkitTask active = previews.remove(id);
                if (active != null) active.cancel();
                return;
            }
            int n = 0;
            for (Location marker : points) {
                if (marker.distanceSquared(p.getLocation()) > 48*48) continue;
                if (!world.isChunkLoaded(marker.getBlockX()>>4,marker.getBlockZ()>>4)) continue;
                for (int y=0; y<4; y++) p.spawnParticle(Particle.DUST, marker.clone().add(0,y*.65,0), 2, .07,.04,.07,0, PURPLE);
                p.spawnParticle(Particle.END_ROD, marker.clone().add(0,2.6,0), 2, .12,.12,.12,0);
                if (++n >= (diagnostic ? 1 : 80)) break;
            }
        }, 0L, 10L);
        previews.put(id, task);
    }
}
