package dev.excedra.itemgen;

import java.util.*;

/** Coherent five-block terrain sectors; visual samples belong only to removable exposed terrain. */
final class DissolvePattern {
    final List<GeneratedLand.Cell> order;
    final Set<TerrainPlan.Pos> visuals;
    DissolvePattern(GeneratedLand land,List<GeneratedLand.Cell> eligible,int samples,long seed){
        double phase=new Random(seed).nextDouble()*Math.PI*2;
        Comparator<GeneratedLand.Cell> sweep=Comparator.comparingDouble((GeneratedLand.Cell c)->score(land,c.pos(),phase)).thenComparingInt(c->c.pos().x()).thenComparingInt(c->c.pos().z()).thenComparingInt(c->c.pos().y());
        order=eligible.stream().sorted(sweep).toList();Set<TerrainPlan.Pos> cells=new HashSet<>();eligible.forEach(c->cells.add(c.pos()));
        List<GeneratedLand.Cell> exposed=order.stream().filter(c->visual(c.expected())&&exposed(c.pos(),cells)).toList();
        Set<TerrainPlan.Pos> selected=new HashSet<>();int n=Math.min(Math.max(0,samples),exposed.size());for(int i=0;i<n;i++)selected.add(exposed.get((int)((long)i*exposed.size()/n)).pos());visuals=Set.copyOf(selected);
    }
    private static double score(GeneratedLand land,TerrainPlan.Pos p,double phase){
        int sx=Math.floorDiv(p.x()-land.ox,5),sz=Math.floorDiv(p.z()-land.oz,5);
        double angle=Math.atan2(sz+.5,sx+.5)+phase;
        return (Math.floorMod((int)Math.floor(angle*4/Math.PI),8)*10000)+Math.hypot(sx,sz)*100+land.maxY-p.y();
    }
    private static boolean exposed(TerrainPlan.Pos p,Set<TerrainPlan.Pos> cells){return !cells.contains(new TerrainPlan.Pos(p.x()+1,p.y(),p.z()))||!cells.contains(new TerrainPlan.Pos(p.x()-1,p.y(),p.z()))||!cells.contains(new TerrainPlan.Pos(p.x(),p.y()+1,p.z()))||!cells.contains(new TerrainPlan.Pos(p.x(),p.y()-1,p.z()))||!cells.contains(new TerrainPlan.Pos(p.x(),p.y(),p.z()+1))||!cells.contains(new TerrainPlan.Pos(p.x(),p.y(),p.z()-1));}
    static boolean visual(String state){String k=GeneratedLand.Cell.key(state).toLowerCase(Locale.ROOT);return !k.endsWith("water")&&!k.endsWith("lava")&&!k.endsWith("air");}
    static int releaseCount(int total,int elapsed,int duration){return (int)Math.min(total,Math.ceil(total*Math.clamp((elapsed-duration*.70)/(duration*.30),0,1)));}
}
