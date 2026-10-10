package dev.excedra.itemgen;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CoreOverhaulVerification {
    private static int checks;
    private static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path folder=Path.of(args.length==0?"work/overhaul-verification":args[0]).resolve(UUID.randomUUID().toString());Files.createDirectories(folder);
        var attached=RealmCoreLayout.attached();check(!attached.isEmpty(),"Attached shrine has terrain");
        for(var p:attached.keySet())check(p.y()>0&&RealmCoreLayout.blocks().containsKey(p),"Attachment never includes starter ground or underside");
        UUID realm=UUID.randomUUID(),world=UUID.randomUUID(),owner=UUID.randomUUID(),other=UUID.randomUUID();
        var store=new RealmCoreStore(folder);var core=new RealmCoreStore.Core(realm,world,owner,0,64,0,2);
        store.placementAnchor(realm,0,64,0);store.standalone(owner,true);
        check(!store.standalone(other),"Staff toggles do not affect other players");
        store.construction(core,attached.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue().name()).toList(),6.5,65,6.5);
        check(store.anchor(realm)!=null&&store.anchor(realm).relative(20,64,-20).equals(List.of(20,0,-20)),"Placed Core registers the relative coordinate origin in all directions");
        var restarted=new RealmCoreStore(folder);check(restarted.anchor(realm).equals(store.anchor(realm)),"Center identity survives restart");
        check(restarted.placementAnchor(realm)[1]==64&&restarted.standalone(owner),"Per-island center and per-staff mode survive restart");
        check(!restarted.protectedAt(world,0,63,0)&&restarted.protectedAt(world,0,65,0),"Attached protection does not protect unrelated land beneath the island");
        var oldCenter=restarted.anchor(realm);restarted.removeCore(core);check(restarted.anchor(realm)==null,"Retrieving Core deactivates expansion center");
        for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1}}){CoreExpansionGeometry.validate(oldCenter,direction[0]*24,64,direction[1]*24,7,4096);check(true,"Expansion supports all directions around the Core");}
        boolean intersects=false;try{CoreExpansionGeometry.validate(oldCenter,4,64,4,7,4096);}catch(IllegalStateException e){intersects=true;}check(intersects,"Central anchor reserves the shrine footprint in adjacency checks");
        boolean exceeds=false;try{CoreExpansionGeometry.validate(oldCenter,4095,64,0,7,4096);}catch(IllegalStateException e){exceeds=true;}check(exceeds,"Expansion limits include the entire shard radius relative to the Core");
        check(CoreExpansionGeometry.bridgeStart(oldCenter,6,64,0,24,64,0).equals(new TerrainPlan.Pos(-18,0,0)),"Bridge planning translates both endpoints through Realm center coordinates");
        var relocated=new RealmCoreStore.Core(realm,world,owner,40,64,0,2);restarted.construction(relocated,List.of("1,1,-4|MOSSY_STONE_BRICKS"),6.5,65,6.5);
        check(!oldCenter.revision().equals(restarted.anchor(realm).revision()),"Relocation invalidates in-flight placement snapshots");
        List<Integer> absolute=List.of(20,64,-20);var relative=restarted.anchor(realm).relative(absolute.get(0),absolute.get(1),absolute.get(2));check(relative.equals(List.of(-20,0,-20))&&absolute.equals(List.of(20,64,-20)),"Existing expansions retain absolute positions after center relocation");
        UUID legacyRealm=UUID.randomUUID();var legacy=new RealmCoreStore.Core(legacyRealm,world,owner,-24,64,0,1);restarted.add(legacy);check(new RealmCoreStore(folder).anchor(legacyRealm)==null,"Existing Cores are not automatically moved or adopted");
        restarted.migrateAnchor(legacy);check(restarted.anchor(legacyRealm).x()==-24&&new RealmCoreStore(folder).get(legacyRealm).equals(legacy),"Explicit migration keeps original structure coordinates");
        try(var files=Files.list(folder)){check(files.anyMatch(p->p.getFileName().toString().startsWith("realm-cores-backup-")),"Migration creates a backup before changing center data");}
        Path broken=folder.resolve("realm-cores.yml.tmp");Files.createDirectory(broken);try{boolean failed=false;try{restarted.standalone(owner,false);}catch(java.io.IOException e){failed=true;}check(failed&&restarted.standalone(owner),"Failed mode save rolls back memory");}finally{Files.delete(broken);}
        var looks=new CoreAppearance(folder);var original=new CoreAppearance.Profile(CoreAppearance.DEFAULTS);check(looks.allowed(owner,original),"Default appearance requires no unlocks");
        var exclusive=original.with("crystal","eclipse");boolean locked=false;try{looks.equip(realm,owner,exclusive);}catch(IllegalStateException e){locked=true;}check(locked&&looks.profile(realm).equals(original)&&looks.unlocked(owner).isEmpty(),"A locked preview cannot save or grant permanent rewards");
        looks.progress(owner,List.of(new CoreRecipe(CoreRecipe.Influence.ROOTS,CoreRecipe.Influence.CRYSTAL,1)));
        check(looks.available(owner,CoreAppearance.option("crystal","crown")),"Rare combined recipe unlocks a hidden crystal");
        check(!looks.available(other,CoreAppearance.option("crystal","crown")),"Discovery unlocks are isolated per owner");
        List<CoreRecipe> patterns=new ArrayList<>();for(int i=0;i<10;i++)patterns.add(new CoreRecipe(CoreRecipe.Influence.ROOTS,i));looks.progress(owner,patterns);
        var custom=original.with("crystal","spire").with("primary","jade").with("secondary","rose").with("effect","pulse").with("decor","pillars").with("attach","relic");looks.equip(realm,owner,custom);
        var reloaded=new CoreAppearance(folder);check(reloaded.profile(realm).equals(custom)&&reloaded.allowed(owner,custom),"All six customization categories persist across restart");
        reloaded.progress(owner,List.of());check(reloaded.allowed(owner,custom),"Resetting recipe journal cannot revoke earned rewards");
        var event=CoreAppearance.option("crystal","eclipse");reloaded.grant(owner,event);reloaded.equip(realm,owner,exclusive);check(new CoreAppearance(folder).allowed(owner,exclusive),"Explicit event reward grants persist");
        Path blocked=folder.resolve("core-customization.yml.tmp");Files.createDirectory(blocked);try{boolean failed=false;try{reloaded.equip(realm,owner,original);}catch(java.io.IOException e){failed=true;}check(failed&&reloaded.profile(realm).equals(exclusive),"Failed cosmetic save preserves equipped selection");}finally{Files.delete(blocked);}
        check(new CoreAppearance.Profile(Map.of("crystal","invalid","primary","unknown")).equals(original),"Malformed profile selections fall back to valid defaults");
        Set<String> ids=new HashSet<>();for(var o:CoreAppearance.CATALOG)check(ids.add(CoreAppearance.key(o))&&CoreAppearance.CATEGORIES.contains(o.category()),"Catalog identities are unique and valid");
        for(int t=0;t<200;t+=4){check(Math.abs(CoreShrineDesign.bob(t,0))<=.121,"Idle movement stays subtle");check(Math.abs(CoreShrineDesign.bob(t+4,0)-CoreShrineDesign.bob(t,0))<.016,"Client interpolation steps remain smooth");}
        check(Math.abs(CoreShrineDesign.bob(0,0)-CoreShrineDesign.bob(200,0))<.00001,"Idle float loops seamlessly");
        check(CoreShrineDesign.rotation(100,0,"gentle")>0,"Gentle floating crystals rotate");
        check(CoreShrineDesign.rotation(100,0,"still")==0,"Explicit still effect remains still");
        check(Math.abs(CoreShrineDesign.rotation(104,0,"gentle")-CoreShrineDesign.rotation(100,0,"gentle"))<.03,"Gentle rotation interpolates smoothly");
        var permissions=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/plugin.yml").toFile());
        for(String node:List.of("testing","admin","reclaim","replace","resetclaim","cosmetics.grant"))check("false".equals(String.valueOf(permissions.get("permissions.worldforge.realmcore."+node+".default"))),"Core staff privilege requires explicit permission: "+node);
        System.out.println("Core overhaul verification passed: "+checks+" checks.");
    }
}
