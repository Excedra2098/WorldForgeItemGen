package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Cosmetic projection only: the actual inventory exchange finishes synchronously. */
final class StarterFusionEffect {
    final JavaPlugin plugin;final Map<UUID,Runnable> cleanups=new HashMap<>();
    StarterFusionEffect(JavaPlugin p){plugin=p;}
    void stop(){List.copyOf(cleanups.values()).forEach(Runnable::run);}
    void play(Player player,ItemStack left,ItemStack right,ItemStack result){
        if(cleanups.containsKey(player.getUniqueId()))cleanups.get(player.getUniqueId()).run();
        if(cleanups.size()>=24)return;
        Location center=player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.9));
        List<ItemDisplay> displays=new ArrayList<>();
        try{for(ItemStack item:List.of(left,right,result))displays.add(center.getWorld().spawn(center,ItemDisplay.class,d->{d.setItemStack(item.asOne());d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.addScoreboardTag("realmshards_temporary");d.setBrightness(new Display.Brightness(15,15));d.setInterpolationDuration(2);d.setTeleportDuration(1);}));}catch(RuntimeException ex){displays.forEach(Entity::remove);plugin.getLogger().warning("Starter fusion projection could not start: "+ex.getMessage());return;}
        BukkitTask[] task={null};int[] age={0};Runnable cleanup=()->{if(task[0]!=null)task[0].cancel();displays.forEach(Entity::remove);cleanups.remove(player.getUniqueId());};cleanups.put(player.getUniqueId(),cleanup);
        task[0]=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            try{
                int t=age[0]++;if(t>=44||!player.isOnline()||player.isDead()||!player.getWorld().equals(center.getWorld())){cleanup.run();return;}
                for(int i=0;i<2;i++){
                    double a=t*.22+i*Math.PI,rad=.65*Math.max(0,1-t/25.0);ItemDisplay d=displays.get(i);
                    d.teleport(center.clone().add(Math.cos(a)*rad,Math.sin(a)*rad*.45,Math.sin(a)*rad));
                    transform(d,t<25?.24f:0,t*.13f);
                    if(t<25)RealmShards.dust(d.getLocation(),i==0?Color.fromRGB(78,228,199):Color.fromRGB(196,129,255),.5f);
                }
                transform(displays.get(2),t<20?0:.18f+Math.min(1,(t-20)/9f)*.22f,t*.035f);
                if(t==22){player.playSound(center,Sound.BLOCK_BEACON_ACTIVATE,.7f,1.45f);player.playSound(center,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1f,.65f);}
                if(t>=22&&t%2==0)for(int n=0;n<12;n++){double a=n*Math.PI/6+t*.07,rad=.35+(t-22)*.017;RealmShards.dust(center.clone().add(Math.cos(a)*rad,Math.sin(a)*rad,0),Color.fromRGB(230,199,116),.4f);}
            }catch(RuntimeException ex){cleanup.run();plugin.getLogger().warning("Starter fusion projection ended: "+ex.getMessage());}
        },1,1);
    }
    static void transform(ItemDisplay d,float scale,float angle){d.setInterpolationDelay(0);d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY(angle),new Vector3f(scale),new Quaternionf()));}
}
