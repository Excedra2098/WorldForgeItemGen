package dev.excedra.itemgen;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
/** Enrolls only new WorldForge records, and installs once after generation reaches READY. */
final class CoreAutomatic implements Listener {
 interface Creator{void create(Player owner,IslandBridge.Realm realm)throws Exception;}
 private final JavaPlugin plugin;private final RealmCoreStore store;private final IslandBridge islands;private final Creator creator;private final BukkitTask task;private final Map<UUID,String> warnings=new HashMap<>();private String hostWarning="";
 CoreAutomatic(JavaPlugin plugin,RealmCoreStore store,IslandBridge islands,Creator creator){this.plugin=plugin;this.store=store;this.islands=islands;this.creator=creator;Bukkit.getPluginManager().registerEvents(this,plugin);tick();task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,20);}
 private List<String> list(String key,List<String> defaults){var values=plugin.getConfig().getStringList("realmcore.automatic."+key);return values.isEmpty()?defaults:values;}
 void tick(){if(!plugin.getConfig().getBoolean("realmcore.automatic.enabled",true))return;
  try{var records=islands.records();if(!store.autoInitialized())store.autoInitialize(records.stream().map(r->r.realm().id()).toList());hostWarning="";
   int completed=0;for(var row:records){var realm=row.realm();try{
    String state=store.autoState(realm.id());if(state.isEmpty()){store.autoState(realm.id(),"WAITING");state="WAITING";}if(!CoreAutoPolicy.pending(state))continue;
    if(store.get(realm.id())!=null){store.autoState(realm.id(),"PLACED");continue;}
    Player owner=Bukkit.getPlayer(realm.owner());if(owner==null||!owner.isOnline()||owner.isDead())continue;
    if(owner.hasPermission("worldforge.realmcore.manual")){store.autoState(realm.id(),"MANUAL");continue;}
    var groups=CorePlayerGroups.groups(owner);if(!CoreAutoPolicy.eligible(groups,list("staff-groups",List.of("Admin","Head-Admin","Owner")),list("player-groups",List.of("default")))){store.autoState(realm.id(),"MANUAL");continue;}
    if(!owner.hasPermission("worldforge.realmcore.automatic")){store.autoState(realm.id(),"MANUAL");continue;}
    if(!CoreAutoPolicy.starter(islands.templateKeys(realm),list("templates",List.of("01_terraced_rune_garden","02_sunrise_lily_basin","03_blossom_pergola_sanctuary","04_riverbridge_rune_garden")))){store.autoState(realm.id(),"SKIPPED");continue;}
    if(!row.phase().equals("READY")||Bukkit.getWorld(realm.world())==null)continue;
    creator.create(owner,realm);warnings.remove(realm.id());if(++completed>=1)break;
   }catch(Exception failure){String message=String.valueOf(failure.getMessage());if(!message.equals(warnings.put(realm.id(),message)))plugin.getLogger().warning("Automatic Core waiting for "+realm.id()+": "+message);}}
  }catch(Exception failure){String message=String.valueOf(failure.getMessage());if(!message.equals(hostWarning)){hostWarning=message;plugin.getLogger().warning("Automatic Core integration waits: "+message);}}
 }
 @EventHandler(priority=EventPriority.MONITOR) public void entered(PlayerChangedWorldEvent event){tick();}
 @EventHandler(priority=EventPriority.MONITOR) public void joined(PlayerJoinEvent event){Bukkit.getScheduler().runTask(plugin,this::tick);}
 void retry(UUID realm)throws java.io.IOException{String state=store.autoState(realm);if(!Set.of("FAILED","EXISTING","WAITING").contains(state)||store.get(realm)!=null)throw new IllegalStateException("This Realm cannot be retried automatically; placed, reclaimed, manual and interrupted Cores stay protected.");store.autoState(realm,"WAITING");warnings.remove(realm);tick();}
 void stop(){task.cancel();}
}
