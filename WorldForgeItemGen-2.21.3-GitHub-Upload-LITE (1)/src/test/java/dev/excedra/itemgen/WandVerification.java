package dev.excedra.itemgen;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.MainHand;

/** Exercise actual casting against an instrumented Bukkit surface, including simultaneous impacts. */
final class WandVerification {
 record ParticleHit(int tick,Location at,int count,Color color){}
 static void run(){
  for(boolean all:List.of(false,true)){
   int[] tick={0},removed={0};List<ParticleHit> hits=new ArrayList<>();List<Location> poses=new ArrayList<>();
   ItemDisplay display=(ItemDisplay)Proxy.newProxyInstance(WandVerification.class.getClassLoader(),new Class<?>[]{ItemDisplay.class},(p,m,a)->{
    if(m.getName().equals("teleport")){poses.add(((Location)a[0]).clone());return true;}
    if(m.getName().equals("remove")){removed[0]++;return null;}if(m.getReturnType()==boolean.class)return true;return null;
   });
   World world=(World)Proxy.newProxyInstance(WandVerification.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->{
    if(m.getName().equals("equals"))return p==a[0];if(m.getName().equals("hashCode"))return 1;
    if(m.getName().equals("spawn")){for(Object arg:a)if(arg instanceof Consumer<?> c)((Consumer<ItemDisplay>)c).accept(display);return display;}
    if(m.getName().equals("spawnParticle")){Verification.check(a[0]==Particle.DUST,"Wand beams use explicit dust colors");var dust=(Particle.DustOptions)a[a.length-1];hits.add(new ParticleHit(tick[0],((Location)a[1]).clone(),(int)a[2],dust.getColor()));return null;}
    throw new AssertionError("Unexpected world access: "+m.getName());
   });
   Player player=(Player)Proxy.newProxyInstance(WandVerification.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
    case "getWorld"->world;case "getEyeLocation"->new Location(world,0,81.6,0,0,0);case "getLocation"->new Location(world,0,80,0,0,0);
    case "getMainHand"->MainHand.RIGHT;case "isOnline"->true;case "swingMainHand","playSound"->null;default->throw new AssertionError("Unexpected player access: "+m.getName());
   });
   List<Location> targets=all?List.of(new Location(world,45,85,30),new Location(world,-60,90,35),new Location(world,110,72,-20)):List.of(new Location(world,90,82,60));
   WandCast cast=new WandCast(world,player,null,all,targets,()->{},()->{});
   for(int i=1;i<=WandCast.DURATION;i++){tick[0]=i;Verification.check(cast.tick()==(i==WandCast.DURATION),"Removal waits until wand cast completes");}
   Verification.check(removed[0]==1,"Temporary wand entity removed exactly once");cast.close();Verification.check(removed[0]==1,"Cleanup is idempotent");
   Color beam=Color.fromRGB(all?0x100B18:0xB64DFF);
   for(Location target:targets)Verification.check(hits.stream().anyMatch(h->h.tick()==WandCast.IMPACT+8&&h.count()==14&&h.at().distanceSquared(target)<.0001&&h.color().equals(beam)),"Every target receives simultaneous correctly colored impact");
   Verification.check(hits.stream().noneMatch(h->h.tick()<WandCast.IMPACT),"No beams before gesture impact");
   if(all)Verification.check(poses.get(21).getY()<poses.getFirst().getY()-.8,"Nullifier visibly jabs toward the ground");
   else Verification.check(poses.stream().allMatch(l->l.distanceSquared(poses.getFirst())<.0001),"Single wand stays at the casting hand while aimed at a remote placement");
  }
  // Weaving biomes into a finished layout preserves every existing building and approach.
  StarterRecipe starter=new StarterRecipe(Collections.nCopies(9,1L),new UUID(31,75));TerrainPlan p=StarterRealms.plan(starter);
  Map<TerrainPlan.Pos,Material> protectedCells=new HashMap<>();
  for(var e:p.blocks.entrySet()){
   var pos=e.getKey();Integer floor=p.surface.get(TerrainPlan.key(pos.x(),pos.z()));
   if(floor!=null&&(pos.y()>floor||e.getValue()==Material.STONE_BRICKS||e.getValue()==Material.SEA_LANTERN))protectedCells.put(pos,e.getValue());
  }
  Map<String,Long> biomes=new TreeMap<>();for(var land:ShardSpec.Land.values())biomes.put(new ShardSpec(5,land,ShardSpec.Variant.RAINBOW).key(),1L);
  StarterRecipe hybrid=new StarterRecipe(starter.counts(),starter.identity(),biomes);StarterBiomeWeave.apply(p,hybrid);
  for(var e:protectedCells.entrySet())Verification.check(p.blocks.get(e.getKey())==e.getValue(),"Biome weave preserves existing architecture and circulation");
  Set<ShardSpec.Land> seen=new HashSet<>();var influences=StarterBiomeWeave.influences(hybrid);
  for(int x=-100;x<=100;x++)for(int z=-100;z<=100;z++)seen.add(StarterBiomeWeave.sector(influences,x,z,.4));
  Verification.check(seen.size()==ShardSpec.Land.values().length,"All represented biomes have contiguous assigned sectors");
  System.out.println("PASS: Actual remote wand casting, synchronized purple/black impacts, ground-jab trajectory, cleanup; biome fusion preserves built architecture and roads.");
 }
}
