package dev.excedra.itemgen;

import java.nio.file.Path;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Exact recorded generation cells; never identifies a landmass by a loose world-wide radius. */
final class GeneratedLand {
    static boolean completedIn(YamlConfiguration record,String world,UUID worldId,UUID realm){
        return "COMPLETE".equals(record.getString("status"))&&world.equals(record.getString("world"))&&realm.toString().equals(record.getString("realm"))&&(!record.contains("world-uuid")||worldId.toString().equals(record.getString("world-uuid")));
    }
    static void requireDisjoint(Collection<GeneratedLand> lands){
        Set<TerrainPlan.Pos> occupied=new HashSet<>();for(var land:lands)for(var cell:land.cells)if(!occupied.add(cell.pos()))throw new IllegalStateException("Overlapping records require manual review; nothing was removed.");
    }
    record Cell(TerrainPlan.Pos pos,String original,String expected) {
        boolean removable(String current){
            if(!expected.contains(":"))return key(current).equals("minecraft:"+expected.toLowerCase(Locale.ROOT));
            return normalized(current).equals(normalized(expected));
        }
        static String key(String data){int p=data.indexOf('[');return p<0?data:data.substring(0,p);}
        static String normalized(String data){
            String key=key(data);
            // Leaf distance, grass snow cover, and flowing-fluid levels can change naturally.
            if(key.equals("minecraft:water")||key.equals("minecraft:lava"))return key;
            return data.replaceAll("distance=\\d+","distance=*").replaceAll("snowy=(true|false)","snowy=*");
        }
    }
    final Path path;final String world,worldUuid;final UUID realm;final int ox,oy,oz;
    final List<Cell> cells;final Map<TerrainPlan.Pos,String> biomes;
    final Set<TerrainPlan.Pos> positions=new HashSet<>();final Set<Long> columns=new HashSet<>();
    int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
    GeneratedLand(Path path,YamlConfiguration y){
        this.path=path;world=Objects.requireNonNull(y.getString("world"));worldUuid=y.getString("world-uuid","");realm=UUID.fromString(Objects.requireNonNull(y.getString("realm")));
        List<Integer> origin=y.getIntegerList("origin");if(origin.size()!=3)throw new IllegalArgumentException("Invalid generation origin");ox=origin.get(0);oy=origin.get(1);oz=origin.get(2);
        cells=new ArrayList<>();biomes=new LinkedHashMap<>();
        for(String line:y.getStringList("cells")){String[] parts=line.split("\\|",4);if(parts.length<3)throw new IllegalArgumentException("Invalid generation cell");TerrainPlan.Pos offset=parse(parts[0]);TerrainPlan.Pos p=new TerrainPlan.Pos(ox+offset.x(),oy+offset.y(),oz+offset.z());String before=Cell.key(parts[1]);if(!Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air").contains(before))throw new IllegalArgumentException("Refusing a generation record that overwrote original terrain");cells.add(new Cell(p,parts[1],parts.length==4&&!parts[3].isBlank()?parts[3]:parts[2]));positions.add(p);columns.add(TerrainPlan.key(p.x(),p.z()));minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());}
        if(cells.isEmpty())throw new IllegalArgumentException("No recorded generation cells");
        for(String line:y.getStringList("biomes")){String[] parts=line.split("\\|",2);if(parts.length!=2)throw new IllegalArgumentException("Invalid biome record");TerrainPlan.Pos p=parse(parts[0]);biomes.put(new TerrainPlan.Pos(ox+p.x(),oy+p.y(),oz+p.z()),parts[1]);}
    }
    static TerrainPlan.Pos parse(String text){String[] p=text.split(",");if(p.length!=3)throw new IllegalArgumentException("Invalid cell position");return new TerrainPlan.Pos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));}
    boolean standingOn(int x,int feetY,int z){return positions.contains(new TerrainPlan.Pos(x,feetY-1,z))||positions.contains(new TerrainPlan.Pos(x,feetY,z));}
    boolean occupies(int x,int y,int z){return columns.contains(TerrainPlan.key(x,z))&&y>=minY-2&&y<=maxY+3;}
    boolean column(int x,int z){return columns.contains(TerrainPlan.key(x,z));}
}
