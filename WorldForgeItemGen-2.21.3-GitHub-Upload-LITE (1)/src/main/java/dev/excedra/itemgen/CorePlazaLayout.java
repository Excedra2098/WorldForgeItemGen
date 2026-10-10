package dev.excedra.itemgen;
import java.util.*;import java.io.*;
final class CorePlazaLayout {
 record Cell(TerrainPlan.Pos pos,String before,String after){}
 static List<Cell> cells(List<String> identities){for(String id:identities){String key=CoreTemplateAnchor.key(id);try(InputStream stream=CorePlazaLayout.class.getResourceAsStream("/core-plazas/"+key+".txt")){if(stream==null)continue;List<Cell> result=new ArrayList<>();for(String line:new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\n")){if(line.isBlank())continue;String[] p=line.strip().split("\\|",5);result.add(new Cell(new TerrainPlan.Pos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])),p[3],p[4]));}return List.copyOf(result);}catch(IOException e){throw new IllegalStateException("Cannot load the starter plaza repair layout.",e);}}return List.of();}
}
