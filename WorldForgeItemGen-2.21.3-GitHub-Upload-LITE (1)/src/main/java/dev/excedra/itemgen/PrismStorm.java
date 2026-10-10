package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Worldforge's shared prism fracture effect. Visual entities never alter terrain or collide. */
final class PrismStorm {
    record Piece(ItemDisplay display,PrismFlight flight){}
    private final Location center;
    private final ShardSpec spec;
    private final List<Piece> pieces=new ArrayList<>();
    private final int count,particleLimit;
    private final long seed;
    private final double radius;
    private final float size;
    private int frame,spawned;
    private boolean bursting;
    PrismStorm(JavaPlugin plugin,Location center,ShardSpec spec,double radius,boolean islandCollapse,long seed){
        this.center=center.clone();this.spec=spec;this.radius=radius;this.seed=seed;size=islandCollapse?1.65f:.90f;
        int max=Math.clamp(plugin.getConfig().getInt("realmshards.shatter-max-fragments",48),12,64);
        count=Math.min(max,16+spec.tier()*5+spec.variant().ordinal()*3);
        particleLimit=Math.clamp(plugin.getConfig().getInt("realmshards.particles-per-tick",96)/3,6,48);
    }
    static String model(ShardSpec spec,int index){String palette=spec.variant()==ShardSpec.Variant.RAINBOW?"rainbow":spec.variant()==ShardSpec.Variant.SHINY?"gold":spec.land()==null?"random":spec.land().name().toLowerCase(Locale.ROOT);return "fx/"+palette+"_"+new String[]{"blade","facet","splinter"}[index%3];}
    void charge(double phase){
        spawn(Math.min(count,spawned+6));for(Piece p:pieces)pose(p,p.flight().charge(phase));
        if(frame++%3==0)trails();
    }
    private void spawn(int end){for(;spawned<end;spawned++){
        PrismFlight flight=new PrismFlight(spawned,seed,radius,size);ItemStack item=new ItemStack(Material.AMETHYST_SHARD);var meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards",model(spec,spawned)));item.setItemMeta(meta);
        ItemDisplay display=center.getWorld().spawn(center,ItemDisplay.class,d->{d.setItemStack(item);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.addScoreboardTag("realmshards_temporary");d.setBrightness(new Display.Brightness(15,15));d.setInterpolationDuration(2);d.setTeleportDuration(1);d.setViewRange(2.5f);});pieces.add(new Piece(display,flight));
    }}
    void shatter(){if(bursting)return;spawn(count);bursting=true;frame=0;ShatterEffects.burst(center.getWorld(),center,particleLimit,color());}
    void tick(){
        if(!bursting)return;frame++;Iterator<Piece> iterator=pieces.iterator();while(iterator.hasNext()){Piece p=iterator.next();if(p.flight().expired(frame)||!p.display().isValid()){p.display().remove();iterator.remove();}else pose(p,p.flight().burst(frame));}
        if(frame%2==0){trails();int n=Math.min(18,particleLimit/2);double r=radius*.45+frame*.38;for(int i=0;i<n;i++){double a=i*Math.PI*2/n+frame*.04;RealmShards.dust(center.clone().add(Math.cos(a)*r,.15+Math.sin(a*3)*.20,Math.sin(a)*r),color(),.65f);}}
    }
    private Color color(){return RealmShards.color(spec,frame);}
    private void trails(){int n=Math.min(pieces.size(),Math.min(8,particleLimit/2));for(int i=0;i<n;i++){Piece p=pieces.get((frame*3+i*5)%pieces.size());RealmShards.dust(p.display().getLocation(),i%3==0?Color.WHITE:color(),.50f);}}
    private void pose(Piece piece,PrismFlight.Pose pose){ItemDisplay d=piece.display();d.teleport(center.clone().add(pose.x(),pose.y(),pose.z()));d.setInterpolationDelay(0);d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateXYZ(pose.rx(),pose.ry(),pose.rz()),new Vector3f(pose.scale()),new Quaternionf()));}
    boolean done(){return bursting&&pieces.isEmpty();}
    void close(){pieces.forEach(p->p.display().remove());pieces.clear();}
}
