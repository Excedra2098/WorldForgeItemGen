package dev.excedra.itemgen;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Yellow seal, actual-block visual proxy contraction, then the original shard flies to its caster. */
final class ReclaimAnimation implements AutoCloseable {
    record Piece(BlockDisplay display,ReclaimMesh.Box box){}
    final World world;final Player player;final Location center;final double radius;final List<ReclaimMesh.Box> mesh;
    final List<Piece> pieces=new ArrayList<>();final ItemStack original;ItemDisplay wand,shard;int tick,meshIndex,shrinkAge=-1;boolean closed;
    ReclaimAnimation(JavaPlugin plugin,World world,Player player,GeneratedLand land,List<GeneratedLand.Cell> eligible,ItemStack original,ItemStack tool){
        this.world=world;this.player=player;this.original=original.clone();center=new Location(world,(land.minX+land.maxX+1)/2.0,(land.minY+land.maxY+1)/2.0,(land.minZ+land.maxZ+1)/2.0);
        radius=Math.hypot(land.maxX-land.minX+2,land.maxZ-land.minZ+2)/2.0;
        mesh=ReclaimMesh.build(eligible,Math.clamp(plugin.getConfig().getInt("realmshards.reclaim-visuals",1024),128,2048));
        Location at=HeldShardDisplay.position(player,false);at.setDirection(center.toVector().subtract(at.toVector()));
        wand=world.spawn(at,ItemDisplay.class,d->{d.setItemStack(tool);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);setup(d);d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateX((float)Math.PI/2),new Vector3f(.65f),new Quaternionf()));});
        player.swingMainHand();player.playSound(player.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,.7f,1.3f);
    }
    static void setup(Display d){d.setPersistent(false);d.addScoreboardTag("realmshards_temporary");d.setViewRange(4);d.setTeleportDuration(2);d.setInterpolationDuration(2);}
    void tick(){
        if(closed)return;if(!player.isOnline()||!player.getWorld().equals(world))throw new IllegalStateException("Reclaimer caster left the world.");tick++;
        double scale=shrinkAge<0?1:ReclaimMesh.shrink(Math.min(80,shrinkAge));
        if(tick%2==0){
            double r=Math.max(.45,radius*scale),y=center.getY()+Math.sin(tick*.06)*.25;
            for(int n=0;n<80;n++){double a=n*Math.PI*2/80+tick*.015;Location at=new Location(world,center.getX()+Math.cos(a)*r,y,center.getZ()+Math.sin(a)*r);world.spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(0xFFE167),1.15f));}
            for(int n=0;n<12;n++){double a=n*Math.PI/6-tick*.035;world.spawnParticle(Particle.DUST,center.clone().add(Math.cos(a)*r*.88,Math.sin(a*2)*r*.16,Math.sin(a)*r*.88),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(0x75F5C5),.7f));}
        }
        if(shrinkAge>=0){shrinkAge++;
            if(shrinkAge<=80&&tick%2==0)for(Piece piece:pieces){var b=piece.box();Location at=new Location(world,b.x()+.002,b.y()+.002,b.z()+.002);at=center.clone().add(at.toVector().subtract(center.toVector()).multiply(scale));piece.display().teleport(at);piece.display().setInterpolationDelay(0);piece.display().setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f((float)(b.width()*scale),(float)(b.height()*scale),(float)(b.depth()*scale)),new Quaternionf()));}
            if(shrinkAge==81){pieces.forEach(p->p.display().remove());pieces.clear();wand.remove();wand=null;
                shard=world.spawn(center,ItemDisplay.class,d->{d.setItemStack(original);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);setup(d);d.setBrightness(new Display.Brightness(15,15));d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(.3f),new Quaternionf()));});player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.8f,1.6f);
            }
            if(shrinkAge>81&&shard!=null){double t=Math.clamp((shrinkAge-81)/32.0,0,1),ease=t*t*(3-2*t);Location hand=HeldShardDisplay.position(player,false),at=center.clone().add(hand.toVector().subtract(center.toVector()).multiply(ease)).add(0,Math.sin(t*Math.PI)*2,0);shard.teleport(at);RealmShards.dust(at,Color.fromRGB(0xFFE167),.8f);}
        }
    }
    boolean sealed(){return tick>=40;}
    boolean capture(){int spawned=0;while(meshIndex<mesh.size()&&spawned++<24){var b=mesh.get(meshIndex++);BlockDisplay d=world.spawn(new Location(world,b.x()+.002,b.y()+.002,b.z()+.002),BlockDisplay.class,e->{e.setBlock(Bukkit.createBlockData(b.data().contains(":")?b.data():"minecraft:"+b.data().toLowerCase(Locale.ROOT)));setup(e);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(b.width(),b.height(),b.depth()),new Quaternionf()));});pieces.add(new Piece(d,b));}return meshIndex==mesh.size();}
    void shrink(){if(shrinkAge<0){shrinkAge=0;player.playSound(player.getLocation(),Sound.BLOCK_BEACON_DEACTIVATE,.8f,1.4f);}}
    boolean returning(){return shrinkAge>=0;}
    boolean done(){return shrinkAge>=113;}
    public void close(){if(closed)return;closed=true;pieces.forEach(p->p.display().remove());pieces.clear();if(wand!=null)wand.remove();if(shard!=null)shard.remove();}
}
