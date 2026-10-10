package dev.excedra.itemgen;
import dev.excedra.itemgen.api.RealmWandVisuals;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Public visual service: never takes, grants or drops real inventory items. */
final class WandConvergence implements RealmWandVisuals {
    final JavaPlugin plugin;final Function<Kind,ItemStack> factory;final NamespacedKey mode,sources;final Map<UUID,Sequence> active=new HashMap<>();
    WandConvergence(JavaPlugin plugin,Function<Kind,ItemStack> factory){this.plugin=plugin;this.factory=factory;mode=new NamespacedKey(plugin,"realm_testing_tool");sources=new NamespacedKey(plugin,"realm_wand_sources");Bukkit.getServicesManager().register(RealmWandVisuals.class,this,plugin,org.bukkit.plugin.ServicePriority.Normal);}
    public ItemStack item(Kind kind){return factory.apply(kind).clone();}
    public Kind kind(ItemStack item){if(item==null||!item.hasItemMeta())return null;String value=item.getItemMeta().getPersistentDataContainer().get(mode,PersistentDataType.STRING);return value==null?null:switch(value){case "single"->Kind.SHARDBREAKER;case "all"->Kind.NULLIFIER;case "reclaim"->Kind.RECLAIMER;case "convergence"->Kind.CONVERGENCE;default->null;};}
    private List<ItemStack> ordered(List<ItemStack> originals){if(originals.size()!=3)throw new IllegalArgumentException("Three individual wands are required");var map=new EnumMap<Kind,ItemStack>(Kind.class);for(ItemStack i:originals){Kind k=kind(i);if(k==null||k==Kind.CONVERGENCE||map.put(k,i.asOne())!=null)throw new IllegalArgumentException("One ShardBreaker, Nullifier and Reclaimer are required");}return List.of(map.get(Kind.SHARDBREAKER),map.get(Kind.NULLIFIER),map.get(Kind.RECLAIMER));}
    public ItemStack mergedItem(List<ItemStack> originals){var list=ordered(originals);ItemStack result=item(Kind.CONVERGENCE);var meta=result.getItemMeta();meta.getPersistentDataContainer().set(sources,PersistentDataType.STRING,String.join(";",list.stream().map(i->Base64.getEncoder().encodeToString(i.serializeAsBytes())).toList()));result.setItemMeta(meta);return result;}
    public List<ItemStack> originalItems(ItemStack merged){if(kind(merged)!=Kind.CONVERGENCE)throw new IllegalArgumentException("A Convergence Wand is required");String encoded=merged.getItemMeta().getPersistentDataContainer().get(sources,PersistentDataType.STRING);if(encoded==null)return List.of(item(Kind.SHARDBREAKER),item(Kind.NULLIFIER),item(Kind.RECLAIMER));String[] parts=encoded.split(";",-1);if(parts.length!=3||encoded.length()>262144)throw new IllegalArgumentException("Invalid stored wand recipe");return ordered(Arrays.stream(parts).map(s->ItemStack.deserializeBytes(Base64.getDecoder().decode(s))).toList());}
    public boolean busy(Player player){return active.containsKey(player.getUniqueId());}
    public CompletableFuture<Boolean> playMerge(Player player,List<ItemStack> originals,ItemStack merged){if(kind(merged)!=Kind.CONVERGENCE)throw new IllegalArgumentException("A Convergence Wand is required");return play(player,ordered(originals),merged,false);}
    public CompletableFuture<Boolean> playUnmerge(Player player,ItemStack merged,List<ItemStack> originals){if(kind(merged)!=Kind.CONVERGENCE)throw new IllegalArgumentException("A Convergence Wand is required");return play(player,ordered(originals),merged,true);}
    private CompletableFuture<Boolean> play(Player player,List<ItemStack> originals,ItemStack merged,boolean reverse){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Call wand visuals on the server thread");if(!player.isOnline()||busy(player))throw new IllegalStateException("Player is unavailable or already in a wand animation");Sequence sequence=new Sequence(player,originals,merged,reverse);active.put(player.getUniqueId(),sequence);try{sequence.start();}catch(RuntimeException ex){sequence.finish(false);throw ex;}return sequence.result;}
    void stop(){for(Sequence s:List.copyOf(active.values()))s.finish(false);Bukkit.getServicesManager().unregister(RealmWandVisuals.class,this);}
    final class Sequence {
        final Player player;final World world;final List<ItemStack> originals;final ItemStack merged;final boolean reverse;final CompletableFuture<Boolean> result=new CompletableFuture<>();
        final List<ItemDisplay> entities=new ArrayList<>(),parts=new ArrayList<>(),fragments=new ArrayList<>();final Location center,hand;ItemDisplay combined,seed;BukkitTask task;int age;boolean finished;
        Sequence(Player p,List<ItemStack> o,ItemStack m,boolean reverse){player=p;world=p.getWorld();originals=o.stream().map(ItemStack::asOne).toList();merged=m.asOne();this.reverse=reverse;Location eye=p.getEyeLocation();Vector forward=eye.getDirection().setY(0);if(forward.lengthSquared()<.01)forward=new Vector(0,0,1);center=eye.add(forward.normalize().multiply(2)).add(0,-.2,0);hand=HeldShardDisplay.position(p,false);}
        ItemDisplay spawn(ItemStack item,Location at,float scale){ItemDisplay d=world.spawn(at,ItemDisplay.class,e->{e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);ReclaimAnimation.setup(e);e.setBrightness(new Display.Brightness(15,15));e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(scale),new Quaternionf()));});entities.add(d);return d;}
        void start(){player.swingMainHand();if(reverse)combined=spawn(merged,hand,.5f);else{
            ItemStack crystal=new ItemStack(Material.PAPER);var meta=crystal.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards","tools/convergence_fragment_2"));crystal.setItemMeta(meta);seed=spawn(crystal,center,.18f);
            for(ItemStack i:originals)parts.add(spawn(i,hand,.5f));
        }task=Bukkit.getScheduler().runTaskTimer(plugin,()->{try{tick();}catch(RuntimeException ex){plugin.getLogger().warning("Wand visual interrupted: "+ex.getMessage());finish(false);}},1,1);}
        void tick(){if(!player.isOnline()||player.isDead()||!player.getWorld().equals(world)){finish(false);return;}age++;
            int[] colors={0x73CCFF,0xCC65FF,0x72F3BF};
            if(!reverse){
                if(age<WandAbsorption.FORM){
                    seed.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY(age*.05f),new Vector3f((float)(WandAbsorption.seedScale(age)*(1+.08*Math.sin(age*.4)))),new Quaternionf()));
                    for(int i=0;i<3;i++){
                        var pose=WandAbsorption.pose(age,i);ItemDisplay d=parts.get(i);
                        if(age==40+i*18){ShatterEffects.burst(world,center,10,Color.fromRGB(colors[i]));player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.65f,1+i*.25f);}
                        if(pose.absorbed()>=1){d.remove();continue;}
                        Location at=center.clone().add(pose.x(),pose.y(),pose.z());if(age<20){double t=age/20.0;t=t*t*(3-2*t);at=hand.clone().add(at.toVector().subtract(hand.toVector()).multiply(t));}
                        d.teleport(at);d.setInterpolationDelay(0);d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateZ((float)(pose.absorbed()*1.2+(i-1)*.25)),new Vector3f((float)pose.scale()),new Quaternionf()));
                        if(pose.absorbed()>0&&age%2==0){Vector inward=center.toVector().subtract(at.toVector());for(int k=0;k<7;k++)RealmShards.dust(at.clone().add(inward.clone().multiply(k/7.0)),Color.fromRGB(colors[i]),.55f);}
                    }
                    if(age%2==0){double a=age*.16;RealmShards.dust(center.clone().add(Math.cos(a)*.22,Math.sin(a*.7)*.15,Math.sin(a)*.22),Color.fromRGB(colors[(age/18)%3]),.6f);}
                }
                if(age==WandAbsorption.FORM){seed.remove();parts.forEach(Entity::remove);combined=spawn(merged,center,.08f);ShatterEffects.burst(world,center,24,Color.fromRGB(0xB8ECFF));player.playSound(player.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,.8f,1.5f);}
                if(age>=WandAbsorption.FORM){double growth=Math.clamp((age-WandAbsorption.FORM)/18.0,0,1),t=Math.clamp((age-WandAbsorption.RETURN)/28.0,0,1);t=t*t*(3-2*t);combined.teleport(center.clone().add(HeldShardDisplay.position(player,false).toVector().subtract(center.toVector()).multiply(t)).add(0,Math.sin(t*Math.PI)*.3,0));combined.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY((float)((1-t)*age*.05)),new Vector3f((float)(.08+.42*growth)),new Quaternionf()));}
            }else{
                if(age<=24){double t=age/24.0;combined.teleport(hand.clone().add(center.toVector().subtract(hand.toVector()).multiply(t)));}
                if(age==25){ItemStack cracked=merged.clone();var meta=cracked.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards","tools/convergence_cracked"));cracked.setItemMeta(meta);combined.setItemStack(cracked);}
                if(age==37){combined.remove();ShatterEffects.burst(world,center,30,Color.fromRGB(0xD4F9E8));player.playSound(player.getLocation(),Sound.BLOCK_GLASS_BREAK,.7f,1.2f);for(ItemStack i:originals)parts.add(spawn(i,center,.1f));for(int i=0;i<18;i++){ItemStack fragment=new ItemStack(Material.PAPER);var meta=fragment.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards","tools/convergence_fragment_"+(i%3)));fragment.setItemMeta(meta);fragments.add(spawn(fragment,center,.4f));}}
                if(age>37){for(int i=0;i<3;i++){var pose=WandOrbit.unmerge(age-37,i);Location at=center.clone().add(pose.x(),pose.y(),pose.z());if(age>80){double t=(age-80)/24.0;at.add(HeldShardDisplay.position(player,false).toVector().subtract(at.toVector()).multiply(Math.min(1,t)));}ItemDisplay d=parts.get(i);d.teleport(at);d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY((float)pose.angle()),new Vector3f((float)Math.min(.5,.1+(age-37)*.015)),new Quaternionf()));if(age%2==0)RealmShards.dust(at,Color.fromRGB(colors[i]),.65f);}
                    for(int i=0;i<fragments.size();i++){ItemDisplay d=fragments.get(i);int t=age-37;double a=i*2.399963;d.teleport(center.clone().add(Math.cos(a)*t*.035,Math.sin(a*1.7)*t*.025,Math.sin(a)*t*.035));d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateXYZ(t*.1f,t*.14f,i),new Vector3f((float)Math.max(0,.4*(1-t/35.0))),new Quaternionf()));if(t>=35)d.remove();}
                }
            }
            if(age>=(reverse?104:WandAbsorption.DURATION))finish(true);
        }
        void finish(boolean ok){if(finished)return;finished=true;if(task!=null)task.cancel();entities.forEach(Entity::remove);active.remove(player.getUniqueId(),this);result.complete(ok);}
    }
}
