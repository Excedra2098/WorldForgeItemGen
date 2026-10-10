package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Custom alpha-masked rift skins on recorded terrain; no vanilla breaking overlays. */
final class FractureSkin {
    static final class Skin {final GeneratedLand.Cell cell;final ItemDisplay display;int stage;Skin(GeneratedLand.Cell c,ItemDisplay d,int s){cell=c;display=d;stage=s;}}
    final World world;final ShardSpec spec;final List<GeneratedLand.Cell> anchors;
    final List<Skin> skins=new ArrayList<>();final Map<Integer,ItemStack> models=new HashMap<>();int cursor;
    FractureSkin(JavaPlugin plugin,World world,GeneratedLand land,List<GeneratedLand.Cell> eligible,ShardSpec spec,long seed){this(plugin,world,land,eligible,spec,seed,1);}
    FractureSkin(JavaPlugin plugin,World world,GeneratedLand land,List<GeneratedLand.Cell> eligible,ShardSpec spec,long seed,int share){this.world=world;this.spec=spec;int limit=Math.max(1,Math.clamp(plugin.getConfig().getInt("realmshards.testing-fracture-panels",192),48,256)/Math.max(1,share));
        anchors=FractureLayout.anchors(land,eligible,limit,seed);
    }
    static int stage(double phase){return Math.clamp(1+(int)(phase*5),1,5);}
    static String model(ShardSpec spec,int stage){String p=spec.variant()==ShardSpec.Variant.RAINBOW?"rainbow":spec.variant()==ShardSpec.Variant.SHINY?"gold":spec.land()==null?"random":spec.land().name().toLowerCase(Locale.ROOT);return "fx/rift_"+p+"_s"+stage;}
    ItemStack item(int stage){return models.computeIfAbsent(stage,s->{ItemStack i=new ItemStack(Material.PAPER);var m=i.getItemMeta();m.setItemModel(new NamespacedKey("realmshards",model(spec,s)));i.setItemMeta(m);return i;});}
    void update(double phase){int stage=stage(phase);for(int n=0;n<8&&cursor<anchors.size();n++){var c=anchors.get(cursor++);var b=world.getBlockAt(c.pos().x(),c.pos().y(),c.pos().z());if(!c.removable(b.getBlockData().getAsString()))continue;
            ItemDisplay d=world.spawn(b.getLocation().add(.5,.5,.5),ItemDisplay.class,e->{e.setItemStack(item(stage));e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setPersistent(false);e.addScoreboardTag("realmshards_temporary");e.setBrightness(new Display.Brightness(15,15));e.setViewRange(2.5f);});d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(1.012f),new Quaternionf()));skins.add(new Skin(c,d,stage));}
        Iterator<Skin> iterator=skins.iterator();while(iterator.hasNext()){Skin skin=iterator.next();var p=skin.cell.pos();if(!skin.cell.removable(world.getBlockAt(p.x(),p.y(),p.z()).getBlockData().getAsString())){skin.display.remove();iterator.remove();continue;}if(skin.stage!=stage){skin.display.setItemStack(item(stage));skin.stage=stage;}}
    }
    void close(){skins.forEach(s->s.display.remove());skins.clear();}
}
