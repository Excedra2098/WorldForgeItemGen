package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Staff moderation reusable testing tools; projections never become inventory rewards. */
final class OwnerRitualTools implements Listener {
    private final RealmShards realms;private final ShardAccess access;final NamespacedKey key;final WandUseEscrow escrow;
    OwnerRitualTools(JavaPlugin plugin,RealmShards realms,ShardAccess access){this.realms=realms;this.access=access;key=new NamespacedKey(plugin,"realm_testing_tool");try{escrow=new WandUseEscrow(plugin);}catch(java.io.IOException e){throw new IllegalStateException("Cannot initialize wand reservations",e);}Bukkit.getPluginManager().registerEvents(this,plugin);}
    void stop(){escrow.stop();}
    static final class Menu implements InventoryHolder {
        Inventory inventory;final UUID owner;Menu(UUID owner){this.owner=owner;}public Inventory getInventory(){return inventory;}
    }
    ItemStack item(boolean all){
        ItemStack item=new ItemStack(Material.AMETHYST_SHARD);ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(all?"Realm Nullifier • ALL Placements":"ShardBreaker Wand • ONE Placement",all?NamedTextColor.LIGHT_PURPLE:NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Staff moderation • reusable crystal wand"),Component.text(all?"Removes ALL recorded shard placements in this island.":"Aim at a recorded placement from up to 256 blocks away."),Component.text("Right-click: "+(all?"ground jab → black beams → all placements":"point → purple beam → one placement")+""),Component.text("Original island and changed blocks are preserved."),Component.text("Removed terrain gives no shard refund.")));
        meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,all?"all":"single");
        meta.setItemModel(new NamespacedKey("realmshards",all?"tools/unbind_all":"tools/unbind_single"));meta.setMaxStackSize(1);item.setItemMeta(meta);return item;
    }
    ItemStack reclaimer(){ItemStack item=new ItemStack(Material.AMETHYST_SHARD);var meta=item.getItemMeta();meta.displayName(Component.text("Realm Reclaimer • Return a Placement",NamedTextColor.YELLOW));meta.lore(List.of(Component.text("Staff moderation • reusable emerald-and-gold wand"),Component.text("Aim at an intact recorded realm and right-click."),Component.text("Yellow seal → shrinking realm → shard returns to you."),Component.text("Returns its exact original shard, including fusion properties."),Component.text("Full inventory refunds wait safely for a free slot.")));meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,"reclaim");meta.setItemModel(new NamespacedKey("realmshards","tools/reclaimer"));meta.setMaxStackSize(1);item.setItemMeta(meta);return item;}
    ItemStack convergence(){ItemStack item=new ItemStack(Material.AMETHYST_SHARD);var meta=item.getItemMeta();meta.displayName(Component.text("Realm Convergence Wand",NamedTextColor.GOLD));meta.lore(List.of(Component.text("Blue precision • violet nullification • emerald reclamation"),Component.text("Forged by converging three individual crystal wands."),Component.text("WFE handles this wand's commands and activation modes.")));meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,"convergence");meta.setItemModel(new NamespacedKey("realmshards","tools/convergence"));meta.setMaxStackSize(1);item.setItemMeta(meta);return item;}
    void open(Player player){
        if(!access.moderator(player))throw new IllegalArgumentException("Only authorized staff can open the testing wand page.");
        Menu menu=new Menu(player.getUniqueId());menu.inventory=Bukkit.createInventory(menu,27,Component.text("\uE001\uE004",NamedTextColor.WHITE).font(net.kyori.adventure.key.Key.key("realmshards:forge")));
        for(int i=0;i<27;i++)menu.inventory.setItem(i,control("panel"," ",""));
        menu.inventory.setItem(11,item(false));menu.inventory.setItem(13,item(true));menu.inventory.setItem(15,reclaimer());
        menu.inventory.setItem(18,control("back","Back to Starter Realms","Return to Page 2."));
        menu.inventory.setItem(26,control("close","Close","Return to your island."));player.openInventory(menu.inventory);
    }
    static ItemStack control(String model,String title,String lore){ItemStack i=new ItemStack(Material.PAPER);ItemMeta m=i.getItemMeta();m.displayName(Component.text(title));m.lore(List.of(Component.text(lore)));m.setItemModel(new NamespacedKey("realmshards","gui/owner_"+model));i.setItemMeta(m);return i;}
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(menu.owner)||!access.moderator(p)){event.getWhoClicked().closeInventory();return;}
        int slot=event.getRawSlot();if(slot<0||slot>=27)return;
        if(slot==11||slot==13||slot==15){if(p.getInventory().firstEmpty()<0){p.sendMessage("Free one storage slot for the testing wand.");return;}p.getInventory().addItem(slot==15?reclaimer():item(slot==13));p.sendMessage("Received reusable "+(slot==15?"Realm Reclaimer (RETURN shard).":slot==13?"Realm Nullifier (ALL placements).":"ShardBreaker Wand (ONE placement)."));}
        else if(slot==18)realms.openStarters(p);else if(slot==26)p.closeInventory();
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void use(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        ItemStack item=event.getItem();if(item==null||!item.hasItemMeta())return;
        String mode=item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);if(mode==null||mode.equals("convergence"))return;
        if(event.getAction()==Action.RIGHT_CLICK_BLOCK&&event.isCancelled())return;event.setCancelled(true);Player player=event.getPlayer();
        activate(player,item,mode,null);
    }
    boolean useCore(Player player,UUID realm){
        ItemStack item=player.getInventory().getItemInMainHand();String mode=item.hasItemMeta()?item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING):null;
        if(!"reclaim".equals(mode))return false;activate(player,item,mode,realm);return true;
    }
    private void activate(Player player,ItemStack item,String mode,UUID coreRealm){
        WandUseEscrow.Lease lease=null;
        try{if(!access.moderator(player))throw new IllegalArgumentException("Only authorized staff can activate testing wands.");if(!mode.equals("single")&&!mode.equals("all")&&!mode.equals("reclaim"))throw new IllegalArgumentException("Invalid testing wand.");
            ItemStack held=player.getInventory().getItemInMainHand();String heldMode=held.hasItemMeta()?held.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING):null;
            if(heldMode==null||(!held.equals(item)&&!heldMode.equals("convergence")))throw new IllegalArgumentException("The activating wand is no longer in your hand.");
            lease=escrow.take(player);if(coreRealm!=null)realms.reclaimCore(player,coreRealm);else if(mode.equals("reclaim"))realms.reclaim(player,lease.item);else realms.testRemove(player,mode.equals("all"),lease.item);
            realms.afterWandUse(player,lease::close);
        }catch(Exception ex){if(lease!=null)lease.close();player.sendMessage(ex.getMessage());}
    }
}
