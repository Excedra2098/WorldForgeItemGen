package dev.excedra.itemgen;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class ReclaimRefunds {
    final JavaPlugin plugin;final ReclaimLedger ledger;
    ReclaimRefunds(JavaPlugin plugin,Path journals)throws IOException{this.plugin=plugin;ledger=new ReclaimLedger(plugin.getDataFolder().toPath().resolve("realm-reclaim-refunds.yml"));
        // RECLAIMED is committed before queueing: recover the narrow journal/queue crash gap.
        try(var files=Files.list(journals)){for(Path path:files.filter(p->p.toString().endsWith(".yml")).toList()){
            var y=YamlConfiguration.loadConfiguration(path.toFile());if(!"RECLAIMED".equals(y.getString("status")))continue;
            String id=id(path);if(ledger.entries.containsKey(id))continue;
            ItemStack shard=y.getItemStack("reserved");String player=y.getString("reclamation.player");
            if(shard==null||player==null){plugin.getLogger().severe("Reclaim refund requires manual review: "+path);continue;}
            queue(path,UUID.fromString(player),shard);
        }}
        ledger.entries.forEach((id,e)->{if(e.state().equals("ISSUING"))plugin.getLogger().severe("Interrupted reclaim delivery "+id+" requires manual review; never auto-issued again.");});
    }
    static String id(Path path){String name=path.getFileName().toString();return name.substring(0,name.length()-4);}
    void queue(Path path,UUID player,ItemStack shard)throws IOException{ItemStack exact=shard.clone();exact.setAmount(1);ledger.queue(id(path),player,Base64.getEncoder().encodeToString(exact.serializeAsBytes()));}
    boolean deliver(Player player){if(!player.isOnline())return false;boolean delivered=false;
        for(String id:ledger.pending(player.getUniqueId())){
            int slot=player.getInventory().firstEmpty();if(slot<0)break;
            try{
                // Decode before marking ISSUING; reserve one exact slot rather than dropping rewards.
                ItemStack shard=ItemStack.deserializeBytes(Base64.getDecoder().decode(ledger.entries.get(id).shard()));shard.setAmount(1);
                ledger.begin(id);player.getInventory().setItem(slot,shard);ledger.complete(id);delivered=true;
            }catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Reclaim refund delivery requires review: "+id,ex);break;}
        }
        return delivered;
    }
}
