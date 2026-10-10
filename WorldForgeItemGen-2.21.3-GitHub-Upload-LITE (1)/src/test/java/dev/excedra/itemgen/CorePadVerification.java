package dev.excedra.itemgen;

import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;

public final class CorePadVerification {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var coreCells=CorePadLayout.blocks();check(coreCells.size()<1024,"Complete Core fits the animation display budget");
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)check(coreCells.containsKey(new TerrainPlan.Pos(x,0,z)),"Every pad tile is covered, including corners");
        var old=RealmCoreLayout.blocks();for(var entry:old.entrySet())check(coreCells.get(entry.getKey())==entry.getValue(),"Full classic floor, body and shrine remain intact");
        for(var p:coreCells.keySet())check(Math.abs(p.x())<=8&&Math.abs(p.z())<=8,"No template wall or walkway is replaced");
        List<String> names=List.of("01_terraced_rune_garden","02_sunrise_lily_basin","03_blossom_pergola_sanctuary","04_riverbridge_rune_garden");
        for(String name:names){
            var schematic=SchematicData.read(Path.of(args[0],name+" (1).schem"));var original=new LinkedHashMap<TerrainPlan.Pos,String>();var result=new LinkedHashMap<>(schematic.states());
            for(var entry:coreCells.entrySet()){
                var p=entry.getKey();var site=new TerrainPlan.Pos(p.x(),22+p.y(),p.z());String state=result.getOrDefault(site,"minecraft:air");Material material=Material.matchMaterial(state.split("\\[")[0]);
                check(material!=null&&CorePadLayout.replaceable(p,material),"Replacement is restricted to the audited pad materials: "+name);
                original.put(p,state);result.put(site,"minecraft:"+entry.getValue().name().toLowerCase(Locale.ROOT));
            }
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)check(!result.get(new TerrainPlan.Pos(x,22,z)).contains("glazed_terracotta"),"Original purple pad is fully hidden");
            for(var entry:schematic.states().entrySet())if(!coreCells.containsKey(new TerrainPlan.Pos(entry.getKey().x(),entry.getKey().y()-22,entry.getKey().z())))check(result.get(entry.getKey()).equals(entry.getValue()),"Surrounding walls, corner decorations, stairs and gardens remain unchanged");
            var restored=CorePadLayout.decode(CorePadLayout.encode(original));check(restored.equals(original),"Restoration keeps all original block states and directions");
            for(var entry:restored.entrySet()){var p=entry.getKey();var site=new TerrainPlan.Pos(p.x(),22+p.y(),p.z());if(entry.getValue().equals("minecraft:air"))result.remove(site);else result.put(site,entry.getValue());}
            check(result.equals(schematic.states()),"Reclaim restores the entire schematic exactly: "+name);
        }
        check(!CorePadLayout.replaceable(new TerrainPlan.Pos(0,0,0),Material.CHEST),"Unexpected storage is never overwritten");
        check(!CorePadLayout.replaceable(new TerrainPlan.Pos(0,-1,0),Material.DIAMOND_BLOCK),"Modified foundation requires staff inspection");
        UUID realm=UUID.randomUUID(),world=UUID.randomUUID(),owner=UUID.randomUUID();Path folder=Path.of("work/pad-verification",realm.toString());var store=new RealmCoreStore(folder);var core=new RealmCoreStore.Core(realm,world,owner,0,122,0,3);
        Map<TerrainPlan.Pos,String> originals=Map.of(new TerrainPlan.Pos(0,0,0),"minecraft:pink_glazed_terracotta[facing=north]");store.padOriginals(core,CorePadLayout.encode(originals));
        check(CorePadLayout.decode(new RealmCoreStore(folder).padOriginals(core)).equals(originals),"Original pad survives a restart before construction commits");
        store.construction(core,coreCells.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue().name()).toList(),10,126,0);
        check(store.protectedAt(world,8,122,8)&&store.protectedAt(world,0,117,0),"Wide deck and full underside are protected");
        check(!store.protectedAt(world,9,122,0),"Surrounding template remains outside the Core");
        Path blocked=folder.resolve("realm-cores.yml.tmp");Files.createDirectory(blocked);boolean failed=false;try{store.removeCore(core);}catch(java.io.IOException e){failed=true;}finally{Files.delete(blocked);}
        check(failed&&store.get(realm)!=null&&!store.padOriginals(core).isEmpty(),"Failed retrieval save retains Core and original terrain");
        store.removeCore(core);check(new RealmCoreStore(folder).padOriginals(core).isEmpty(),"Successful retrieval clears the restoration ledger");
        var anchor=new RealmCoreStore.Anchor(realm,world,0,122,0,"r");boolean overlap=false;try{CoreExpansionGeometry.validate(anchor,13,122,0,5,4096,8);}catch(IllegalStateException e){overlap=true;}check(overlap,"Expansions cannot overlap the wider Core deck");
        CoreExpansionGeometry.validate(anchor,14,122,0,5,4096,8);
        System.out.println("Core pad verification passed: "+checks+" checks; "+coreCells.size()+" Core cells, all four schematics restored exactly.");
    }
}
