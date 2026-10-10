package dev.excedra.itemgen;

import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class RealmCoreVerification {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Stack extends ItemStack {
        final Material material;final boolean metadata;int count;
        Stack(Material material,int count,boolean metadata){this.material=material;this.count=count;this.metadata=metadata;}
        public Material getType(){return material;}
        public int getAmount(){return count;}
        public void setAmount(int count){this.count=count;}
        public boolean hasItemMeta(){return metadata;}
        public Stack clone(){return new Stack(material,count,metadata);}
    }
    public static void main(String[] args)throws Exception {
        var deduplicated=RealmCoreStore.canonicalPatterns(List.of("1:ROOTS:123","1:ROOTS:123","2:ROOTS:CRYSTAL:555","2:CRYSTAL:ROOTS:555","2:ROOTS:CRYSTAL:556"));
        check(deduplicated.equals(List.of("1:ROOTS:123","2:ROOTS:CRYSTAL:555","2:ROOTS:CRYSTAL:556")),"Exact duplicate patterns and swapped pair aliases collapse while different seeds survive");
        for(int tick=0;tick<=CoreForgeSequence.DURATION;tick++){
            var frame=CoreForgeSequence.frame(tick);check(frame.size()==16,"Ritual particle work is bounded per frame");
            for(var point:frame)check(Double.isFinite(point.x())&&Double.isFinite(point.y())&&Double.isFinite(point.z())&&Math.abs(point.x())<=3&&Math.abs(point.z())<=5&&point.y()>1&&point.y()<7,"Ritual stays near the basin and heart with finite coordinates");
        }
        check(CoreForgeSequence.frame(0).getFirst().z()< -2&&CoreForgeSequence.frame(60).getFirst().y()>5,"Ritual travels from basin to heart");
        check(Arrays.stream(CoreRecipe.Influence.values()).map(CoreForgeSequence::color).distinct().count()==5,"Influences have distinct ritual colors");
        var layout=RealmCoreLayout.legacy();
        check(layout.containsKey(new TerrainPlan.Pos(0,0,-6)),"Creator approach has a solid Core floor");
        check(!layout.containsKey(new TerrainPlan.Pos(0,1,-6))&&!layout.containsKey(new TerrainPlan.Pos(0,2,-6)),"Creator approach has standing headroom");
        for(var position:layout.keySet())check(Math.abs(position.x())<=6&&Math.abs(position.z())<=6,"Core fits a 13 by 13 starter island footprint");
        var coreGround=new ConnectionBridgeVerification.Ground(){public boolean canBuild(int x,int y,int z){return Math.abs(x+22)>6||Math.abs(z)>6||y < -2||y>8;}};
        for(var entry:layout.entrySet()){var p=entry.getKey();coreGround.cells.put(new TerrainPlan.Pos(p.x()-22,p.y(),p.z()),entry.getValue());}
        var coreWorld=UUID.randomUUID();
        var bridgeCore=new RealmCoreStore.Core(UUID.randomUUID(),coreWorld,UUID.randomUUID(),-22,0,0);
        var coreBridge=ConnectionBridge.plan(ConnectionBridgeVerification.island(0),-22,0,-6,coreGround);
        check(coreBridge.anchors().keySet().stream().anyMatch(p->bridgeCore.contains(coreWorld,p.x(),p.y(),p.z())),"Bridge can anchor on the unchanged Core edge");
        check(coreBridge.clearance().stream().anyMatch(p->!coreBridge.blocks().containsKey(p)&&bridgeCore.contains(coreWorld,p.x(),p.y(),p.z())),"Core anchor has read-only headroom inside protection");
        for(var p:coreBridge.blocks().keySet())check(!bridgeCore.contains(coreWorld,p.x(),p.y(),p.z()),"Bridge additions stay outside permanent Core protection");
        Set<Map<TerrainPlan.Pos,Material>> appearances=new HashSet<>();
        Set<Map<TerrainPlan.Pos,Material>> combinations=new HashSet<>();int families=0;
        for(var primary:CoreRecipe.Influence.values())for(var secondary:CoreRecipe.Influence.values())if(primary.ordinal()<secondary.ordinal()){
            families++;var pair=new CoreRecipe(primary,secondary,82371);
            check(pair.equals(new CoreRecipe(secondary,primary,82371)),"Influence order does not create duplicate families");
            check(CoreRecipe.decode(pair.encode()).equals(pair),"Combined patterns preserve both Influences and seed");
            check(pair.costs().get(primary.material)==primary.amount&&pair.costs().get(secondary.material)==secondary.amount&&pair.costs().get(Material.DIRT)==16,"Combined forging charges both Influences and one Foundation");
            check(pair.plan().blocks.equals(CoreRecipe.decode(pair.encode()).plan().blocks),"Combined saved patterns reproduce exactly");
            combinations.add(pair.plan().blocks);
            int compatible=0;
            for(var rotation:PlacementRotation.values()){
                var ground=new ConnectionBridgeVerification.Ground();ground.land(-25,-18,-3,3,0);
                try{ConnectionBridge.plan(pair.plan().rotated(rotation),-22,0,0,ground);compatible++;}catch(IllegalStateException unavailable){}
            }
            check(compatible>0,"Every combined family has a compatible bridge landing");
            ItemStack[] ingredients=new ItemStack[36];int ingredientSlot=0;
            for(var cost:pair.costs().entrySet())ingredients[ingredientSlot++]=new Stack(cost.getKey(),cost.getValue(),false);
            var exchange=CoreInventoryExchange.plan(ingredients,pair.costs(),new Stack(Material.PAPER,1,true));
            check(Arrays.stream(exchange).filter(Objects::nonNull).count()==1,"Pair exchange consumes exactly both Influences and returns one reward");
            for(int seed=0;seed<12;seed++){
                var plan=new CoreRecipe(primary,secondary,seed).plan();
                for(var entry:plan.blocks.entrySet()){
                    var p=entry.getKey();check(Math.abs(p.x())<=plan.radius+4&&Math.abs(p.z())<=plan.radius+4&&p.y()>=plan.minY()&&p.y()<=plan.maxY(),"Combined terrain stays inside placement bounds");
                }
                if(pair.has(CoreRecipe.Influence.ROOTS))check(plan.blocks.containsValue(Material.ROOTED_DIRT),"Roots remain visible in paired terrain");
                if(pair.has(CoreRecipe.Influence.MASONRY))check(plan.blocks.containsValue(Material.CHISELED_STONE_BRICKS),"Ruins remain visible in paired terrain");
                if(pair.has(CoreRecipe.Influence.HIGHLAND))check(plan.blocks.containsValue(Material.TUFF),"Terraces remain visible in paired terrain");
                if(pair.has(CoreRecipe.Influence.CRYSTAL))check(plan.blocks.containsValue(Material.AMETHYST_BLOCK),"Crystal deposits remain visible in paired terrain");
                if(pair.has(CoreRecipe.Influence.WATER))for(var entry:plan.blocks.entrySet()){
                    var p=entry.getKey();if(entry.getValue()!=Material.WATER||p.x()*p.x()+(p.z()-4)*(p.z()-4)>1)continue;
                    check(plan.blocks.containsKey(new TerrainPlan.Pos(p.x(),p.y()-1,p.z())),"Combined spring has a sealed floor");
                    for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})check(plan.blocks.containsKey(new TerrainPlan.Pos(p.x()+direction[0],p.y(),p.z()+direction[1])),"Combined spring has contained water at its sides");
                }
            }
        }
        check(families==10&&combinations.size()==10,"Ten pairs have ten distinct terrain layouts");
        boolean duplicateInfluence=false;try{new CoreRecipe(CoreRecipe.Influence.ROOTS,CoreRecipe.Influence.ROOTS,1);}catch(IllegalArgumentException expected){duplicateInfluence=true;}check(duplicateInfluence,"Same Influence cannot fill both slots");
        check(new CoreRecipe(CoreRecipe.Influence.ROOTS,123).encode().equals("1:ROOTS:123"),"Existing single recipe encoding remains unchanged");
        for(var influence:CoreRecipe.Influence.values()){
            CoreRecipe recipe=new CoreRecipe(influence,82371);check(CoreRecipe.decode(recipe.encode()).equals(recipe),"Pattern encoding preserves its influence and seed");
            TerrainPlan first=recipe.plan(),second=CoreRecipe.decode(recipe.encode()).plan();
            check(first.blocks.equals(second.blocks)&&first.surface.equals(second.surface),"Saved patterns reproduce exact terrain");appearances.add(first.blocks);
            int bridges=0;
            for(var rotation:PlacementRotation.values()){
                var ground=new ConnectionBridgeVerification.Ground();ground.land(-25,-18,-3,3,0);
                try{ConnectionBridge.plan(first.rotated(rotation),-22,0,0,ground);bridges++;}catch(IllegalStateException incompatible){}
            }
            check(bridges>0,"Every recipe has a compatible bridge landing in the fixture");
            for(int seed=0;seed<30;seed++){
                TerrainPlan plan=new CoreRecipe(influence,seed).plan();
                for(var entry:plan.blocks.entrySet()){
                    var p=entry.getKey();check(Math.abs(p.x())<=plan.radius+4&&Math.abs(p.z())<=plan.radius+4&&p.y()>=plan.minY()&&p.y()<=plan.maxY(),"Recipe stays inside placement clearance");
                }
                var rotated=plan.rotated(PlacementRotation.CLOCKWISE_90);
                check(rotated.blocks.size()==plan.blocks.size(),"Pattern rotation does not lose terrain");
            }
        }
        check(appearances.size()==5,"All five Influences must produce different land, not just different names");
        check(new CoreRecipe(CoreRecipe.Influence.WATER,1).plan().blocks.containsValue(Material.WATER),"Water Influence forms a real spring pool");
        check(new CoreRecipe(CoreRecipe.Influence.MASONRY,1).plan().blocks.containsValue(Material.CHISELED_STONE_BRICKS),"Masonry Influence forms real ruins");
        check(new CoreRecipe(CoreRecipe.Influence.HIGHLAND,1).plan().blocks.containsValue(Material.TUFF),"Highland Influence exposes rocky terraces");
        check(new CoreRecipe(CoreRecipe.Influence.CRYSTAL,1).plan().blocks.containsValue(Material.AMETHYST_BLOCK),"Crystal Influence forms mineral veins");
        boolean invalid=false;try{CoreRecipe.decode("2:ROOTS:1");}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"Unknown recipe versions fail closed");
        inventory();
        UUID realm=UUID.randomUUID(),world=UUID.randomUUID(),player=UUID.randomUUID();
        var foundation=new RealmCoreStore.Core(realm,world,player,100,64,-100);
        for(var p:RealmCoreLayout.legacy().keySet())check(foundation.contains(world,100+p.x(),64+p.y(),-100+p.z()),"Every original Core block lies inside permanent protection");
        check(!foundation.contains(UUID.randomUUID(),100,64,-100),"Protection must not cross world identities");
        check(!foundation.contains(world,107,64,-100),"Attached modules can exist outside the original foundation");
        Path folder=Path.of(args.length==0?"work/core-verification":args[0]).resolve(UUID.randomUUID().toString());
        coreShardVerification(folder.resolve("core-shards"));
        coreClaimResetVerification(folder.resolve("claim-reset"));
        manifestationVerification(folder.resolve("manifestation"));
        classicDesignVerification(folder.resolve("classic-design"));
        resetVerification(folder.resolve("reset-discoveries"));
        RealmCoreStore store=new RealmCoreStore(folder);store.add(foundation);
        RealmCoreStore restored=new RealmCoreStore(folder);check(restored.get(realm).equals(foundation),"Core registration survives restart");
        UUID pairPlayer=UUID.randomUUID();var savedPair=new CoreRecipe(CoreRecipe.Influence.ROOTS,CoreRecipe.Influence.CRYSTAL,777);
        check(!restored.known(pairPlayer,savedPair),"Knowing roots does not reveal a combination automatically");
        ledger(folder,"pair",pairPlayer,"COMPLETE",savedPair);var pairedRestore=new RealmCoreStore(folder);
        check(pairedRestore.patterns(pairPlayer).contains(savedPair)&&pairedRestore.known(pairPlayer,new CoreRecipe(CoreRecipe.Influence.CRYSTAL,CoreRecipe.Influence.ROOTS,888)),"Combined journal and discovery survive restart and recognize the family");
        var duplicateData=YamlConfiguration.loadConfiguration(folder.resolve("realm-cores.yml").toFile());
        duplicateData.set("discoveries."+pairPlayer+".patterns",List.of(savedPair.encode(),savedPair.encode(),"2:CRYSTAL:ROOTS:777","2:ROOTS:CRYSTAL:778"));
        duplicateData.save(folder.resolve("realm-cores.yml").toFile());
        var cleaned=new RealmCoreStore(folder);check(cleaned.patterns(pairPlayer).size()==2,"Startup cleans old duplicates without losing a distinct arrangement");
        var cleanData=YamlConfiguration.loadConfiguration(folder.resolve("realm-cores.yml").toFile());
        check(cleanData.getStringList("discoveries."+pairPlayer+".patterns").size()==2,"Duplicate cleanup is persisted to disk");
        check(new RealmCoreStore(folder).patterns(pairPlayer).size()==2,"Replaying completed forge ledgers does not duplicate journal entries");
        Path recreatedFolder=folder.resolve("realm-recreation");var recreation=new RealmCoreStore(recreatedFolder);
        recreation.add(foundation);ledger(recreatedFolder,"original",pairPlayer,"COMPLETE",savedPair);new RealmCoreStore(recreatedFolder);
        var removedRealmData=YamlConfiguration.loadConfiguration(recreatedFolder.resolve("realm-cores.yml").toFile());removedRealmData.set("cores",null);removedRealmData.save(recreatedFolder.resolve("realm-cores.yml").toFile());
        var recreated=new RealmCoreStore(recreatedFolder);recreated.add(new RealmCoreStore.Core(UUID.randomUUID(),UUID.randomUUID(),pairPlayer,0,64,0));
        check(new RealmCoreStore(recreatedFolder).patterns(pairPlayer).equals(List.of(savedPair)),"Removing and recreating Realm records retains the original personal recipe exactly once");
        check(restored.protectedAt(world,100,64,-100),"Permanent foundation protection survives restart");
        boolean duplicate=false;try{restored.add(foundation);}catch(IllegalStateException expected){duplicate=true;}check(duplicate,"A Realm cannot create a replacement Core");
        CoreRecipe discovery=new CoreRecipe(CoreRecipe.Influence.MASONRY,456);
        ledger(folder,"complete",player,"COMPLETE",discovery);ledger(folder,"prepared",player,"PREPARED",discovery);
        RealmCoreStore recovered=new RealmCoreStore(folder);
        check(recovered.patterns(player).equals(List.of(discovery)),"Completed forge ledger recovers the discovery journal");
        check(recovered.known(player,CoreRecipe.Influence.MASONRY),"A completed experiment teaches its recipe");
        check(!recovered.known(UUID.randomUUID(),CoreRecipe.Influence.MASONRY),"Discoveries remain personal");
        check(recovered.pending(player),"An ambiguous interrupted forge locks further exchanges");
        check(new RealmCoreStore(folder).patterns(player).size()==1,"Repeated recovery does not duplicate saved patterns");
        System.out.println("Realm Core verification passed: "+checks+" checks (five visible recipes, exact patterns, inventory preflight, permanent foundations and journal recovery)");
    }
    private static void inventory(){
        var recipe=new CoreRecipe(CoreRecipe.Influence.ROOTS,1);ItemStack[] before=new ItemStack[36];int slot=0;
        for(var cost:recipe.costs().entrySet())before[slot++]=new Stack(cost.getKey(),cost.getValue(),false);
        Stack reward=new Stack(Material.AMETHYST_SHARD,1,true);var after=CoreInventoryExchange.plan(before,recipe.costs(),reward);
        check(Arrays.stream(after).filter(Objects::nonNull).count()==1,"Successful forging consumes exactly the recipe inputs");
        check(Arrays.stream(after).anyMatch(item->item!=null&&item.getType()==Material.AMETHYST_SHARD&&item.hasItemMeta()),"The finished custom Shard is placed in storage");
        slot=0;for(var cost:recipe.costs().entrySet())check(before[slot++].getAmount()==cost.getValue(),"Preflight must not change original stacks");
        before[0]=new Stack(Material.OAK_SAPLING,8,true);reject(()->CoreInventoryExchange.plan(before,recipe.costs(),reward));
        check(before[1].getAmount()==16,"A named/custom ingredient cannot cause partial consumption");
        var crystal=new CoreRecipe(CoreRecipe.Influence.CRYSTAL,1);ItemStack[] customShard=new ItemStack[36];slot=0;
        for(var cost:crystal.costs().entrySet())customShard[slot++]=new Stack(cost.getKey(),cost.getValue(),cost.getKey()==Material.AMETHYST_SHARD);
        reject(()->CoreInventoryExchange.plan(customShard,crystal.costs(),reward));
        ItemStack[] full=new ItemStack[36];Arrays.fill(full,new Stack(Material.COBBLESTONE,64,false));slot=0;
        for(var cost:recipe.costs().entrySet())full[slot++]=new Stack(cost.getKey(),cost.getValue()+1,false);
        reject(()->CoreInventoryExchange.plan(full,recipe.costs(),reward));
        check(full[0].getAmount()==9,"No output capacity means no ingredient loss");
    }
    private static void resetVerification(Path folder)throws Exception {
        UUID player=UUID.randomUUID(),other=UUID.randomUUID();
        var core=new RealmCoreStore.Core(UUID.randomUUID(),UUID.randomUUID(),player,0,64,0);
        var recipe=new CoreRecipe(CoreRecipe.Influence.MASONRY,321);
        var setup=new RealmCoreStore(folder);setup.add(core);
        ledger(folder,"testing",player,"COMPLETE",recipe);ledger(folder,"other",other,"COMPLETE",recipe);
        var store=new RealmCoreStore(folder);check(store.patterns(player).size()==1,"Test discoveries are present before reset");
        store.resetDiscoveries(player);
        check(store.patterns(player).isEmpty()&&!store.known(player,CoreRecipe.Influence.MASONRY),"Reset clears saved patterns and learned recipes");
        check(store.get(core.realm()).equals(core)&&store.patterns(other).size()==1,"Reset preserves the Core and other players' journals");
        var restart=new RealmCoreStore(folder);check(restart.patterns(player).isEmpty()&&restart.generation(player)==1,"Old completed ledgers cannot repopulate a reset journal after restart");
        ledger(folder,"new-learning",player,"COMPLETE",recipe);
        Path newLedger=folder.resolve("core-forge-transactions/new-learning.yml");var learned=YamlConfiguration.loadConfiguration(newLedger.toFile());learned.set("discovery-generation",restart.generation(player));learned.save(newLedger.toFile());
        var relearned=new RealmCoreStore(folder);check(relearned.patterns(player).equals(List.of(recipe)),"The same recipe can be learned again after reset");
        relearned.resetDiscoveries(player);check(new RealmCoreStore(folder).patterns(player).isEmpty(),"A second reset also suppresses later testing ledgers");
        ledger(folder,"unfinished",player,"PREPARED",recipe);var pendingStore=new RealmCoreStore(folder);
        boolean rejected=false;try{pendingStore.resetDiscoveries(player);}catch(IllegalStateException expected){rejected=true;}
        check(rejected&&pendingStore.pending(player)&&pendingStore.generation(player)==2,"Reset cannot discard an unresolved inventory transaction");
    }
    private static void coreShardVerification(Path folder)throws Exception {
        UUID player=UUID.randomUUID(),realm=UUID.randomUUID(),world=UUID.randomUUID();var store=new RealmCoreStore(folder);
        String serial=store.issue(player,realm,false);store.validateCoreItem(player,realm,serial);
        check(new RealmCoreStore(folder).claimed(player),"NPC's one-time Core grant survives restart");
        boolean repeated=false;try{store.issue(player,realm,false);}catch(IllegalStateException expected){repeated=true;}check(repeated,"NPC cannot grant the item a second time");
        store.coreState(player,serial,"PLACING");boolean copied=false;try{store.validateCoreItem(player,realm,serial);}catch(IllegalStateException expected){copied=true;}check(copied,"Copied Core items cannot be activated during or after placement");
        var core=new RealmCoreStore.Core(realm,world,player,24,64,0);store.construction(core,List.of("0,0,0|DEEPSLATE_BRICKS"),.5,65,.5);store.coreState(player,serial,"PLACED");
        var restart=new RealmCoreStore(folder);check(restart.cells(core).size()==1&&restart.returnPoint(core)[1]==65,"Core retrieval cells and original island return point persist");
        String retrieved=restart.prepareRetrieval(core,player);restart.removeCore(core);restart.coreState(player,retrieved,"RETRIEVED");restart.validateCoreItem(player,realm,retrieved);
        check(new RealmCoreStore(folder).claimed(player)&&new RealmCoreStore(folder).get(realm)==null,"Staff retrieval removes the registered Core but preserves the spent NPC claim");
        String replacement=restart.issue(player,realm,true);boolean oldRejected=false;try{restart.validateCoreItem(player,realm,retrieved);}catch(IllegalStateException expected){oldRejected=true;}check(oldRejected,"Staff replacement invalidates previously issued or retrieved items");
        restart.validateCoreItem(player,realm,replacement);restart.resetDiscoveries(player);check(restart.claimed(player),"Resetting discoveries does not reset the Core grant");
        for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            TerrainPlan plan=new TerrainPlan(RealmCoreLayout.RADIUS,ShardSpec.Land.FOREST,0,RealmCoreLayout.MIN_Y,RealmCoreLayout.MAX_Y);plan.blocks.putAll(RealmCoreLayout.blocks());
            var ground=new ConnectionBridgeVerification.Ground();int x=-direction[0]*24,z=-direction[1]*24;ground.land(x-6,x+6,z-6,z+6,0);var before=new HashMap<>(ground.cells);
            var bridge=ConnectionBridge.plan(plan,x,0,z,ground);
            check(!bridge.blocks().isEmpty()&&!bridge.anchors().isEmpty()&&ground.cells.equals(before),"Core placement bridges a 13 by 13 starter island in every cardinal direction without changing it");
            for(var p:bridge.blocks().keySet())check(!before.containsKey(p),"Core bridge never overwrites starter island blocks");
        }
    }
    private static void coreClaimResetVerification(Path folder)throws Exception {
        UUID player=UUID.randomUUID(),other=UUID.randomUUID(),realm=UUID.randomUUID();var store=new RealmCoreStore(folder);
        String old=store.issue(player,realm,false),otherSerial=store.issue(other,UUID.randomUUID(),false);
        var recipe=new CoreRecipe(CoreRecipe.Influence.CRYSTAL,901);ledger(folder,"discovery",player,"COMPLETE",recipe);store=new RealmCoreStore(folder);
        store.resetCoreClaim(player);check(!store.claimed(player)&&store.patterns(player).equals(List.of(recipe)),"Claim reset re-enables NPC eligibility without clearing discoveries");
        boolean invalidated=false;try{store.validateCoreItem(player,realm,old);}catch(IllegalStateException expected){invalidated=true;}check(invalidated,"Reset invalidates every old Core item identity");
        check(new RealmCoreStore(folder).claimed(other)&&!new RealmCoreStore(folder).claimed(player),"Reset survives restart and affects only the selected player");
        String fresh=store.issue(player,realm,false);check(!old.equals(fresh),"NPC can grant a new unique item after staff reset");
        store.validateCoreItem(player,realm,fresh);store.coreState(player,fresh,"PLACING");
        boolean interrupted=false;try{store.resetCoreClaim(player);}catch(IllegalStateException expected){interrupted=true;}check(interrupted&&store.claimed(player),"An interrupted placement cannot be reset into duplicate eligibility");
        store.coreState(player,fresh,"ISSUED");var core=new RealmCoreStore.Core(realm,UUID.randomUUID(),player,24,64,0);store.add(core);
        boolean placed=false;try{store.resetCoreClaim(player);}catch(IllegalStateException expected){placed=true;}check(placed&&store.get(realm).equals(core),"A placed Core must be retrieved before claim reset");
        String captured=store.prepareRetrieval(core,player);store.removeCore(core);
        boolean retrieving=false;try{store.resetCoreClaim(player);}catch(IllegalStateException expected){retrieving=true;}check(retrieving,"An interrupted retrieval also blocks claim reset");
        store.coreState(player,captured,"RETRIEVED");store.resetCoreClaim(player);check(!store.claimed(player),"Successfully retrieved Core claims can be reset for testing");
        String last=store.issue(player,realm,false);Path blocker=folder.resolve("realm-cores.yml.tmp");Files.createDirectory(blocker);
        boolean failedSave=false;try{store.resetCoreClaim(player);}catch(java.io.IOException expected){failedSave=true;}finally{Files.delete(blocker);}
        store.validateCoreItem(player,realm,last);check(failedSave&&store.claimed(player),"Failed persistence restores the previous valid claim in memory");
        var saved=new RealmCoreStore(folder);saved.validateCoreItem(player,realm,last);check(saved.claimed(other)&&saved.patterns(player).equals(List.of(recipe)),"Failed reset preserves disk state, discoveries and other claims");
        saved.resetCoreClaim(player);saved.resetCoreClaim(player);check(!new RealmCoreStore(folder).claimed(player),"Repeated resets of an unclaimed player are harmless");
    }
    private static void manifestationVerification(Path folder)throws Exception {
        for(int tick=0;tick<=CoreManifestation.RETURN_DURATION;tick++){
            var flight=CoreManifestation.returnFlight(10000000,70,-10000000,10000024,65,-10000020,tick);
            check(Double.isFinite(flight.x())&&Double.isFinite(flight.y())&&Double.isFinite(flight.z())&&flight.scale()>=.16f&&flight.scale()<=.8f,"Returning crystal has finite positions and shrinks safely even far from world origin");
            check(flight.x()>=10000000&&flight.x()<=10000024&&flight.z()<=-10000000&&flight.z()>=-10000020,"Crystal flight stays on the path toward staff");
        }
        var departure=CoreManifestation.returnFlight(2,5,8,20,1,30,0);var arrival=CoreManifestation.returnFlight(2,5,8,20,1,30,CoreManifestation.RETURN_DURATION);
        check(departure.x()==2&&departure.y()==5&&departure.z()==8&&arrival.x()==20&&arrival.y()==1&&arrival.z()==30,"Return starts at the completed heart and finishes exactly at staff's hand");
        check(CoreManifestation.returnFlight(2,5,8,25,1,35,CoreManifestation.RETURN_DURATION).x()==25,"The destination can track staff movement without retaining an old hand location");
        var layout=RealmCoreLayout.blocks();
        for(var cell:layout.keySet()){
            for(int tick=0;tick<=CoreManifestation.DURATION;tick+=2)for(boolean reclaim:List.of(false,true)){
                var frame=CoreManifestation.pose(cell,tick,reclaim);
                check(Float.isFinite(frame.x())&&Float.isFinite(frame.y())&&Float.isFinite(frame.z())&&frame.scale()>=0&&frame.scale()<=1,"Staged crystal projection is finite with bounded block scales");
                check(Math.abs(frame.x())<16&&Math.abs(frame.z())<16&&frame.y()>-8&&frame.y()<18,"Fragments stay within the Core animation envelope");
            }
            var full=CoreManifestation.pose(cell,CoreManifestation.DURATION,false);check(full.x()==cell.x()&&full.y()==cell.y()&&full.z()==cell.z()&&full.scale()==1,"Staggered assembly finishes exactly on real terrain coordinates");
            var original=CoreManifestation.pose(cell,0,true);check(original.x()==cell.x()&&original.y()==cell.y()&&original.z()==cell.z()&&original.scale()==1,"Reclamation starts with the intact Core");
            var small=CoreManifestation.pose(cell,CoreManifestation.DURATION,true);check(small.x()==0&&small.y()==5&&small.z()==0&&small.scale()==0,"Reclaimed fragments disappear into the heart crystal");
            check(CoreManifestation.pose(cell,0,false).scale()==0,"Terrain projection stays hidden during crystal launch");
        }
        var first=CoreManifestation.pose(new TerrainPlan.Pos(0,0,0),55,false);var second=CoreManifestation.pose(new TerrainPlan.Pos(1,0,0),55,false);
        check(first.scale()!=second.scale(),"Blocks assemble at different times instead of uniformly expanding");
        check(!CoreManifestation.pose(new TerrainPlan.Pos(2,0,0),50,true).equals(CoreManifestation.pose(new TerrainPlan.Pos(2,0,0),50,false)),"Reclamation is a distinct fracture trajectory, not placement in reverse");
        UUID owner=UUID.randomUUID(),realm=UUID.randomUUID(),world=UUID.randomUUID();var store=new RealmCoreStore(folder);String serial=store.issue(owner,realm,false);
        store.coreState(owner,serial,"PLACING");boolean busy=false;try{store.issue(owner,UUID.randomUUID(),true);}catch(IllegalStateException expected){busy=true;}check(busy,"Staff replacement cannot rotate the item identity during crystallization");
        var core=new RealmCoreStore.Core(realm,world,owner,24,64,0);
        store.construction(core,List.of("0,0,0|DEEPSLATE_BRICKS","-15,0,0|STONE_BRICKS","-15,-1,0|DEEPSLATE_BRICKS"),.5,65,.5);store.coreState(owner,serial,"PLACED");
        check(store.protectedAt(world,9,64,0)&&store.protectedAt(world,9,63,0),"Recorded bridge deck and support cannot be harvested outside the Core cube");
        check(!store.protectedAt(world,8,64,0)&&!store.protectedAt(UUID.randomUUID(),9,64,0),"Bridge protection excludes neighboring terrain and other worlds");
        var restored=new RealmCoreStore(folder);check(restored.protectedAt(world,9,64,0)&&restored.protectedAt(world,9,63,0),"Bridge protection survives a restart");
        String captured=restored.prepareRetrieval(core,owner);restored.removeCore(core);
        boolean retrieving=false;try{restored.issue(owner,UUID.randomUUID(),true);}catch(IllegalStateException expected){retrieving=true;}check(retrieving,"No replacement item can be issued during staff shrink animation");
        check(!restored.protectedAt(world,9,64,0)&&!restored.protectedAt(world,24,64,0),"Retrieval clears foundation and bridge protection together");restored.coreState(owner,captured,"RETRIEVED");restored.validateCoreItem(owner,realm,captured);
        restored.coreState(owner,captured,"PLACING");UUID staff=UUID.randomUUID();restored.refundPlacement(owner,captured,staff);
        check(restored.refunds(owner).isEmpty()&&restored.refunds(staff).size()==1,"Cancelled escrow is returned to the actual placer, including staff placing for an owner");
        var refundRestart=new RealmCoreStore(folder);check(refundRestart.refunds(staff).getFirst().serial().equals(captured),"Offline placement refunds persist through restart");
        String renewed=refundRestart.issue(owner,realm,true);boolean stale=false;try{refundRestart.validateCoreItem(owner,realm,captured);}catch(IllegalStateException expected){stale=true;}check(stale&&refundRestart.refunds(staff).getFirst().serial().equals(renewed),"Refund retries rotate identity and invalidate old copies while retaining the pending delivery");
        Path blocked=folder.resolve("realm-cores.yml.tmp");Files.createDirectory(blocked);boolean failedAck=false;
        try{refundRestart.acknowledgeRefund(owner,renewed);}catch(java.io.IOException expected){failedAck=true;}finally{Files.delete(blocked);}
        check(failedAck&&refundRestart.refunds(staff).size()==1,"Failed refund acknowledgement retains recoverable delivery");
        refundRestart.acknowledgeRefund(owner,renewed);check(new RealmCoreStore(folder).refunds(staff).isEmpty(),"Delivered refunds cannot be issued again after restart");
    }
    private static void classicDesignVerification(Path folder)throws Exception {
        var layout=RealmCoreLayout.blocks();check(layout.size()<=1024,"Shrine terrain stays inside the bounded animation budget");
        for(int design:new int[]{0,1}){var map=design==0?RealmCoreLayout.legacy():layout;var approach=RealmCoreLayout.approach(design);check(map.containsKey(new TerrainPlan.Pos(approach.x(),approach.y()-1,approach.z()))&&!map.containsKey(approach)&&!map.containsKey(new TerrainPlan.Pos(approach.x(),approach.y()+1,approach.z())),"Home approach has a solid floor and headroom for both designs");}
        check(layout.get(new TerrainPlan.Pos(0,0,-4))==Material.CHISELED_DEEPSLATE,"Existing forge-basin marker remains compatible");
        check(!layout.containsKey(new TerrainPlan.Pos(0,6,0))&&layout.containsKey(new TerrainPlan.Pos(-1,6,0))&&layout.containsKey(new TerrainPlan.Pos(1,6,0)),"Upper crystal floats inside an open stone frame");
        for(var entry:layout.entrySet()){
            var p=entry.getKey();check(Math.abs(p.x())<=5&&Math.abs(p.z())<=5&&p.y()>=RealmCoreLayout.MIN_Y&&p.y()<=RealmCoreLayout.MAX_Y,"Classic terrain occupies the reference 11 by 11 footprint");
            if(entry.getValue()==Material.LANTERN)check(layout.containsKey(new TerrainPlan.Pos(p.x(),p.y()-1,p.z())),"Every actual lantern has support so physics cannot drop free materials");
            if(CoreShrineDesign.stone(entry.getValue()))check(CoreShrineDesign.groups().contains(new TerrainPlan.Pos(Math.floorDiv(p.x(),3),Math.floorDiv(p.y(),3),Math.floorDiv(p.z(),3))),"Every custom stone cell has a compact render group");
        }
        check(CoreShrineDesign.groups().size()<=64&&CoreShrineDesign.ornaments().size()<=32,"Persistent shrine display count is bounded");
        for(var ornament:CoreShrineDesign.ornaments())check(Math.abs(ornament.x()-.5)<6&&Math.abs(ornament.z()-.5)<6&&ornament.y()>-7&&ornament.y()<10&&ornament.scale()>0&&ornament.scale()<=2,"Crystal, vine, basin and lantern ornaments fit the protected shrine site");
        UUID world=UUID.randomUUID();var classic=new RealmCoreStore.Core(UUID.randomUUID(),world,UUID.randomUUID(),24,64,0,1);var store=new RealmCoreStore(folder);store.add(classic);
        var restarted=new RealmCoreStore(folder);check(restarted.get(classic.realm()).design()==1&&restarted.protectedAt(world,24,57,0)&&!restarted.protectedAt(world,30,64,0),"New design and full underside protection survive restart with the compact footprint");
        var legacy=new RealmCoreStore.Core(UUID.randomUUID(),world,UUID.randomUUID(),-24,64,0);restarted.add(legacy);check(new RealmCoreStore(folder).get(legacy.realm()).design()==0&&!legacy.contains(world,-24,61,0),"Existing legacy Core structure and protection bounds remain unchanged");
    }
    private static void reject(Runnable action){boolean invalid=false;try{action.run();}catch(IllegalStateException expected){invalid=true;}check(invalid,"Invalid inventory must fail before mutation");}
    private static void ledger(Path folder,String name,UUID player,String status,CoreRecipe recipe)throws Exception {
        YamlConfiguration ledger=new YamlConfiguration();ledger.set("player",player.toString());ledger.set("status",status);ledger.set("recipe",recipe.encode());
        RealmShards.atomicSave(ledger,folder.resolve("core-forge-transactions").resolve(name+".yml"));
    }
}
