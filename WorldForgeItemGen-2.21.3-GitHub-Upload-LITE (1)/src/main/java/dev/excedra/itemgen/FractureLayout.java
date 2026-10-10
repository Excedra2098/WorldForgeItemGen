package dev.excedra.itemgen;

import java.util.*;

/** Linked radial rifts spread across exposed top, side and underside cells of the recorded land. */
final class FractureLayout {
    static List<GeneratedLand.Cell> anchors(GeneratedLand land,List<GeneratedLand.Cell> eligible,int max,long seed){
        Set<TerrainPlan.Pos> positions=new HashSet<>();eligible.forEach(c->positions.add(c.pos()));double rotation=new Random(seed).nextDouble()*Math.PI*2;
        List<GeneratedLand.Cell> candidates=new ArrayList<>(),exposed=new ArrayList<>();
        for(var cell:eligible){if(!DissolvePattern.visual(cell.expected()))continue;var p=cell.pos();boolean edge=!positions.contains(new TerrainPlan.Pos(p.x()+1,p.y(),p.z()))||!positions.contains(new TerrainPlan.Pos(p.x()-1,p.y(),p.z()))||!positions.contains(new TerrainPlan.Pos(p.x(),p.y()+1,p.z()))||!positions.contains(new TerrainPlan.Pos(p.x(),p.y()-1,p.z()))||!positions.contains(new TerrainPlan.Pos(p.x(),p.y(),p.z()+1))||!positions.contains(new TerrainPlan.Pos(p.x(),p.y(),p.z()-1));if(!edge)continue;exposed.add(cell);
            double x=p.x()-land.ox,z=p.z()-land.oz,r=Math.hypot(x,z),a=Math.atan2(z,x)-rotation-.06*Math.sin(r*.5);if(r<1.5||Math.abs(Math.sin(a*3.5))*r/3.5<1.10)candidates.add(cell);
        }
        if(candidates.size()<max/2)candidates=exposed;
        candidates.sort(Comparator.comparingDouble((GeneratedLand.Cell c)->Math.hypot(c.pos().x()-land.ox,c.pos().z()-land.oz)).thenComparingInt(c->c.pos().y()));
        int n=Math.min(max,candidates.size());List<GeneratedLand.Cell> result=new ArrayList<>();for(int i=0;i<n;i++)result.add(candidates.get((int)((long)i*candidates.size()/n)));return List.copyOf(result);
    }
}
