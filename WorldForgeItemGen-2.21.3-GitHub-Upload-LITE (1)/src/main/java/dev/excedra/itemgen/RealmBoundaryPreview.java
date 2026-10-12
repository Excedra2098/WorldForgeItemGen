package dev.excedra.itemgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.block.data.BlockData;
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
    private final Map<UUID, List<BlockDisplay>> debris = new HashMap<>();
    private final Random random = new Random();
    private final Map<UUID, BukkitTask> watches = new HashMap<>();
    private final java.util.Set<UUID> hybridMode = new java.util.HashSet<>();
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
        stopWatch(p.getUniqueId());
        BukkitTask prior = previews.remove(p.getUniqueId());
        if (prior != null) prior.cancel();
        clearDebris(p);
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
        else { animateEnhanced(p,w,points,cx+.5,cz+.5); if(hybridMode.remove(p.getUniqueId())) createDebris(p,w,points,cx+.5,cz+.5); }
    }

    /** Brighter, client-only perimeter preview. Nearby points are connected only
     * when actually adjacent, so the animation never bridges empty corners. */
    private void animateEnhanced(Player p, World world, List<Location> points, double centerX, double centerZ) {
        final UUID id=p.getUniqueId();
        final int[] cycles={0};
        final Particle.DustOptions glow=new Particle.DustOptions(Color.fromRGB(185,65,255),2.5f);
        final Particle.DustOptions deep=new Particle.DustOptions(Color.fromRGB(100,40,205),1.7f);
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(!p.isOnline()||p.getWorld()!=world||cycles[0]++>=60){
                BukkitTask active=previews.remove(id);
                if(active!=null)active.cancel();
                clearDebris(p);
                return;
            }
            int displayed=0;
            for(Location edge:points){
                if(edge.distanceSquared(p.getLocation())>48*48)continue;
                if(!world.isChunkLoaded(edge.getBlockX()>>4,edge.getBlockZ()>>4))continue;
                double dx=edge.getX()-centerX,dz=edge.getZ()-centerZ;
                double len=Math.max(.01,Math.sqrt(dx*dx+dz*dz));
                double ox=dx/len*.65,oz=dz/len*.65;
                Location outside=edge.clone().add(ox,-.4,oz);
                // Bright upper rim plus lower, darker void-facing fracture layer.
                p.spawnParticle(Particle.DUST,outside,3,.15,.11,.15,0,glow);
                p.spawnParticle(Particle.END_ROD,outside.clone().add(0,.45,0),1,.04,.06,.04,0);
                p.spawnParticle(Particle.DUST,outside.clone().add(0,-1.4,0),3,.2,.22,.2,0,deep);
                if((displayed&3)==0) {
                    p.spawnParticle(Particle.DUST,outside.clone().add(0,-2.8,0),2,.14,.35,.14,0,deep);
                    p.spawnParticle(Particle.PORTAL,outside.clone().add(0,-2.2,0),2,.16,.4,.16,.01);
                }
                // Small neighboring spans; do not draw long chords through the island.
                for(Location other:points){
                    double px=other.getX()-edge.getX(),pz=other.getZ()-edge.getZ();
                    double ds=px*px+pz*pz;
                    if(ds<.9||ds>4.1||Math.abs(other.getY()-edge.getY())>1.6)continue;
                    Location mid=edge.clone().add(px*.5+ox,-.4,pz*.5+oz);
                    p.spawnParticle(Particle.DUST,mid,2,.08,.06,.08,0,glow);
                    break;
                }
                if(++displayed>=100)break;
            }
        },0L,10L);
        previews.put(id,task);
    }

    /** Temporary non-solid fragments around detected outer terrain. No blocks modified. */
    void hybrid(Player p, int radius) {
        stopWatch(p.getUniqueId());
        hybridMode.add(p.getUniqueId());
        perimeter(p, radius);
        hybridMode.remove(p.getUniqueId());
    }

    /** Explicit staff opt-in only. Recalibrates every 25 seconds on the staff's Realm;
     * automatically ends when the player leaves the server or changes worlds. */
    void watch(Player p, int radius) {
        stopWatch(p.getUniqueId());
        final World startingWorld = p.getWorld();
        final UUID id = p.getUniqueId();
        final int r = Math.max(12, Math.min(32, radius));
        p.sendMessage("§dBoundary staff watch enabled. §7Refreshing every 25 seconds; use /realmshards boundary off to stop.");
        // First update must run from the next tick, after the task is registered.
        BukkitTask watch = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || p.getWorld() != startingWorld) {
                stopWatch(id);
                cancel(p);
                return;
            }
            try {
                hybridMode.add(id);
                perimeter(p, r);
                hybridMode.remove(id);
            } catch (RuntimeException failure) {
                hybridMode.remove(id);
                plugin.getLogger().warning("Stopping boundary staff watch for " + id + ": " + failure.getMessage());
                stopWatch(id);
                cancel(p);
            }
        }, 1L, 500L);
        watches.put(id, watch);
    }

    private void stopWatch(UUID id) {
        BukkitTask task = watches.remove(id);
        if (task != null) task.cancel();
    }

    private void createDebris(Player p, World world, List<Location> points, double cx, double cz) {
        clearDebris(p);
        List<BlockDisplay> spawned = new ArrayList<>();
        Material[] palette = {Material.COBBLED_DEEPSLATE, Material.CRACKED_STONE_BRICKS,
            Material.CRYING_OBSIDIAN, Material.AMETHYST_BLOCK};
        // Balanced treatment: clustered medium stones, sparse crystal shards,
        // and deterministic spacing per preview (not uniform around the rim).
        int count = Math.min(18, Math.max(6, points.size()/7));
        for (int i=0; i<count; i++) {
            // Pair occasional neighboring fragments into small broken-rock clusters.
            int anchor = (int)((long)i*points.size()/count);
            if(i%4==1) anchor=Math.max(0,anchor-2);
            Location edge = points.get(Math.min(points.size()-1,anchor));
            double dx=edge.getX()-cx, dz=edge.getZ()-cz;
            double length=Math.max(.1,Math.hypot(dx,dz));
            double outward=1.65+random.nextDouble()*1.3;
            Location at=edge.clone().add(dx/length*outward,
                -1.25-random.nextDouble()*2.8, dz/length*outward);
            if(!world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)) continue;
            if(!world.getBlockAt(at).getType().isAir()) continue;
            // Crystal pieces are deliberately rarer than structural stone.
            Material chosen = i%6==0 ? Material.AMETHYST_BLOCK : palette[random.nextInt(2)];
            BlockData data=chosen.createBlockData();
            float scale=.52f+random.nextFloat()*.42f;
            BlockDisplay display=world.spawn(at,BlockDisplay.class,d -> {
                d.setPersistent(false); d.setGravity(false); d.setInvulnerable(true);
                d.addScoreboardTag("worldforge_boundary_preview");
                d.setBlock(data);
                d.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(-scale/2,-scale/2,-scale/2),
                    new org.joml.AxisAngle4f(random.nextFloat()*6.28f,0,1,0),
                    new org.joml.Vector3f(scale,scale,scale),
                    new org.joml.AxisAngle4f(random.nextFloat()*.55f,1,0,0)));
            });
            spawned.add(display);
        }
        debris.put(p.getUniqueId(),spawned);
        // Independent expiry: debris must not rely on the particle task remaining active.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<BlockDisplay> current = debris.get(p.getUniqueId());
            if(current==spawned) clearDebris(p);
        }, 600L);
        p.sendMessage("§dHybrid preview: §f"+spawned.size()+" temporary floating fragments; automatically cleared in 30 seconds.");
    }

    void stopAll() {
        for(BukkitTask task:new ArrayList<>(watches.values())) task.cancel();
        watches.clear();
        for(BukkitTask task:new ArrayList<>(previews.values())) task.cancel();
        previews.clear();
        for(List<BlockDisplay> displays:new ArrayList<>(debris.values()))
            for(BlockDisplay display:displays) if(display!=null && display.isValid()) display.remove();
        debris.clear();
        hybridMode.clear();
    }

    private void clearDebris(Player p) {
        List<BlockDisplay> old=debris.remove(p.getUniqueId());
        if(old!=null)for(BlockDisplay d:old)if(d.isValid())d.remove();
    }

    private void cancel(Player p) {
        BukkitTask old = previews.remove(p.getUniqueId());
        if (old != null) old.cancel();
        clearDebris(p);
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
