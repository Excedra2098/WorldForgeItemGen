package dev.excedra.itemgen;



import java.util.*;

import java.util.function.*;

import org.bukkit.*;

import org.bukkit.entity.*;

import org.bukkit.inventory.ItemStack;

import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitTask;

import org.bukkit.util.*;

import org.bukkit.util.Vector;

import org.joml.Quaternionf;

import org.joml.Vector3f;



/** Rotating visual replacement for the pack's empty first/third person hand contexts. */

final class HeldShardDisplay {

    record Hand(UUID player,boolean offhand){}

    record Held(ItemDisplay display,String key){}

    private final JavaPlugin plugin;

    private final Function<ItemStack,ShardSpec> read;
    private final Function<ItemStack,StarterRecipe> starterRead;

    private final Map<Hand,Held> held=new HashMap<>();

    private BukkitTask task;private int tick;

    HeldShardDisplay(JavaPlugin p,Function<ItemStack,ShardSpec> read,Function<ItemStack,StarterRecipe> starterRead){plugin=p;this.read=read;this.starterRead=starterRead;}

    void start(){task=Bukkit.getScheduler().runTaskTimer(plugin,this::update,2,2);}

    void stop(){if(task!=null)task.cancel();held.values().forEach(h->h.display().remove());held.clear();}

    private void update(){

        tick+=2;Set<Hand> seen=new HashSet<>();

        for(Player player:Bukkit.getOnlinePlayers())if(!player.isDead()&&!player.isInvisible()&&player.getGameMode()!=GameMode.SPECTATOR)for(boolean off:List.of(false,true)){

            ItemStack stack=off?player.getInventory().getItemInOffHand():player.getInventory().getItemInMainHand();ShardSpec spec=read.apply(stack);if(spec==null)continue;

            String appearance=stack.getItemMeta().getAsString();

            Hand hand=new Hand(player.getUniqueId(),off);seen.add(hand);Location at=position(player,off);Held current=held.get(hand);

            if(current!=null&&(!current.display().isValid()||!current.display().getWorld().equals(player.getWorld())||!current.key().equals(appearance))){current.display().remove();held.remove(hand);current=null;}

            if(current==null){ItemStack visual=stack.clone();visual.setAmount(1);ItemDisplay display=at.getWorld().spawn(at,ItemDisplay.class,d->{d.setItemStack(visual);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.addScoreboardTag("realmshards_temporary");d.addScoreboardTag("realmshards_held");d.setBrightness(new Display.Brightness(15,15));d.setInterpolationDuration(2);d.setTeleportDuration(2);d.setViewRange(1.5f);});current=new Held(display,appearance);held.put(hand,current);}

            StarterRecipe relic=starterRead.apply(stack);
            ItemDisplay display=current.display();display.teleport(at.clone().add(0,relic==null?0:Math.sin(tick*.07)*.025,0));display.setInterpolationDelay(0);display.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY(relic==null?tick*.05f:(float)Math.sin(tick*.025)*.22f).rotateZ((float)Math.sin(tick*.04)*(relic==null?.08f:.045f)),new Vector3f(relic==null?.28f:.28f+Math.min(12,relic.units())*.010f),new Quaternionf()));

            if(relic!=null&&tick%8==0){int dominant=StarterIslandDesign.dominant(relic);int[] tones={0xB793FF,0x74E7B1,0xEA66D3,0xFFD899,0x75DAED,0xE7D9AE,0xFF9D4B,0xC9A1FF,0x8AB6EC};double a=tick*.07;for(int n=0;n<3;n++)RealmShards.dust(at.clone().add(Math.cos(a+n*2.094)*.19,.12+Math.sin(a+n*2.094)*.06,Math.sin(a+n*2.094)*.10),Color.fromRGB(tones[dominant]),.22f);}
            if(tick%6==0&&spec.variant()!=ShardSpec.Variant.STANDARD)for(int n=0;n<4;n++){double angle=n*Math.PI/2+tick*.08;RealmShards.dust(at.clone().add(Math.cos(angle)*.16,Math.sin(angle)*.13,Math.sin(angle)*.16),RealmShards.color(spec,tick),.35f);}

        }

        Iterator<Map.Entry<Hand,Held>> iterator=held.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(!seen.contains(entry.getKey())){entry.getValue().display().remove();iterator.remove();}}

    }

    static Location position(Player player,boolean offhand){

        Location eye=player.getEyeLocation();Vector forward=eye.getDirection().normalize();Vector right=new Vector(-Math.cos(Math.toRadians(eye.getYaw())),0,-Math.sin(Math.toRadians(eye.getYaw())));

        boolean left=player.getMainHand()==org.bukkit.inventory.MainHand.LEFT;if(offhand)left=!left;

        return eye.add(forward.multiply(.80)).add(right.multiply(left?-.36:.36)).add(0,-.48,0);

    }

}

