package dev.excedra.itemgen;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Owner-only file browser; content-addressed snapshots survive source edits/deletion. */
final class SchematicForge implements Listener {
    record Template(String id,String name,SchematicData.Data data){}
    final JavaPlugin plugin;final RealmShards shards;final ShardAccess access;final NamespacedKey key;
    final Path inputs,library;final Map<String,Template> templates=new HashMap<>();final Set<UUID> loading=new HashSet<>();
    final ExecutorService io=Executors.newSingleThreadExecutor();boolean stopped;
    SchematicForge(JavaPlugin plugin,RealmShards shards,ShardAccess access)throws IOException{
        this.plugin=plugin;this.shards=shards;this.access=access;key=new NamespacedKey(plugin,"schematic_template");
        inputs=plugin.getDataFolder().toPath().resolve("schematics");library=plugin.getDataFolder().toPath().resolve("shard-library");Files.createDirectories(inputs);Files.createDirectories(library);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        io.submit(()->{try(var files=Files.list(library)){for(Path file:files.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.schem")).toList()){
            try{var data=SchematicData.read(file);String id=file.getFileName().toString().replace(".schem","");if(!id.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))))throw new IOException("Library snapshot was modified.");String name=Files.exists(library.resolve(id+".txt"))?Files.readString(library.resolve(id+".txt")):"Custom Realm";
                Bukkit.getScheduler().runTask(plugin,()->{if(!stopped)try{validate(data);templates.put(id,new Template(id,name,data));}catch(Exception e){plugin.getLogger().warning("Template rejected: "+e.getMessage());}});
            }catch(Exception e){plugin.getLogger().warning("Template could not load: "+file.getFileName()+" "+e.getMessage());}
        }}catch(IOException e){plugin.getLogger().warning("Cannot read schematic library: "+e.getMessage());}});
    }
    void stop(){stopped=true;io.shutdownNow();}
    String id(ItemStack item){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);}
    TerrainPlan plan(ItemStack item,ShardSpec spec){String id=id(item);if(id==null)return null;Template t=templates.get(id);if(t==null)throw new IllegalStateException("Custom template is not ready or is missing. Ask an Owner to restore its library file.");return new TerrainPlan(t.data(),spec.land());}
    void validate(SchematicData.Data data){for(String state:new HashSet<>(data.states().values()))Bukkit.createBlockData(state);}
    static final class Menu implements InventoryHolder {
        final UUID player;final List<Path> files;final int page;final Path selected;final ShardSpec spec;Inventory inventory;
        Menu(Player p,List<Path> files,int page,Path selected,ShardSpec spec){player=p.getUniqueId();this.files=files;this.page=page;this.selected=selected;this.spec=spec;}
        public Inventory getInventory(){return inventory;}
    }
    void browse(Player p){if(!access.owner(p))throw new IllegalStateException("Only the Owner rank can create schematic shards.");
        p.sendMessage("§bReading schematic files…");io.submit(()->{try(var files=Files.list(inputs)){List<Path> list=files.filter(f->Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)&&f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schem")).sorted().limit(500).toList();
            Bukkit.getScheduler().runTask(plugin,()->{if(!stopped&&p.isOnline()&&access.owner(p))open(p,list,0,null,new ShardSpec(3,ShardSpec.Land.FOREST,ShardSpec.Variant.STANDARD));});
        }catch(Exception e){plugin.getLogger().warning("Schematic listing failed: "+e.getMessage());}});
    }
    void open(Player p,List<Path> files,int page,Path selected,ShardSpec spec){if(!access.owner(p)){p.closeInventory();return;}
        Menu menu=new Menu(p,files,page,selected,spec);menu.inventory=Bukkit.createInventory(menu,54,Component.text("\uE001\uE002",NamedTextColor.WHITE).font(net.kyori.adventure.key.Key.key("realmshards:forge")));
        for(int i=0;i<54;i++)menu.inventory.setItem(i,shards.button(Material.PAPER," ","","panel"));
        for(int i=0;i<28&&page*28+i<files.size();i++){Path file=files.get(page*28+i);menu.inventory.setItem(10+i/7*9+i%7,shards.button(Material.PAPER,(file.equals(selected)?"§aSelected: ":"§b")+file.getFileName(),"Select this schematic • terrain only","schematic"));}
        menu.inventory.setItem(45,shards.button(Material.PAPER,"§bPrevious page","Page "+(page+1),"previous"));
        menu.inventory.setItem(46,shards.button(Material.PAPER,"§bTier "+spec.tier(),"Cycle tier • imported shape stays exact","tier"));
        menu.inventory.setItem(47,shards.button(Material.PAPER,"§e"+spec.variant(),"Cycle variant • Rainbow tier III+","variant"));
        menu.inventory.setItem(48,shards.button(Material.PAPER,"§bBiome: "+spec.land(),"Cycle atmosphere and shard appearance","realm"));
        menu.inventory.setItem(49,shards.button(Material.PAPER,"§aForge custom shard",selected==null?"Choose a schematic first":selected.getFileName()+" • validate, save and give one shard","forge"));
        menu.inventory.setItem(50,shards.button(Material.PAPER,"§bNext page","Drop .schem files into plugins/WorldForgeItemGen/schematics","next"));
        menu.inventory.setItem(51,shards.button(Material.PAPER,"§bRefresh files","Reread the schematic folder","reload"));
        menu.inventory.setItem(52,shards.button(Material.PAPER,"§7Back","Return to Realm Forge","close"));p.openInventory(menu.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(m.player)||!access.owner(p)){e.getWhoClicked().closeInventory();return;}int slot=e.getRawSlot();if(slot<0||slot>=54)return;
        if(slot>=10&&slot<=43&&slot%9>=1&&slot%9<=7){int i=m.page*28+(slot/9-1)*7+slot%9-1;if(i<m.files.size())open(p,m.files,m.page,m.files.get(i),m.spec);return;}
        if(slot==52){shards.open(p,m.spec);return;}if(slot==51){browse(p);return;}
        if(slot==49){if(m.selected==null){p.sendMessage("§cChoose a schematic first.");return;}forge(p,m.selected,m.spec);return;}
        int page=m.page,t=m.spec.tier();var v=m.spec.variant();var land=m.spec.land();
        if(slot==45)page=Math.max(0,page-1);if(slot==50)page=Math.min(Math.max(0,(m.files.size()-1)/28),page+1);
        if(slot==46){t=t%5+1;if(t<3&&v==ShardSpec.Variant.RAINBOW)v=ShardSpec.Variant.STANDARD;}
        if(slot==47){v=ShardSpec.Variant.values()[(v.ordinal()+1)%3];if(t<3&&v==ShardSpec.Variant.RAINBOW)v=ShardSpec.Variant.STANDARD;}
        if(slot==48)land=ShardSpec.Land.values()[(land.ordinal()+1)%ShardSpec.Land.values().length];
        open(p,m.files,page,m.selected,new ShardSpec(t,land,v));
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    void forge(Player p,Path source,ShardSpec spec){if(!access.owner(p)||!loading.add(p.getUniqueId()))return;p.closeInventory();p.sendMessage("§bValidating custom realm…");
        io.submit(()->{try{
            if(!source.toRealPath().getParent().equals(inputs.toRealPath())||Files.isSymbolicLink(source))throw new IOException("Schematic must be a regular file inside the schematic folder.");
            if(Files.size(source)>8*1024*1024)throw new IOException("Maximum compressed file size is 8 MB.");byte[] bytes;try(var input=Files.newInputStream(source)){bytes=input.readNBytes(8*1024*1024+1);}if(bytes.length>8*1024*1024)throw new IOException("Maximum compressed file size is 8 MB.");
            String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));Path snapshot=library.resolve(id+".schem");
            Path temp=Files.createTempFile(library,"import-",".tmp");try{Files.write(temp,bytes);SchematicData.Data data=SchematicData.read(temp);
                Bukkit.getScheduler().runTask(plugin,()->{if(stopped)return;try{if(!p.isOnline()||!access.owner(p))throw new IllegalStateException("Owner access is required.");validate(data);
                    String name=source.getFileName().toString().replaceFirst("(?i)\\.schem$","");
                    io.submit(()->{try{Files.move(temp,snapshot,StandardCopyOption.REPLACE_EXISTING);Files.writeString(library.resolve(id+".txt"),name);
                        Bukkit.getScheduler().runTask(plugin,()->{loading.remove(p.getUniqueId());if(stopped)return;Template t=new Template(id,name,data);templates.put(id,t);
                            if(!p.isOnline()||!access.owner(p))return;if(p.getInventory().firstEmpty()<0){p.sendMessage("§cTemplate saved. Free an inventory slot and forge again.");return;}
                            ItemStack item=shards.item(spec);var meta=item.getItemMeta();meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,id);meta.displayName(Component.text(name+" RealmShard",NamedTextColor.AQUA));
                            var lore=new ArrayList<>(meta.lore());lore.add(Component.text("Custom land • "+data.width()+"×"+data.height()+"×"+data.length(),NamedTextColor.GOLD));meta.lore(lore);item.setItemMeta(meta);p.getInventory().addItem(item);p.sendMessage("§aCustom shard created. Right-click in your own island to release "+name+".");});
                    }catch(Exception e){failure(p,e);try{Files.deleteIfExists(temp);}catch(IOException ignored){}}});
                }catch(Exception e){loading.remove(p.getUniqueId());p.sendMessage("§c"+e.getMessage());io.submit(()->{try{Files.deleteIfExists(temp);}catch(IOException ignored){}});}});
            }catch(Exception e){Files.deleteIfExists(temp);throw e;}
        }catch(Exception e){failure(p,e);}});
    }
    void failure(Player p,Exception e){if(stopped)return;Bukkit.getScheduler().runTask(plugin,()->{loading.remove(p.getUniqueId());if(p.isOnline())p.sendMessage("§cImport failed: "+e.getMessage());});}
}
