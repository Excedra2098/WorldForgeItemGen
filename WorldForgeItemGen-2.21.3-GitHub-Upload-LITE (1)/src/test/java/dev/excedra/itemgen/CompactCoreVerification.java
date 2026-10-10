package dev.excedra.itemgen;
import java.util.*;import java.nio.file.*;import org.bukkit.Material;
public final class CompactCoreVerification {
 private static int checks;private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  var blocks=CompactCoreLayout.blocks();check(blocks.size()==38,"Compact collision footprint is limited to 38 cells");check(CompactCoreLayout.groups().size()==16,"Renderer and pack use sixteen two-block groups");
  for(var e:blocks.entrySet()){var p=e.getKey();check(Math.abs(p.x())<=2&&Math.abs(p.z())<=2&&p.y()>=1&&p.y()<=2,"Only 5x5 base and low pedestal collide");check(e.getValue()==Material.BARRIER||e.getValue()==Material.CHISELED_DEEPSLATE,"No collectible building materials in cosmetic body");}
  UUID world=UUID.randomUUID(),realm=UUID.randomUUID(),owner=UUID.randomUUID();var core=new RealmCoreStore.Core(realm,world,owner,0,125,0,5);check(core.contains(world,2,133,2)&&!core.contains(world,3,125,0),"Only compact region protects island terrain");check(RealmCoreLayout.approach(5).equals(new TerrainPlan.Pos(3,1,0)),"Home is outside solid pedestal on walkway");
  var names=List.of("01_terraced_rune_garden","02_sunrise_lily_basin","03_blossom_pergola_sanctuary","04_riverbridge_rune_garden");
  for(String name:names){var old=SchematicData.read(Path.of(args[0],name+" (1).schem"));var fresh=SchematicData.read(Path.of(args[1],name+".schem"));var expected=new LinkedHashMap<>(old.states());var diff=CorePlazaLayout.cells(List.of(name));check(diff.size()>1600,"Audited plaza repair resource exists for every island");
   for(var c:diff){var p=c.pos();var absolute=new TerrainPlan.Pos(p.x(),22+p.y(),p.z());check(expected.getOrDefault(absolute,"minecraft:air").equals(c.before()),"Repair preflight matches original block state");if(c.after().equals("minecraft:air"))expected.remove(absolute);else expected.put(absolute,c.after());}
   check(expected.equals(fresh.states()),"Runtime repair exactly reproduces updated schematic");
   for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
    for(int y=22;y<=25;y++)check(fresh.states().containsKey(new TerrainPlan.Pos(x,y,z)),"Solid four-layer plaza has no opening or exposed lower floor");
    for(int y=26;y<=29;y++)check(!fresh.states().containsKey(new TerrainPlan.Pos(x,y,z)),"Removed pit walls and decorations cannot obstruct the new compact Core");
   }
   for(var p:blocks.keySet())check(!fresh.states().containsKey(new TerrainPlan.Pos(p.x(),25+p.y(),p.z())),"Compact placement replaces only air above finished floor");
   var placed=new LinkedHashMap<>(fresh.states());var snapshots=new LinkedHashMap<TerrainPlan.Pos,String>();for(var e:blocks.entrySet()){var p=e.getKey();var at=new TerrainPlan.Pos(p.x(),25+p.y(),p.z());snapshots.put(p,placed.getOrDefault(at,"minecraft:air"));placed.put(at,"minecraft:"+e.getValue().name().toLowerCase(Locale.ROOT));}
   for(var e:CorePadLayout.decode(CorePadLayout.encode(snapshots)).entrySet()){var p=e.getKey();var at=new TerrainPlan.Pos(p.x(),25+p.y(),p.z());if(e.getValue().equals("minecraft:air"))placed.remove(at);else placed.put(at,e.getValue());}check(placed.equals(fresh.states()),"Reclaim preserves permanent flat plaza and surrounding garden exactly");
   for(var e:old.states().entrySet())if(Math.abs(e.getKey().x())>10||Math.abs(e.getKey().z())>10||e.getKey().y()<22||e.getKey().y()>29)check(Objects.equals(fresh.states().get(e.getKey()),e.getValue()),"All outside terrain is preserved");
  }
  Path folder=Path.of("work/compact-verification",realm.toString());var store=new RealmCoreStore(folder);store.plazaBackup(realm,world,0,122,0,List.of("0,0,0|minecraft:stone_bricks"));store.plazaReady(realm,true);check(new RealmCoreStore(folder).plazaReady(realm),"Permanent plaza readiness survives restart and allows reactivation after building elsewhere");
  System.out.println("Compact Core verification passed: "+checks+" checks, all four starter/VIP schematics.");
 }
}
