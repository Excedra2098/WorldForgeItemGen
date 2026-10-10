package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Temporary, non-dropping crystal projection; terrain commits separately. */
final class CoreManifestation {
    static final int DURATION=100;
    static final int RETURN_DURATION=32;
    static final int SEQUENCE_DURATION=160, LAUNCH_DURATION=36, FRAGMENT_COUNT=32;
    record Fragment(float x,float y,float z,float scale){}
    private static float smooth(double value){float t=(float)Math.clamp(value,0,1);return t*t*(3-2*t);}
    static float crystalScale(int tick,boolean reclaim){
        if(reclaim)return tick<64?1-smooth((tick-12)/48d):.8f*smooth((tick-112)/38d);
        if(tick<=LAUNCH_DURATION)return .8f-.38f*(1-smooth(tick/(double)LAUNCH_DURATION));
        if(tick<76)return .8f*(1-smooth((tick-LAUNCH_DURATION)/40d));
        return smooth((tick-112)/42d);
    }
    static int terrainTick(int tick,boolean reclaim){return reclaim?Math.min(DURATION,tick):tick<76?0:Math.clamp(20+(tick-76)*80/(SEQUENCE_DURATION-76),0,DURATION);}
    static Fragment fragment(int index,int tick,boolean reclaim){
        double angle=index*Math.PI*(3-Math.sqrt(5));
        float radius=1.5f+(index%5)*.26f;
        float ox=(float)Math.cos(angle+tick*.025)*radius,oz=(float)Math.sin(angle+tick*.025)*radius;
        float oy=(index%9-4)*.28f;
        float size=.045f+(index%4)*.011f;
        if(reclaim){
            float release=smooth((tick-16-index%8*3)/24d),gather=smooth((tick-80-index%8*2)/46d);
            float sourceX=(float)Math.cos(angle)*.34f,sourceZ=(float)Math.sin(angle)*.34f,sourceY=(index%7-3)*.34f;
            return new Fragment((sourceX+(ox-sourceX)*release)*(1-gather),(sourceY+(oy-sourceY)*release)*(1-gather),(sourceZ+(oz-sourceZ)*release)*(1-gather),size*release*(1-gather));
        }
        float release=smooth((tick-LAUNCH_DURATION-index%8*3)/22d),assemble=smooth((tick-80-index%8*2)/46d);
        // Fragment tips settle along the main crystal and its unequal satellites.
        float tx=index%4==0?-.61f:index%4==1?.61f:(float)Math.cos(angle)*.22f;
        float tz=index%4==0?-.12f:index%4==1?.12f:index%4==2?.56f:(float)Math.sin(angle)*.22f;
        float ty=(index%7-3)*.32f;
        return new Fragment(ox*release+(tx-ox*release)*assemble,oy*release+(ty-oy*release)*assemble,oz*release+(tz-oz*release)*assemble,size*release*(1-smooth((tick-126)/24d)));
    }
    record Flight(double x,double y,double z,float scale){}
    static Flight returnFlight(double sx,double sy,double sz,double tx,double ty,double tz,int tick){
        double progress=Math.clamp(tick/(double)RETURN_DURATION,0,1);
        if(progress>=1)return new Flight(tx,ty,tz,.16f);
        double arc=Math.min(3,Math.hypot(tx-sx,tz-sz)*.12);
        return new Flight(sx+(tx-sx)*progress,sy+(ty-sy)*progress+Math.sin(progress*Math.PI)*arc,sz+(tz-sz)*progress,(float)(.8-.64*progress));
    }
    record Pose(float x,float y,float z,float scale){}
    static Pose pose(TerrainPlan.Pos cell,int tick,boolean reclaim){
        tick=Math.clamp(tick,0,DURATION);
        int hash=Math.floorMod(cell.x()*31+cell.y()*17+cell.z()*13,19);
        if(reclaim){
            float release=Math.clamp((tick-12-hash)/22f,0,1);
            float gather=Math.clamp((tick-48-hash)/28f,0,1);gather=gather*gather*(3-2*gather);
            double angle=tick*.12+hash;
            float x=cell.x()+.5f+(float)Math.cos(angle)*release*1.6f;
            float y=cell.y()+.5f+release*(2+hash*.06f);
            float z=cell.z()+.5f+(float)Math.sin(angle)*release*1.6f;
            float scale=(1-release*.7f)*(1-gather);
            return new Pose(x*(1-gather)-scale/2,y+(5-y)*gather-scale/2,z*(1-gather)-scale/2,scale);
        }
        float assemble=Math.clamp((tick-40-hash)/25f,0,1);
        if(assemble>=1)return new Pose(cell.x(),cell.y(),cell.z(),1);
        float eased=assemble*assemble*(3-2*assemble);
        double angle=tick*.13+hash;
        float orbitX=(float)Math.cos(angle)*(2+hash*.08f),orbitZ=(float)Math.sin(angle)*(2+hash*.08f);
        float orbitY=6+(float)Math.sin(angle*.7)*1.4f;
        float scale=tick<20?0:.16f+.84f*eased;
        return new Pose(orbitX+(cell.x()+.5f-orbitX)*eased-scale/2,orbitY+(cell.y()+.5f-orbitY)*eased-scale/2,orbitZ+(cell.z()+.5f-orbitZ)*eased-scale/2,scale);
    }
    private final List<Display> displays=new ArrayList<>();
    private final List<TerrainPlan.Pos> cells=new ArrayList<>();
    private final Location origin;
    private final boolean reclaim;
    private BukkitRunnable task;
    private Display crystal;
    private final boolean custom;
    private final boolean compact;
    private final double heartY;
    private final CoreAppearance.Profile profile;
    private Location flightStart;
    private Player returnRecipient;
    private final List<Display> splinters=new ArrayList<>();
    void flightFrom(Location hand){flightStart=hand.clone();crystal.teleport(hand);}
    void returnTo(Player staff){returnRecipient=staff;}
    CoreManifestation(World world,RealmCoreStore.Core core,Map<TerrainPlan.Pos,Material> blocks,boolean reclaim,CoreAppearance.Profile profile){
        if(core.design()==4)blocks=RaisedCorePadLayout.visible(blocks);
        if(blocks.size()>1024)throw new IllegalStateException("Core projection exceeds its safe visual limit.");
        origin=new Location(world,core.x(),core.y(),core.z());this.reclaim=reclaim;this.profile=profile;custom=core.design()>=1;compact=core.design()==5;heartY=compact?CompactCoreLayout.CRYSTAL_Y:5;
        try{
            crystal=crystal(world,origin.clone().add(.5,heartY,.5));
            crystalPose(0);
            for(int i=0;i<FRAGMENT_COUNT;i++){
                Display shard=crystal(world,origin.clone().add(.5,heartY,.5));
                if(shard instanceof ItemDisplay item)item.setItemStack(CompactCoreLayout.model("fragment_"+profile.get(i%4<2?"secondary":"primary")));
                shard.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(0),new Quaternionf()));splinters.add(shard);
            }
            for(var entry:blocks.entrySet()){
                var cell=entry.getKey();
                Display display;
                if(core.design()==5||custom&&CoreShrineDesign.stone(entry.getValue()))display=world.spawn(origin,ItemDisplay.class,d->{d.setItemStack(core.design()==5?CompactCoreLayout.model("pedestal_tile"):CoreShrineDesign.model(entry.getValue()==Material.CRACKED_DEEPSLATE_BRICKS?"raised_cracked":"stone"));d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.setInvulnerable(true);d.addScoreboardTag("realmshards_temporary");d.setInterpolationDuration(2);d.setViewRange(2);apply(d,pose(cell,0,reclaim));});
                else display=world.spawn(origin,BlockDisplay.class,d->{
                    d.setBlock(entry.getValue().createBlockData());d.setPersistent(false);d.setInvulnerable(true);d.addScoreboardTag("realmshards_temporary");
                    d.setBrightness(new Display.Brightness(15,15));d.setInterpolationDuration(2);d.setViewRange(2);
                    apply(d,pose(cell,0,reclaim));
                });displays.add(display);cells.add(cell);
            }
        }catch(RuntimeException failure){close();throw failure;}
    }
    void start(JavaPlugin plugin,Runnable complete,java.util.function.Consumer<Exception> failed){
        Location launch=flightStart==null?origin.clone().add(.5,2,.5):flightStart;
        origin.getWorld().playSound(launch,reclaim?Sound.BLOCK_AMETHYST_BLOCK_BREAK:Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,1.5f,reclaim?.65f:.55f);

        task=new BukkitRunnable(){int tick;
            public void run(){
                try{
                    tick+=2;
                    if(reclaim&&returnRecipient!=null&&tick>SEQUENCE_DURATION){
                        if(returnCrystal(tick-SEQUENCE_DURATION)){complete.run();close();}return;
                    }
                    if(!crystal.isValid())throw new IllegalStateException("Core crystal projection interrupted.");
                    crystalPose(tick);
                    Location heart=origin.clone().add(.5,heartY,.5);
                    if(!reclaim&&flightStart!=null){double travel=smooth(tick/(double)LAUNCH_DURATION);Location at=flightStart.clone().add(heart.toVector().subtract(flightStart.toVector()).multiply(travel)).add(0,Math.sin(travel*Math.PI)*2,0);crystal.teleport(at);if(tick<=LAUNCH_DURATION)RealmShards.dust(at,Color.fromRGB(CoreAppearance.color(profile.get("primary"))),.65f);}
                    for(int i=0;i<splinters.size();i++){
                        var shard=splinters.get(i);if(!shard.isValid())throw new IllegalStateException("Core fragment projection interrupted.");var frame=fragment(i,tick,reclaim);
                        shard.setInterpolationDelay(0);shard.setTransformation(new Transformation(new Vector3f(frame.x(),frame.y(),frame.z()),new Quaternionf().rotateXYZ(tick*.045f+i,i*.3f,tick*.04f),new Vector3f(frame.scale()),new Quaternionf()));
                    }
                    for(int i=0;i<displays.size();i++){var d=displays.get(i);if(!d.isValid())throw new IllegalStateException("Core projection interrupted.");d.setInterpolationDelay(0);var frame=pose(cells.get(i),terrainTick(tick,reclaim),reclaim);float spin=frame.scale()>=.999f?0:tick*.09f+i*.3f;apply(d,frame,spin);}
                    double radius=reclaim?3*(1-tick/(double)SEQUENCE_DURATION)+.2:tick<76?2:3;
                    for(int i=0;i<16;i++){double a=i*Math.PI/8+tick*.10;RealmShards.dust(origin.clone().add(.5+Math.cos(a)*radius,tick>=80&&!reclaim?1.2:5+Math.sin(a*2)*.5,.5+Math.sin(a)*radius),Color.fromRGB(i%2==0?0xB38CFF:0x7CF3EF),.8f);}
                    if(reclaim&&tick>=14&&tick<=78&&tick%8==0){origin.getWorld().playSound(origin,Sound.BLOCK_AMETHYST_BLOCK_BREAK,1.2f,.65f+Math.floorMod(tick,24)/40f);if(tick%16==0)origin.getWorld().playSound(origin,Sound.BLOCK_GLASS_BREAK,.55f,.75f);}
                    if(!reclaim&&tick%20==0)origin.getWorld().playSound(origin,Sound.BLOCK_BEACON_AMBIENT,1.3f,.5f+tick/200f);
                    if(tick%12==0)origin.getWorld().playSound(origin,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.7f,reclaim?1.7f-tick/160f:.6f+tick/200f);
                    if(tick==SEQUENCE_DURATION)origin.getWorld().playSound(origin,reclaim?Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE:Sound.BLOCK_BEACON_ACTIVATE,1.8f,reclaim?.8f:.55f);
                    if(tick>=SEQUENCE_DURATION){
                        if(reclaim&&returnRecipient!=null){displays.forEach(Entity::remove);displays.clear();cells.clear();splinters.forEach(Entity::remove);splinters.clear();crystal.setTeleportDuration(2);}
                        else{complete.run();close();}
                    }
                }catch(Exception failure){close();failed.accept(failure);}
            }
        };
        try{task.runTaskTimer(plugin,2,2);}catch(RuntimeException failure){close();throw failure;}
    }
    private Display crystal(World world,Location at){
        if(custom)return world.spawn(at,ItemDisplay.class,d->{d.setItemStack(compact?CompactCoreLayout.model(CompactCoreLayout.crystalModel(profile)):CoreShrineDesign.model("crystal"));d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.setInvulnerable(true);d.addScoreboardTag("realmshards_temporary");d.setBrightness(new Display.Brightness(15,15));d.setInterpolationDuration(2);d.setTeleportDuration(2);d.setViewRange(2);});
        return world.spawn(at,BlockDisplay.class,d->{d.setBlock(Material.AMETHYST_BLOCK.createBlockData());d.setPersistent(false);d.setInvulnerable(true);d.addScoreboardTag("realmshards_temporary");d.setBrightness(new Display.Brightness(15,15));d.setInterpolationDuration(2);});
    }
    private static void apply(Display display,Pose pose){apply(display,pose,0);}
    private static void apply(Display display,Pose pose,float spin){float center=display instanceof ItemDisplay?pose.scale()/2:0;display.setTransformation(new Transformation(new Vector3f(pose.x()+center,pose.y()+center,pose.z()+center),new Quaternionf().rotateXYZ(spin*.7f,spin,spin*.3f),new Vector3f(pose.scale()),new Quaternionf()));}
    private boolean returnCrystal(int tick){
        if(tick>RETURN_DURATION)return true;
        if(!returnRecipient.isOnline()||returnRecipient.isDead()||!returnRecipient.getWorld().equals(origin.getWorld())||!crystal.isValid())return true;
        Location target=HeldShardDisplay.position(returnRecipient,false),heart=origin.clone().add(.5,heartY,.5);
        if(target.distanceSquared(heart)>128*128)return true;
        Flight flight=returnFlight(heart.getX(),heart.getY(),heart.getZ(),target.getX(),target.getY(),target.getZ(),tick);
        Location at=new Location(origin.getWorld(),flight.x(),flight.y(),flight.z());
        crystal.teleport(at);crystal.setInterpolationDelay(0);
        Quaternionf rotation=new Quaternionf().rotateY(tick*.18f).rotateZ((float)Math.PI/4);
        Vector3f offset=custom?new Vector3f():rotation.transform(new Vector3f(-flight.scale()/2,-flight.scale()/2,-flight.scale()/2));
        crystal.setTransformation(new Transformation(offset,rotation,new Vector3f(flight.scale()),new Quaternionf()));
        RealmShards.dust(at,Color.fromRGB(0xBB8AFF),.8f);RealmShards.dust(at.clone().add(0,.12,0),Color.fromRGB(0x7CF3EF),.45f);
        if(tick>=RETURN_DURATION)origin.getWorld().playSound(target,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.7f,1.6f);return false;
    }
    private void crystalPose(int tick){float scale=crystalScale(tick,reclaim);Quaternionf rotation=new Quaternionf().rotateY(tick*.10f);if(!compact)rotation.rotateZ((float)Math.PI/4);Vector3f offset=custom?new Vector3f():rotation.transform(new Vector3f(-scale/2,-scale/2,-scale/2));crystal.setInterpolationDelay(0);crystal.setTransformation(new Transformation(offset,rotation,new Vector3f(scale),new Quaternionf()));}
    void close(){if(task!=null)task.cancel();if(crystal!=null)crystal.remove();splinters.forEach(Entity::remove);splinters.clear();displays.forEach(Entity::remove);displays.clear();cells.clear();}
}
