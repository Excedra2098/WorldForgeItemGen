package dev.excedra.itemgen;

import java.util.*;

/** Pure geometry: fissures and removal order are derived only from removable recorded cells. */
final class CollapsePattern {
    record Crack(GeneratedLand.Cell cell,double onset){}
    final List<Crack> cracks;
    final List<GeneratedLand.Cell> order;
    final double cx,cy,cz,radius;
    CollapsePattern(GeneratedLand land,List<GeneratedLand.Cell> eligible,int limit,long seed){
        cx=land.ox+.5;cy=land.oy+.5;cz=land.oz+.5;
        radius=Math.max(2,Math.max(land.maxX-land.minX,land.maxZ-land.minZ)/2.0);
        Comparator<GeneratedLand.Cell> outward=Comparator.comparingDouble((GeneratedLand.Cell c)->distance(c.pos())).thenComparingInt(c->c.pos().x()).thenComparingInt(c->c.pos().z()).thenComparingInt(c->-c.pos().y());
        order=eligible.stream().sorted(outward).toList();
        Map<Long,GeneratedLand.Cell> top=new HashMap<>(),bottom=new HashMap<>();
        for(var cell:eligible){if(!solidVisual(cell.expected()))continue;long key=TerrainPlan.key(cell.pos().x(),cell.pos().z());top.merge(key,cell,(a,b)->a.pos().y()>=b.pos().y()?a:b);bottom.merge(key,cell,(a,b)->a.pos().y()<=b.pos().y()?a:b);}
        double rotation=new Random(seed).nextDouble()*Math.PI*2;
        List<GeneratedLand.Cell> fissures=top.values().stream().filter(c->onFissure(c.pos(),rotation)).sorted(outward).toList();
        if(fissures.isEmpty())fissures=top.values().stream().sorted(outward).toList();
        List<GeneratedLand.Cell> lower=bottom.values().stream().filter(c->onFissure(c.pos(),rotation)).sorted(outward).toList();
        List<Crack> selected=new ArrayList<>();Set<TerrainPlan.Pos> used=new HashSet<>();
        sample(fissures,Math.max(1,limit*3/4),selected,used);sample(lower,limit-selected.size(),selected,used);
        cracks=List.copyOf(selected);
    }
    private void sample(List<GeneratedLand.Cell> candidates,int count,List<Crack> selected,Set<TerrainPlan.Pos> used){
        int n=Math.min(count,candidates.size());for(int i=0;i<n;i++){var c=candidates.get((int)((long)i*candidates.size()/n));if(used.add(c.pos()))selected.add(new Crack(c,Math.min(.72,distance(c.pos())/radius*.65)));}
    }
    double distance(TerrainPlan.Pos pos){return Math.hypot(pos.x()+.5-cx,pos.z()+.5-cz);}
    private boolean onFissure(TerrainPlan.Pos pos,double rotation){
        double r=distance(pos);if(r<1.5)return true;
        double angle=Math.atan2(pos.z()+.5-cz,pos.x()+.5-cx)-rotation-.10*Math.sin(r*.6);
        double nearest=Math.abs(Math.sin(angle*3.5))*r/3.5;
        return nearest<.75+radius*.025;
    }
    static boolean solidVisual(String state){
        String key=GeneratedLand.Cell.key(state).toLowerCase(Locale.ROOT);return !key.endsWith("water")&&!key.endsWith("lava")&&!key.endsWith("air")&&!key.endsWith("_leaves")&&!key.endsWith("end_rod");
    }
    static float progress(double phase,double onset){return (float)Math.clamp((phase-onset)/Math.max(.1,1-onset),0,.95);}
}
