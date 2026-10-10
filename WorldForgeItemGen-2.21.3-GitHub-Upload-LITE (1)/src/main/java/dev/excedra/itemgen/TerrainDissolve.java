package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pack-defined crystal rupture at actual terrain cells. No block particles or glowing cubes. */
final class TerrainDissolve {
    record Fragment(ItemDisplay display,Location origin,double angle,int born,float size,boolean pulse){}
    private final World world;private final ShardSpec spec;private final int max;
    private final List<Fragment> pieces=new ArrayList<>();private final Map<String,ItemStack> models=new HashMap<>();int tick,sequence;
    TerrainDissolve(JavaPlugin plugin,World world,ShardSpec spec){this(plugin,world,spec,1);}
    TerrainDissolve(JavaPlugin plugin,World world,ShardSpec spec,int share){this.world=world;this.spec=spec;max=Math.max(1,Math.clamp(plugin.getConfig().getInt("realmshards.testing-dissolve-visuals",180),48,256)/Math.max(1,share));}
    void detach(Location at,BlockData ignored){
        Location origin=at.clone().add(.5,.5,.5);for(int i=0;i<7&&pieces.size()<max;i++){double angle=sequence*2.399963+i*Math.PI*2/3;spawn(origin,i==0?rubbleModel():PrismStorm.model(spec,sequence++),angle,i<2?2.1f:i<5?.9f:.35f,false);}
    }
    private String rubbleModel(){String p=spec.variant()==ShardSpec.Variant.RAINBOW?"rainbow":spec.variant()==ShardSpec.Variant.SHINY?"gold":spec.land()==null?"random":spec.land().name().toLowerCase(Locale.ROOT);return "fx/rupture_"+p;}
    void burst(List<GeneratedLand.Cell> anchors){
        int count=Math.min(32,anchors.size());for(int i=0;i<count;i++){var p=anchors.get(i*anchors.size()/count).pos();detach(new Location(world,p.x(),p.y(),p.z()),null);}
    }
    void pulse(Location at){String palette=spec.variant()==ShardSpec.Variant.RAINBOW?"rainbow":spec.variant()==ShardSpec.Variant.SHINY?"gold":spec.land()==null?"random":spec.land().name().toLowerCase(Locale.ROOT);if(pieces.size()<max)spawn(at.clone().add(0,1.5,0),"fx/pulse_"+palette,0,4,true);}
    private void spawn(Location at,String model,double angle,float size,boolean pulse){
        ItemStack item=models.computeIfAbsent(model,key->{ItemStack i=new ItemStack(Material.PAPER);var m=i.getItemMeta();m.setItemModel(new NamespacedKey("realmshards",key));i.setItemMeta(m);return i;});
        ItemDisplay d=world.spawn(at,ItemDisplay.class,e->{e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setPersistent(false);e.addScoreboardTag("realmshards_temporary");e.setBrightness(new Display.Brightness(15,15));e.setInterpolationDuration(2);e.setTeleportDuration(1);e.setViewRange(2.5f);if(pulse)e.setBillboard(Display.Billboard.CENTER);});
        d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY((float)angle),new Vector3f(pulse?.25f:size),new Quaternionf()));pieces.add(new Fragment(d,at.clone(),angle,tick,size,pulse));
    }
    void tick(){tick++;Iterator<Fragment> it=pieces.iterator();while(it.hasNext()){Fragment f=it.next();int t=tick-f.born();if(t>=30||!f.display().isValid()){f.display().remove();it.remove();continue;}
        var pose=RuptureFlight.pose(t,f.angle(),f.size(),f.pulse());Location at=f.origin().clone().add(pose.x(),pose.y(),pose.z());float size=pose.scale();
        f.display().teleport(at);f.display().setInterpolationDelay(0);f.display().setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateXYZ(f.pulse()?0:t*.10f,f.pulse()?0:(float)f.angle()+t*.13f,t*.055f),new Vector3f(size),new Quaternionf()));}}
    boolean done(){return pieces.isEmpty();}
    void close(){pieces.forEach(p->p.display().remove());pieces.clear();}
}
