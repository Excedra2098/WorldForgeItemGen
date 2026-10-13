package dev.excedra.itemgen;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Player-only placement tool; never delegates to staff removal handlers. */
final class RealmweaverWand implements Listener, CommandExecutor {
    private final RealmShards realms;
    private final NamespacedKey key;
    private final Map<UUID,Selection> selections=new HashMap<>();
    private final BukkitTask ticker;
    private static final class Selection {
        final int slot;
        final RealmShards.PreparedShard shard;
        Location target;
        RealmShards.PreparedShard placement;
        boolean valid;
        String validationMessage="";
        PlacementRotation rotation=PlacementRotation.NONE;
        final long expires=System.currentTimeMillis()+120_000;
        Selection(int slot,RealmShards.PreparedShard shard,Location target){this.slot=slot;this.shard=shard;this.target=target;}
    }
    private static final class Menu implements InventoryHolder {
        final boolean choosing;
        final UUID player;
        Inventory inventory;
        Menu(UUID player,boolean choosing){this.player=player;this.choosing=choosing;}
        public Inventory getInventory(){return inventory;}
    }
    RealmweaverWand(JavaPlugin plugin,RealmShards realms) {
        this.realms=realms;key=new NamespacedKey(plugin,"realmweaver_wand");
        Bukkit.getPluginManager().registerEvents(this,plugin);
        PluginCommand command=plugin.getCommand("realmweaver");
        if(command!=null)command.setExecutor(this);
        Bukkit.getScheduler().runTask(plugin,()->Bukkit.getOnlinePlayers().forEach(this::upgrade));
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::preview,10,10);
    }

    private void upgrade(Player player){ItemStack[] items=player.getInventory().getContents();for(int slot=0;slot<items.length;slot++)if(wand(items[slot])){ItemStack item=items[slot];ItemMeta meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards","wands/realmweaver"));item.setItemMeta(meta);player.getInventory().setItem(slot,item);}}
    @EventHandler public void upgradeJoined(PlayerJoinEvent event){upgrade(event.getPlayer());}

    void stop(){ticker.cancel();selections.clear();}
    private boolean wand(ItemStack item){return item!=null&&item.hasItemMeta()&&Integer.valueOf(1).equals(item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.INTEGER));}
    private ItemStack item(){
        ItemStack item=button(Material.BLAZE_ROD,"Realmweaver Wand","Right-click to select or position a Shard.","Sneak-right-click to change your selection.");
        ItemMeta meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards","wands/realmweaver"));meta.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,1);item.setItemMeta(meta);return item;
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("Use this command in game.");return true;}
        if(!player.hasPermission("worldforge.realmweaver.obtain")){player.sendMessage("§cYou cannot obtain this wand.");return true;}
        if(args.length!=0){player.sendMessage("§eUsage: /realmweaver");return true;}
        for(ItemStack existing:player.getInventory().getContents())if(wand(existing)){player.sendMessage("§eYou already have a Realmweaver Wand.");return true;}
        int free=player.getInventory().firstEmpty();
        if(free<0){player.sendMessage("§eFree an inventory slot first.");return true;}
        player.getInventory().setItem(free,item());player.sendMessage("§bYour Realmweaver Wand is ready.");return true;
    }
    private void require(Player player){
        if(!player.hasPermission("worldforge.realmweaver.use"))throw new IllegalStateException("You cannot use the Realmweaver Wand.");
        if(!player.hasPermission("worldforge.realmshards.use"))throw new IllegalStateException("You cannot activate shards.");
        if(!wand(player.getInventory().getItemInMainHand()))throw new IllegalStateException("Hold your Realmweaver Wand.");
        ItemStack held=player.getInventory().getItemInMainHand();ItemMeta meta=held.getItemMeta();meta.setItemModel(new NamespacedKey("realmshards","wands/realmweaver"));held.setItemMeta(meta);player.getInventory().setItemInMainHand(held);
        new IslandBridge().requirePlacement(player);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void use(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||!wand(event.getItem()))return;
        if(event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR&&event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)return;
        if(event.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK&&event.isCancelled())return;
        event.setCancelled(true);Player player=event.getPlayer();
        try{
            require(player);Selection selected=selections.get(player.getUniqueId());
            if(player.isSneaking()||selected==null){selections.remove(player.getUniqueId());open(player,true);}
            else{selected.target=aim(player,selected.shard.plan());open(player,false);}
        }catch(Exception failure){player.sendMessage("§c"+failure.getMessage());}
    }
    private Location aim(Player player,TerrainPlan plan){
        Location target=player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply((plan.radius+7)*Math.sqrt(2)));
        target.setX(target.getBlockX());target.setY(target.getBlockY());target.setZ(target.getBlockZ());return target;
    }
    private static ItemStack button(Material material,String name,String... lore){
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.displayName(Component.text(name));
        meta.lore(Arrays.stream(lore).map(Component::text).toList());item.setItemMeta(meta);return item;
    }
    private void open(Player player,boolean choosing){
        Menu menu=new Menu(player.getUniqueId(),choosing);menu.inventory=Bukkit.createInventory(menu,54,Component.text(choosing?"Select a Realm Shard":"Realmweaver Placement"));
        if(choosing){
            for(int slot=0;slot<36;slot++){ItemStack source=player.getInventory().getItem(slot);if(realms.read(source)!=null)menu.inventory.setItem(slot,source.clone());}
            menu.inventory.setItem(49,button(Material.BARRIER,"Cancel"));
        }else{
            Selection selection=selections.get(player.getUniqueId());
            selection.placement=null;
            try{selection.placement=realms.connectShard(player,selection.shard,selection.target,selection.rotation);realms.validatePlacement(player,selection.placement,selection.target,PlacementRotation.NONE);selection.valid=true;selection.validationMessage="Placement valid - bridge connected";}
            catch(Exception invalid){selection.valid=false;selection.validationMessage=invalid.getMessage();}
            menu.inventory.setItem(22,selection.shard.source().clone());
            menu.inventory.setItem(31,button(selection.valid?Material.LIME_DYE:Material.RED_DYE,selection.valid?"Ready to forge":"Placement unavailable",selection.validationMessage));
            menu.inventory.setItem(45,button(Material.COMPASS,"Retarget","Close this menu; aim elsewhere and right-click again."));
            menu.inventory.setItem(46,button(Material.CLOCK,"Rotate 90°","Preview rotation: "+selection.rotation));
            menu.inventory.setItem(48,button(Material.LIME_DYE,"Confirm placement","Forge at this location after existing safety checks; uses one Shard."));
            menu.inventory.setItem(49,button(Material.BARRIER,"Cancel placement"));
            menu.inventory.setItem(50,button(Material.CHEST,"Select another Shard"));
            menu.inventory.setItem(4,button(Material.PAPER,"Placement position",selection.target.getBlockX()+", "+selection.target.getBlockY()+", "+selection.target.getBlockZ(),realms.coreCoordinates(player,selection.target),"Green: valid terrain; gold: bridge; red: invalid placement."));
        }
        player.openInventory(menu.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!menu.player.equals(player.getUniqueId()))return;
        int slot=event.getRawSlot();if(slot<0||slot>=54)return;
        try{
            require(player);
            if(menu.choosing){
                if(slot==49){player.closeInventory();return;}
                if(slot>=36)return;
                ItemStack shown=menu.inventory.getItem(slot),source=player.getInventory().getItem(slot);
                if(shown==null||source==null||!shown.isSimilar(source))throw new IllegalStateException("Selected Shard changed in your inventory; reopen the selection menu.");
                ShardSpec spec=realms.read(source);if(spec==null)return;
                if(!selections.containsKey(player.getUniqueId())&&selections.size()>=4)throw new IllegalStateException("Placement previews are busy; try again shortly.");
                var prepared=realms.prepareShard(source,spec);
                selections.put(player.getUniqueId(),new Selection(slot,prepared,aim(player,prepared.plan())));open(player,false);return;
            }
            Selection selected=selections.get(player.getUniqueId());
            if(selected==null)throw new IllegalStateException("Your placement selection expired. Select the Shard again.");
            if(System.currentTimeMillis()>selected.expires)throw new IllegalStateException("Your placement selection expired. Select the Shard again.");
            switch(slot){
                case 45 -> player.closeInventory();
                case 46 -> {selected.rotation=PlacementRotation.values()[(selected.rotation.ordinal()+1)%4];open(player,false);}
                case 48 -> {if(selected.placement==null)throw new IllegalStateException(selected.validationMessage);realms.activateSelected(player,selected.placement,selected.slot,selected.target,PlacementRotation.NONE);selections.remove(player.getUniqueId());player.closeInventory();}
                case 49 -> {selections.remove(player.getUniqueId());player.closeInventory();}
                case 50 -> {selections.remove(player.getUniqueId());open(player,true);}
                default -> {}
            }
        }catch(Exception failure){player.sendMessage("§c"+failure.getMessage());}
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    private void preview(){
        for(var iterator=selections.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();Player player=Bukkit.getPlayer(entry.getKey());Selection selected=entry.getValue();
            if(player==null||player.isDead()||System.currentTimeMillis()>selected.expires||!player.getWorld().equals(selected.target.getWorld())){iterator.remove();continue;}
            if(!wand(player.getInventory().getItemInMainHand()))continue;
            ItemStack source=player.getInventory().getItem(selected.slot);
            if(source==null||!source.isSimilar(selected.shard.source())){iterator.remove();player.sendMessage("§eSelected Shard moved or changed; preview cancelled. Reopen Realmweaver.");continue;}
            draw(player,selected,selected.valid?Color.fromRGB(70,220,170):Color.fromRGB(230,70,70));
        }
    }
    private void draw(Player player,Selection selected,Color color){
        var plan=selected.shard.plan();int stride=Math.max(1,(plan.surface.size()+95)/96),index=0;
        Collection<TerrainPlan.Pos> points;
        if(!plan.surface.isEmpty()){
            List<TerrainPlan.Pos> surface=new ArrayList<>();
            for(var entry:plan.surface.entrySet())if(index++%stride==0)surface.add(new TerrainPlan.Pos((int)(entry.getKey()>>32),entry.getValue()+1,(int)(long)entry.getKey()));
            points=surface;
        }else{
            Map<Long,Integer> heights=new HashMap<>();for(var p:plan.blocks.keySet())heights.merge(TerrainPlan.key(p.x(),p.z()),p.y()+1,Math::max);
            List<TerrainPlan.Pos> surface=new ArrayList<>();stride=Math.max(1,(heights.size()+95)/96);index=0;
            for(var entry:heights.entrySet())if(index++%stride==0)surface.add(new TerrainPlan.Pos((int)(entry.getKey()>>32),entry.getValue(),(int)(long)entry.getKey()));
            points=surface;
        }
        if(selected.placement!=null)for(var point:selected.placement.plan().bridgePath)
            player.spawnParticle(Particle.DUST,selected.target.clone().add(point.x()+.5,point.y()+.15,point.z()+.5),1,0,0,0,0,new Particle.DustOptions(selected.valid?Color.fromRGB(235,185,70):Color.fromRGB(230,70,70),1.6f));
        for(var point:points){var rotated=selected.rotation.apply(point.x(),point.y(),point.z());player.spawnParticle(Particle.DUST,selected.target.clone().add(rotated.x()+.5,rotated.y()+.1,rotated.z()+.5),1,0,0,0,0,new Particle.DustOptions(color,1));}
    }
    @EventHandler public void quit(PlayerQuitEvent event){selections.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent event){selections.remove(event.getEntity().getUniqueId());}
    @EventHandler public void world(PlayerChangedWorldEvent event){selections.remove(event.getPlayer().getUniqueId());}
}
