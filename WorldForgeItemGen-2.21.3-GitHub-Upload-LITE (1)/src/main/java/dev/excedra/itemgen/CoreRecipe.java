package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

/** Versioned Woodland recipe: its seed is the exact pattern identity. */
record CoreRecipe(Influence influence,Influence secondary,long seed) {
    enum Influence {
        ROOTS("Rootwoven Woodland",Material.MOSS_BLOCK,8,"Roots bind the woodland together."),
        MASONRY("Forgotten Woodland",Material.MOSSY_STONE_BRICKS,12,"Roots can reclaim what builders leave behind."),
        HIGHLAND("Highland Woodland",Material.TUFF,16,"Weathered stone remembers the heights."),
        WATER("Springfed Woodland",Material.PRISMARINE_CRYSTALS,4,"Water gathers where living earth shelters it."),
        CRYSTAL("Crystalroot Woodland",Material.AMETHYST_SHARD,8,"Minerals can take root beneath a living forest.");
        final String title,clue;final Material material;final int amount;
        Influence(String title,Material material,int amount,String clue){this.title=title;this.material=material;this.amount=amount;this.clue=clue;}
    }
    CoreRecipe(Influence influence,long seed){this(influence,null,seed);}
    CoreRecipe {Objects.requireNonNull(influence);if(secondary==influence)throw new IllegalArgumentException("Choose two different Influences.");if(secondary!=null&&secondary.ordinal()<influence.ordinal()){Influence swap=influence;influence=secondary;secondary=swap;}}
    boolean has(Influence option){return influence==option||secondary==option;}
    boolean sameFamily(CoreRecipe other){return influence==other.influence&&secondary==other.secondary;}
    String title(){if(secondary==null)return influence.title;return switch(influence){
        case ROOTS->switch(secondary){case MASONRY->"Rootclaimed Ruins";case HIGHLAND->"Terraced Rootwood";case WATER->"Springwoven Roots";case CRYSTAL->"Crystalline Rootwood";default->throw new AssertionError();};
        case MASONRY->switch(secondary){case HIGHLAND->"Highland Sanctuary";case WATER->"Springfed Sanctuary";case CRYSTAL->"Crystal Sanctuary";default->throw new AssertionError();};
        case HIGHLAND->secondary==Influence.WATER?"Terraced Springs":"Crystal Highlands";
        case WATER->"Prismatic Springs";
        default->throw new AssertionError();};}
    String encode(){return secondary==null?"1:"+influence.name()+":"+seed:"2:"+influence.name()+":"+secondary.name()+":"+seed;}
    static CoreRecipe decode(String encoded){
        String[] parts=encoded.split(":",-1);
        if(parts.length==3&&parts[0].equals("1"))return new CoreRecipe(Influence.valueOf(parts[1]),Long.parseLong(parts[2]));
        if(parts.length==4&&parts[0].equals("2"))return new CoreRecipe(Influence.valueOf(parts[1]),Influence.valueOf(parts[2]),Long.parseLong(parts[3]));
        throw new IllegalArgumentException("Unsupported Core recipe version");
    }
    Map<Material,Integer> costs(){
        Map<Material,Integer> costs=new LinkedHashMap<>();costs.put(Material.OAK_SAPLING,8);costs.put(Material.DIRT,16);costs.put(Material.STONE,8);
        costs.merge(influence.material,influence.amount,Integer::sum);if(secondary!=null)costs.merge(secondary.material,secondary.amount,Integer::sum);return costs;
    }
    TerrainPlan plan(){
        if(secondary!=null)return combinedPlan();
        TerrainPlan base=new TerrainPlan(7,ShardSpec.Land.FOREST,seed,2);
        TerrainPlan result=new TerrainPlan(7,ShardSpec.Land.FOREST,seed,-7,14);
        if(influence==Influence.HIGHLAND){
            for(var entry:base.blocks.entrySet()){
                var p=entry.getKey();int rise=Math.floorDiv(p.x()+7,4);
                result.put(p.x(),p.y()+rise,p.z(),entry.getValue());
            }
            for(var entry:base.surface.entrySet()){
                int x=(int)(entry.getKey()>>32),z=(int)(long)entry.getKey(),rise=Math.floorDiv(x+7,4);
                result.surface.put(entry.getKey(),entry.getValue()+rise);
                if(x%4==0)for(int y=entry.getValue()-1;y<=entry.getValue()+rise;y++)result.put(x,y,z,Material.TUFF);
            }
            return result;
        }
        result.blocks.putAll(base.blocks);result.surface.putAll(base.surface);
        switch(influence){
            case ROOTS -> {
                for(int arm=0;arm<4;arm++)for(int n=0;n<=5;n++){
                    int x=arm<2?(arm==0?n:-n):0,z=arm>=2?(arm==2?n:-n):0;
                    Integer top=result.surface.get(TerrainPlan.key(x,z));if(top==null)continue;
                    result.put(x,top,z,Material.ROOTED_DIRT);
                    result.put(x,top+1,z,n%3==0?Material.MOSS_BLOCK:Material.OAK_LOG);
                }
            }
            case MASONRY -> {
                int floor=3;
                result.blocks.keySet().removeIf(p->Math.abs(p.x())<=3&&Math.abs(p.z())<=3&&p.y()>floor);
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
                    int originalTop=result.surface.getOrDefault(TerrainPlan.key(x,z),0);
                    for(int y=originalTop+1;y<floor;y++)result.put(x,y,z,Material.STONE_BRICKS);
                    result.put(x,floor,z,Math.floorMod(x+z,3)==0?Material.MOSSY_STONE_BRICKS:Material.STONE_BRICKS);
                    result.surface.put(TerrainPlan.key(x,z),floor);
                }
                result.pillar(-3,floor+1,-3,3,Material.MOSSY_STONE_BRICKS,Material.CHISELED_STONE_BRICKS);
                result.pillar(3,floor+1,3,2,Material.CRACKED_STONE_BRICKS,Material.MOSSY_STONE_BRICKS);
                for(int x=-3;x<=2;x++)result.put(x,floor+1,-3,Material.CRACKED_STONE_BRICKS);
            }
            case WATER -> {
                result.blocks.keySet().removeIf(p->p.x()*p.x()+p.z()*p.z()<=12&&p.y()>=-1);
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(x*x+z*z<=12){
                    result.put(x,-2,z,Material.STONE);
                    result.put(x,-1,z,x*x+z*z<=4?Material.PRISMARINE:Material.CLAY);
                    result.put(x,0,z,x*x+z*z<=4?Material.WATER:Material.GRASS_BLOCK);
                    result.surface.put(TerrainPlan.key(x,z),0);
                }
            }
            case CRYSTAL -> {
                for(int x=-5;x<=5;x++){
                    int z=Math.floorMod(x,3)-1;Integer top=result.surface.get(TerrainPlan.key(x,z));if(top==null)continue;
                    result.put(x,top-1,z,Material.AMETHYST_BLOCK);
                    if(x%3==0){result.put(x,top,z,Material.CALCITE);result.put(x,top+1,z,Material.AMETHYST_BLOCK);}
                }
            }
            case HIGHLAND -> throw new AssertionError();
        }
        return result;
    }
    private TerrainPlan combinedPlan(){
        TerrainPlan result=new CoreRecipe(has(Influence.HIGHLAND)?Influence.HIGHLAND:Influence.ROOTS,seed).plan();
        // Begin with plain woodland unless terraces are required; do not import a single recipe's roots.
        if(!has(Influence.HIGHLAND)){
            TerrainPlan base=new TerrainPlan(7,ShardSpec.Land.FOREST,seed,2);
            result=new TerrainPlan(7,ShardSpec.Land.FOREST,seed,-7,14);result.blocks.putAll(base.blocks);result.surface.putAll(base.surface);
        }
        if(has(Influence.MASONRY)){
            int floor=3;
            if(has(Influence.HIGHLAND))for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)floor=Math.max(floor,result.surface.getOrDefault(TerrainPlan.key(x,z),0));
            floor=Math.min(floor,8);final int level=floor;
            result.blocks.keySet().removeIf(p->Math.abs(p.x())<=2&&Math.abs(p.z())<=2&&p.y()>level);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
                int top=result.surface.getOrDefault(TerrainPlan.key(x,z),0);
                for(int y=top+1;y<floor;y++)result.put(x,y,z,Material.STONE_BRICKS);
                result.put(x,floor,z,has(Influence.CRYSTAL)&&x==z?Material.CALCITE:Material.MOSSY_STONE_BRICKS);result.surface.put(TerrainPlan.key(x,z),floor);
            }
            result.pillar(-2,floor+1,-2,3,Material.MOSSY_STONE_BRICKS,Material.CHISELED_STONE_BRICKS);
            result.pillar(2,floor+1,2,2,Material.CRACKED_STONE_BRICKS,Material.CHISELED_STONE_BRICKS);
        }
        if(has(Influence.WATER)){
            int h=Math.max(0,Math.min(8,result.surface.getOrDefault(TerrainPlan.key(0,4),0)));final int level=h;
            result.blocks.keySet().removeIf(p->p.x()*p.x()+(p.z()-4)*(p.z()-4)<=4&&p.y()>=level-1);
            for(int x=-2;x<=2;x++)for(int dz=-2;dz<=2;dz++)if(x*x+dz*dz<=4){int z=4+dz;
                int originalTop=result.surface.getOrDefault(TerrainPlan.key(x,z),h-2);
                for(int y=originalTop+1;y<h-2;y++)result.put(x,y,z,Material.STONE);
                result.put(x,h-2,z,Material.STONE);result.put(x,h-1,z,has(Influence.CRYSTAL)?Material.PRISMARINE:Material.CLAY);
                result.put(x,h,z,x*x+dz*dz<=1?Material.WATER:Material.STONE_BRICKS);result.surface.put(TerrainPlan.key(x,z),h);
            }
        }
        if(has(Influence.ROOTS))for(int z=-4;z<=4;z++){
            Integer top=result.surface.get(TerrainPlan.key(-4,z));if(top==null)continue;
            result.put(-4,top,z,Material.ROOTED_DIRT);result.put(-4,top+1,z,z%3==0?Material.MOSS_BLOCK:Material.OAK_LOG);
            if(has(Influence.CRYSTAL)&&z%2==0)result.put(-4,top-1,z,Material.AMETHYST_BLOCK);
        }
        if(has(Influence.CRYSTAL))for(int x=4;x<=6;x++)for(int z=-2;z<=2;z++){
            Integer top=result.surface.get(TerrainPlan.key(x,z));if(top==null)continue;
            result.put(x,top-1,z,Material.AMETHYST_BLOCK);if((x+z)%3==0){result.put(x,top,z,Material.CALCITE);result.put(x,top+1,z,Material.AMETHYST_BLOCK);}
        }
        return result;
    }
}
