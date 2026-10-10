package dev.excedra.itemgen;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Transaction-keyed durable refunds. Uncertain interrupted deliveries never auto-issue twice. */
final class ReclaimLedger {
    record Entry(UUID player,String shard,String state){}
    final Path file;final Map<String,Entry> entries=new LinkedHashMap<>();
    ReclaimLedger(Path file)throws IOException{this.file=file;Files.createDirectories(file.getParent());var y=YamlConfiguration.loadConfiguration(file.toFile());
        for(String id:y.getKeys(false)){String state=y.getString(id+".state");if(!Set.of("PENDING","ISSUING","DELIVERED").contains(state))throw new IOException("Invalid reclaim delivery state: "+id);entries.put(id,new Entry(UUID.fromString(y.getString(id+".player")),Objects.requireNonNull(y.getString(id+".shard")),state));}}
    boolean queue(String id,UUID player,String shard)throws IOException{
        if(!id.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid reclamation transaction id");
        Entry old=entries.get(id);if(old!=null){if(!old.player().equals(player)||!old.shard().equals(shard))throw new IllegalStateException("Refund receipt differs from this transaction");return false;}
        entries.put(id,new Entry(player,shard,"PENDING"));try{save();}catch(IOException ex){entries.remove(id);throw ex;}return true;
    }
    List<String> pending(UUID player){return entries.entrySet().stream().filter(e->e.getValue().player().equals(player)&&e.getValue().state().equals("PENDING")).map(Map.Entry::getKey).toList();}
    Entry begin(String id)throws IOException{Entry e=entries.get(id);if(e==null||!e.state().equals("PENDING"))throw new IllegalStateException("Refund is not pending");change(id,e,"ISSUING");return e;}
    void complete(String id)throws IOException{Entry e=entries.get(id);if(e==null||!e.state().equals("ISSUING"))throw new IllegalStateException("Refund is not issuing");change(id,e,"DELIVERED");}
    private void change(String id,Entry e,String state)throws IOException{entries.put(id,new Entry(e.player(),e.shard(),state));try{save();}catch(IOException ex){entries.put(id,e);throw ex;}}
    private void save()throws IOException{var y=new YamlConfiguration();entries.forEach((id,e)->{y.set(id+".player",e.player().toString());y.set(id+".shard",e.shard());y.set(id+".state",e.state());});RealmShards.atomicSave(y,file);}
}
