package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

public final class ConnectionBridgeVerification {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static class Ground implements ConnectionBridge.Ground {
        final Map<TerrainPlan.Pos,Material> cells=new HashMap<>();
        public boolean air(int x,int y,int z){return !cells.containsKey(new TerrainPlan.Pos(x,y,z));}
        public String state(int x,int y,int z){return cells.get(new TerrainPlan.Pos(x,y,z)).name();}
        public Integer floor(int x,int z,int nearY){
            for(int y=nearY+1;y>=nearY-6;y--)if(ConnectionBridge.terrain(cells.get(new TerrainPlan.Pos(x,y,z)))&&air(x,y+1,z)&&air(x,y+2,z))return y;
            return null;
        }
        void land(int minX,int maxX,int minZ,int maxZ,int y){for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)cells.put(new TerrainPlan.Pos(x,y,z),Material.GRASS_BLOCK);}
    }
    static TerrainPlan island(int y){
        TerrainPlan plan=new TerrainPlan(4,ShardSpec.Land.FOREST,123,-2,5);
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){plan.put(x,y,z,Material.GRASS_BLOCK);plan.surface.put(TerrainPlan.key(x,z),y);}
        return plan;
    }
    public static void main(String[] args){run();}
    static void run(){
        Ground world=new Ground();world.land(-25,-18,-3,3,0);TerrainPlan first=island(0);
        var before=new HashMap<>(world.cells);var original=new HashMap<>(first.blocks);
        var crossing=ConnectionBridge.plan(first,-22,0,0,world);
        verify(first,world,crossing);
        check(world.cells.equals(before)&&first.blocks.equals(original),"Preview planning must not mutate either island");
        check(!crossing.anchors().isEmpty(),"The first crossing needs an existing shoreline anchor");
        // Move coordinates to a second target; the first generated island is now source land.
        Ground next=new Ground();
        for(var entry:before.entrySet()){var p=entry.getKey();next.cells.put(new TerrainPlan.Pos(p.x()-20,p.y(),p.z()),entry.getValue());}
        for(var entry:crossing.blocks().entrySet()){var p=entry.getKey();next.cells.put(new TerrainPlan.Pos(p.x()-20,p.y(),p.z()),entry.getValue());}
        for(var entry:first.blocks.entrySet()){var p=entry.getKey();next.cells.put(new TerrainPlan.Pos(p.x()-20,p.y(),p.z()),entry.getValue());}
        TerrainPlan second=island(0);var expansion=ConnectionBridge.plan(second,-18,0,0,next);verify(second,next,expansion);
        check(expansion.path().getFirst().x()>=-24&&expansion.path().getFirst().x()<=-16,"Second crossing starts on the first Shard");
        Ground desert=new Ground();desert.land(-25,-18,-3,3,0);desert.cells.replaceAll((position,material)->Material.SAND);
        var desertBridge=ConnectionBridge.plan(island(0),-22,0,0,desert);verify(island(0),desert,desertBridge);
        Ground diagonal=new Ground();diagonal.land(-24,-18,-24,-18,0);
        var diagonalCrossing=ConnectionBridge.plan(island(3),-21,0,-21,diagonal);verify(island(3),diagonal,diagonalCrossing);
        check(diagonalCrossing.path().getFirst().y()!=diagonalCrossing.path().getLast().y(),"The planner supports stepped elevation changes");
        Ground blocked=new Ground();blocked.land(-25,-18,-3,3,0);for(int z=-5;z<=5;z++)blocked.cells.put(new TerrainPlan.Pos(-12,1,z),Material.CHEST);
        reject(()->ConnectionBridge.plan(island(0),-22,0,0,blocked),"Headroom obstruction must reject placement");
        Ground floorBlocked=new Ground();floorBlocked.land(-25,-18,-3,3,0);for(int z=-5;z<=5;z++)floorBlocked.cells.put(new TerrainPlan.Pos(-12,0,z),Material.CHEST);
        reject(()->ConnectionBridge.plan(island(0),-22,0,0,floorBlocked),"An occupied deck block must never be overwritten");
        Ground remote=new Ground();remote.land(-110,-100,-2,2,0);
        reject(()->ConnectionBridge.plan(island(0),-105,0,0,remote),"Oversized bridges must be rejected");
        reject(()->ConnectionBridge.plan(island(12),-22,0,0,world),"Steep bridges must be rejected");
        reject(()->ConnectionBridge.plan(island(0),-22,0,0,new Ground()),"Floating placement needs an existing foothold");
        Ground isolated=new Ground();isolated.land(-25,-22,-2,2,0);isolated.land(-8,-6,-2,2,0);
        var connected=ConnectionBridge.plan(island(0),-24,0,0,isolated);
        check(connected.path().getFirst().x()<=-22,"Unconnected nearby land must not replace the player's source island");
        System.out.println("Bridge verification passed: "+checks+" checks (first island, second Shard, diagonal/elevated paths, protected land, obstructions and limits)");
    }
    private static void verify(TerrainPlan island,Ground ground,ConnectionBridge.Result crossing){
        check(!crossing.blocks().isEmpty(),"A separated Shard requires generated bridge blocks");
        for(var p:crossing.blocks().keySet()){
            check(ground.air(p.x(),p.y(),p.z()),"Every bridge write must target empty space");
            check(!island.blocks.containsKey(p),"Bridge must preserve the original Shard pattern");
        }
        for(var p:crossing.clearance())check(ground.air(p.x(),p.y(),p.z()),"Reserved bridge clearance must be unobstructed");
        for(var p:crossing.anchors().keySet())check(!ground.air(p.x(),p.y(),p.z()),"Existing anchor blocks are recorded separately from writes");
        for(int i=0;i<crossing.path().size();i++){
            var p=crossing.path().get(i);var deck=new TerrainPlan.Pos(p.x(),p.y()-1,p.z());
            check(!ground.air(deck.x(),deck.y(),deck.z())||island.blocks.containsKey(deck)||crossing.blocks().containsKey(deck),"Each path position has a supporting deck");
            check(!crossing.blocks().containsKey(p)&&!island.blocks.containsKey(p),"Preview path must sit above the actual deck");
            if(i>0){var previous=crossing.path().get(i-1);check(Math.abs(p.x()-previous.x())+Math.abs(p.z()-previous.z())==1&&Math.abs(p.y()-previous.y())<=1,"Path must have connected cardinal steps");}
        }
    }
    private static void reject(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalStateException expected){rejected=true;}check(rejected,message);}
}
