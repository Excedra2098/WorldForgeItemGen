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
