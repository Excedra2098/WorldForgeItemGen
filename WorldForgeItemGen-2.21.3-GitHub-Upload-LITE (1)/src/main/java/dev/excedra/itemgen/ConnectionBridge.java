package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

/** Plans a bounded stone crossing without replacing either island's existing blocks. */
final class ConnectionBridge {
    static final int MAX_LENGTH=64;
    interface Ground {
        Integer floor(int x,int z,int nearY);
        boolean air(int x,int y,int z);
        String state(int x,int y,int z);
        default boolean canBuild(int x,int y,int z){return true;}
    }
    record Cell(int x,int y,int z) {}
    record Result(Map<TerrainPlan.Pos,Material> blocks,Map<TerrainPlan.Pos,String> anchors,
                  Set<TerrainPlan.Pos> clearance,List<TerrainPlan.Pos> path) {}

    static boolean terrain(Material material) {
        return material!=null&&switch(material){
            case GRASS_BLOCK,DIRT,COARSE_DIRT,ROOTED_DIRT,PODZOL,MYCELIUM,MOSS_BLOCK,
                 SAND,RED_SAND,GRAVEL,CLAY,MUD,PACKED_MUD,SNOW,
                 STONE,ANDESITE,DIORITE,GRANITE,DEEPSLATE,TUFF,CALCITE,
                 STONE_BRICKS,MOSSY_STONE_BRICKS,CRACKED_STONE_BRICKS,
                 DEEPSLATE_BRICKS,DEEPSLATE_TILES,SANDSTONE,RED_SANDSTONE,
                 SMOOTH_SANDSTONE,CUT_SANDSTONE,TERRACOTTA,ORANGE_TERRACOTTA,
                 RED_TERRACOTTA,BLACKSTONE,BASALT,SMOOTH_BASALT,END_STONE,
                 END_STONE_BRICKS,PRISMARINE,PRISMARINE_BRICKS,SNOW_BLOCK,
                 NETHERRACK,CRIMSON_NYLIUM,WARPED_NYLIUM,PURPUR_BLOCK -> true;
            default -> false;
        };
    }

