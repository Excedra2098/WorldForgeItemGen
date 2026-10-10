package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

/** Many themes inform one sanctuary, rather than stacking nine independent structures. */
final class StarterConfluenceCitadel {
    static void build(TerrainPlan p,StarterRecipe r,Material stone,Material glow){
        Material ivory=Material.CALCITE,accent=StarterIslandDesign.dominant(r)==6?Material.CUT_COPPER:Material.AMETHYST_BLOCK;
        // A two-level inhabited podium, open cardinal entrances and recessed water courts.
        StarterRealmDistricts.terrace(p,0,3,0,8,stone);
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)if(x*x+z*z<56){
            if(Math.max(Math.abs(x),Math.abs(z))<=4)p.put(x,4,z,Material.SMOOTH_STONE);
            if((Math.abs(x)==7||Math.abs(z)==7)&&Math.abs(x)>1&&Math.abs(z)>1)p.put(x,4,z,ivory);
        }
        // Colonnade wings leave the courtyard and approaches open.
        for(int side:new int[]{-1,1}){
            for(int n:new int[]{-5,-2,2,5}){
                p.pillar(side*6,4,n,5,ivory,stone);p.pillar(n,4,side*6,5,ivory,stone);
            }
            for(int n=-6;n<=6;n++)if(Math.abs(n)>1){p.put(side*6,9,n,stone);p.put(n,9,side*6,stone);if(n%3==0){p.put(side*6,10,n,accent);p.put(n,10,side*6,accent);}}
            for(int n=-1;n<=1;n++){
                p.put(n,10,side*6,stone);p.put(side*6,10,n,stone);
            }
            // Water garden framed and supported; no uncontained fluid columns.
            StarterRealmDistricts.basin(p,side*4,4,0,ivory,Material.PRISMARINE_BRICKS);
        }
        // Diagonal watch chambers are part of the same facade and palette.
        for(int x:new int[]{-5,5})for(int z:new int[]{-5,5}){
            for(int y=4;y<=11;y++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))==1)p.put(x+dx,y,z+dz,y==7?Material.TINTED_GLASS:ivory);
            }
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<4)p.put(x+dx,12,z+dz,stone);
            p.put(x,13,z,accent);p.put(x,14,z,glow);
        }
        int style=Math.floorMod(r.seed(),3);
        if(style==1){
            // Living atlas: a single tree grows through the centre of the architecture.
            for(int y=5;y<=15;y++)p.put(0,y,0,Material.CHERRY_LOG);
            for(int arm=0;arm<4;arm++){double a=arm*Math.PI/2;int x=(int)(Math.cos(a)*3),z=(int)(Math.sin(a)*3);
                for(int n=1;n<=3;n++)p.put((int)(Math.cos(a)*n),12+n/2,(int)(Math.sin(a)*n),Material.CHERRY_LOG);
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy<=2;dy++)if(dx*dx+dz*dz+dy*dy<7)p.put(x+dx,15+dy,z+dz,Material.CHERRY_LEAVES);
            }
            p.put(0,18,0,accent);
        }else{
            // Atlas tower, with a hollow lantern cage rather than overlapping rings.
            for(int y=5;y<=15;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)p.put(x,y,z,(x==0&&z==0)?accent:ivory);
            for(int y=16;y<=20;y++)for(int x:new int[]{-2,2})for(int z:new int[]{-2,2})p.put(x,y,z,stone);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)p.put(x,21,z,stone);
            p.pillar(0,16,0,3,accent,glow);p.put(0,22,0,accent);
            if(style==2)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(Math.max(Math.abs(x),Math.abs(z))==3)p.put(x,13,z,stone);
        }
        // Cardinal steps ensure the taller podium connects to surrounding routes.
        for(int n=-1;n<=1;n++)for(int side:new int[]{-1,1}){
            p.put(n,3,side*8,stone);p.put(side*8,3,n,stone);
            for(int m=5;m<=7;m++){p.put(n,3,side*m,stone);p.put(side*m,3,n,stone);}
        }
    }
    static void pavilion(TerrainPlan p,StarterRecipe r,int x,int y,int z,int furnishing,Material stone,Material glow){
        Material accent=StarterIslandDesign.dominant(r)==6?Material.CUT_COPPER:Material.AMETHYST_BLOCK;
        for(int dx:new int[]{-2,2})for(int dz:new int[]{-2,2})p.pillar(x+dx,y,z+dz,4,Material.CALCITE,stone);
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(Math.max(Math.abs(dx),Math.abs(dz))==3)p.put(x+dx,y+5,z+dz,stone);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)p.put(x+dx,y+6,z+dz,stone);
        p.put(x,y+7,z,accent);
        // Each non-dominant ingredient has its own attached wing; it is never dropped by random sampling.
        switch(furnishing){
            case 1->{p.put(x,y,z,Material.CHERRY_LOG);p.pillar(x,y+1,z,2,Material.CHERRY_LOG,Material.CHERRY_LEAVES);for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)p.put(x+dx,y+6,z+dz,Material.MOSS_BLOCK);p.put(x-1,y,z-1,Material.FLOWERING_AZALEA);}
            case 4->{StarterRealmDistricts.basin(p,x,y-1,z,Material.PRISMARINE_BRICKS,Material.SEA_LANTERN);p.put(x,y+6,z,Material.PRISMARINE_BRICKS);}
            case 6->{p.put(x-1,y,z,Material.ANVIL);p.put(x+1,y,z,Material.SMITHING_TABLE);}
            case 5->{p.put(x-1,y,z,Material.BONE_BLOCK);p.put(x+1,y,z,Material.BONE_BLOCK);p.put(x,y+1,z,glow);}
            case 3->{p.pillar(x,y,z,2,stone,glow);for(int n=0;n<3;n++)p.put(x+n,y+3+n/2,z,Material.CUT_COPPER);p.put(x,y+6,z,Material.CUT_COPPER);}
            case 8->{p.put(x-1,y,z+1,Material.BOOKSHELF);p.put(x+1,y,z+1,Material.BOOKSHELF);p.pillar(x,y,z,2,stone,glow);p.pillar(x,y+7,z,2,stone,accent);}
            case 2->{for(int dx:new int[]{-1,1})p.pillar(x+dx,y,z,3,Material.CRYING_OBSIDIAN,accent);p.put(x,y+3,z,stone);}
            case 7->{p.pillar(x,y,z,4,Material.AMETHYST_BLOCK,Material.BUDDING_AMETHYST);p.put(x-1,y,z,Material.CALCITE);p.put(x+1,y,z,Material.CALCITE);}
            default->p.pillar(x,y,z,2,stone,accent);
        }
        // Distinct rooflines make each attached wing readable from above as well
        // as inside. All sit on the same facade; additions never overlap neighbours.
        switch(furnishing){
            case 0->{for(int dx:new int[]{-2,2})p.pillar(x+dx,y+7,z,2,Material.CALCITE,accent);for(int dx=-2;dx<=2;dx++)p.put(x+dx,y+9,z,stone);}
            case 1->{p.pillar(x,y+7,z,2,Material.CHERRY_LOG,Material.CHERRY_LEAVES);for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx*dx+dz*dz<6)p.put(x+dx,y+10,z+dz,Material.CHERRY_LEAVES);p.put(x,y+11,z,Material.CHERRY_LEAVES);}
            case 2->{p.pillar(x-1,y+7,z,3,stone,Material.CRYING_OBSIDIAN);p.pillar(x+1,y+7,z,3,stone,Material.CRYING_OBSIDIAN);p.put(x,y+10,z,accent);}
            case 3->{for(int dy=0;dy<3;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx*dx+dz*dz<=5-dy*2)p.put(x+dx,y+7+dy,z+dz,Material.CUT_COPPER);p.put(x+1,y+10,z,Material.CUT_COPPER);}
            case 4->StarterRealmDistricts.basin(p,x,y+7,z,Material.PRISMARINE_BRICKS,Material.PRISMARINE_BRICKS);
            case 5->{for(int n=0;n<=12;n++){double a=n*Math.PI/12;p.put(x+(int)Math.round(Math.cos(a)*2),y+7+(int)Math.round(Math.sin(a)*3),z,Material.BONE_BLOCK);p.put(x+(int)Math.round(Math.cos(a)*2),y+7+(int)Math.round(Math.sin(a)*3),z+1,Material.BONE_BLOCK);}}
            case 6->{for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)p.put(x+dx,y+7,z+dz,Material.CUT_COPPER);p.pillar(x+1,y+8,z-1,2,stone,Material.SHROOMLIGHT);}
            case 7->{for(int dx=-1;dx<=1;dx++)p.pillar(x+dx,y+7,z,2+(dx==0?2:0),Material.AMETHYST_BLOCK,Material.BUDDING_AMETHYST);}
            case 8->{p.pillar(x,y+7,z,5,Material.CALCITE,accent);p.put(x-1,y+10,z,stone);p.put(x+1,y+10,z,stone);}
        }
    }
}
