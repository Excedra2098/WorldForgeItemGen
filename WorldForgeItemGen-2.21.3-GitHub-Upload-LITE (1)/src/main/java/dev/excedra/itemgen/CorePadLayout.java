package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

/** Full Core inside the audited 17x17 template recess; outer island terrain is excluded. */
final class CorePadLayout {
    static final int RADIUS=8;
    static Map<TerrainPlan.Pos,Material> blocks(){
        Map<TerrainPlan.Pos,Material> result=new LinkedHashMap<>(RealmCoreLayout.blocks());
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)result.putIfAbsent(new TerrainPlan.Pos(x,0,z),Math.floorMod(x*3+z,7)==0?Material.MOSSY_STONE_BRICKS:Material.DEEPSLATE_TILES);
        return result;
    }
    static boolean replaceable(TerrainPlan.Pos cell,Material original){
        if(cell.y()>0)return original==Material.AIR||original==Material.CAVE_AIR||original==Material.VOID_AIR;
        if(cell.y()==0)return Set.of(Material.PURPLE_GLAZED_TERRACOTTA,Material.MAGENTA_GLAZED_TERRACOTTA,Material.PINK_GLAZED_TERRACOTTA,Material.STONE_BRICKS).contains(original);
        return Set.of(Material.STONE_BRICKS,Material.MOSSY_STONE_BRICKS,Material.CRACKED_STONE_BRICKS,Material.ANDESITE,Material.SEA_LANTERN).contains(original);
    }
    static Map<TerrainPlan.Pos,String> decode(List<String> rows){
        Map<TerrainPlan.Pos,String> result=new LinkedHashMap<>();
        for(String row:rows){String[] parts=row.split("\\|",2),p=parts[0].split(",");if(parts.length!=2||p.length!=3)throw new IllegalStateException("Invalid Core pad restoration data.");
            var cell=new TerrainPlan.Pos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));if(result.put(cell,parts[1])!=null)throw new IllegalStateException("Duplicate Core restoration cell.");}
        return result;
    }
    static List<String> encode(Map<TerrainPlan.Pos,String> states){return states.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue()).toList();}
    static Set<TerrainPlan.Pos> floorGroups(){
        Set<TerrainPlan.Pos> groups=new LinkedHashSet<>();var original=RealmCoreLayout.blocks();
        for(var p:blocks().keySet())if(p.y()==0&&!original.containsKey(p))groups.add(new TerrainPlan.Pos(Math.floorDiv(p.x(),3),0,Math.floorDiv(p.z(),3)));
        return groups;
    }
    static String groupName(TerrainPlan.Pos group){return "pad_floor_"+group.x()+"_"+group.z();}
}
