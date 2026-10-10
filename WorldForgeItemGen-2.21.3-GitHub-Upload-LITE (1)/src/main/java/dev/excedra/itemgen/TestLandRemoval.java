package dev.excedra.itemgen;

import java.nio.file.*;

import java.util.*;

import org.bukkit.*;

import org.bukkit.block.*;

import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.entity.*;

import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.util.Transformation;

import org.bukkit.util.Vector;

import org.joml.Quaternionf;

import org.joml.Vector3f;

/** Testing-only removal of completed addon transactions; never deletes a realm world. */

final class TestLandRemoval {

    private final JavaPlugin plugin;

    private final Path folder;

    private final int budget;

    private final IslandBridge islands;private final ReclaimRefunds reclaimRefunds;

    private final Map<UUID,Job> jobs=new HashMap<>();
    private final Map<UUID,Batch> batches=new HashMap<>();


    TestLandRemoval(JavaPlugin p,Path folder,int budget,IslandBridge islands,ReclaimRefunds reclaimRefunds){plugin=p;this.folder=folder;this.budget=budget;this.islands=islands;this.reclaimRefunds=reclaimRefunds;}

    boolean busy(World w){return jobs.containsKey(w.getUID())||batches.containsKey(w.getUID());}
    void afterWandUse(World world,Runnable returned){Batch b=batches.get(world.getUID());if(b!=null){b.onClose=returned;return;}Job j=jobs.get(world.getUID());if(j!=null){j.onClose=returned;return;}returned.run();}
    boolean reserved(Location l){Batch b=batches.get(l.getWorld().getUID());if(b!=null&&b.lands.stream().anyMatch(land->land.occupies(l.getBlockX(),l.getBlockY(),l.getBlockZ())))return true;Job j=jobs.get(l.getWorld().getUID());return j!=null&&j.land.occupies(l.getBlockX(),l.getBlockY(),l.getBlockZ());}
    void stop(){for(Job j:List.copyOf(jobs.values()))j.abort("Server stopping");for(Batch b:List.copyOf(batches.values()))b.abort("Server stopping");batches.clear();}
    void removeAll(Player admin)throws Exception{removeAll(admin,null);}
    void removeAll(Player admin,ItemStack ritual)throws Exception{
        if(!plugin.getConfig().getBoolean("realmshards.testing-removal-enabled",true))throw new IllegalStateException("Testing removal is disabled in config.yml.");
        if(!(new ShardAccess(plugin).staff(admin)||new ShardAccess(plugin).moderator(admin)))throw new IllegalStateException("Only Admin, Head-Admin and Owner can remove test placements.");
        World w=admin.getWorld();if(busy(w)||jobs.size()+batches.size()>=2)throw new IllegalStateException("A testing removal is already running; try again shortly.");
        IslandBridge.Realm realm=islands.describe(w);List<GeneratedLand> lands=new ArrayList<>();
        try(var files=Files.list(folder)){for(Path path:files.filter(p->p.toString().endsWith(".yml")).sorted().toList()){
            YamlConfiguration y=YamlConfiguration.loadConfiguration(path.toFile());
            if(!GeneratedLand.completedIn(y,w.getName(),w.getUID(),realm.id()))continue;
            GeneratedLand land=new GeneratedLand(path,y);
            lands.add(land);
        }}
        GeneratedLand.requireDisjoint(lands);
        if(lands.isEmpty())throw new IllegalStateException("No completed shard placements were recorded in this island.");
        Location safe=islands.returnCandidates(w).stream().filter(l->ritual==null||w.equals(l.getWorld())).filter(l->lands.stream().allMatch(land->safe(l,w,land))).findFirst().orElseThrow(()->new IllegalStateException("No safe return location outside all placements was found; nothing removed."));
        Batch batch=new Batch(admin,w,realm.id(),List.copyOf(lands),safe,ritual);batches.put(w.getUID(),batch);
        admin.sendActionBar(Component.text("Targeting all "+lands.size()+" recorded placements",NamedTextColor.LIGHT_PURPLE));
        batch.start();
    }
    private final class Batch {
        final Player admin;final World world;final UUID realm;final List<GeneratedLand> lands;final Location safe;final ItemStack ritual;
        final List<Job> running=new ArrayList<>();BukkitTask task;WandCast cast;int cursor;boolean stopping;Runnable onClose=()->{};
        Batch(Player admin,World world,UUID realm,List<GeneratedLand> lands,Location safe,ItemStack ritual){this.admin=admin;this.world=world;this.realm=realm;this.lands=lands;this.safe=safe;this.ritual=ritual;}
        void start()throws Exception{
            try{
                // Validate and snapshot every placement before changing any transaction state.
                for(var land:lands){
                    if(!"COMPLETE".equals(YamlConfiguration.loadConfiguration(land.path.toFile()).getString("status")))throw new IllegalStateException("A placement record changed.");
                    Job j=new Job(admin,world,land,safe);running.add(j);j.bulk=true;j.visualShare=lands.size();j.prepare();
                }
                for(Job j:running)j.start(false);
                if(ritual!=null)cast=new WandCast(world,admin,ritual,true,running.stream().map(Job::center).toList());
                task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
                    try{
                        if(!admin.isOnline()||!(new ShardAccess(plugin).staff(admin)||new ShardAccess(plugin).moderator(admin)))throw new IllegalStateException("Staff access lost.");
                        if(!islands.describe(world).id().equals(realm))throw new IllegalStateException("Island identity changed.");
                        
            if(cast!=null&&!cast.tick())return;
                        // One block and biome budget shared by every placement, rotated for fairness.
                        TickWorkBudget blocks=new TickWorkBudget(budget,3),biomes=new TickWorkBudget(64,2);
                        int n=running.size();for(int k=0;k<n;k++)running.get((cursor+k)%n).step(blocks,biomes);cursor=(cursor+1)%n;
                        if(running.stream().allMatch(j->j.finished)){long removed=running.stream().mapToLong(j->j.removed.size()).sum(),preserved=running.stream().mapToLong(j->j.skipped).sum();close();if(admin.isOnline())admin.sendMessage("§aRemoved all "+lands.size()+" realm placements: "+removed+" blocks removed; "+preserved+" changed blocks preserved. Your original island remains intact.");}
                    }catch(Exception ex){abort("Bulk removal interrupted: "+ex.getMessage());}
                },1,1);
            }catch(Exception ex){abort("Unable to begin bulk removal: "+ex.getMessage(),false);throw ex;}
        }
        void close(){if(task!=null)task.cancel();if(cast!=null)cast.close();for(Job j:running)for(int[] c:j.chunks)world.removePluginChunkTicket(c[0],c[1],plugin);batches.remove(world.getUID(),this);onClose.run();onClose=()->{};}
        void abort(String reason){abort(reason,true);}
        void abort(String reason,boolean notify){if(stopping)return;stopping=true;for(Job j:running)if(j.journal!=null)j.abort(reason);close();if(notify&&admin.isOnline())admin.sendMessage("§c"+reason);}
    }
    void removeStanding(Player admin)throws Exception{removeStanding(admin,null);}
    void removeStanding(Player admin,ItemStack ritual)throws Exception{removeSelected(admin,ritual,false);}
    void reclaim(Player admin,ItemStack ritual)throws Exception{removeSelected(admin,ritual,true);}
    private void removeSelected(Player admin,ItemStack ritual,boolean reclaim)throws Exception{

        if(!plugin.getConfig().getBoolean("realmshards.testing-removal-enabled",true))throw new IllegalStateException("Testing removal is disabled in config.yml.");

        if(!(new ShardAccess(plugin).staff(admin)||new ShardAccess(plugin).moderator(admin)))throw new IllegalStateException("Testing removal permission is required.");

        World w=admin.getWorld();if(busy(w)||jobs.size()+batches.size()>=2)throw new IllegalStateException("A testing removal is already running; try again shortly.");

        IslandBridge.Realm realm=islands.describe(w);Location feet=admin.getLocation();List<GeneratedLand> matches=new ArrayList<>();
        var hit=ritual==null?null:admin.rayTraceBlocks(Math.clamp(plugin.getConfig().getInt("realmshards.testing-wand-range",256),16,512),FluidCollisionMode.NEVER);
        Block aimed=hit==null?null:hit.getHitBlock();

        try(var files=Files.list(folder)){for(Path path:files.filter(p->p.toString().endsWith(".yml")).toList()){

            YamlConfiguration y=YamlConfiguration.loadConfiguration(path.toFile());

            if(!"COMPLETE".equals(y.getString("status"))||!w.getName().equals(y.getString("world"))||!realm.id().toString().equals(y.getString("realm")))continue;

            if(y.contains("world-uuid")&&!w.getUID().toString().equals(y.getString("world-uuid")))continue;

            GeneratedLand land=new GeneratedLand(path,y);
            // Wands require a visible recorded cell, never an approximate island bounding box.
            boolean aimedAt=aimed!=null&&land.positions.contains(new TerrainPlan.Pos(aimed.getX(),aimed.getY(),aimed.getZ()));
            if(ritual!=null?aimedAt:land.standingOn(feet.getBlockX(),feet.getBlockY(),feet.getBlockZ()))matches.add(land);

        }}

        if(matches.isEmpty())throw new IllegalStateException("Aim at a visible block of a completed shard placement with the wand, or stand on it for the command. Original island terrain cannot be targeted.");

        if(matches.size()!=1)throw new IllegalStateException("Overlapping records require manual review; nothing was removed.");

        GeneratedLand land=matches.getFirst();Location safe=islands.returnCandidates(w).stream().filter(l->ritual==null||w.equals(l.getWorld())).filter(l->safe(l,w,land)).findFirst().orElseThrow(()->new IllegalStateException("No safe return location was found; nothing was removed."));

        Job job=new Job(admin,w,land,safe);job.reclaim=reclaim;job.ritual=ritual;job.prepare();jobs.put(w.getUID(),job);

        try{job.start();}catch(Exception e){job.abort("Unable to begin testing removal");throw e;}

    }

    private boolean safe(Location l,World deletingWorld,GeneratedLand land){

        if(l==null||l.getWorld()==null||!l.getWorld().getWorldBorder().isInside(l))return false;

        if(l.getWorld().equals(deletingWorld)&&land.column(l.getBlockX(),l.getBlockZ()))return false;

        return l.getBlock().isPassable()&&l.clone().add(0,1,0).getBlock().isPassable()&&l.clone().add(0,-1,0).getBlock().getType().isSolid();

    }

    private final class Job {

        final Player admin;final World world;final GeneratedLand land;final Location safe;ItemStack ritual;WandCast cast;int visualShare=1;boolean started;

        final ProtectionBridge.Guard guard;

        final List<GeneratedLand.Cell> eligible=new ArrayList<>();

        final Map<TerrainPlan.Pos,String> before=new LinkedHashMap<>();

        final Map<TerrainPlan.Pos,Biome> biomeBefore=new LinkedHashMap<>(),biomeRestore=new LinkedHashMap<>();

        final List<TerrainPlan.Pos> removed=new ArrayList<>(),biomesChanged=new ArrayList<>();

        final List<int[]> chunks=new ArrayList<>();



        CollapsePattern collapse;Color glow;int age,shatteredAt=-1,biomeIndex;

        TerrainDissolve dissolve;DissolvePattern sweep;FractureSkin fractureSkin;

        final int dissolveTicks=Math.clamp(plugin.getConfig().getInt("realmshards.testing-dissolve-ticks",100),60,200);

        final int crackTicks=Math.clamp(plugin.getConfig().getInt("realmshards.testing-collapse-ticks",90),40,160);

        final int effectBudget=Math.clamp(plugin.getConfig().getInt("realmshards.particles-per-tick",96),16,160);

        List<TerrainPlan.Pos> biomeEntries;

        YamlConfiguration journal;BukkitTask task;int index,skipped;boolean finished,ruptured,bulk,reclaim;ItemStack returnShard;ReclaimAnimation reclaimer;Runnable onSuccess=()->{},onFailure=()->{},onClose=()->{};
        Job(Player p,World w,GeneratedLand land,Location safe){admin=p;world=w;this.land=land;this.safe=safe;guard=ProtectionBridge.checker(p);}

        Block block(TerrainPlan.Pos p){return world.getBlockAt(p.x(),p.y(),p.z());}

        void prepare(){

            journal=YamlConfiguration.loadConfiguration(land.path.toFile());

            for(GeneratedLand.Cell cell:land.cells){Block b=block(cell.pos());String data=b.getBlockData().getAsString();if(!cell.removable(data)){skipped++;continue;}if(!guard.allowed(b.getLocation()))throw new IllegalStateException("Permanent Core or region protection denies removal here.");eligible.add(cell);before.put(cell.pos(),data);}

            if(reclaim){
                if(skipped>0)throw new IllegalStateException("This placement has mined or changed blocks. Restore it before reclaiming; no shard refunded.");
                returnShard=journal.getItemStack("reserved");if(returnShard==null||returnShard.getType().isAir())throw new IllegalStateException("The original shard is missing from this placement record; cannot safely refund.");returnShard=returnShard.clone();returnShard.setAmount(1);
                for(var cell:eligible){var state=block(cell.pos()).getState(false);if(state instanceof org.bukkit.inventory.InventoryHolder holder&&java.util.Arrays.stream(holder.getInventory().getContents()).anyMatch(i->i!=null&&!i.getType().isAir()))throw new IllegalStateException("Empty placement containers before reclaiming.");}
                journal.set("reclamation.player",admin.getUniqueId().toString());
            }
            String biome=journal.getString("generated-biome","");

            if(biome.isBlank()){String spec=journal.getString("spec","");biome=TerrainPlan.biomeFor(ShardSpec.Land.valueOf(spec.split("_")[0].toUpperCase(Locale.ROOT))).getKey().toString();}

            for(var e:land.biomes.entrySet()){Biome original=Registry.BIOME.get(NamespacedKey.fromString(e.getValue()));if(original==null)throw new IllegalStateException("Unknown saved biome; nothing was removed.");Block b=block(e.getKey());if(b.getBiome().getKey().toString().equals(biome)){biomeBefore.put(e.getKey(),b.getBiome());biomeRestore.put(e.getKey(),original);}}

            collapse=new CollapsePattern(land,eligible,64,journal.getLong("seed",land.realm.getLeastSignificantBits()));

            sweep=new DissolvePattern(land,eligible,480,journal.getLong("seed",land.realm.getLeastSignificantBits()));

            eligible.clear();eligible.addAll(sweep.order);biomeEntries=new ArrayList<>(biomeRestore.keySet());

            String spec=journal.getString("spec","");glow=spec.endsWith("shiny")?Color.fromRGB(255,210,90):Color.fromRGB(90,225,255);

            String[] parts=spec.split("_");ShardSpec shard=new ShardSpec(Integer.parseInt(parts[1].substring(1)),parts[0].equals("random")?null:ShardSpec.Land.valueOf(parts[0].toUpperCase(Locale.ROOT)),ShardSpec.Variant.valueOf(parts[2].toUpperCase(Locale.ROOT)));

            if(!reclaim){dissolve=new TerrainDissolve(plugin,world,shard,visualShare);fractureSkin=new FractureSkin(plugin,world,land,eligible,shard,journal.getLong("seed",0),visualShare);}
            if(reclaim)eligible.sort(java.util.Comparator.comparingDouble((GeneratedLand.Cell c)->Math.hypot(c.pos().x()-land.ox,c.pos().z()-land.oz)).reversed());

        }

        void start()throws Exception{start(true);}
        void start(boolean scheduled)throws Exception{

            for(int x=Math.floorDiv(land.minX,16);x<=Math.floorDiv(land.maxX,16);x++)for(int z=Math.floorDiv(land.minZ,16);z<=Math.floorDiv(land.maxZ,16);z++){world.addPluginChunkTicket(x,z,plugin);chunks.add(new int[]{x,z});}

            journal.set("testing-removal.admin",admin.getUniqueId().toString());journal.set("testing-removal.started",System.currentTimeMillis());

            journal.set("testing-removal.restore-cells",before.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue()).toList());save(reclaim?"RECLAIMING":"REMOVING");started=true;

            evacuate();if(reclaim)reclaimer=new ReclaimAnimation(plugin,world,admin,land,eligible,returnShard,ritual);else if(ritual!=null)cast=new WandCast(world,admin,ritual,false,List.of(center()));
            sound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,1.0f,.55f);if(!bulk&&!reclaim)admin.sendMessage("§eThe fragment is fracturing. Stand clear and watch it shatter. No shard is refunded.");

            plugin.getLogger().info("Testing removal by "+admin.getName()+": "+land.path.getFileName()+" in "+world.getName());

            if(scheduled)task=Bukkit.getScheduler().runTaskTimer(plugin,()->{try{step(new TickWorkBudget(budget,3),new TickWorkBudget(64,2));}catch(Exception e){plugin.getLogger().log(java.util.logging.Level.WARNING,"Testing removal interrupted",e);abort("Testing removal interrupted; changes rolled back where safe.");}},1,1);

        }

        void evacuate(){

            if(!safe(safe,world,land))throw new IllegalStateException("The return location is no longer safe.");

            for(Entity e:world.getNearbyEntities(new Location(world,(land.minX+land.maxX)/2.0,(land.minY+land.maxY)/2.0,(land.minZ+land.maxZ)/2.0),(land.maxX-land.minX)/2.0+2,(land.maxY-land.minY)/2.0+5,(land.maxZ-land.minZ)/2.0+2))if(e instanceof LivingEntity){Location l=e.getLocation();if(land.occupies(l.getBlockX(),l.getBlockY(),l.getBlockZ())){Location returnTo=safe.clone();returnTo.setDirection(center().toVector().subtract(returnTo.clone().add(0,1.6,0).toVector()));if(!e.teleport(returnTo))throw new IllegalStateException("An occupant could not be moved safely.");}}

        }

        void step(TickWorkBudget work,TickWorkBudget biomes)throws Exception{

            if(!admin.isOnline()||!(new ShardAccess(plugin).staff(admin)||new ShardAccess(plugin).moderator(admin)))throw new IllegalStateException("Staff access lost during removal.");
            if(finished)return;if(!islands.describe(world).id().equals(land.realm))throw new IllegalStateException("Realm changed during removal.");evacuate();

            if(reclaim){stepReclaim(work,biomes);return;}
            if(cast!=null&&!cast.tick())return;
            age++;if(age<=crackTicks){fracture();return;}

            if(shatteredAt<0)shatter();dissolve.tick();fractureSkin.update(1);

            int unbindAge=age-shatteredAt+1;

            if(unbindAge<dissolveTicks*.70){

                if(unbindAge%10==0&&admin.isOnline())admin.sendActionBar(Component.text("Realm unbinding • energy building • "+(int)(70.0*unbindAge/(dissolveTicks*.70))+"%",NamedTextColor.LIGHT_PURPLE));

                return;

            }

            if(!ruptured){ruptured=true;dissolve.burst(fractureSkin.anchors);}

            

            int released=DissolvePattern.releaseCount(eligible.size(),age-shatteredAt+1,dissolveTicks);

            for(;index<released&&work.acquire();index++){GeneratedLand.Cell cell=eligible.get(index);Block b=block(cell.pos());if(!cell.removable(b.getBlockData().getAsString())){skipped++;continue;}if(!guard.allowed(b.getLocation()))throw new IllegalStateException("Protection changed during removal.");

                if(sweep.visuals.contains(cell.pos())){dissolve.detach(b.getLocation(),b.getBlockData());if(!cell.removable(b.getBlockData().getAsString())||!guard.allowed(b.getLocation()))throw new IllegalStateException("Terrain or protection changed while detaching a visual fragment.");}

                b.setBlockData(Bukkit.createBlockData(cell.original()),false);removed.add(cell.pos());}

            if(age%10==0&&admin.isOnline())admin.sendActionBar(Component.text("Realm unbinding • "+(int)(100.0*index/Math.max(1,eligible.size()))+"%",NamedTextColor.AQUA));

            if(age%20==0&&index<eligible.size())sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME,.45f,.65f+(float)index/Math.max(1,eligible.size()));

            if(index<eligible.size())return;

            while(biomeIndex<biomeEntries.size()&&biomes.acquire()){TerrainPlan.Pos pos=biomeEntries.get(biomeIndex++);Block b=block(pos);if(b.getBiome().equals(biomeBefore.get(pos))){b.setBiome(biomeRestore.get(pos));biomesChanged.add(pos);}}

            if(biomeIndex<biomeEntries.size()||!dissolve.done())return;

            journal.set("testing-removal.removed",removed.size());journal.set("testing-removal.preserved",skipped);save("REMOVED");finished=true;cleanup();

            if(!bulk&&admin.isOnline())admin.sendMessage("§aShard fragment removed: "+removed.size()+" blocks. Preserved "+skipped+" changed blocks. Your original island remains intact.");
            onSuccess.run();
        }

        void stepReclaim(TickWorkBudget work,TickWorkBudget biomes)throws Exception{
            if(!admin.isOnline()||!new ShardAccess(plugin).moderator(admin))throw new IllegalStateException("Reclaimer moderation access lost.");
            reclaimer.tick();age++;
            if(!reclaimer.sealed()||!reclaimer.capture())return;
            if(!reclaimer.returning()){
                for(;index<eligible.size()&&work.acquire();index++){var cell=eligible.get(index);Block b=block(cell.pos());if(!cell.removable(b.getBlockData().getAsString())||!guard.allowed(b.getLocation()))throw new IllegalStateException("Placement or protection changed during reclamation.");b.setBlockData(Bukkit.createBlockData(cell.original()),false);removed.add(cell.pos());}
                if(age%10==0)admin.sendActionBar(Component.text("Drawing realm into its shard • "+(int)(100.0*index/Math.max(1,eligible.size()))+"%",NamedTextColor.YELLOW));
                if(index<eligible.size())return;
                while(biomeIndex<biomeEntries.size()&&biomes.acquire()){var pos=biomeEntries.get(biomeIndex++);Block b=block(pos);if(b.getBiome().equals(biomeBefore.get(pos))){b.setBiome(biomeRestore.get(pos));biomesChanged.add(pos);}}
                if(biomeIndex<biomeEntries.size())return;reclaimer.shrink();
            }
            if(!reclaimer.done())return;
            journal.set("reclamation.removed",removed.size());save("RECLAIMED");finished=true;cleanup();
            try{reclaimRefunds.queue(land.path,admin.getUniqueId(),returnShard);boolean delivered=reclaimRefunds.deliver(admin);admin.sendMessage("§eRealm reclaimed: "+removed.size()+" blocks returned to void. "+(delivered?"Your original Realm Shard has been returned.":"Your original shard is queued; free an inventory slot to receive it."));}
            catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Reclaimed terrain; refund recovery required for "+land.path,ex);admin.sendMessage("§cRealm reclaimed; its saved refund needs recovery. The placement cannot be reclaimed twice.");}
        }

        Location center(){return new Location(world,collapse.cx,collapse.cy,collapse.cz);}

        void sound(Sound sound,float volume,float pitch){for(Player p:world.getPlayers())if(p.getLocation().distanceSquared(center())<160*160)p.playSound(p.getLocation(),sound,volume,pitch);}

        Color color(){if(journal.getString("spec","").endsWith("rainbow"))return Color.fromRGB(java.awt.Color.HSBtoRGB((age%120)/120f,.8f,1)&0xffffff);return glow;}

        void fracture(){

            double phase=age/(double)crackTicks;fractureSkin.update(phase);

            if(age%24==0)sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,.55f,(float)(.50+phase*.60));

            if(age%10==0&&admin.isOnline())admin.sendActionBar(Component.text(new String[]{"Hairline rifts","Seams spreading","World splitting","Fractures opening","Rupture imminent"}[FractureSkin.stage(phase)-1]+" • "+(int)(phase*100)+"%",NamedTextColor.AQUA));

        }

        void shatter(){

            shatteredAt=age;sound(Sound.BLOCK_GLASS_BREAK,.9f,.65f);sound(Sound.BLOCK_BEACON_DEACTIVATE,.8f,.65f);dissolve.pulse(center());

        }

        void abort(String reason){

            if(finished)return;finished=true;boolean restored=true;

            try{for(TerrainPlan.Pos p:removed){Block b=block(p);if(b.getType().isAir())b.setBlockData(Bukkit.createBlockData(before.get(p)),false);else restored=false;}for(TerrainPlan.Pos p:biomesChanged){Block b=block(p);if(b.getBiome().equals(biomeRestore.get(p)))b.setBiome(biomeBefore.get(p));else restored=false;}if(started)save(restored?"COMPLETE":"REVIEW_REQUIRED");}catch(Exception e){plugin.getLogger().severe("Testing removal requires manual review: "+land.path+" "+e);}

            cleanup();onFailure.run();if(!bulk&&admin.isOnline())admin.sendMessage("§c"+reason);
        }

        void save(String status)throws Exception{journal.set("status",status);RealmShards.atomicSave(journal,land.path);}

        void cleanup(){if(reclaimer!=null)reclaimer.close();if(cast!=null)cast.close();if(task!=null)task.cancel();if(dissolve!=null)dissolve.close();if(fractureSkin!=null)fractureSkin.close();if(!batches.containsKey(world.getUID()))for(int[] c:chunks)world.removePluginChunkTicket(c[0],c[1],plugin);jobs.remove(world.getUID(),this);onClose.run();onClose=()->{};}

    }

}

