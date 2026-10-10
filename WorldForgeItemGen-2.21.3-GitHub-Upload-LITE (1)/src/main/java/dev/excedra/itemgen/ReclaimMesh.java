package dev.excedra.itemgen;
import java.util.*;

/** Exact greedy cuboids of recorded blocks; capped, evenly sampled only when a layout is complex. */
final class ReclaimMesh {
    record Box(int x,int y,int z,int width,int height,int depth,String data){long volume(){return (long)width*height*depth;}}
    static List<Box> build(List<GeneratedLand.Cell> cells,int limit){
        Map<TerrainPlan.Pos,String> remaining=new HashMap<>();for(var cell:cells)if(DissolvePattern.visual(cell.expected()))remaining.put(cell.pos(),cell.expected());
        List<TerrainPlan.Pos> order=new ArrayList<>(remaining.keySet());order.sort(Comparator.comparingInt(TerrainPlan.Pos::y).thenComparingInt(TerrainPlan.Pos::z).thenComparingInt(TerrainPlan.Pos::x));List<Box> boxes=new ArrayList<>();
        for(var pos:order){String data=remaining.get(pos);if(data==null)continue;int w=1,d=1,h=1;
            // Non-full block geometry is kept as individual instances rather than stretched.
            String key=GeneratedLand.Cell.key(data).toLowerCase(Locale.ROOT);boolean cube=!key.matches(".*(stairs|slab|fence|wall|pane|bars|chain|carpet|bud|roots|sapling|torch|flower|anvil|sign|banner|trapdoor|door|snow|azalea|mushroom|grass|vine|rod|button|pressure_plate|chest)$");
            if(cube){while(data.equals(remaining.get(new TerrainPlan.Pos(pos.x()+w,pos.y(),pos.z()))))w++;
                while(layer(remaining,pos,w,1,d,data))d++;
                while(layer(remaining,pos,w,d,h,data,true))h++;
            }
            for(int dx=0;dx<w;dx++)for(int dy=0;dy<h;dy++)for(int dz=0;dz<d;dz++)remaining.remove(new TerrainPlan.Pos(pos.x()+dx,pos.y()+dy,pos.z()+dz));
            boxes.add(new Box(pos.x(),pos.y(),pos.z(),w,h,d,data));
        }
        if(boxes.size()<=limit)return List.copyOf(boxes);
        // Retain large silhouettes plus spatially distributed small architectural details.
        boxes.sort(Comparator.comparingLong(Box::volume).reversed());List<Box> result=new ArrayList<>(boxes.subList(0,limit/2));
        List<Box> details=new ArrayList<>(boxes.subList(limit/2,boxes.size()));details.sort(Comparator.comparingInt(Box::y).thenComparingInt(Box::z).thenComparingInt(Box::x));
        int n=limit-result.size();for(int i=0;i<n;i++)result.add(details.get((int)((long)i*details.size()/n)));return List.copyOf(result);
    }
    private static boolean layer(Map<TerrainPlan.Pos,String> map,TerrainPlan.Pos p,int w,int unused,int dz,String data){for(int dx=0;dx<w;dx++)if(!data.equals(map.get(new TerrainPlan.Pos(p.x()+dx,p.y(),p.z()+dz))))return false;return true;}
    private static boolean layer(Map<TerrainPlan.Pos,String> map,TerrainPlan.Pos p,int w,int depth,int dy,String data,boolean vertical){for(int dx=0;dx<w;dx++)for(int dz=0;dz<depth;dz++)if(!data.equals(map.get(new TerrainPlan.Pos(p.x()+dx,p.y()+dy,p.z()+dz))))return false;return true;}
    static double shrink(int tick){double t=Math.clamp(tick/80.0,0,1);return 1-.985*(t*t*(3-2*t));}
}
