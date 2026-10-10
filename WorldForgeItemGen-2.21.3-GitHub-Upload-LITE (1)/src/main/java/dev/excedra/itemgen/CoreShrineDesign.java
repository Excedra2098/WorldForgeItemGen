package dev.excedra.itemgen;

import java.util.*;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Local custom models over protected terrain; no global vanilla texture overrides. */
final class CoreShrineDesign {
    record Ornament(String model,double x,double y,double z,float scale,boolean glow){}
    static boolean stone(Material material){return switch(material){case CRACKED_DEEPSLATE_BRICKS,DEEPSLATE_BRICKS,DEEPSLATE_TILES,MOSSY_STONE_BRICKS,TUFF,CHISELED_DEEPSLATE->true;default->false;};}
    static List<Ornament> ornaments(){
        List<Ornament> result=new ArrayList<>();
        result.add(new Ornament("crystal",.5,2.9,-3.5,1.2f,true));
        result.add(new Ornament("crystal",.5,6.2,.5,.9f,true));
        result.add(new Ornament("sigil",.5,6.2,-.25,1.25f,true));
        result.add(new Ornament("basin",.5,1.08,-3.5,1.75f,true));
        for(int side:new int[]{-1,1}){
            result.add(new Ornament("crystal",side*2+.5,3.6,1.5,.6f,true));
            result.add(new Ornament("chain",side*2+.5,4.7,1.5,1f,false));
            result.add(new Ornament("lantern",side*3+.5,3.5,1.5,.85f,true));
            result.add(new Ornament("lantern",side*4+.5,2.5,-2.5,.85f,true));
            result.add(new Ornament("ivy",side*4+.5,3.0,.98,2f,false));
            result.add(new Ornament("ivy",side*1+.5,6.5,-.02,1.5f,false));
            result.add(new Ornament("crystal",side*4+.5,-2.6,-2.5,.7f,true));
            result.add(new Ornament("chain",side*4+.5,-1.4,-2.5,1f,false));
            result.add(new Ornament("crystal",side*2+.5,-4.3,.5,.65f,true));
            result.add(new Ornament("crystal",side*.8+.5,-5.2,.5,.6f,true));
            result.add(new Ornament("ivy",side*4+.5,-1.2,-4.02,2f,false));
        }
        result.add(new Ornament("crystal",.5,-5.7,.5,.9f,true));
        return List.copyOf(result);
    }
    static Set<TerrainPlan.Pos> groups(){
        Set<TerrainPlan.Pos> result=new LinkedHashSet<>();
        RealmCoreLayout.blocks().forEach((p,m)->{if(stone(m))result.add(new TerrainPlan.Pos(Math.floorDiv(p.x(),3),Math.floorDiv(p.y(),3),Math.floorDiv(p.z(),3)));});return Set.copyOf(result);
    }
    static String groupName(TerrainPlan.Pos p){return "stone_"+p.x()+"_"+p.y()+"_"+p.z();}
    public static void main(String[] args)throws Exception{
        List<String> rows=new ArrayList<>();RealmCoreLayout.blocks().forEach((p,m)->rows.add(p.x()+"|"+p.y()+"|"+p.z()+"|"+m.name()+"|"+stone(m)));
        java.nio.file.Files.write(java.nio.file.Path.of(args[0]),rows);
        if(args.length>1)java.nio.file.Files.write(java.nio.file.Path.of(args[1]),ornaments().stream().map(o->o.model()+"|"+o.x()+"|"+o.y()+"|"+o.z()+"|"+o.scale()).toList());
    }
    static ItemStack model(String name){ItemStack item=new ItemStack(Material.AMETHYST_SHARD);ItemMeta meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards","core/classic/"+name));item.setItemMeta(meta);return item;}

    private final RealmCoreStore store;
    private final CoreAppearance appearance;
    private final Predicate<UUID> available;
    private final Map<UUID,List<ItemDisplay>> active=new HashMap<>();
    private final Map<UUID,List<Interaction>> targets=new HashMap<>();
    UUID interactionRealm(Entity entity){for(var e:targets.entrySet())if(e.getValue().contains(entity))return e.getKey();return null;}
    private record Moving(ItemDisplay display,float scale,int phase){}
    private record Preview(UUID player,CoreAppearance.Profile profile,long expires){}
    private final Map<UUID,List<Moving>> moving=new HashMap<>();
    private final Map<UUID,Preview> previews=new HashMap<>();
    private final BukkitTask task,animation;
    private int tick;
    CoreShrineDesign(JavaPlugin plugin,RealmCoreStore store,CoreAppearance appearance,Predicate<UUID> available){this.store=store;this.appearance=appearance;this.available=available;task=Bukkit.getScheduler().runTaskTimer(plugin,this::refresh,10,20);animation=Bukkit.getScheduler().runTaskTimer(plugin,this::animate,1,4);}
    void preview(UUID realm,UUID player,CoreAppearance.Profile profile){previews.put(realm,new Preview(player,profile,System.currentTimeMillis()+60000));detach(realm);refresh();}
    void clearPreview(UUID realm){if(previews.remove(realm)!=null){detach(realm);refresh();}}
    private CoreAppearance.Profile profile(UUID realm){Preview preview=previews.get(realm);return preview==null?appearance.profile(realm):preview.profile();}
    void refresh(){
        for(var entry:List.copyOf(previews.entrySet())){Preview preview=entry.getValue();Player viewer=Bukkit.getPlayer(preview.player());var core=store.get(entry.getKey());if(System.currentTimeMillis()>preview.expires()||viewer==null||!viewer.isOnline()||core==null||!viewer.getWorld().getUID().equals(core.world())||viewer.getLocation().distanceSquared(new Location(viewer.getWorld(),core.x(),core.y()+1,core.z()))>144){previews.remove(entry.getKey());detach(entry.getKey());}}
        Set<UUID> seen=new HashSet<>();
        for(var core:store.all()){
            if(!available.test(core.realm()))continue;
            World world=Bukkit.getWorld(core.world());if(world==null)continue;
            boolean loaded=true;for(int x=(core.x()-5)>>4;x<=(core.x()+5)>>4;x++)for(int z=(core.z()-5)>>4;z<=(core.z()+5)>>4;z++)if(!world.isChunkLoaded(x,z))loaded=false;
            if(!loaded)continue;
            // No entities or animation work for a Core with no nearby viewers.
            if(world.getPlayers().stream().noneMatch(p->Math.abs(p.getLocation().getX()-core.x())<64&&Math.abs(p.getLocation().getZ()-core.z())<64))continue;
            seen.add(core.realm());var current=active.get(core.realm());if(current!=null&&current.stream().allMatch(Entity::isValid)&&targets.getOrDefault(core.realm(),List.of()).stream().allMatch(Entity::isValid))continue;
            detach(core.realm());List<ItemDisplay> displays=new ArrayList<>();List<Moving> crystals=new ArrayList<>();List<Interaction> hitboxes=new ArrayList<>();CoreAppearance.Profile chosen=profile(core.realm());
            try{
                if(core.design()==5){compact(world,core,chosen,displays,crystals,hitboxes);active.put(core.realm(),displays);moving.put(core.realm(),crystals);targets.put(core.realm(),hitboxes);continue;}
                Set<TerrainPlan.Pos> groups=new LinkedHashSet<>();if(core.design()>=1)((core.design()==2||core.design()==4)?RealmCoreLayout.attached():RealmCoreLayout.blocks()).forEach((p,m)->{if(stone(m))groups.add(new TerrainPlan.Pos(Math.floorDiv(p.x(),3),Math.floorDiv(p.y(),3),Math.floorDiv(p.z(),3)));});
                for(var group:groups)displays.add(spawn(world,new Location(world,core.x()+group.x()*3+1.5,core.y()+group.y()*3+1.5,core.z()+group.z()*3+1.5),((core.design()==2||core.design()==4)?"attached_":"")+groupName(group),3.002f,false));
                if(core.design()==3)for(var group:CorePadLayout.floorGroups())displays.add(spawn(world,new Location(world,core.x()+group.x()*3+1.5,core.y()+1.5,core.z()+group.z()*3+1.5),CorePadLayout.groupName(group),3.002f,false));
                if(core.design()==4)for(var group:RaisedCorePadLayout.groups())displays.add(spawn(world,new Location(world,core.x()+group.x()*3+1.5,core.y()+group.y()*3+1.5,core.z()+group.z()*3+1.5),RaisedCorePadLayout.groupName(group),3.002f,false));
                int phase=0;
                // Legacy block geometry stays intact; its new cosmetic heart sits above the basin.
                for(var o:core.design()<1?List.of(ornaments().getFirst()):ornaments()){
                    if((core.design()==2||core.design()==4)&&o.y()<0)continue;
                    String name=o.model();if(name.equals("crystal"))name=CoreAppearance.crystalModel(chosen);
                    ItemDisplay d=spawn(world,new Location(world,core.x()+o.x(),core.y()+o.y(),core.z()+o.z()),name,o.scale(),o.glow());displays.add(d);
                    if(o.model().equals("crystal")&&o.y()>2&&Math.abs(o.x()-.5)<.1){crystals.add(new Moving(d,o.scale(),phase++*40));hitboxes.add(world.spawn(new Location(world,core.x()+o.x(),core.y()+o.y()-.9,core.z()+o.z()),Interaction.class,h->{h.setInteractionWidth(1.6f);h.setInteractionHeight(1.8f);h.setResponsive(true);h.setPersistent(false);h.setInvulnerable(true);h.addScoreboardTag("realmshards_temporary");h.addScoreboardTag("realmcore_crystal_target");}));}
                }
                String decoration=chosen.get("decor");
                if(!decoration.equals("none"))for(int n=0;n<4;n++){
                    double angle=Math.PI/4+n*Math.PI/2;boolean pillar=decoration.equals("pillars");String model=pillar?"pillar":decoration.equals("runes")?"sigil":decoration.equals("winter")?"crystal_twin_winter":CoreAppearance.crystalModel(chosen);
                    ItemDisplay d=spawn(world,new Location(world,core.x()+.5+Math.cos(angle)*2.5,core.y()+(pillar?1.8:3.6),core.z()-3.5+Math.sin(angle)*1.8),model,pillar?.8f:.28f,true);displays.add(d);if(!pillar)crystals.add(new Moving(d,.28f,n*30));
                }
                String attachment=chosen.get("attach");
                if(!attachment.equals("none")){
                    String model=attachment.equals("relic")?"sigil":"ring";ItemDisplay d=spawn(world,new Location(world,core.x()+.5,core.y()+3.0,core.z()-3.5),model,attachment.equals("halo")?1.6f:1.2f,true);displays.add(d);crystals.add(new Moving(d,attachment.equals("halo")?1.6f:1.2f,70));
                }
                active.put(core.realm(),displays);moving.put(core.realm(),crystals);targets.put(core.realm(),hitboxes);
            }catch(RuntimeException failure){displays.forEach(Entity::remove);hitboxes.forEach(Entity::remove);}
        }
        for(UUID realm:List.copyOf(active.keySet()))if(!seen.contains(realm))detach(realm);
    }
    private void compact(World world,RealmCoreStore.Core core,CoreAppearance.Profile chosen,List<ItemDisplay> displays,List<Moving> moving,List<Interaction> hitboxes){
        displays.add(spawnCompact(world,new Location(world,core.x()+.5,core.y()+2,core.z()+.5),"pedestal_complete",2,false));
        ItemDisplay crystal=spawnCompact(world,new Location(world,core.x()+.5,core.y()+CompactCoreLayout.CRYSTAL_Y,core.z()+.5),CompactCoreLayout.crystalModel(chosen),1,true);displays.add(crystal);moving.add(new Moving(crystal,1,0));
        hitboxes.add(target(world,new Location(world,core.x()+.5,core.y()+3.6,core.z()+.5),1.8f,3.4f));
        hitboxes.add(target(world,new Location(world,core.x()+.5,core.y()+1,core.z()+.5),5,2));
        String decor=chosen.get("decor");if(!decor.equals("none"))for(int n=0;n<4;n++){double a=Math.PI/4+n*Math.PI/2;String model=decor.equals("pillars")?"pillar":decor.equals("runes")?"sigil":decor.equals("winter")?"crystal_twin_winter":CoreAppearance.crystalModel(chosen);ItemDisplay d=spawn(world,new Location(world,core.x()+.5+Math.cos(a)*1.8,core.y()+3.2,core.z()+.5+Math.sin(a)*1.8),model,decor.equals("pillars")?.35f:.22f,true);displays.add(d);if(!decor.equals("pillars"))moving.add(new Moving(d,.22f,n*30));}
        String attach=chosen.get("attach");if(!attach.equals("none")){ItemDisplay d=spawn(world,new Location(world,core.x()+.5,core.y()+CompactCoreLayout.CRYSTAL_Y,core.z()+.5),attach.equals("relic")?"sigil":"ring",1.1f,true);displays.add(d);moving.add(new Moving(d,1.1f,70));}
    }
    private Interaction target(World world,Location at,float width,float height){return world.spawn(at,Interaction.class,h->{h.setInteractionWidth(width);h.setInteractionHeight(height);h.setResponsive(true);h.setPersistent(false);h.setInvulnerable(true);h.addScoreboardTag("realmshards_temporary");h.addScoreboardTag("realmcore_crystal_target");});}
    private ItemDisplay spawnCompact(World world,Location at,String model,float scale,boolean glow){ItemDisplay d=spawn(world,at,"stone",scale,glow);d.setItemStack(CompactCoreLayout.model(model));return d;}
    static float rotation(int tick,int phase,String effect){return effect.equals("still")?0:(float)((tick+phase)*Math.PI/(effect.equals("gentle")?500:250));}
    static float bob(int tick,int phase){return (float)Math.sin((tick+phase)*Math.PI/100)*.12f;}
    private void animate(){tick=(tick+4)%20000;
        for(var entry:moving.entrySet()){
            if(!available.test(entry.getKey()))continue;CoreAppearance.Profile p=profile(entry.getKey());String effect=p.get("effect");if(effect.equals("still"))continue;
            for(Moving m:entry.getValue())if(m.display().isValid()){
                float angle=rotation(tick,m.phase(),effect);float scale=m.scale()*(effect.equals("pulse")?1+(float)Math.sin(tick*Math.PI/50)*.035f:1);
                m.display().setInterpolationDelay(0);m.display().setInterpolationDuration(4);
                boolean relic=m.phase()==70&&p.get("attach").equals("relic");
                m.display().setTransformation(new Transformation(new Vector3f(relic?(float)Math.cos(angle):0,bob(tick,m.phase()),relic?(float)Math.sin(angle):0),new Quaternionf().rotateY(angle),new Vector3f(scale),new Quaternionf()));
            }
            if(tick%20==0&&!entry.getValue().isEmpty()){
                var d=entry.getValue().getFirst().display();if(!d.isValid())continue;Location at=d.getLocation();
                double a=tick*.06;RealmShards.dust(at.clone().add(Math.cos(a)*.7,bob(tick,0),Math.sin(a)*.7),Color.fromRGB(CoreAppearance.color(p.get("primary"))),.55f);
                RealmShards.dust(at.clone().add(-Math.cos(a)*.7,.3,-Math.sin(a)*.7),Color.fromRGB(CoreAppearance.color(p.get("secondary"))),.4f);
                if(effect.equals("aurora"))for(int n=0;n<3;n++)RealmShards.dust(at.clone().add(Math.cos(a+n)*1.2,.3*n,Math.sin(a+n)*1.2),Color.fromRGB(CoreAppearance.color(n%2==0?p.get("primary"):p.get("secondary"))),.5f);
            }
        }
    }
    private ItemDisplay spawn(World world,Location at,String model,float scale,boolean glow){return world.spawn(at,ItemDisplay.class,d->{
        d.setItemStack(model(model));d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.setInvulnerable(true);d.addScoreboardTag("realmshards_temporary");d.addScoreboardTag("realmcore_shrine");d.setViewRange(2.5f);if(glow)d.setBrightness(new Display.Brightness(15,15));
        d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(scale),new Quaternionf()));
    });}
    void detach(UUID realm){var hitboxes=targets.remove(realm);if(hitboxes!=null)hitboxes.forEach(Entity::remove);moving.remove(realm);var items=active.remove(realm);if(items!=null)items.forEach(Entity::remove);}
    void stop(){task.cancel();animation.cancel();previews.clear();for(UUID realm:List.copyOf(active.keySet()))detach(realm);}
}
