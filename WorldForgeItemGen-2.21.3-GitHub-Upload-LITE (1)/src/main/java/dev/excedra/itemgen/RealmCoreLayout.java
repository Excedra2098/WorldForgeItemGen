package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

/** The original Awakened foundation; upgrades must be attached outside its bounds. */
final class RealmCoreLayout {
    static final int RADIUS=5,MIN_Y=-7,MAX_Y=9;
    static int radius(int design){return design==5?CompactCoreLayout.RADIUS:design>=3?CorePadLayout.RADIUS:design==0?6:RADIUS;}
    static Map<TerrainPlan.Pos,Material> forDesign(int design){return design==5?CompactCoreLayout.blocks():design==4?RaisedCorePadLayout.blocks():design==3?CorePadLayout.blocks():design==2?attached():design>=1?blocks():legacy();}
    static TerrainPlan.Pos approach(int design){return design==5?new TerrainPlan.Pos(3,1,0):design>=1?new TerrainPlan.Pos(2,1,-5):new TerrainPlan.Pos(0,1,-6);}
    static Map<TerrainPlan.Pos,Material> attached(){
        Map<TerrainPlan.Pos,Material> upper=new LinkedHashMap<>();blocks().forEach((p,m)->{if(p.y()>0)upper.put(p,m);});return upper;
    }
    static Map<TerrainPlan.Pos,Material> blocks(){
        Map<TerrainPlan.Pos,Material> blocks=new LinkedHashMap<>();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
            if(Math.abs(x)+Math.abs(z)>9)continue;
            put(blocks,x,0,z,Math.floorMod(x*3+z,7)==0?Material.MOSSY_STONE_BRICKS:Material.DEEPSLATE_BRICKS);
            int depth=1+Math.max(0,4-Math.max(Math.abs(x),Math.abs(z)));
            for(int y=-1;y>=-depth;y--)put(blocks,x,y,z,Math.floorMod(x*13+z*7+y,6)==0?Material.TUFF:Material.DEEPSLATE_TILES);
        }
        for(int side:new int[]{-1,1}){
            for(int y=1;y<=4;y++){put(blocks,side*4,y,1,Material.DEEPSLATE_BRICKS);put(blocks,side*4,y,2,Material.MOSSY_STONE_BRICKS);}
            for(int x=2;x<=4;x++){put(blocks,side*x,5,1,Material.DEEPSLATE_BRICKS);put(blocks,side*x,5,2,Material.DEEPSLATE_BRICKS);}
            put(blocks,side*3,6,2,Material.MOSSY_STONE_BRICKS);put(blocks,side*3,7,2,Material.DEEPSLATE_TILES);
            put(blocks,side*3,2,1,Material.DEEPSLATE_BRICKS);
            put(blocks,side*3,3,1,Material.LANTERN);
            put(blocks,side*4,1,-3,Material.MOSSY_STONE_BRICKS);put(blocks,side*4,2,-3,Material.LANTERN);
            put(blocks,side*2,1,3,Material.MOSSY_STONE_BRICKS);
        }
        // Open suspended frame, with a custom crystal and rune ornament inside.
        for(int x=-1;x<=1;x++)for(int z=0;z<=1;z++){
            put(blocks,x,8,z,Material.DEEPSLATE_TILES);
            if(x!=0)for(int y=5;y<=7;y++)put(blocks,x,y,z,Material.DEEPSLATE_BRICKS);
        }
        put(blocks,-1,9,1,Material.DEEPSLATE_TILES);put(blocks,1,9,1,Material.MOSSY_STONE_BRICKS);
        // Preserve the original forge-basin interaction marker and approach.
        for(int x=-1;x<=1;x++)for(int z=-5;z<=-3;z++)if(Math.abs(x)==1||z!=-4)put(blocks,x,1,z,Material.MOSSY_STONE_BRICKS);
        put(blocks,0,0,-4,Material.CHISELED_DEEPSLATE);put(blocks,0,0,-2,Material.CRYING_OBSIDIAN);
        return blocks;
    }
    static Map<TerrainPlan.Pos,Material> legacy(){
        Map<TerrainPlan.Pos,Material> blocks=new LinkedHashMap<>();
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)if(Math.abs(x)+Math.abs(z)<=10)
            put(blocks,x,0,z,Math.floorMod(x+z,5)==0?Material.TUFF:Material.DEEPSLATE_BRICKS);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)put(blocks,x,0,z,Material.CHISELED_DEEPSLATE);
        for(int x=-1;x<=1;x++)for(int y=4;y<=6;y++)for(int z=-1;z<=1;z++)
            put(blocks,x,y,z,x==0&&y==5&&z<=0?Material.AMETHYST_BLOCK:Material.DEEPSLATE_TILES);
        for(int side:new int[]{-1,1})for(int y=1;y<=6;y++){
            int x=side*(5-y/3);put(blocks,x,y,2,Material.DEEPSLATE_BRICKS);if(y<4)put(blocks,x,y,3,Material.TUFF);
        }
        for(int y=1;y<=8;y++)put(blocks,y>5?-1:0,y,5-y/3,Material.DEEPSLATE_TILES);
        for(int x=-1;x<=1;x++)for(int z=-5;z<=-3;z++)if(Math.abs(x)==1||z!=-4)put(blocks,x,1,z,Material.DEEPSLATE_BRICKS);
        put(blocks,0,0,-4,Material.CHISELED_DEEPSLATE);put(blocks,0,0,-2,Material.AMETHYST_BLOCK);
        put(blocks,0,0,-1,Material.CRYING_OBSIDIAN);
        return blocks;
    }
    private static void put(Map<TerrainPlan.Pos,Material> blocks,int x,int y,int z,Material material){blocks.put(new TerrainPlan.Pos(x,y,z),material);}
}
