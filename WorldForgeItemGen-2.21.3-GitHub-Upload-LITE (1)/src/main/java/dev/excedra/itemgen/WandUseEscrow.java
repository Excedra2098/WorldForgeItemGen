package dev.excedra.itemgen;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Real held wand is reserved before any cast display appears, then returned once. */
final class WandUseEscrow {
    final JavaPlugin plugin;final Path folder;final Map<UUID,Lease> active=new HashMap<>();final BukkitTask task;
    WandUseEscrow(JavaPlugin plugin)throws IOException{
        this.plugin=plugin;folder=plugin.getDataFolder().toPath().resolve("wand-use-escrow");Files.createDirectories(folder);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::recover,40,40);
    }
    Lease take(Player player)throws IOException{
        if(active.containsKey(player.getUniqueId()))throw new IllegalStateException("Wait for your wand to return.");
        int slot=player.getInventory().getHeldItemSlot();ItemStack actual=player.getInventory().getItem(slot);
        if(actual==null||actual.getType().isAir()||actual.getAmount()!=1)throw new IllegalStateException("Hold one wand in your main hand.");
        Lease lease=new Lease(UUID.randomUUID(),player.getUniqueId(),slot,actual.clone());
        lease.write("RESERVING");
        try{player.getInventory().setItem(slot,null);player.saveData();lease.write("ACTIVE");}
        catch(Exception e){try{player.getInventory().setItem(slot,actual);player.saveData();lease.write("DONE");}catch(Exception rollback){e.addSuppressed(rollback);}throw new IOException("Unable to reserve wand safely; check the server log.",e);}
        active.put(player.getUniqueId(),lease);return lease;
    }
    void recover(){
        try(var files=Files.list(folder)){
            for(Path file:files.filter(p->p.toString().endsWith(".properties")).toList())try{
                Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
                String state=p.getProperty("state");if(!state.equals("ACTIVE")&&!state.equals("READY"))continue;
                UUID owner=UUID.fromString(p.getProperty("owner"));if(active.containsKey(owner))continue;
                Lease lease=new Lease(UUID.fromString(p.getProperty("id")),owner,Integer.parseInt(p.getProperty("slot")),ItemStack.deserializeBytes(Base64.getDecoder().decode(p.getProperty("item"))));
                if(state.equals("ACTIVE"))lease.write("READY");lease.deliver();
            }catch(Exception e){plugin.getLogger().log(java.util.logging.Level.WARNING,"Wand return needs review: "+file,e);}
        }catch(IOException e){plugin.getLogger().warning("Unable to read wand returns: "+e.getMessage());}
    }
    void stop(){for(Lease lease:List.copyOf(active.values()))lease.close();task.cancel();}
    final class Lease implements AutoCloseable{
        final UUID id,owner;final int slot;final ItemStack item;boolean closed;
        Lease(UUID id,UUID owner,int slot,ItemStack item){this.id=id;this.owner=owner;this.slot=slot;this.item=item;}
        Path file(){return folder.resolve(id+".properties");}
        void write(String state)throws IOException{
            Properties p=new Properties();p.setProperty("id",id.toString());p.setProperty("owner",owner.toString());p.setProperty("slot",Integer.toString(slot));p.setProperty("item",Base64.getEncoder().encodeToString(item.serializeAsBytes()));p.setProperty("state",state);
            Path temp=Files.createTempFile(folder,"wand-",".tmp");try{try(var out=Files.newOutputStream(temp)){p.store(out,"WorldForge real wand reservation");}try{Files.move(temp,file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
        }
        public void close(){if(closed)return;closed=true;
            try{write("READY");active.remove(owner,this);deliver();}
            catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Reserved wand requires recovery: "+id,e);}
        }
        void deliver()throws IOException{
            Player player=Bukkit.getPlayer(owner);if(player==null||!player.isOnline()||player.isDead())return;
            int target=returnSlot(player.getInventory().getStorageContents(),slot);if(target<0)return;
            write("ISSUING"); // A crash in this save window requires inspection, never automatic duplication.
            player.getInventory().setItem(target,item.clone());player.saveData();write("DONE");
        }
    }
    static int returnSlot(ItemStack[] current,int preferred){if(preferred>=0&&preferred<current.length&&(current[preferred]==null||current[preferred].getType().isAir()))return preferred;for(int i=0;i<current.length;i++)if(current[i]==null||current[i].getType().isAir())return i;return -1;}
}
