package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;

/** Traversable districts share a palette, road network and subterranean history. */
final class StarterRealmDistricts {
    record District(int x,int y,int z,int theme) {}
    static void build(TerrainPlan p,StarterRecipe r,Random random,double phase,Material trim,Material glow){
        List<District> districts=new ArrayList<>();
        boolean coherent=StarterIslandDesign.diversity(r)>=2;List<Integer> themes=new ArrayList<>();for(int i=0;i<9;i++)if(StarterIslandDesign.has(r,i)&&i!=StarterIslandDesign.dominant(r))themes.add(i);Collections.shuffle(themes,random);int count=coherent?themes.size():3+(int)Math.min(2,Math.log1p(r.units())/Math.log(3));
        for(int n=0;n<count;n++){
            double angle=phase+n*Math.PI*2/count+(random.nextDouble()-.5)*.22;
            double distance=p.radius*((coherent?.78:.60)+random.nextDouble()*.05);
            int x=(int)Math.round(Math.cos(angle)*distance),z=(int)Math.round(Math.sin(angle)*distance);
            int y=StarterIslandDesign.top(p,x,z);
            District d=new District(x,y,z,coherent?themes.get(n):choose(r,random));districts.add(d);
            terrace(p,x,y,z,4,trim);
            road(p,0,3,0,x,y,z,trim,glow);
        }
        // Encircling routes make districts part of one inhabited world.
        for(int n=0;n<count;n++){
            District a=districts.get(n),b=districts.get((n+1)%count);
            road(p,a.x,a.y,a.z,b.x,b.y,b.z,trim,glow);
        }
        for(District d:districts){if(coherent)StarterConfluenceCitadel.pavilion(p,r,d.x,d.y+1,d.z,d.theme,trim,glow);else landmark(p,r,d,random,trim,glow);}
        // A chamber in the floating bedrock and a sloped entrance from a district.
        District entrance=districts.get(0);int chamberY=-5;
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)if(x*x+z*z<=20){
            p.put(x,chamberY,z,trim);
            for(int y=chamberY+1;y<=chamberY+4;y++)p.blocks.remove(new TerrainPlan.Pos(x,y,z));
            p.put(x,chamberY+5,z,(x+z)%4==0?glow:trim);
        }
        int length=Math.max(12,Math.max(Math.abs(entrance.x),Math.abs(entrance.z)));
        for(int n=0;n<=length;n++){
            double t=n/(double)length;int x=(int)Math.round(entrance.x*t),z=(int)Math.round(entrance.z*t),y=(int)Math.round(chamberY+(entrance.y-chamberY)*t);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                p.put(x+dx,y,z+dz,trim);
                for(int h=1;h<=3;h++)p.blocks.remove(new TerrainPlan.Pos(x+dx,y+h,z+dz));
            }
        }
        p.pillar(-3,chamberY+1,-2,2,trim,glow);p.pillar(3,chamberY+1,2,2,trim,glow);
        // Small life, ruins and strata reinforce scale between the larger landmarks.
        for(int n=0;n<(coherent?24:70+r.tier()*12);n++){
            int x=random.nextInt(p.radius*2-5)-p.radius+3,z=random.nextInt(p.radius*2-5)-p.radius+3;
            Integer y=p.surface.get(TerrainPlan.key(x,z));if(y==null||Math.hypot(x,z)<7||p.blocks.containsKey(new TerrainPlan.Pos(x,y+1,z)))continue;
            if(districts.stream().anyMatch(d->Math.hypot(d.x-x,d.z-z)<5))continue;
            Material floor=p.blocks.get(new TerrainPlan.Pos(x,y,z));if(floor==trim)continue;
            int theme=choose(r,random);
            if(theme==1||theme==4){p.put(x,y+1,z,Material.MOSS_CARPET);if(!coherent&&n%6==0)p.tree(x,y+1,z,Material.CHERRY_LOG,Material.AZALEA_LEAVES,3);}
            else if(!coherent&&(theme==7||theme==2))p.pillar(x,y+1,z,1+random.nextInt(3),Material.AMETHYST_BLOCK,Material.BUDDING_AMETHYST);
            else if(!coherent&&n%9==0)p.pillar(x,y+1,z,1+random.nextInt(3),theme==5?Material.BONE_BLOCK:trim,glow);
        }
    }
    static int choose(StarterRecipe r,Random random){double ticket=random.nextDouble()*r.starterUnits();for(int i=0;i<9;i++){ticket-=r.counts().get(i);if(ticket<0)return i;}return StarterIslandDesign.dominant(r);}
    static void terrace(TerrainPlan p,int x,int y,int z,int radius,Material trim){
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(dx*dx+dz*dz<=radius*radius){
            int old=StarterIslandDesign.top(p,x+dx,z+dz);
            for(int h=Math.min(old,y)-2;h<=y;h++)p.put(x+dx,h,z+dz,h==y?trim:Material.TUFF);
            for(int h=y+1;h<=Math.max(old,y)+3;h++)p.blocks.remove(new TerrainPlan.Pos(x+dx,h,z+dz));
            p.surface.put(TerrainPlan.key(x+dx,z+dz),y);
        }
    }
    static void road(TerrainPlan p,int ax,int ay,int az,int bx,int by,int bz,Material trim,Material glow){
        int length=Math.max(Math.abs(ax-bx),Math.abs(az-bz));
        for(int n=0;n<=length;n++){
            double t=length==0?0:n/(double)length;int x=(int)Math.round(ax+(bx-ax)*t),z=(int)Math.round(az+(bz-az)*t),y=(int)Math.round(ay+(by-ay)*t);
            if(Math.hypot(x,z)<5)continue;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                int old=StarterIslandDesign.top(p,x+dx,z+dz);
                for(int h=Math.min(old,y)-2;h<=y;h++)p.put(x+dx,h,z+dz,trim);
                for(int h=y+1;h<=Math.max(old,y)+3;h++)p.blocks.remove(new TerrainPlan.Pos(x+dx,h,z+dz));
                p.surface.put(TerrainPlan.key(x+dx,z+dz),y);
            }
            if(n%6==0)p.put(x,y,z,glow);
        }
    }
    static void landmark(TerrainPlan p,StarterRecipe r,District d,Random random,Material trim,Material glow){
        int x=d.x,y=d.y+1,z=d.z;Material accent=StarterIslandDesign.has(r,7)?Material.AMETHYST_BLOCK:StarterIslandDesign.has(r,6)?Material.CUT_COPPER:Material.PRISMARINE_BRICKS;
        switch(d.theme){
            case 0->{ // Open cloister: columns, broken vault, benches, ceremonial inset.
                for(int dx:new int[]{-3,3})for(int dz:new int[]{-2,2})p.pillar(x+dx,y,z+dz,4+random.nextInt(2),trim,accent);
                for(int dx=-3;dx<=3;dx++)p.put(x+dx,y+5,z+2,dx%2==0?accent:trim);
                for(int dx=-2;dx<=2;dx++)p.put(x+dx,y,z+2,trim);p.put(x,y,z,glow);
            }
            case 1->{ // Small open garden shelter, with living canopy and a rooted pond.
                for(int dx:new int[]{-2,2})for(int dz:new int[]{-2,2})p.pillar(x+dx,y,z+dz,4,Material.CHERRY_LOG,Material.CHERRY_LEAVES);
                for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(dx*dx+dz*dz<14)p.put(x+dx,y+5,z+dz,Material.AZALEA_LEAVES);
                basin(p,x,y-1,z,Material.MOSS_BLOCK,glow);p.put(x,y,z+3,Material.FLOWERING_AZALEA);
            }
            case 2->{StarterIslandDesign.arch(p,x,y,z,Material.CRYING_OBSIDIAN,glow);for(int dz=-2;dz<=2;dz++)p.put(x,y-1,z+dz,Material.AMETHYST_BLOCK);p.pillar(x-3,y,z-3,3,trim,Material.CRYING_OBSIDIAN);}
            case 3->{ // Observatory platform with a tilted visual telescope and broken orbit.
                StarterIslandDesign.orbit(p,x,y+4,z,3,Material.CUT_COPPER,glow);
                p.pillar(x,y,z,2,trim,accent);for(int n=0;n<4;n++)p.put(x+n,y+3+n/2,z,Material.CUT_COPPER);
            }
            case 4->{basin(p,x,y-1,z,Material.PRISMARINE_BRICKS,glow);for(int dz:new int[]{-3,3})for(int dx=-3;dx<=3;dx++)p.put(x+dx,y,z+dz,Material.MOSS_BLOCK);StarterIslandDesign.waterfall(p,x+3,z,glow);}
            case 5->{for(int n=0;n<=16;n++){double a=n*Math.PI/16;p.put(x+(int)Math.round(Math.cos(a)*3),y+(int)Math.round(Math.sin(a)*5),z,Material.BONE_BLOCK);}for(int dz=-3;dz<=3;dz++)p.put(x,y-1,z+dz,accent);p.pillar(x+2,y,z+2,2,Material.BONE_BLOCK,glow);}
            case 6->{ // Roofed foundry hall, open on two sides, no flowing lava.
                for(int dx:new int[]{-3,3})for(int dz:new int[]{-2,2})p.pillar(x+dx,y,z+dz,4,Material.POLISHED_BLACKSTONE_BRICKS,Material.CUT_COPPER);
                for(int dx=-3;dx<=3;dx++)for(int dz=-2;dz<=2;dz++)p.put(x+dx,y+5,z+dz,Material.CUT_COPPER);
                p.put(x-2,y,z,Material.ANVIL);p.put(x+2,y,z,Material.SMITHING_TABLE);p.put(x,y-1,z,Material.MAGMA_BLOCK);p.put(x,y,z,Material.IRON_BARS);
            }
            case 7->{for(int dx:new int[]{-2,2})StarterIslandDesign.crystal(p,x+dx,y,z+1,4+random.nextInt(4),accent);for(int dz=-2;dz<=2;dz++)p.put(x,y-1,z+dz,Material.TINTED_GLASS);p.put(x,y,z-3,Material.CHISELED_STONE_BRICKS);}
            default->{ // Walk-in archive tower with openings, inset windows and layered crown.
                int height=6+random.nextInt(3);
                for(int h=0;h<height;h++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.max(Math.abs(dx),Math.abs(dz))==2){
                    if(h<3&&(dx==0||dz==0))continue;
                    p.put(x+dx,y+h,z+dz,h==4&&((dx==0)||(dz==0))?Material.LIGHT_BLUE_STAINED_GLASS:trim);
                }
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)p.put(x+dx,y+height,z+dz,accent);
                p.put(x,y+height+1,z,glow);p.put(x-1,y,z+1,Material.BOOKSHELF);p.put(x+1,y,z+1,Material.BOOKSHELF);
            }
        }
    }
    static void basin(TerrainPlan p,int x,int y,int z,Material rim,Material glow){
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            p.put(x+dx,y-1,z+dz,glow);p.put(x+dx,y,z+dz,Math.max(Math.abs(dx),Math.abs(dz))==2?rim:Material.WATER);
        }
    }
}
