package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

/** Designed topology and shared architecture, rather than independent scattered motifs. */
final class StarterIslandDesign {
    static int dominant(StarterRecipe r){int best=0;for(int i=1;i<9;i++)if(r.counts().get(i)>r.counts().get(best))best=i;return best;}
    static int diversity(StarterRecipe r){return (int)r.counts().stream().filter(n->n>0).count();}
    static boolean has(StarterRecipe r,int i){return r.counts().get(i)>0;}
    static String title(StarterRecipe r){return (r.biomes().isEmpty()?"":"Biomewoven ")+baseTitle(r);}
    static String baseTitle(StarterRecipe r){
        if(diversity(r)==9)return "Eclipse Atlas Sanctuary";
        if(diversity(r)>=3)return StarterRealms.NAMES[dominant(r)]+" Confluence Citadel";
        if(has(r,1)&&has(r,6))return "Emberroot Crucible";
        if(has(r,0)&&has(r,4))return "Crown of the Falling Tides";
        if(has(r,5)&&has(r,7))return "Prismatic Leviathan";
        if(has(r,3)&&has(r,8))return "Astral Helix";
        if(has(r,1)&&has(r,5))return "The Living Colossus";
        if(has(r,2)&&has(r,4))return "The Riftwater Divide";
        if(has(r,0)&&has(r,7))return "The Glass Coronation";
        return java.util.stream.IntStream.range(0,9).filter(i->has(r,i)).mapToObj(i->StarterRealms.NAMES[i]).reduce((a,b)->a+" / "+b).orElseThrow()+" Sanctum";
    }
    static TerrainPlan create(StarterRecipe r){
        int radius=14+(int)Math.min(10,Math.log1p(r.units())/Math.log(2)*2)+Math.min(6,Math.max(0,diversity(r)-2)),primary=dominant(r);
        TerrainPlan p=new TerrainPlan(radius,StarterBiomeWeave.dominantLand(r,StarterRealms.LANDS[primary]),r.seed(),-radius-4,28);
        Random random=new Random(r.seed());double phase=random.nextDouble()*Math.PI*2;
        Material stone=primary==6?Material.POLISHED_BLACKSTONE:primary==7?Material.CALCITE:Material.DEEPSLATE;
        Material skin=primary==6?Material.POLISHED_BLACKSTONE_BRICKS:primary==3?Material.SMOOTH_QUARTZ:primary==8?Material.DEEPSLATE_TILES:primary==1||primary==4?Material.MOSS_BLOCK:primary==5?Material.BONE_BLOCK:primary==7?Material.CALCITE:Material.MOSSY_STONE_BRICKS;
        // Weighted biome geometry creates one continuous island with real terraces and voids.
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            double d=Math.hypot(x,z)/radius,a=Math.atan2(z,x)+phase;
            double shape=0;for(int i=0;i<9;i++)shape+=r.counts().get(i)*edge(i,a);shape/=r.starterUnits();
            // Seeded lobes and ridgelines change the whole silhouette, not just edge noise.
            shape+=.08*Math.sin(a*(2+Math.floorMod(r.seed(),4)))+.06*Math.cos(a*3+phase*2);
            if(d>shape)continue;
            if(diversity(r)<3&&primary==0&&d<.25&&Math.abs(z)>1)continue;
            if(diversity(r)<3&&primary==2&&Math.abs(x)<=1&&Math.abs(z)>1)continue;
            int top=primary==4?2+(int)Math.floor((1-d)*7):primary==8?(int)Math.floor((1-d)*5):
                (int)Math.round(Math.sin(a*2)*1.4)+(d<.40?3:d<.68?1:0);
            top+=(int)Math.round(Math.sin(x*.19+phase)*Math.cos(z*.17-phase)*2);
            if(d<.32)top=3;
            int bottom=-3-(int)((1-d)*(radius*.62))-random.nextInt(3);
            p.surface.put(TerrainPlan.key(x,z),top);
            for(int y=bottom;y<=top;y++){
                Material mat=y==top?skin:y>=top-2?Material.TUFF:stone;
                if(primary==2&&x>1)mat=y==top?Material.BLACKSTONE:Material.BASALT;
                if(y<-3&&(x*13+z*7+y)%31==0)mat=has(r,6)?Material.MAGMA_BLOCK:Material.AMETHYST_BLOCK;
                p.put(x,y,z,mat);
            }
        }
        // Walkable core terrace and radial approaches establish intentional composition.
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(x*x+z*z<=11)p.put(x,3,z,skin);
        Material trim=primary==6?Material.POLISHED_BLACKSTONE_BRICKS:primary==5?Material.BONE_BLOCK:Material.STONE_BRICKS;
        Material glow=primary==6?Material.SHROOMLIGHT:primary==7?Material.AMETHYST_BLOCK:Material.SEA_LANTERN;
        for(int a=0;a<3;a++){double angle=phase+a*Math.PI*2/3;for(int n=3;n<radius-2;n++){int x=(int)Math.round(Math.cos(angle)*n),z=(int)Math.round(Math.sin(angle)*n);Integer y=p.surface.get(TerrainPlan.key(x,z));if(y!=null){p.put(x,y,z,trim);if(n%6==0)p.put(x,y,z,glow);}}}
        // A central feature inherits materials and form from ALL contributing themes.
        if(diversity(r)<3){
        if(primary==1)livingCore(p,r,trim,glow);
        else if(primary==8)helix(p,r,trim,glow);
        else if(primary==5)ribs(p,r,glow);
        else if(primary==6)crucible(p,trim,glow);
        else if(primary==7){crystal(p,0,4,0,12,Material.AMETHYST_BLOCK);for(int n=0;n<5;n++){double a=n*Math.PI*2/5;crystal(p,(int)(Math.cos(a)*4),4,(int)(Math.sin(a)*4),5+n,Material.AMETHYST_BLOCK);}}
        else if(primary==2){for(int side:new int[]{-1,1})crystal(p,side*4,3,0,12,Material.CRYING_OBSIDIAN);for(int z=-7;z<=7;z++)p.put(0,0,z,Material.AMETHYST_BLOCK);}
        else if(primary==4){for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){p.put(x,4,z,Material.PRISMARINE_BRICKS);p.put(x,5,z,Math.max(Math.abs(x),Math.abs(z))==3?Material.PRISMARINE_BRICKS:Material.WATER);}p.pillar(0,6,0,2,Material.PRISMARINE,glow);}
        else if(primary==3){p.pillar(0,4,0,5,Material.CUT_COPPER,Material.AMETHYST_BLOCK);orbit(p,0,10,0,5,Material.CUT_COPPER,glow);}
        else {for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)p.put(x,6,z,trim);p.pillar(0,7,0,3,Material.CHISELED_STONE_BRICKS,Material.AMETHYST_BLOCK);}

        if(diversity(r)==1&&has(r,0))for(int n=0;n<3+Math.min(3,r.counts().get(0)-1);n++){double a=phase+n*Math.PI*2/(3+Math.min(3,r.counts().get(0)-1));int x=(int)(Math.cos(a)*(radius*.63)),z=(int)(Math.sin(a)*(radius*.63));arch(p,x,top(p,x,z)+1,z,has(r,7)?Material.AMETHYST_BLOCK:trim,glow);}
        if(diversity(r)==1&&has(r,3))orbit(p,0,12,0,7,has(r,6)?Material.CUT_COPPER:Material.CHISELED_STONE_BRICKS,glow);
        if(diversity(r)==1&&has(r,4))for(int side:new int[]{-1,1}){waterfall(p,side*(radius-4),0,glow);if(has(r,0))arch(p,side*(radius-4),top(p,side*(radius-4),0)+2,0,Material.PRISMARINE_BRICKS,glow);}
        if(diversity(r)==1&&has(r,2))for(int z=-radius+3;z<=radius-3;z++)if(Math.abs(z)>2){Integer y=p.surface.get(TerrainPlan.key(2,z));if(y!=null){p.put(2,y,z,Material.CRYING_OBSIDIAN);p.put(-2,y,z,has(r,4)?Material.PRISMARINE:Material.AMETHYST_BLOCK);}}
        if(diversity(r)==1&&has(r,7)&&primary!=7)for(int side:new int[]{-1,1}){int x=side*(radius-5),z=4;crystal(p,x,top(p,x,z)+1,z,4+r.tier(),has(r,5)?Material.BONE_BLOCK:Material.AMETHYST_BLOCK);}
        if(diversity(r)==1&&has(r,5)&&has(r,1))for(int n=-1;n<=1;n++)arch(p,n*5,4,-4,Material.BONE_BLOCK,Material.AMETHYST_BLOCK);
        if(diversity(r)==1&&has(r,6)&&primary!=6&&!has(r,1))crucible(p,Material.POLISHED_BLACKSTONE_BRICKS,glow);
        }
        StarterRealmDistricts.build(p,r,random,phase,trim,glow);
        if(diversity(r)>=3)StarterConfluenceCitadel.build(p,r,trim,glow);
        StarterBiomeWeave.apply(p,r);
        return p;
    }
    static double edge(int theme,double a){return switch(theme){case 0->.88+.09*Math.cos(a*7);case 1->.81+.14*Math.sin(a*3);case 2->.85+.10*Math.cos(a*2);case 3->.83+.12*Math.cos(a*5);case 4->.88+.08*Math.sin(a*3);case 5->.77+.18*Math.cos(a);case 6->.77+.16*Math.cos(a*4);case 7->.80+.16*Math.cos(a*6);default->.86+.08*Math.cos(a*3);};}
    static int top(TerrainPlan p,int x,int z){return p.surface.getOrDefault(TerrainPlan.key(x,z),1);}
    static void arch(TerrainPlan p,int x,int y,int z,Material stone,Material glow){
        for(int side:new int[]{-1,1})for(int h=0;h<5;h++)for(int depth=0;depth<2;depth++)p.put(x+side*3,y+h,z+depth,stone);
        for(int a=-3;a<=3;a++)for(int depth=0;depth<2;depth++)p.put(x+a,y+5+(Math.abs(a)<2?1:0),z+depth,stone);
        p.put(x,y+7,z,glow);
    }
    static void livingCore(TerrainPlan p,StarterRecipe r,Material trim,Material glow){
        Material wood=has(r,6)?Material.STRIPPED_DARK_OAK_LOG:Material.CHERRY_LOG;
        Material leaves=has(r,7)?Material.AMETHYST_BLOCK:has(r,6)?Material.AZALEA_LEAVES:Material.CHERRY_LEAVES;
        for(int y=4;y<=12+r.tier();y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)p.put(x+(y>11?1:0),y,z,wood);
        for(int arm=0;arm<5;arm++){double a=arm*Math.PI*2/5;for(int n=1;n<=6;n++){int x=(int)Math.round(Math.cos(a)*n),z=(int)Math.round(Math.sin(a)*n);p.put(x,10+n/2,z,wood);p.put(x,-n,z,has(r,5)?Material.BONE_BLOCK:wood);}
            int x=(int)(Math.cos(a)*5),z=(int)(Math.sin(a)*5);for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-1;dy<=2;dy++)if(dx*dx+dz*dz+dy*dy*2<12)p.put(x+dx,13+arm%3+dy,z+dz,leaves);p.put(x,12,z,glow);}
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-1;dy<=2;dy++)if(dx*dx+dz*dz+dy*dy<7)p.put(1+dx,17+dy,dz,leaves);
        if(has(r,6))for(int x=-2;x<=2;x++)p.put(x,4,2,Material.MAGMA_BLOCK);
        if(diversity(r)==1&&has(r,8))helix(p,r,trim,glow);
    }
    static void helix(TerrainPlan p,StarterRecipe r,Material stone,Material glow){
        for(int y=4;y<19+r.tier();y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)p.put(x,y,z,(x==0&&z==0)?glow:stone);
        for(int n=0;n<70;n++){double a=n*.23;int x=(int)Math.round(Math.cos(a)*4),z=(int)Math.round(Math.sin(a)*4),y=4+n/5;p.put(x,y,z,stone);p.put(x+Integer.signum(x),y,z,stone);if(n%9==0)p.put(x,y+1,z,glow);}
    }
    static void ribs(TerrainPlan p,StarterRecipe r,Material glow){for(int z=-6;z<=6;z+=3)for(int a=0;a<=24;a++){double t=Math.PI*a/24;int x=(int)Math.round(Math.cos(t)*7),y=4+(int)Math.round(Math.sin(t)*10);p.put(x,y,z,has(r,7)&&a%5==0?Material.AMETHYST_BLOCK:Material.BONE_BLOCK);p.put(x,y,z+1,Material.BONE_BLOCK);}p.pillar(0,4,-6,2,Material.BONE_BLOCK,glow);}
    static void crucible(TerrainPlan p,Material stone,Material glow){for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){p.put(x,4,z,stone);if(Math.max(Math.abs(x),Math.abs(z))==3)p.put(x,5,z,stone);else p.put(x,5,z,Material.MAGMA_BLOCK);}for(int x:new int[]{-3,3}){p.pillar(x,6,0,5,stone,glow);for(int y=12;y<=16;y++)p.put(x,y,0,Material.IRON_CHAIN);}p.put(0,6,0,glow);}
    static void crystal(TerrainPlan p,int x,int y,int z,int h,Material mat){for(int dy=0;dy<h;dy++){int w=dy<h/2?1:0;for(int dx=-w;dx<=w;dx++)for(int dz=-w;dz<=w;dz++)p.put(x+dx+dy/5,y+dy,z+dz,mat);}p.put(x+h/5,y+h,z,Material.BUDDING_AMETHYST);}
    static void orbit(TerrainPlan p,int x,int y,int z,int radius,Material stone,Material glow){for(int n=0;n<100;n++){if(n%31>24)continue;double a=n*Math.PI/50;int dx=(int)Math.round(Math.cos(a)*radius),dy=(int)Math.round(Math.sin(a)*radius);p.put(x+dx,y+dy,z,n%12==0?glow:stone);p.put(x+dx,y+dy,z+1,stone);}}
    static void waterfall(TerrainPlan p,int x,int z,Material glow){int y=top(p,x,z)+1;for(int dy=-12;dy<=y;dy++){p.put(x-1,dy,z,Material.PRISMARINE_BRICKS);p.put(x+1,dy,z,Material.PRISMARINE_BRICKS);p.put(x,dy,z+1,Material.PRISMARINE_BRICKS);p.put(x,dy,z-1,Material.LIGHT_BLUE_STAINED_GLASS);p.put(x,dy,z,Material.WATER);}p.put(x,-13,z,glow);for(int dx=-1;dx<=1;dx++)p.put(x+dx,y+1,z,Material.PRISMARINE_BRICKS);}
}
