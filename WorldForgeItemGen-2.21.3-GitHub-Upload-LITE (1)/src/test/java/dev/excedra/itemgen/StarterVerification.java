package dev.excedra.itemgen;
import java.util.*;
final class StarterVerification {
 static void run(){
  Verification.check(StarterGrant.parse(new String[]{"give","Seth","voidroot","3"},null).slots()==3,"Named aliases and quantities");
  Verification.check(StarterGrant.parse(new String[]{"give","crown"},"Seth").player().equals("Seth"),"Legacy self give");
  Verification.check(StarterGrant.parse(new String[]{"giveall","Seth","4"},null).slots()==36,"Complete collections capacity");
  for(String[] bad:new String[][]{{"give","crown"},{"give","Seth","missing"},{"give","Seth","root","0"},{"give","Seth","root","-1"},{"give","Seth","root","37"},{"giveall","Seth","5"},{"giveall","Seth","x"},{"give","Seth","root","1","extra"}}){boolean refused=false;try{StarterGrant.parse(bad,null);}catch(IllegalArgumentException ex){refused=true;}Verification.check(refused,"Grant validation before mutation");}
  Verification.check(java.util.Arrays.stream(StarterRealms.THEME_SLOTS).distinct().count()==9,"Nine unique GUI sockets");

  for(int a=0;a<9;a++)for(int b=0;b<9;b++){
   StarterRecipe x=StarterRecipe.single(a),y=StarterRecipe.single(b),m=x.merge(y);
   Verification.check(m.units()==2&&m.counts().get(a)==(a==b?2:1),"Recipe weights");
   Verification.check(StarterRecipe.decode(m.encode()).equals(m),"Recipe persistence");
   Verification.check(!m.identity().equals(x.identity()),"New discovery signature");
   TerrainPlan p=StarterRealms.plan(m),q=StarterRealms.plan(m);
   Verification.check(p.blocks.equals(q.blocks),"Persistent starter seed");
   for(var point:p.blocks.keySet())Verification.check(Math.abs(point.x())<=p.radius+4&&Math.abs(point.z())<=p.radius+4&&point.y()>=p.minY()&&point.y()<=p.maxY(),"Starter clearance bounds");
  }
  StarterRecipe full=StarterRecipe.single(0);for(int i=1;i<9;i++)full=full.merge(StarterRecipe.single(i));
  Verification.check(full.tier()==5&&full.units()==9,"Tier ceiling independent of fusion depth");
  Verification.check(full.merge(StarterRecipe.single(0)).units()==10,"More than nine units supported");
  StarterRecipe bulk=StarterRecipe.aggregate(List.of(full,full),List.of(64,64));Verification.check(bulk.units()==1152&&bulk.counts().get(0)==128,"All stack amounts preserve nested recipes");
  Verification.check(StarterRecipe.decode(bulk.encode()).equals(bulk),"Large recipe persistence");
  boolean overflow=false;try{StarterRecipe.aggregate(List.of(new StarterRecipe(List.of(Long.MAX_VALUE,0L,0L,0L,0L,0L,0L,0L,0L),new UUID(1,2))),List.of(2));}catch(ArithmeticException ex){overflow=true;}Verification.check(overflow,"Overflow refused before consumption");
  // Every biome, tier and legal variant remains a named, persistent ingredient.
  for(var land:ShardSpec.Land.values())for(int tier=1;tier<=5;tier++)for(var variant:ShardSpec.Variant.values()){
   if(tier<3&&variant==ShardSpec.Variant.RAINBOW)continue;
   ShardSpec spec=new ShardSpec(tier,land,variant);StarterRecipe hybrid=StarterRecipe.single(0).merge(StarterRecipe.biome(spec));
   Verification.check(hybrid.units()==2&&hybrid.biomes().get(spec.key())==1,"Cross-family fusion preserves biome identity");
   Verification.check(hybrid.tier()>=tier&&hybrid.variant()==variant,"Tier and rarity survive fusion");
   Verification.check(StarterRecipe.decode(hybrid.encode()).equals(hybrid),"Hybrid persistence");
   TerrainPlan plan=StarterRealms.plan(hybrid);Verification.check(plan.land==land,"Biome atmosphere survives");
   Verification.check(plan.blocks.equals(StarterRealms.plan(hybrid).blocks),"Hybrid deterministic generation");
   for(var pos:plan.blocks.keySet())Verification.check(Math.abs(pos.x())<=plan.radius+4&&Math.abs(pos.z())<=plan.radius+4&&pos.y()>=plan.minY()&&pos.y()<=plan.maxY(),"Biomewoven clearance bounds");
   StarterRecipe nested=StarterRecipe.aggregate(List.of(hybrid,hybrid),List.of(32,64));Verification.check(nested.starterUnits()==96&&nested.biomes().get(spec.key())==96&&nested.units()==192,"Mixed bulk fusion conserves nested weights");
  }
  StarterRecipe uncharted=StarterRecipe.single(1).merge(StarterRecipe.biome(new ShardSpec(5,null,ShardSpec.Variant.RAINBOW)));
  Verification.check(StarterRealms.plan(uncharted).blocks.equals(StarterRealms.plan(StarterRecipe.decode(uncharted.encode())).blocks),"Uncharted resolution persists with signature");
  for(String bad:List.of("forest_t6_standard=1","snow_t2_rainbow=1","bogus_t3_standard=1","forest_t1_standard=0","forest_t1_standard=-1","forest_t1_standard=1;forest_t1_standard=2")){
   boolean invalid=false;try{StarterRecipe.decode("00000000-0000-0000-0000-000000000001:1,0,0,0,0,0,0,0,0:"+bad);}catch(RuntimeException ex){invalid=true;}Verification.check(invalid,"Malformed mixed recipe refused");
  }
  Verification.check(org.bukkit.Particle.FLASH.getDataType()==org.bukkit.Color.class,"Owner flight uses the shared color-safe shatter burst");
  var record=new org.bukkit.configuration.file.YamlConfiguration();var world=UUID.randomUUID();var realm=UUID.randomUUID();record.set("status","COMPLETE");record.set("world","testworld");record.set("realm",realm.toString());
  Verification.check(GeneratedLand.completedIn(record,"testworld",world,realm),"Legacy completed record accepted");record.set("world-uuid",world.toString());
  Verification.check(!GeneratedLand.completedIn(record,"testworld",UUID.randomUUID(),realm),"Other world UUID excluded from removeall");
  Verification.check(!GeneratedLand.completedIn(record,"otherworld",world,realm)&&!GeneratedLand.completedIn(record,"testworld",world,UUID.randomUUID()),"Other realms excluded from removeall");record.set("status","REMOVED");Verification.check(!GeneratedLand.completedIn(record,"testworld",world,realm),"Already removed transactions excluded");
  int largest=0;
  for(int mask=1;mask<512;mask++){
   List<Long> counts=new ArrayList<>(Collections.nCopies(9,0L));for(int i=0;i<9;i++)if((mask&(1<<i))!=0)counts.set(i,1L);
   StarterRecipe recipe=new StarterRecipe(counts,new UUID(812,mask));TerrainPlan plan=StarterRealms.plan(recipe);largest=Math.max(largest,plan.blocks.size());
   for(var pos:plan.blocks.keySet())Verification.check(Math.abs(pos.x())<=plan.radius+4&&Math.abs(pos.z())<=plan.radius+4&&pos.y()>=plan.minY()&&pos.y()<=plan.maxY(),"Every hybrid fits reserved volume");
   Verification.check(plan.maxY()>9&&plan.minY()<0,"Extended starter clearance");
  }
  StarterRecipe one=new StarterRecipe(List.of(1L,0L,0L,0L,0L,0L,0L,0L,0L),new UUID(1,2)),two=new StarterRecipe(List.of(2L,0L,0L,0L,0L,0L,0L,0L,0L),new UUID(1,2));
  Verification.check(StarterRealms.plan(two).radius>StarterRealms.plan(one).radius,"Every matching unit expands footprint");
  Set<Integer> layouts=new HashSet<>();
  for(int n=0;n<80;n++){
   StarterRecipe varied=new StarterRecipe(List.of(400L,100L,7L,3L,91L,5L,19L,33L,2L),new UUID(871,n+10));TerrainPlan plan=StarterRealms.plan(varied);
   layouts.add(plan.blocks.hashCode());Verification.check(plan.radius<=30&&plan.blocks.size()<40000,"Deep fusion keeps bounded work");
   for(var pos:plan.blocks.keySet())Verification.check(Math.abs(pos.x())<=plan.radius+4&&Math.abs(pos.z())<=plan.radius+4&&pos.y()>=plan.minY()&&pos.y()<=plan.maxY(),"Large recipes fit reserved bounds");
  }
  Verification.check(layouts.size()==80,"Identical ingredients with different signatures create different world layouts");
  StarterRecipe huge=new StarterRecipe(List.of(Long.MAX_VALUE,0L,0L,0L,0L,0L,0L,0L,0L),new UUID(91,72));Verification.check(StarterRealms.plan(huge).radius==24&&huge.tier()==5,"Machine limit never expands work without bound");
  System.out.println("PASS: all 511 starter theme sets fit reserved volume; largest starter plan "+largest+" blocks; 80 distinct deep-fusion layouts.");
  System.out.println("PASS: starter same/mixed recipes, persistence, fresh identities, seeded terrain, clearance and deep fusion.");
 }
}
