package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A bounded, temporary wand gesture and beam cast; terrain work begins after impact. */
final class WandCast implements AutoCloseable {
    static final int DURATION=44,IMPACT=22;
    final World world;final Player owner;final boolean all;final List<Location> targets;
    final Location hand,ground;final ItemDisplay wand;final Runnable impactSound;final boolean nativeBeams;final List<ItemDisplay> beams=new ArrayList<>();int age;boolean closed;
    WandCast(World world,Player owner,ItemStack item,boolean all,List<Location> targets){
        this(world,owner,item,all,targets,
            ()->owner.playSound(owner.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.8f,all?.6f:1.2f),
            ()->owner.playSound(owner.getLocation(),all?Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE:Sound.BLOCK_BEACON_ACTIVATE,1,all?.45f:1.5f));
    }
    WandCast(World world,Player owner,ItemStack item,boolean all,List<Location> targets,Runnable chargeSound,Runnable impactSound){
        this.impactSound=impactSound;nativeBeams=item!=null;
        this.world=world;this.owner=owner;this.all=all;this.targets=targets.stream().map(Location::clone).toList();
        hand=HeldShardDisplay.position(owner,false);ground=owner.getLocation().clone();
        // The jab remains beside the caster, even when the ground is outside a test placement.
        Vector forward=owner.getLocation().getDirection().setY(0);if(forward.lengthSquared()<.01)forward=new Vector(0,0,1);forward.normalize();ground.add(forward.multiply(.65)).add(0,.15,0);
        wand=world.spawn(hand,ItemDisplay.class,d->{d.setItemStack(item);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.addScoreboardTag("realmshards_temporary");d.setBrightness(new Display.Brightness(15,15));d.setTeleportDuration(1);d.setInterpolationDuration(1);});
        owner.swingMainHand();chargeSound.run();
    }
    static double jab(int tick){double t=Math.clamp((tick-8)/14.0,0,1);return t*t;}
    boolean tick(){
        if(closed)return true;if(!owner.isOnline()||!owner.getWorld().equals(world))throw new IllegalStateException("Wand caster left the world.");age++;
        Location at;
        if(all){double t=jab(age);at=hand.clone().add(ground.toVector().subtract(hand.toVector()).multiply(t));
            wand.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateZ((float)(-.45*(1-t))),new Vector3f(.7f),new Quaternionf()));
        }else{at=hand.clone();at.setDirection(targets.getFirst().toVector().subtract(at.toVector()));
            wand.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateX((float)Math.PI/2),new Vector3f(.65f),new Quaternionf()));}
        wand.teleport(at);
        if(age==IMPACT){owner.swingMainHand();impactSound.run();}
        if(age>=IMPACT){
            Location origin=all?ground.clone().add(0,.25,0):at;
            Color c=Color.fromRGB(all?0x100B18:0xB64DFF);
            // Every target has a beam on the same tick; total sample density is bounded.
            int samples=Math.max(2,Math.min(96,480/Math.max(1,targets.size())));
            double progress=Math.min(1,(age-IMPACT+1)/8.0);
            if(nativeBeams&&beams.isEmpty()){
                ItemStack beamItem=new ItemStack(Material.PAPER);var meta=beamItem.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards",all?"tools/beam_all":"tools/beam_single"));beamItem.setItemMeta(meta);
                for(Location target:targets)beams.add(world.spawn(origin,ItemDisplay.class,d->{d.setItemStack(beamItem);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.addScoreboardTag("realmshards_temporary");d.setBrightness(new Display.Brightness(15,15));d.setViewRange(8);d.setTeleportDuration(1);d.setInterpolationDuration(1);}));
            }
            for(int n=0;n<beams.size();n++){
                Vector delta=targets.get(n).toVector().subtract(origin.toVector()).multiply(progress);double length=delta.length();if(length<.001)continue;
                ItemDisplay beam=beams.get(n);beam.teleport(origin.clone().add(delta.clone().multiply(.5)));
                Vector direction=delta.clone().normalize();Quaternionf rotation=new Quaternionf().rotateTo(new Vector3f(0,1,0),new Vector3f((float)direction.getX(),(float)direction.getY(),(float)direction.getZ()));
                beam.setInterpolationDelay(0);beam.setTransformation(new Transformation(new Vector3f(),rotation,new Vector3f(.7f,(float)length,.7f),new Quaternionf()));
            }
            for(Location target:targets){Vector delta=target.toVector().subtract(origin.toVector());
                for(int k=0;k<samples;k++){double t=progress*k/(samples-1.0);Location point=origin.clone().add(delta.clone().multiply(t));world.spawnParticle(Particle.DUST,point,1,0,0,0,0,new Particle.DustOptions(c,all?1.65f:1.05f));}
                if(age==IMPACT+8){world.spawnParticle(Particle.DUST,target,14,1,1,1,0,new Particle.DustOptions(c,1.7f));}
            }
            if(all){for(int k=0;k<16;k++){double a=k*Math.PI/8;world.spawnParticle(Particle.DUST,ground.clone().add(Math.cos(a)*1.2,.12,Math.sin(a)*1.2),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(0x63338D),.8f));}}
        }
        if(age>=DURATION){close();return true;}return false;
    }
    public void close(){if(closed)return;closed=true;wand.remove();beams.forEach(Entity::remove);beams.clear();}
}
