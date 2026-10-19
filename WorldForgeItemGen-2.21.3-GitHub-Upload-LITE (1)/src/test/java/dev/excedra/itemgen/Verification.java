package dev.excedra.itemgen;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

/** Runs outside the server: seeded terrain, catalogue invariants and all exported asset references. */
public class Verification {
    static int assertions;
    static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        StarterVerification.run();
        WandVerification.run();ReclaimerVerification.run(Path.of(args[0]).toAbsolutePath().getParent().resolve("target/reclaim-fixtures-"+UUID.randomUUID()));
        int catalogue=0,largest=0;
        for(int tier=1;tier<=5;tier++)for(ShardSpec.Land land:ShardSpec.Land.values()){
            TerrainPlan a=new TerrainPlan(3+tier*2,land,82501,tier),b=new TerrainPlan(3+tier*2,land,82501,tier);
            check(a.blocks.equals(b.blocks),"Seed reproducibility: "+land+" "+tier);
            check(!a.blocks.isEmpty(),"Empty terrain: "+land);
            largest=Math.max(largest,a.blocks.size());
            for(var e:a.blocks.entrySet()){TerrainPlan.Pos p=e.getKey();check(Math.abs(p.x())<=a.radius+2&&Math.abs(p.z())<=a.radius+2,"Terrain outside reserved footprint: "+land);check(p.y()>=-a.radius-3&&p.y()<=12,"Terrain outside vertical clearance: "+land);check(e.getValue()!=org.bukkit.Material.AIR,"Planned air mutation");}
            for(ShardSpec.Variant variant:ShardSpec.Variant.values())if(tier>=3||variant!=ShardSpec.Variant.RAINBOW){ShardSpec s=new ShardSpec(tier,land,variant);check(s.key().matches("[a-z]+_t[1-5]_(standard|shiny|rainbow)"),"Invalid model identifier");catalogue++;}
        }
        // Trunks must never shade a recorded grass support: vanilla would decay it
        // to dirt after placement and reject an otherwise untouched reclamation.
        for(ShardSpec.Land wooded:List.of(ShardSpec.Land.FOREST,ShardSpec.Land.JUNGLE))
            for(int tier=1;tier<=5;tier++)
                for(long seed=82501;seed<82526;seed++){
                    TerrainPlan woodedPlan=new TerrainPlan(3+tier*2,wooded,seed,tier);
                    org.bukkit.Material trunk=wooded==ShardSpec.Land.FOREST?org.bukkit.Material.OAK_LOG:org.bukkit.Material.JUNGLE_LOG;
                    for(var entry:woodedPlan.blocks.entrySet())if(entry.getValue()==trunk){
                        var pos=entry.getKey();
                        var below=new TerrainPlan.Pos(pos.x(),pos.y()-1,pos.z());
                        check(woodedPlan.blocks.get(below)!=org.bukkit.Material.GRASS_BLOCK,
                            "Unstable grass beneath generated trunk: "+wooded+" tier="+tier+" seed="+seed+" at "+below);
                        // The tree leaves span two cells beyond the trunk; the
                        // one-cell fringe can be shaded too. No grass may remain
                        // in this generated canopy footprint.
                        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
                            int x=pos.x()+dx,z=pos.z()+dz;
                            Integer groundY=woodedPlan.surface.get(TerrainPlan.key(x,z));
                            if(groundY==null)continue;
                            var ground=new TerrainPlan.Pos(x,groundY,z);
                            check(woodedPlan.blocks.get(ground)!=org.bukkit.Material.GRASS_BLOCK,
                                "Unstable canopy grass: "+wooded+" tier="+tier+" seed="+seed+" at "+ground);
                        }
                    }
                }
        check(catalogue==156,"Biome catalogue count");
        SchematicVerification.run(Path.of(args[0]).toAbsolutePath().getParent().resolve("target/schematic-fixtures"));
        verifyClaims(Path.of(args[0]).toAbsolutePath().getParent().resolve("target/claim-fixtures-"+UUID.randomUUID()));
        verifyVariantSizes();
        verifyRemovalRecords();
        verifyTickBudgets();
        verifyJournalWorker(Path.of(args[0]).toAbsolutePath().getParent().resolve("target/verification-journals"));
        verifyCollapse();
        verifyShatterParticles();
        verifyPrismFlights();
        verifyHeldPositions();
        verifyTerrainDissolve();
        verifyCustomFractures(Path.of(args[0]));
        boolean rejected=false;try{new ShardSpec(2,ShardSpec.Land.FOREST,ShardSpec.Variant.RAINBOW);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Rainbow tier restriction");
        rejected=false;try{new ShardSpec(0,null,ShardSpec.Variant.STANDARD);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Invalid tier accepted");
        Path root=Path.of(args[0]);int modelCount=0,itemCount=0;
        JsonArray atlasSources=JsonParser.parseString(Files.readString(root.resolve("assets/minecraft/atlases/items.json"))).getAsJsonObject().getAsJsonArray("sources");
        java.util.Set<String> stitched=new java.util.HashSet<>();
        for(JsonElement source:atlasSources){JsonObject entry=source.getAsJsonObject();if(entry.get("type").getAsString().equals("minecraft:directory")){
            String directory=entry.get("source").getAsString(),prefix=entry.get("prefix").getAsString();Path textures=root.resolve("assets/realmshards/textures/"+directory);
            if(Files.exists(textures))try(var pngs=Files.walk(textures)){for(Path png:pngs.filter(p->p.toString().endsWith(".png")).toList()){String relative=textures.relativize(png).toString().replace('\\','/');stitched.add("realmshards:"+prefix+relative.substring(0,relative.length()-4));}}
        }}

        try(var files=Files.walk(root.resolve("assets/realmshards/models"))){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()){
            JsonObject model=JsonParser.parseString(Files.readString(file)).getAsJsonObject();modelCount++;
            JsonObject textures=model.getAsJsonObject("textures");for(var e:textures.entrySet()){String texture=e.getValue().getAsString();if(texture.startsWith("realmshards:")){check(Files.exists(root.resolve("assets/realmshards/textures/"+texture.substring(12)+".png")),"Missing texture: "+texture);check(stitched.contains(texture),"Texture not stitched into item atlas: "+texture);}}
            if(model.has("elements"))for(JsonElement element:model.getAsJsonArray("elements")){JsonObject box=element.getAsJsonObject();JsonArray from=box.getAsJsonArray("from"),to=box.getAsJsonArray("to");for(int i=0;i<3;i++){double lo=from.get(i).getAsDouble(),hi=to.get(i).getAsDouble();check(lo<=hi&&lo>=-16&&hi<=32,"Invalid cuboid coordinates in "+file);}
                if(box.has("rotation")){double angle=box.getAsJsonObject("rotation").get("angle").getAsDouble();check(List.of(-45.0,-22.5,0.0,22.5,45.0).contains(angle),"Unsupported cuboid rotation");}
                for(var face:box.getAsJsonObject("faces").entrySet()){String ref=face.getValue().getAsJsonObject().get("texture").getAsString();check(ref.startsWith("#")&&textures.has(ref.substring(1)),"Unresolved face texture in "+file);}
            }
        }}
        try(var files=Files.walk(root.resolve("assets/realmshards/items"))){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()){JsonObject item=JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("model");if(item.get("type").getAsString().equals("minecraft:select")){check(item.get("property").getAsString().equals("minecraft:display_context"),"Hand render selector property");JsonObject choice=item.getAsJsonArray("cases").get(0).getAsJsonObject();check(choice.getAsJsonArray("when").size()==4&&choice.getAsJsonObject("model").get("type").getAsString().equals("minecraft:empty"),"Static hand view must yield to rotating display");item=item.getAsJsonObject("fallback");}String model=item.get("model").getAsString();check(Files.exists(root.resolve("assets/realmshards/models/"+model.substring(12)+".json")),"Unresolved item model: "+file);itemCount++;}}
        check(modelCount==itemCount,"Item/model catalogue mismatch");
        verifyFragmentTextures(root);
        if(args.length>1){
            Class<?> core=Class.forName("com.worldforge.WorldforgePlugin",false,Verification.class.getClassLoader());
            Class<?> island=core.getMethod("islandService").getReturnType();
            check(island.getMethod("forWorld",org.bukkit.World.class).getReturnType()==Optional.class,"Worldforge forWorld signature");
            check(island.getMethod("canBuild",UUID.class,UUID.class).getReturnType()==boolean.class,"Worldforge canBuild signature");
            Class<?> record=Class.forName("com.worldforge.realm.island.RealmWorldRecord",false,Verification.class.getClassLoader());
            for(String method:List.of("ownerId","realmId","worldName","phase"))check(record.getMethod(method).getParameterCount()==0,"Worldforge record signature: "+method);
            for(String method:List.of("x","y","z"))check(record.getMethod(method).getReturnType()==double.class,"Worldforge home coordinate: "+method);
            check(island.getMethod("lobby").getReturnType()==org.bukkit.Location.class,"Worldforge safe return signature");
        }
        JsonObject pack=JsonParser.parseString(Files.readString(root.resolve("pack.mcmeta"))).getAsJsonObject().getAsJsonObject("pack");check(pack.getAsJsonArray("min_format").get(0).getAsInt()==88,"Pack target");
        System.out.println("PASS: "+assertions+" assertions; 60 seeded terrain combinations; 156 biome appearances; "+itemCount+" item/model references. Largest plan: "+largest+" blocks.");
    }
    static void verifyClaims(Path folder)throws Exception{
        ShardClaims claims=new ShardClaims(folder);UUID id=UUID.randomUUID();int[] grants={0};
        check(claims.grant(id,"forest_t3_standard",()->false,()->{grants[0]++;return true;})==ShardClaims.Result.NO_SPACE,"Full inventory does not consume claim");
        check(!claims.claimed(id)&&grants[0]==0,"Full inventory grants nothing");
        check(claims.grant(id,"forest_t3_standard",()->true,()->{grants[0]++;return true;})==ShardClaims.Result.CLAIMED,"One reward granted");
        check(new ShardClaims(folder).grant(id,"forest_t5_rainbow",()->true,()->{grants[0]++;return true;})==ShardClaims.Result.ALREADY_CLAIMED&&grants[0]==1,"Restart and changed tier cannot duplicate claim");
        UUID failed=UUID.randomUUID();check(claims.grant(failed,"forest_t3_standard",()->true,()->false)==ShardClaims.Result.NO_SPACE&&!claims.claimed(failed),"Failed delivery leaves claim available");
        UUID pending=UUID.randomUUID();try{claims.grant(pending,"forest_t3_standard",()->true,()->{throw new IllegalStateException("interrupted delivery");});}catch(IllegalStateException expected){}
        check(new ShardClaims(folder).grant(pending,"forest_t3_standard",()->true,()->true)==ShardClaims.Result.ALREADY_CLAIMED,"Interrupted delivery cannot duplicate reward");
        System.out.println("PASS: One-time UUID claims survive restart, prevent duplicates and preserve full/failed-inventory eligibility.");
    }
    static void verifyVariantSizes(){
        int largest=0;
        for(int tier=1;tier<=5;tier++)for(ShardSpec.Land land:ShardSpec.Land.values()){
            TerrainPlan standard=new TerrainPlan(TerrainSizing.radius(3+tier*2,ShardSpec.Variant.STANDARD),land,82501,tier);
            TerrainPlan shiny=new TerrainPlan(TerrainSizing.radius(3+tier*2,ShardSpec.Variant.SHINY),land,82501,tier);
            check(shiny.surface.size()>standard.surface.size(),"Shiny needs more land: "+land+" "+tier);
            check(shiny.blocks.size()>standard.blocks.size(),"Shiny needs more volume: "+land+" "+tier);
            List<TerrainPlan> plans=new ArrayList<>(List.of(standard,shiny));
            if(tier>=3){TerrainPlan rainbow=new TerrainPlan(TerrainSizing.radius(3+tier*2,ShardSpec.Variant.RAINBOW),land,82501,tier);check(rainbow.surface.size()>shiny.surface.size(),"Rainbow needs more land: "+land+" "+tier);check(rainbow.blocks.size()>shiny.blocks.size(),"Rainbow needs more volume: "+land+" "+tier);plans.add(rainbow);}
            for(TerrainPlan p:plans){largest=Math.max(largest,p.blocks.size());for(var pos:p.blocks.keySet())check(Math.abs(pos.x())<=p.radius+4&&Math.abs(pos.z())<=p.radius+4&&pos.y()>=-p.radius-3&&pos.y()<=12,"Variant outside protected volume");}
        }
        System.out.println("PASS: Standard < Shiny < Rainbow land area and block volume for every eligible tier/biome. Largest variant: "+largest+" blocks.");
    }
    static void verifyRemovalRecords(){
        org.bukkit.configuration.file.YamlConfiguration y=new org.bukkit.configuration.file.YamlConfiguration();
        y.set("world","test_world");y.set("realm",UUID.randomUUID().toString());y.set("origin",List.of(-100,80,120));
        y.set("cells",List.of("0,0,0|minecraft:air|STONE|minecraft:stone","1,0,0|minecraft:air|OAK_LEAVES|minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]","0,-1,0|minecraft:air|DIRT"));
        y.set("biomes",List.of("0,0,0|minecraft:plains"));
        GeneratedLand land=new GeneratedLand(Path.of("fixture.yml"),y);
        check(land.standingOn(-100,81,120),"Must select exact support cell");check(!land.standingOn(0,81,0),"Original island must not match");check(!land.standingOn(-100,100,120),"Flying far above fragment must not match");check(land.occupies(-100,80,120),"Removal reservation");check(!land.occupies(-99,1000,120),"Reservation height");
        check(land.cells.getFirst().removable("minecraft:stone"),"Recorded state matches");check(!land.cells.getFirst().removable("minecraft:gold_block"),"Player changed block preserved");
        check(land.cells.get(1).removable("minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]"),"Natural leaf distance allowed");check(!land.cells.get(1).removable("minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]"),"Edited persistent state preserved");
        check(land.cells.get(2).removable("minecraft:dirt"),"Legacy 2.0 material records supported");check(!land.cells.get(2).removable("minecraft:stone"),"Legacy modified material preserved");
        y.set("origin",List.of(-140,80,120));GeneratedLand separate=new GeneratedLand(Path.of("second-fixture.yml"),y);GeneratedLand.requireDisjoint(List.of(land,separate));check(separate.cells.size()==land.cells.size(),"Multiple disjoint recorded placements accepted");
        boolean overlap=false;try{GeneratedLand.requireDisjoint(List.of(land,land));}catch(IllegalStateException ex){overlap=true;}check(overlap,"Overlapping bulk removal records refused before mutations");
        y.set("cells",List.of("0,0,0|minecraft:stone|DIRT"));boolean refused=false;try{new GeneratedLand(Path.of("invalid.yml"),y);}catch(IllegalArgumentException e){refused=true;}check(refused,"Never remove a record that replaced original terrain");
        System.out.println("PASS: Precise fragment selection, changed-block preservation, legacy records and original-terrain rejection.");
    }
    static void verifyTickBudgets(){
        java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong();
        TickWorkBudget budget=new TickWorkBudget(3,2,clock::get);
        check(budget.acquire()&&budget.acquire()&&budget.acquire()&&!budget.acquire(),"Per-tick work count cap");
        budget=new TickWorkBudget(100,2,clock::get);check(budget.acquire(),"Budget must make progress");clock.set(2_000_000);check(!budget.acquire(),"Elapsed-time cap yields to next tick");
        budget=new TickWorkBudget(100,2,clock::get);clock.set(20_000_000);check(budget.acquire()&&!budget.acquire(),"One slow unit must not starve work or continue a burst");
        // The incremental scan must cover exactly the same volume as the former full scan.
        int radius=23,r=radius+4,width=2*r+1,height=radius+16;Set<TerrainPlan.Pos> seen=new HashSet<>();
        for(int cell=0;cell<width*width*height;cell++){int y=cell%height-radius-3,column=cell/height,z=column%width-r,x=column/width-r;check(seen.add(new TerrainPlan.Pos(x,y,z)),"Duplicate clearance cell");check(x>=-r&&x<=r&&z>=-r&&z<=r&&y>=-radius-3&&y<=12,"Clearance mapping bounds");}
        check(seen.size()==width*width*height,"Full clearance volume coverage");
        System.out.println("PASS: Work/time caps and full incremental Tier V Rainbow clearance coverage.");
    }
    static void verifyJournalWorker(Path root)throws Exception{
        Files.createDirectories(root);Path file=root.resolve("async.yml");
        List<String> cells=new ArrayList<>();for(int i=0;i<15526;i++)cells.add(i+",0,0|minecraft:air|STONE|minecraft:stone");
        String header="status: COMMITTING\nworld: test\nrealm: "+UUID.randomUUID()+"\norigin: [0, 80, 0]\n";
        JournalWriter.Snapshot snapshot=new JournalWriter.Snapshot(file,header,cells,List.of("0,0,0|minecraft:plains"));cells.clear();
        check(snapshot.cells().size()==15526,"Worker snapshot must be immutable");
        org.bukkit.configuration.file.YamlConfiguration parsed=new org.bukkit.configuration.file.YamlConfiguration();parsed.loadFromString(snapshot.render());
        check(parsed.getStringList("cells").size()==15526,"Large async journal YAML compatibility");new GeneratedLand(file,parsed);
        try(JournalWriter writer=new JournalWriter()){
            writer.write(snapshot);
            writer.write(new JournalWriter.Snapshot(file,header.replace("COMMITTING","COMPLETE"),snapshot.cells(),snapshot.biomes())).join();
            check(Files.readString(file).startsWith("status: COMPLETE"),"Completion write must follow committing write");
            check(!Files.exists(file.resolveSibling("async.yml.tmp")),"Atomic save leaves no temporary file");
            boolean failed=false;try{writer.write(new JournalWriter.Snapshot(root.resolve("missing-parent/failure.yml"),header,List.of(),List.of())).join();}catch(java.util.concurrent.CompletionException e){failed=true;}check(failed,"Disk failures must reach the main-thread future");
            writer.write(new JournalWriter.Snapshot(file,header.replace("COMMITTING","REFUNDED"),snapshot.cells(),snapshot.biomes()));
        }
        check(Files.readString(file).startsWith("status: REFUNDED"),"Shutdown must flush terminal cancellation after pending completion");
        Files.delete(file);
        System.out.println("PASS: Large immutable journal snapshots, YAML compatibility, ordered atomic saves, failure propagation and shutdown flush.");
    }
    static void verifyCollapse()throws Exception{
        for(ShardSpec.Land biome:ShardSpec.Land.values())for(int tier:List.of(1,3,5)){
            TerrainPlan terrain=new TerrainPlan(tier==5?23:3+tier*2,biome,82501,tier);
            var y=new org.bukkit.configuration.file.YamlConfiguration();y.set("world","test_world");y.set("realm",UUID.randomUUID().toString());y.set("origin",List.of(-300,80,250));
            y.set("cells",terrain.blocks.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|minecraft:air|"+e.getValue().name()).toList());
            GeneratedLand land=new GeneratedLand(Path.of("collapse.yml"),y);
            // Every seventh recorded cell represents a changed player block and is excluded.
            List<GeneratedLand.Cell> removable=new ArrayList<>();for(int i=0;i<land.cells.size();i++)if(i%7!=0)removable.add(land.cells.get(i));
            CollapsePattern pattern=new CollapsePattern(land,removable,64,82501),again=new CollapsePattern(land,removable,64,82501);
            check(pattern.cracks.equals(again.cracks)&&pattern.order.equals(again.order),"Deterministic collapse for "+biome+" "+tier);
            check(pattern.cracks.size()<=64&&!pattern.cracks.isEmpty(),"Bounded crack anchor count");
            Set<GeneratedLand.Cell> allowed=new HashSet<>(removable);Set<TerrainPlan.Pos> anchors=new HashSet<>();
            for(var crack:pattern.cracks){check(allowed.contains(crack.cell()),"Cracks only touch removable recorded cells");check(anchors.add(crack.cell().pos()),"Unique damage anchor");check(CollapsePattern.solidVisual(crack.cell().expected()),"No fluid/leaves damage overlay");
                float previous=0;for(int tick=0;tick<=90;tick++){float next=CollapsePattern.progress(tick/90.0,crack.onset());check(next>=previous&&next>=0&&next<=.95f,"Cracks must spread monotonically within API limits");previous=next;}check(CollapsePattern.progress(1,crack.onset())==.95f,"Every selected fracture reaches shatter strength");}
            check(new HashSet<>(pattern.order).equals(allowed)&&pattern.order.size()==removable.size(),"Collapse must remove every eligible cell exactly once");double previous=-1;for(var cell:pattern.order){double distance=pattern.distance(cell.pos());check(distance>=previous,"Removal wave must move outward, rather than reversing build order");previous=distance;}
            check(new CollapsePattern(land,List.of(),64,82501).cracks.isEmpty(),"No effects on a completely modified fragment");
        }
        check(org.bukkit.entity.Player.class.getMethod("sendBlockDamage",org.bukkit.Location.class,float.class,int.class).getReturnType()==void.class,"Paper crack overlay API");
        check(org.bukkit.entity.BlockDisplay.class.getMethod("setBlock",org.bukkit.block.data.BlockData.class).getReturnType()==void.class,"Paper visual debris API");
        System.out.println("PASS: Collapse patterns across 36 tier/biome cases; changed-cell exclusion, bounded unique anchors, progressive cracks and outward removal order; Paper visual API signatures.");
    }
    static void verifyShatterParticles(){
        List<org.bukkit.Particle> emitted=new ArrayList<>();
        org.bukkit.World world=(org.bukkit.World)java.lang.reflect.Proxy.newProxyInstance(Verification.class.getClassLoader(),new Class<?>[]{org.bukkit.World.class},(proxy,method,args)->{
            if(!method.getName().equals("spawnParticle"))throw new AssertionError("Unexpected world access in burst: "+method.getName());
            org.bukkit.Particle particle=(org.bukkit.Particle)args[0];emitted.add(particle);
            Class<?> required=particle.getDataType();Object payload=args.length==4?args[3]:null;
            check(required==Void.class||required.isInstance(payload),"Missing or wrong particle payload: "+particle+" requires "+required.getName());
            check((int)args[2]>0&&(int)args[2]<=48,"Bounded burst count");return null;
        });
        for(org.bukkit.Color color:List.of(org.bukkit.Color.AQUA,org.bukkit.Color.YELLOW,org.bukkit.Color.FUCHSIA)){
            emitted.clear();org.bukkit.Location center=new org.bukkit.Location(world,-100,80,120);
            ShatterEffects.burst(world,center,96,color);
            check(emitted.contains(org.bukkit.Particle.FLASH)&&emitted.contains(org.bukkit.Particle.FIREWORK),"Both burst effects must run without a payload exception");
            check(center.getY()==80,"Burst must not move the fragment center");
        }
        System.out.println("PASS: Actual shatter burst calls satisfy Paper 26.2 particle payload contracts for all variant colors.");
    }
    static void verifyPrismFlights(){
        for(boolean collapse:List.of(false,true))for(int index=0;index<64;index++){
            PrismFlight flight=new PrismFlight(index,82501,collapse?24:2.4,collapse?1.65f:.90f),again=new PrismFlight(index,82501,collapse?24:2.4,collapse?1.65f:.90f);
            check(flight.charge(1).equals(again.charge(1)),"Deterministic prism poses");check(flight.charge(1).x()==flight.burst(0).x()&&flight.charge(1).z()==flight.burst(0).z(),"No jump between charged and shattering pose");
            double previous=0;for(int tick=0;tick<80;tick++){var pose=flight.burst(tick);double distance=Math.hypot(pose.x(),pose.z());check(Double.isFinite(distance)&&Double.isFinite(pose.y())&&Float.isFinite(pose.scale()),"Finite prism trajectory");check(distance>=previous&&pose.scale()>=0&&pose.scale()<=flight.size,"Outward travel with bounded dissolve scale");previous=distance;}
            check(flight.expired(80)&&flight.burst(80).scale()==0,"All prism displays finish and dissolve");
        }
        for(int tier=1;tier<=5;tier++)for(ShardSpec.Land land:ShardSpec.Land.values())for(ShardSpec.Variant variant:ShardSpec.Variant.values())if(tier>=3||variant!=ShardSpec.Variant.RAINBOW){ShardSpec spec=new ShardSpec(tier,land,variant);for(int i=0;i<3;i++)check(PrismStorm.model(spec,i).matches("fx/[a-z]+_(blade|facet|splinter)"),"Prism model identifier");}
        System.out.println("PASS: Prism charge/burst continuity, deterministic staggered flights, finite outward motion and complete dissolve.");
    }
    static void verifyHeldPositions(){
        for(float yaw:new float[]{0,90,180,-90})for(var main:org.bukkit.inventory.MainHand.values()){
            org.bukkit.entity.Player player=(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(Verification.class.getClassLoader(),new Class<?>[]{org.bukkit.entity.Player.class},(proxy,method,args)->switch(method.getName()){case "getEyeLocation"->new org.bukkit.Location(null,0,80,0,yaw,0);case "getMainHand"->main;default->throw new AssertionError("Unexpected hand positioning access: "+method.getName());});
            var right=HeldShardDisplay.position(player,false);var left=HeldShardDisplay.position(player,true);
            check(right.toVector().distance(left.toVector())>.7&&right.toVector().distance(left.toVector())<.73,"Main/offhand must occupy opposite hands");check(right.getY()==79.52&&left.getY()==79.52,"Stable held height");check(Math.abs(right.toVector().lengthSquared()-left.toVector().lengthSquared())<.00001,"Symmetric hand offsets");
        }
        System.out.println("PASS: Rotating display positioning respects main/offhand, left-handed players and facing direction.");
    }
    static void verifyFragmentTextures(Path root)throws Exception{
        int textures=0;try(var files=Files.list(root.resolve("assets/realmshards/textures/realmshards"))){for(Path file:files.filter(p->p.getFileName().toString().startsWith("fx_")&&p.toString().endsWith(".png")).toList()){
            var image=javax.imageio.ImageIO.read(file.toFile());check(image.getWidth()==64,"Sharp fragment texture resolution");boolean rainbow=file.toString().contains("rainbow");check(image.getHeight()==64*(rainbow?48:1),"Animation frame dimensions");int opaque=0,transparent=0;for(int y=0;y<64;y++)for(int x=0;x<64;x++){if((image.getRGB(x,y)>>>24)==0)transparent++;else opaque++;}check(transparent>opaque&&opaque>200,"Fragment must be a transparent triangular silhouette");if(rainbow){check(Files.exists(Path.of(file+".mcmeta")),"Rainbow fragment animation metadata");check(image.getRGB(30,30)!=image.getRGB(30,30+16*64),"Rainbow fragment colors change between frames");}textures++;
        }}check(textures==45,"All biome, gold and rainbow triangular fragment textures");
        System.out.println("PASS: 45 transparent faceted fragment textures; animated Rainbow color frames.");
    }
    static void verifyTerrainDissolve(){
        for(ShardSpec.Land biome:ShardSpec.Land.values()){
            TerrainPlan terrain=new TerrainPlan(23,biome,82501,5);var y=new org.bukkit.configuration.file.YamlConfiguration();y.set("world","test");y.set("realm",UUID.randomUUID().toString());y.set("origin",List.of(-200,80,300));
            y.set("cells",terrain.blocks.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|minecraft:air|"+e.getValue().name()).toList());GeneratedLand land=new GeneratedLand(Path.of("dissolve.yml"),y);
            List<GeneratedLand.Cell> eligible=new ArrayList<>();for(int i=0;i<land.cells.size();i++)if(i%9!=0)eligible.add(land.cells.get(i));
            DissolvePattern pattern=new DissolvePattern(land,eligible,144,82501),again=new DissolvePattern(land,eligible,144,82501);Set<GeneratedLand.Cell> permitted=new HashSet<>(eligible);
            check(pattern.order.equals(again.order)&&pattern.visuals.equals(again.visuals),"Deterministic rune bands");check(new HashSet<>(pattern.order).equals(permitted)&&pattern.order.size()==eligible.size(),"Unbinding removes every eligible cell exactly once");check(pattern.visuals.size()<=144&&!pattern.visuals.isEmpty(),"Bounded actual terrain visual samples");
            Set<TerrainPlan.Pos> positions=new HashSet<>();eligible.forEach(c->positions.add(c.pos()));for(var p:pattern.visuals)check(positions.contains(p),"No visual copy of modified/original island terrain");
            check(DissolvePattern.releaseCount(1000,69,100)==0,"No terrain disappears before final rupture");check(DissolvePattern.releaseCount(1000,85,100)==500,"Rupture releases terrain in final phase");int previous=0;for(int tick=0;tick<=140;tick++){int released=DissolvePattern.releaseCount(eligible.size(),tick,100);check(released>=previous&&released<=eligible.size(),"Timed dissolve wave must advance without skipping its end");previous=released;}check(previous==eligible.size(),"Wave eventually exposes every recorded cell");
            int firstY=pattern.order.getFirst().pos().y(),lastY=pattern.order.getLast().pos().y();check(firstY>lastY,"Terrain should peel from structures/surface down to underside");
        }
        for(double angle:List.of(0.0,1.2,3.8)){
            var initial=RuptureFlight.pose(0,angle,1,false);check(initial.x()==0&&initial.y()==0&&initial.z()==0&&initial.scale()==1,"Custom shards start at the actual fracture position");double oldY=-1;float oldSize=1;
            for(int t=0;t<=30;t++){var p=RuptureFlight.pose(t,angle,1,false);check(Double.isFinite(p.x()+p.y()+p.z())&&p.y()>=oldY&&p.scale()<=oldSize&&p.scale()>=0,"Sharp shard rupture travels upward and dissolves safely");oldY=p.y();oldSize=p.scale();var pulse=RuptureFlight.pose(t,angle,4,true);check(pulse.x()==0&&pulse.y()==0&&pulse.z()==0&&pulse.scale()>=0&&pulse.scale()<=4,"Custom pulse stays anchored with bounded scale");}
            check(RuptureFlight.pose(30,angle,1,false).scale()==0&&RuptureFlight.pose(30,angle,4,true).scale()==0,"Shards and custom pulse finish dissolving");
        }
        System.out.println("PASS: Actual-terrain dissolve waves for all 12 biomes; timed coverage, modified-cell exclusion, bounded sampling, custom sharp-shard and pulse trajectories.");
    }
    static void verifyCustomFractures(Path root)throws Exception{
        int previousStage=1;for(int tick=0;tick<=100;tick++){int stage=FractureSkin.stage(tick/100.0);check(stage>=previousStage&&stage>=1&&stage<=5,"Fracture stages advance monotonically");previousStage=stage;}check(previousStage==5,"Fracture reaches final rupture stage");
        List<String> palettes=new ArrayList<>(Arrays.stream(ShardSpec.Land.values()).map(v->v.name().toLowerCase(Locale.ROOT)).toList());palettes.addAll(List.of("random","gold","rainbow"));
        for(String palette:palettes){int oldCoverage=0;for(int stage=1;stage<=5;stage++){
            Path texture=root.resolve("assets/realmshards/textures/realmshards/rift_"+palette+"_s"+stage+".png");var image=javax.imageio.ImageIO.read(texture.toFile());int coverage=0;for(int y=0;y<64;y++)for(int x=0;x<64;x++)if((image.getRGB(x,y)>>>24)>0)coverage++;
            check(coverage>=oldCoverage&&coverage<64*64*.6,"Custom rifts widen but preserve transparent terrain visibility");oldCoverage=coverage;
            check(Files.exists(root.resolve("assets/realmshards/items/fx/rift_"+palette+"_s"+stage+".json")),"Custom stage item definition exists");
        }check(Files.exists(root.resolve("assets/realmshards/items/fx/pulse_"+palette+".json")),"Custom rupture pulse asset");}
        for(ShardSpec.Land biome:ShardSpec.Land.values()){
            TerrainPlan terrain=new TerrainPlan(23,biome,82501,5);var y=new org.bukkit.configuration.file.YamlConfiguration();y.set("world","test");y.set("realm",UUID.randomUUID().toString());y.set("origin",List.of(-100,80,200));y.set("cells",terrain.blocks.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|minecraft:air|"+e.getValue().name()).toList());GeneratedLand land=new GeneratedLand(Path.of("rifts.yml"),y);
            var anchors=FractureLayout.anchors(land,land.cells,192,82501);check(anchors.size()<=192&&!anchors.isEmpty(),"Rift field obeys display cap");Set<GeneratedLand.Cell> recorded=new HashSet<>(land.cells);Set<TerrainPlan.Pos> unique=new HashSet<>();int minY=1000,maxY=-1000;Set<Integer> quadrants=new HashSet<>();for(var anchor:anchors){var p=anchor.pos();check(recorded.contains(anchor)&&unique.add(p),"Every rift anchor is a unique recorded cell");minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());quadrants.add((p.x()>=land.ox?1:0)+(p.z()>=land.oz?2:0));}check(quadrants.size()==4&&minY<land.oy&&maxY>=land.oy,"Rifts span all quadrants, surface and underside");
        }
        // The user's explicit constraint: regressions must not reintroduce vanilla mining or glow-box visuals.
        for(String type:List.of("TestLandRemoval$Job","TerrainDissolve","FractureSkin")){try(var in=Verification.class.getResourceAsStream(type+".class")){String bytecode=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);for(String forbidden:List.of("sendBlockDamage","spawnParticle","setGlowing","setGlowColorOverride","org/bukkit/entity/BlockDisplay"))check(!bytecode.contains(forbidden),"Custom removal must not invoke "+forbidden+" in "+type);}}
        System.out.println("PASS: Five custom widening fracture stages, all palette/pulse assets, island-wide bounded rift coverage; no vanilla mining particles, overlays or glowing block visuals in removal.");
    }
}
