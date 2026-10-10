package dev.excedra.itemgen;
import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;

public class StarterPreviewExport {
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);Gson gson=new Gson();
        int[][] recipes={{0},{1},{2},{3},{4},{5},{6},{7},{8},{1,6},{0,4},{5,7},{3,8},{0,0,0,0,0}};
        String[] names={"Crownstone","Voidroot","Worldscar","Starfall","Cascade","Titanbone","Emberforge","Prismheart","Monolith","Emberroot Crucible","Crown of Falling Tides","Prismatic Leviathan","Astral Helix","Crownstone x5"};
        for(int n=0;n<recipes.length;n++){
            List<Long> counts=new ArrayList<>(Collections.nCopies(9,0L));for(int i:recipes[n])counts.set(i,counts.get(i)+1);
            StarterRecipe recipe=new StarterRecipe(counts,new UUID(6200,n+52));TerrainPlan p=StarterRealms.plan(recipe);
            List<Object> blocks=new ArrayList<>();for(var e:p.blocks.entrySet())blocks.add(List.of(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue().name()));
            Files.writeString(out.resolve(String.format("%02d.json",n)),gson.toJson(Map.of("name",names[n],"blocks",blocks)));
        }
        for(int n=0;n<4;n++){
            var counts=n==3?List.of(5L,5L,3L,2L,5L,1L,40L,2L,1L):List.of(40L,40L,3L,2L,5L,1L,5L,2L,2L);
            StarterRecipe recipe=new StarterRecipe(counts,new UUID(8500,991+n));TerrainPlan p=StarterRealms.plan(recipe);
            List<Object> blocks=new ArrayList<>();for(var e:p.blocks.entrySet())blocks.add(List.of(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue().name()));
            Files.writeString(out.resolve(String.format("%02d.json",14+n)),gson.toJson(Map.of("name",n==3?"Forge-dominant / 64 units":"Same 100-unit recipe / world "+(n+1),"blocks",blocks)));
        }
        List<StarterRecipe> special=new ArrayList<>();
        for(int n=0;n<3;n++)special.add(new StarterRecipe(Collections.nCopies(9,1L),new UUID(1900,80+n)));
        special.add(new StarterRecipe(List.of(0L,3L,0L,0L,1L,0L,0L,0L,0L),new UUID(1900,89),Map.of("snow_t4_shiny",2L)));
        special.add(new StarterRecipe(List.of(1L,0L,0L,0L,0L,0L,0L,0L,0L),new UUID(1900,90),Map.of("desert_t5_rainbow",1L)));
        String[] labels={"All nine / Atlas I","All nine / Atlas II","All nine / Atlas III","Voidroot + Cascade + Snow","Crownstone + Rainbow Desert"};
        for(int n=0;n<special.size();n++){
            TerrainPlan p=StarterRealms.plan(special.get(n));List<Object> blocks=new ArrayList<>();for(var e:p.blocks.entrySet())blocks.add(List.of(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue().name()));
            Files.writeString(out.resolve(String.format("%02d.json",18+n)),gson.toJson(Map.of("name",labels[n],"blocks",blocks)));
        }
    }
}
