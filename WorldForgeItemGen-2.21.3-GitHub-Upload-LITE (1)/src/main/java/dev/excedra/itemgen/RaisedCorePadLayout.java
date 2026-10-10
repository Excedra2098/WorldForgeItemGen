package dev.excedra.itemgen;
import java.util.*;
import org.bukkit.Material;
/** Raised, solid fitted deck. Coordinates are relative to the new top floor. */
final class RaisedCorePadLayout {
 static final int RISE=4;
 static Material base(int x,int y,int z){if(y==0){int edge=Math.max(Math.abs(x),Math.abs(z));if(edge==7&&Math.floorMod(x+z,3)==0)return Material.CHISELED_DEEPSLATE;if(edge==7)return Material.POLISHED_BLACKSTONE;if((x==0&&z<=-5)||(z==-4&&Math.abs(x)>=3))return Math.floorMod(x+z,3)==0?Material.CRACKED_DEEPSLATE_BRICKS:Material.DEEPSLATE_BRICKS;}int n=Math.floorMod(x*17+z*31+y*13,11);return n<3?Material.CRACKED_DEEPSLATE_BRICKS:n==3?Material.MOSSY_STONE_BRICKS:Material.DEEPSLATE_TILES;}
 static Map<TerrainPlan.Pos,Material> blocks(){
  var result=new LinkedHashMap<TerrainPlan.Pos,Material>();
  for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-RISE;y<=0;y++)result.put(new TerrainPlan.Pos(x,y,z),base(x,y,z));
  result.putAll(RealmCoreLayout.attached());
  result.put(new TerrainPlan.Pos(0,0,-4),Material.CHISELED_DEEPSLATE);result.put(new TerrainPlan.Pos(0,0,-2),Material.CRYING_OBSIDIAN);
  return result;
 }
 static boolean replaceable(TerrainPlan.Pos p,Material m){
  int originalY=p.y()+RISE;
  if(originalY==0)return CorePadLayout.replaceable(new TerrainPlan.Pos(p.x(),0,p.z()),m);
  if(m==Material.AIR||m==Material.CAVE_AIR||m==Material.VOID_AIR)return true;
  // Only the four schematic corner pillars intersect the reserved fill.
  return Math.abs(p.x())==8&&Math.abs(p.z())==8&&originalY>=1&&originalY<=4&&(m==Material.STONE_BRICKS||originalY==2&&m==Material.CYAN_GLAZED_TERRACOTTA);
 }
 static Map<TerrainPlan.Pos,Material> visible(Map<TerrainPlan.Pos,Material> blocks){
  var result=new LinkedHashMap<TerrainPlan.Pos,Material>();
  for(var e:blocks.entrySet()){var p=e.getKey();if(p.y()>0||!blocks.containsKey(new TerrainPlan.Pos(p.x()+1,p.y(),p.z()))||!blocks.containsKey(new TerrainPlan.Pos(p.x()-1,p.y(),p.z()))||!blocks.containsKey(new TerrainPlan.Pos(p.x(),p.y()+1,p.z()))||!blocks.containsKey(new TerrainPlan.Pos(p.x(),p.y()-1,p.z()))||!blocks.containsKey(new TerrainPlan.Pos(p.x(),p.y(),p.z()+1))||!blocks.containsKey(new TerrainPlan.Pos(p.x(),p.y(),p.z()-1)))result.put(p,e.getValue());}
  return result;
 }
 static Set<TerrainPlan.Pos> groups(){var result=new LinkedHashSet<TerrainPlan.Pos>();visible(blocks()).forEach((p,m)->{if(p.y()<=0)result.add(new TerrainPlan.Pos(Math.floorDiv(p.x(),3),Math.floorDiv(p.y(),3),Math.floorDiv(p.z(),3)));});return result;}
 static String groupName(TerrainPlan.Pos p){return "raised_base_"+p.x()+"_"+p.y()+"_"+p.z();}
 public static void main(String[] args)throws Exception{List<String> rows=new ArrayList<>();visible(blocks()).forEach((p,m)->{if(p.y()<=0)rows.add(p.x()+"|"+p.y()+"|"+p.z()+"|"+m.name());});java.nio.file.Files.write(java.nio.file.Path.of(args[0]),rows);}
}
