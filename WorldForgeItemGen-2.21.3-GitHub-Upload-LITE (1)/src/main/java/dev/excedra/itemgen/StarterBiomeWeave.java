package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

/** Biome ingredients become weighted terrain regions and landmarks in starter architecture. */
final class StarterBiomeWeave {
    record Influence(ShardSpec.Land land,double power) {}
    static List<Influence> influences(StarterRecipe r){
        var powers=new EnumMap<ShardSpec.Land,Double>(ShardSpec.Land.class);
        for(var e:r.biomes().entrySet()){
            ShardSpec s=StarterRecipe.parseBiome(e.getKey());
            // Uncharted shards resolve reproducibly from the resulting relic's stored signature.
            var land=s.land()!=null?s.land():ShardSpec.Land.values()[Math.floorMod(r.seed()^e.getKey().hashCode(),ShardSpec.Land.values().length)];
            double variant=s.variant()==ShardSpec.Variant.RAINBOW?1.7:s.variant()==ShardSpec.Variant.SHINY?1.3:1;
            powers.merge(land,e.getValue().doubleValue()*s.tier()*variant,Double::sum);
        }
        return powers.entrySet().stream().map(e->new Influence(e.getKey(),e.getValue())).toList();
    }
    static ShardSpec.Land dominantLand(StarterRecipe r,ShardSpec.Land fallback){
        return influences(r).stream().max(Comparator.comparingDouble(Influence::power)).map(Influence::land).orElse(fallback);
    }
    static ShardSpec.Land choose(List<Influence> list,Random random){
        double total=list.stream().mapToDouble(Influence::power).sum(),ticket=random.nextDouble()*total;
        for(var i:list){ticket-=i.power;if(ticket<0)return i.land;}return list.getLast().land;
    }
    static void apply(TerrainPlan p,StarterRecipe r){
        if(r.biomes().isEmpty())return;
        var list=influences(r);double power=list.stream().mapToDouble(Influence::power).sum();
        double coverage=Math.clamp(power/(power+r.starterUnits()),.18,.86);
        var natural=EnumSet.of(Material.MOSS_BLOCK,Material.MOSSY_STONE_BRICKS,Material.CALCITE,Material.BONE_BLOCK,Material.SMOOTH_QUARTZ,Material.DEEPSLATE_TILES,Material.BLACKSTONE,Material.POLISHED_BLACKSTONE_BRICKS);
        int primary=StarterIslandDesign.dominant(r);Material road=primary==6?Material.POLISHED_BLACKSTONE_BRICKS:primary==5?Material.BONE_BLOCK:Material.STONE_BRICKS;
        // Seeded, contiguous weighted sectors share the same district roads and building footprints.
        // Biomes change the world beneath the architecture, never stack another island on top.
        double phase=new Random(r.seed()^0x4F726967696EL).nextDouble()*Math.PI*2;
        for(var cell:p.surface.entrySet()){
            int x=(int)(cell.getKey()>>32),z=cell.getKey().intValue(),y=cell.getValue();
            Material original=p.blocks.get(new TerrainPlan.Pos(x,y,z));
            if(original==null||original==road||!natural.contains(original)||Math.hypot(x,z)<7)continue;
            // Keep all existing facades, terrace plinths, pools and approach roads intact.
            if(p.blocks.containsKey(new TerrainPlan.Pos(x,y+1,z)))continue;
            double radial=Math.hypot(x,z)/p.radius;
            if(radial<1-coverage)continue;
            ShardSpec.Land land=sector(list,x,z,phase);
            p.put(x,y,z,surface(land));
            for(int h=1;h<=2;h++){var pos=new TerrainPlan.Pos(x,y-h,z);Material below=p.blocks.get(pos);if(below==Material.TUFF||below==Material.DEEPSLATE||below==Material.BASALT)p.put(x,y-h,z,strata(land,h));}
            // Low garden details cannot obscure the unified silhouette or block passage.
            if(Math.floorMod(x*37+z*19+(int)r.seed(),29)==0){
                Material detail=switch(land){case FOREST,JUNGLE->Material.MOSS_CARPET;case SNOW->Material.SNOW;
                    case NETHER->Material.CRIMSON_ROOTS;case MUSHROOM->Material.RED_MUSHROOM;
                    case CRYSTAL->Material.SMALL_AMETHYST_BUD;default->null;};
                if(detail!=null)p.put(x,y+1,z,detail);
            }
        }
    }
    static ShardSpec.Land sector(List<Influence> list,int x,int z,double phase){
        double angle=(Math.atan2(z,x)-phase)%(Math.PI*2);if(angle<0)angle+=Math.PI*2;
        double ticket=angle/(Math.PI*2)*list.stream().mapToDouble(Influence::power).sum();
        for(var influence:list){ticket-=influence.power();if(ticket<0)return influence.land();}return list.getLast().land();
    }
    static Material surface(ShardSpec.Land land){return switch(land){
        case FOREST,JUNGLE->Material.GRASS_BLOCK;case SNOW->Material.SNOW_BLOCK;case DESERT->Material.SMOOTH_SANDSTONE;
        case VOLCANIC->Material.BLACKSTONE;case OCEAN->Material.PRISMARINE;case CRYSTAL->Material.CALCITE;
        case RUINS->Material.MOSSY_STONE_BRICKS;case NETHER->Material.CRIMSON_NYLIUM;case END->Material.END_STONE;
        case MUSHROOM->Material.MYCELIUM;case BADLANDS->Material.RED_SANDSTONE;
    };}
    static Material strata(ShardSpec.Land land,int layer){return switch(land){
        case DESERT,OCEAN->Material.SANDSTONE;case BADLANDS->layer==1?Material.ORANGE_TERRACOTTA:Material.YELLOW_TERRACOTTA;
        case NETHER->Material.NETHERRACK;case END->Material.PURPUR_BLOCK;case SNOW->Material.PACKED_ICE;
        case CRYSTAL->Material.AMETHYST_BLOCK;case VOLCANIC->Material.BASALT;default->Material.DIRT;
    };}
}