    static Result plan(TerrainPlan island,int playerX,int playerY,int playerZ,Ground ground) {
        Integer initial=ground.floor(playerX,playerZ,playerY);
        if(initial==null)throw new IllegalStateException("Stand above walkable island terrain to anchor a bridge.");
        // Search only walkable land connected to the player's current foothold.
        Map<Long,Cell> reachable=new LinkedHashMap<>();ArrayDeque<Cell> queue=new ArrayDeque<>();
        Cell first=new Cell(playerX,initial,playerZ);queue.add(first);reachable.put(TerrainPlan.key(playerX,playerZ),first);
        while(!queue.isEmpty()){
            Cell cell=queue.remove();
            for(int[] step:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                int x=cell.x()+step[0],z=cell.z()+step[1];long key=TerrainPlan.key(x,z);
                if(Math.abs(x-playerX)>12||Math.abs(z-playerZ)>12||reachable.containsKey(key))continue;
                Integer y=ground.floor(x,z,cell.y());
                if(y==null||Math.abs(y-cell.y())>1)continue;
                Cell next=new Cell(x,y,z);reachable.put(key,next);queue.add(next);
            }
        }
        Map<Long,Integer> tops=new HashMap<>();
        for(var entry:island.blocks.entrySet()){
            var p=entry.getKey();if(terrain(entry.getValue()))tops.merge(TerrainPlan.key(p.x(),p.z()),p.y(),Math::max);
        }
        Cell start=null,end=null;long closest=Long.MAX_VALUE;
        for(var entry:tops.entrySet()){
            int x=(int)(entry.getKey()>>32),z=(int)(long)entry.getKey(),y=entry.getValue();
            if(island.blocks.containsKey(new TerrainPlan.Pos(x,y+1,z))||island.blocks.containsKey(new TerrainPlan.Pos(x,y+2,z)))continue;
            for(Cell candidate:reachable.values()){
                long dx=(long)x-candidate.x(),dz=(long)z-candidate.z();long distance=dx*dx+dz*dz;
                if(distance<closest){closest=distance;start=candidate;end=new Cell(x,y,z);}
            }
        }
        if(start==null)throw new IllegalStateException("This Shard has no clear terrain landing for a bridge.");
        int length=Math.abs(end.x()-start.x())+Math.abs(end.z()-start.z());
        if(length<2)throw new IllegalStateException("Move the Shard farther into clear space.");
        if(length>MAX_LENGTH)throw new IllegalStateException("Bridge would exceed 64 blocks; move closer to existing land.");
        if(Math.abs(end.y()-start.y())>Math.min(8,length))throw new IllegalStateException("Bridge elevation is too steep; aim closer to island height.");
        List<Cell> centers=new ArrayList<>();int x=start.x(),z=start.z();
        for(int i=0;i<=length;i++){
            int y=start.y()+(int)Math.round((end.y()-start.y())*i/(double)length);
            centers.add(new Cell(x,y,z));
            if(i==length)break;
            // Monotone cardinal steps keep diagonal paths physically connected.
            if(Math.abs(end.x()-x)>=Math.abs(end.z()-z))x+=Integer.signum(end.x()-x);
            else z+=Integer.signum(end.z()-z);
        }
        Map<Long,Integer> deck=new LinkedHashMap<>();
        for(int i=0;i<centers.size();i++){
            Cell center=centers.get(i);
            // Narrow ends avoid changing the shore; the crossing is three blocks wide.
            int width=i==0||i==centers.size()-1?0:1;
            for(int dx=-width;dx<=width;dx++)for(int dz=-width;dz<=width;dz++)
                deck.merge(TerrainPlan.key(center.x()+dx,center.z()+dz),center.y(),Math::max);
        }
        Map<TerrainPlan.Pos,Material> blocks=new LinkedHashMap<>();
        Map<TerrainPlan.Pos,String> anchors=new LinkedHashMap<>();Set<TerrainPlan.Pos> clearance=new LinkedHashSet<>();
        for(var entry:deck.entrySet()){
            int dx=(int)(entry.getKey()>>32),dz=(int)(long)entry.getKey(),y=entry.getValue();
            Integer islandFloor=tops.get(entry.getKey());
            if(islandFloor!=null){
                if(Math.abs(islandFloor-y)>1||island.blocks.containsKey(new TerrainPlan.Pos(dx,islandFloor+1,dz))||island.blocks.containsKey(new TerrainPlan.Pos(dx,islandFloor+2,dz)))
                    throw new IllegalStateException("Bridge landing conflicts with the Shard; rotate or retarget it.");
                continue;
            }
            var floor=new TerrainPlan.Pos(dx,y,dz);
            if(!ground.air(dx,y,dz)){
                Integer existing=ground.floor(dx,dz,y);
                if(existing==null||existing!=y)throw new IllegalStateException("Bridge route crosses existing blocks; retarget it.");
                anchors.put(floor,ground.state(dx,y,dz));
            }else{
                if(!ground.canBuild(dx,y,dz)){
                    if(centers.stream().anyMatch(c->c.x()==dx&&c.z()==dz))throw new IllegalStateException("Protection denies the bridge center; retarget the Shard.");
                    continue;
                }
                blocks.put(floor,Math.floorMod(dx+dz,7)==0?Material.MOSSY_STONE_BRICKS:Material.STONE_BRICKS);
                clearance.add(floor);
                // A shallow stone underside keeps the bridge from looking paper-thin.
                if(ground.air(dx,y-1,dz)&&ground.canBuild(dx,y-1,dz)){var support=new TerrainPlan.Pos(dx,y-1,dz);blocks.put(support,Material.DEEPSLATE_BRICKS);clearance.add(support);}
            }
            for(int h=1;h<=2;h++){
                var head=new TerrainPlan.Pos(dx,y+h,dz);
                if(island.blocks.containsKey(head)||!ground.air(dx,y+h,dz))throw new IllegalStateException("Bridge needs two clear blocks of headroom.");
                clearance.add(head);
            }
        }
        if(anchors.isEmpty())throw new IllegalStateException("No existing land anchor was found.");
        List<TerrainPlan.Pos> path=centers.stream().map(c->{
            long column=TerrainPlan.key(c.x(),c.z());
            int floor=tops.containsKey(column)?tops.get(column):deck.get(column);
            return new TerrainPlan.Pos(c.x(),floor+1,c.z());
        }).toList();
        return new Result(blocks,anchors,clearance,path);
    }
}
