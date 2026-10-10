package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/** Seeded plan built before any world mutation. Coordinates are local to the island fragment. */
final class TerrainPlan {
    record Pos(int x, int y, int z) {}
    final LinkedHashMap<Pos, Material> blocks = new LinkedHashMap<>();
    final Map<Pos,String> states=new HashMap<>();
    final Map<Pos,String> bridgeAnchors=new LinkedHashMap<>();
    final Set<Pos> bridgeClearance=new LinkedHashSet<>();
    final List<Pos> bridgePath=new ArrayList<>();
    final Map<Long,Integer> surface = new HashMap<>();
    final int radius;
    private final int customTop;
    private final int customBottom;
    final long seed;
    final ShardSpec.Land land;
    TerrainPlan(int radius, ShardSpec.Land land, long seed, int tier) {
        this.radius = radius; this.land = land; this.seed=seed;customTop=-1;customBottom=-radius;
        Random rng = new Random(seed);
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            double distance=Math.sqrt(x*x+z*z)/(double)radius;
            if(distance > 1.0 + .035*Math.sin(x*.8+z*.6)) continue;
            int top=(int)Math.round(Math.sin(x*.42+seed%17)*.8+Math.cos(z*.35)*.8);
            int bottom=-2-(int)Math.round((1-distance)*(radius*.75));
            surface.put(key(x,z),top);
            for(int y=bottom;y<=top;y++) put(x,y,z,y==top?top():y>=top-2?soil():stone());
            if(land==ShardSpec.Land.OCEAN && distance<.72) { put(x,top,z,Material.PRISMARINE); put(x,top+1,z,Material.WATER); }
        }
        int count=Math.max(2,tier*2);
        for(int i=0;i<count;i++) {
            int x=rng.nextInt(radius*2-3)-radius+2, z=rng.nextInt(radius*2-3)-radius+2;
            Integer y=surface.get(key(x,z)); if(y==null) continue;
            switch(land) {
                case FOREST,JUNGLE -> tree(x,y+1,z,land==ShardSpec.Land.JUNGLE?Material.JUNGLE_LOG:Material.OAK_LOG,land==ShardSpec.Land.JUNGLE?Material.JUNGLE_LEAVES:Material.OAK_LEAVES,tier>=4?5:3);
                case SNOW -> tree(x,y+1,z,Material.SPRUCE_LOG,Material.SPRUCE_LEAVES,4);
                case DESERT -> { for(int h=1;h<=3;h++) put(x,y+h,z,Material.CACTUS); }
                case CRYSTAL -> pillar(x,y+1,z,2+rng.nextInt(4),Material.AMETHYST_BLOCK,Material.BUDDING_AMETHYST);
                case RUINS -> pillar(x,y+1,z,3+rng.nextInt(3),Material.MOSSY_STONE_BRICKS,Material.CHISELED_STONE_BRICKS);
                case NETHER -> pillar(x,y+1,z,3+rng.nextInt(3),Material.BLACKSTONE,Material.CRYING_OBSIDIAN);
                case END -> pillar(x,y+1,z,3+rng.nextInt(4),Material.OBSIDIAN,Material.END_ROD);
                case MUSHROOM -> mushroom(x,y+1,z,3,Material.RED_MUSHROOM_BLOCK);
                case VOLCANIC -> pillar(x,y+1,z,2+rng.nextInt(3),Material.BASALT,Material.MAGMA_BLOCK);
                case OCEAN -> pillar(x,y+2,z,1,Material.PRISMARINE,Material.SEA_LANTERN);
                case BADLANDS -> pillar(x,y+1,z,3+rng.nextInt(3),Material.TERRACOTTA,Material.RED_TERRACOTTA);
            }
        }
        if(land==ShardSpec.Land.VOLCANIC){
            int height=2+tier;
            for(int h=0;h<height;h++){int width=Math.max(1,3-h/2);for(int x=-width;x<=width;x++)for(int z=-width;z<=width;z++)if(x*x+z*z<=width*width+1)put(x,h+2,z,Material.BASALT);}
            put(0,height+1,0,Material.LAVA);
        }
        if(tier>=4 && (land==ShardSpec.Land.FOREST || land==ShardSpec.Land.JUNGLE || land==ShardSpec.Land.OCEAN)) {
            int x=radius-2; Integer y=surface.get(key(x,0));
            if(y!=null) { for(int h=y+1;h>=-radius;h--) put(x,h,0,Material.WATER); }
        }
        if(tier==5 && land==ShardSpec.Land.RUINS) {
            for(int x=-3;x<=3;x++) put(x,5,0,Material.STONE_BRICKS);
            pillar(-3,1,0,4,Material.MOSSY_STONE_BRICKS,Material.STONE_BRICKS);
            pillar(3,1,0,4,Material.MOSSY_STONE_BRICKS,Material.STONE_BRICKS);
        }
        if(tier==5 && rng.nextDouble()<.12 && EnumSet.of(ShardSpec.Land.FOREST,ShardSpec.Land.JUNGLE,ShardSpec.Land.SNOW,ShardSpec.Land.DESERT,ShardSpec.Land.END).contains(land)){
            Material relic=land==ShardSpec.Land.DESERT?Material.CUT_SANDSTONE:land==ShardSpec.Land.END?Material.PURPUR_BLOCK:Material.MOSSY_STONE_BRICKS;
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)put(x,2,z,relic);
            pillar(-2,3,0,4,relic,relic);pillar(2,3,0,4,relic,relic);for(int x=-2;x<=2;x++)put(x,7,0,relic);
        }
    }
    TerrainPlan(SchematicData.Data data,ShardSpec.Land land){
        this.radius=Math.max(data.width(),data.length())/2+1;this.land=land;this.seed=0;customTop=data.height()-1;customBottom=0;
        for(var e:data.states().entrySet()){var block=org.bukkit.Bukkit.createBlockData(e.getValue());blocks.put(e.getKey(),block.getMaterial());states.put(e.getKey(),block.getAsString());}
    }
    TerrainPlan(int radius,ShardSpec.Land land,long seed,int min,int max){this.radius=radius;this.land=land;this.seed=seed;customBottom=min;customTop=max;}
    TerrainPlan rotated(PlacementRotation rotation) {
        Objects.requireNonNull(rotation, "rotation");
        if(!bridgePath.isEmpty())throw new IllegalStateException("Rotate the Shard before attaching its bridge.");
        TerrainPlan result=new TerrainPlan(radius,land,seed,minY(),maxY());
        for(var entry:blocks.entrySet()) {
            Pos p=entry.getKey();var offset=rotation.apply(p.x(),p.y(),p.z());
            Pos target=new Pos(offset.x(),offset.y(),offset.z());
            result.blocks.put(target,entry.getValue());
            String state=states.get(p);
            if(state!=null) {
                var data=org.bukkit.Bukkit.createBlockData(state);
                data.rotate(org.bukkit.block.structure.StructureRotation.valueOf(rotation.name()));
                result.states.put(target,data.getAsString());
            }
        }
        for(var entry:surface.entrySet()) {
            int x=(int)(entry.getKey()>>32),z=(int)(long)entry.getKey();
            var offset=rotation.apply(x,0,z);
            result.surface.put(key(offset.x(),offset.z()),entry.getValue());
        }
        return result;
    }
    int minY(){return customBottom;}
    int maxY(){return customTop<0?9:customTop;}
    static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    void put(int x,int y,int z,Material m){blocks.put(new Pos(x,y,z),m);}
    void pillar(int x,int y,int z,int height,Material stem,Material cap){for(int h=0;h<height;h++)put(x,y+h,z,stem);put(x,y+height,z,cap);}
    void tree(int x,int y,int z,Material log,Material leaves,int height){
        for(int h=0;h<height;h++)put(x,y+h,z,log);
        for(int h=height-2;h<=height;h++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<4)blocks.putIfAbsent(new Pos(x+dx,y+h,z+dz),leaves);
        put(x,y+height+1,z,leaves);
    }
    void mushroom(int x,int y,int z,int height,Material cap){for(int h=0;h<height;h++)put(x,y+h,z,Material.MUSHROOM_STEM);for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)put(x+dx,y+height,z+dz,cap);}
    Material top(){return switch(land){case SNOW->Material.SNOW_BLOCK;case DESERT->Material.SAND;case VOLCANIC->Material.BLACKSTONE;case OCEAN->Material.SAND;case CRYSTAL->Material.CALCITE;case RUINS->Material.MOSS_BLOCK;case NETHER->Material.CRIMSON_NYLIUM;case END->Material.END_STONE;case MUSHROOM->Material.MYCELIUM;case BADLANDS->Material.RED_SANDSTONE;default->Material.GRASS_BLOCK;};}
    Material soil(){return switch(land){case DESERT,OCEAN->Material.SANDSTONE;case BADLANDS->Material.ORANGE_TERRACOTTA;case NETHER->Material.NETHERRACK;case END->Material.END_STONE;default->Material.DIRT;};}
    Material stone(){return switch(land){case VOLCANIC,NETHER->Material.BASALT;case BADLANDS->Material.TERRACOTTA;case END->Material.END_STONE;default->Material.STONE;};}
    Biome biome(){return biomeFor(land);}
    static Biome biomeFor(ShardSpec.Land land){return switch(land){case FOREST->Biome.FOREST;case SNOW->Biome.SNOWY_TAIGA;case DESERT->Biome.DESERT;case VOLCANIC->Biome.BASALT_DELTAS;case OCEAN->Biome.WARM_OCEAN;case CRYSTAL->Biome.DRIPSTONE_CAVES;case RUINS->Biome.PLAINS;case NETHER->Biome.CRIMSON_FOREST;case END->Biome.THE_END;case MUSHROOM->Biome.MUSHROOM_FIELDS;case JUNGLE->Biome.JUNGLE;case BADLANDS->Biome.BADLANDS;};}
}
