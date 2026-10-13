package dev.excedra.itemgen;

import java.util.*;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Draft changes and staff previews never grant permanent unlocks. */
final class CoreCustomization implements Listener {
    private static final Map<String,String> TITLES=Map.of("crystal","Core Crystal","primary","Primary Color","secondary","Secondary Color","effect","Magical Effects","decor","Decorations","attach","Upgrades & Attachments");
    private final JavaPlugin plugin;private final RealmCoreStore store;private final CoreAppearance appearance;private final CoreShrineDesign shrine;private final Function<Player,RealmCoreStore.Core> require;
    private final Map<UUID,Draft> drafts=new HashMap<>();
    private static final class Draft {final RealmCoreStore.Core core;CoreAppearance.Profile profile;Draft(RealmCoreStore.Core core,CoreAppearance.Profile profile){this.core=core;this.profile=profile;}}
    private static final class Menu implements InventoryHolder {final UUID player;final String category;final int page;final Map<Integer,CoreAppearance.Option> options=new HashMap<>();Inventory inventory;Menu(UUID player,String category,int page){this.player=player;this.category=category;this.page=page;}public Inventory getInventory(){return inventory;}}
    CoreCustomization(JavaPlugin plugin,RealmCoreStore store,CoreAppearance appearance,CoreShrineDesign shrine,Function<Player,RealmCoreStore.Core> require){this.plugin=plugin;this.store=store;this.appearance=appearance;this.shrine=shrine;this.require=require;Bukkit.getPluginManager().registerEvents(this,plugin);}
    void open(Player p,RealmCoreStore.Core core)throws java.io.IOException {appearance.progress(core.creator(),store.patterns(core.creator()));drafts.put(p.getUniqueId(),new Draft(core,appearance.profile(core.realm())));show(p,"");}
    private static ItemStack button(Material material,String title,String... lines){return CoreGui.button(material,title,lines);}
    private void show(Player p,String category){show(p,category,0);}
    private void show(Player p,String category,int page){Draft draft=drafts.get(p.getUniqueId());if(draft==null)return;Menu menu=new Menu(p.getUniqueId(),category,page);menu.inventory=Bukkit.createInventory(menu,54,CoreGui.title("Core • "+(category.isEmpty()?"Customization":category.equals("unlocks")?"Unlocks & Discoveries":TITLES.getOrDefault(category,"Preview & Reset"))));CoreGui.fill(menu.inventory);
        if(category.isEmpty()){
            int[] slots={10,12,14,28,30,32};String[] cats={"crystal","primary","decor","effect","attach","secondary"};Material[] icons={Material.AMETHYST_SHARD,Material.PURPLE_DYE,Material.MOSSY_STONE_BRICKS,Material.GLOWSTONE_DUST,Material.END_CRYSTAL,Material.CYAN_DYE};
            for(int n=0;n<slots.length;n++)menu.inventory.setItem(slots[n],button(icons[n],TITLES.get(cats[n]),"Unsaved selection: "+draft.profile.get(cats[n]),"Browse options; Apply saves your changes."));
            menu.inventory.setItem(21,button(Material.WRITABLE_BOOK,"Unlocks & Discoveries",store.patterns(draft.core.creator()).size()+" saved patterns","Rewards stay unlocked after recipes are reset.","Hidden rewards appear only after discovery."));
            menu.inventory.setItem(23,button(Material.SPYGLASS,"Preview & Reset","Preview for 60 seconds; Apply to save unlocked choices."));
        }else if(category.equals("preview")){
            menu.inventory.setItem(20,button(Material.SPYGLASS,"Preview draft","Temporary appearance preview (60 seconds).","Closing the menu discards the preview, not saved cosmetics."));
            menu.inventory.setItem(22,button(Material.LIME_DYE,"Apply unlocked draft","Save your selected unlocked appearance.","Does not modify terrain or discovered recipes."));
            menu.inventory.setItem(24,button(Material.MILK_BUCKET,"Restore original appearance","Reset this draft to the default appearance.","Your saved appearance changes only after Apply."));
            menu.inventory.setItem(31,button(Material.BARRIER,"Discard draft","Discard unsaved selections; keep saved appearance."));
        }else{
            int index=0;boolean staff=p.hasPermission("worldforge.realmcore.testing");
            for(var o:CoreAppearance.CATALOG){if(!category.equals("unlocks")&&!o.category().equals(category))continue;boolean unlocked=appearance.available(draft.core.creator(),o);if(o.secret()&&!unlocked&&!staff)continue;
                int number=index++;if(number<page*28||number>=page*28+28)continue;int visible=number-page*28;int slot=10+visible/7*9+visible%7;menu.options.put(slot,o);boolean selected=draft.profile.get(o.category()).equals(o.id());
                ItemStack icon=button(unlocked?selected?Material.ENCHANTED_BOOK:Material.AMETHYST_SHARD:Material.GRAY_DYE,(selected?"Selected • ":unlocked?"Unlocked • ":"Locked • ")+o.title(),"Category: "+o.category(),o.grantOnly()?"Special event / staff reward":o.discoveries()+" saved patterns to discover",appearance.profile(draft.core.realm()).get(o.category()).equals(o.id())?"Currently equipped":"Select for draft; Apply to save.");
                if((unlocked||staff)&&Set.of("crystal","primary","secondary").contains(o.category())){ItemMeta meta=icon.getItemMeta();meta.setItemModel(draft.core.design()==5?new NamespacedKey("realmcore",CompactCoreLayout.crystalModel(draft.profile.with(o.category(),o.id()))):new NamespacedKey("realmshards","core/classic/"+CoreAppearance.crystalModel(draft.profile.with(o.category(),o.id()))));icon.setItemMeta(meta);}
                menu.inventory.setItem(slot,icon);
            }
        }
        if(!category.isEmpty()&&!category.equals("preview")){menu.inventory.setItem(46,button(Material.ARROW,"Previous rewards"));menu.inventory.setItem(53,button(Material.ARROW,"Next rewards"));}
        menu.inventory.setItem(45,button(Material.ARROW,"Categories"));menu.inventory.setItem(48,button(Material.SPYGLASS,"Preview & Reset"));menu.inventory.setItem(49,button(Material.BARRIER,"Close / discard unsaved changes"));p.openInventory(menu.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Menu menu))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(menu.player))return;int slot=e.getRawSlot();if(slot<0||slot>=54)return;
        try{Draft d=drafts.get(p.getUniqueId());if(d==null||!d.core.equals(require.apply(p))||!d.core.equals(store.get(d.core.realm())))throw new IllegalStateException("Core changed; reopen customization.");
            if(slot==46){show(p,menu.category,Math.max(0,menu.page-1));return;}if(slot==53){show(p,menu.category,Math.min(1,menu.page+1));return;}
            if(slot==49){p.closeInventory();return;}if(slot==45){show(p,"");return;}if(slot==48){show(p,"preview");return;}
            if(menu.category.isEmpty()){String cat=switch(slot){case 10->"crystal";case 12->"primary";case 14->"decor";case 28->"effect";case 30->"attach";case 32->"secondary";case 21->"unlocks";case 23->"preview";default->null;};if(cat!=null)show(p,cat);return;}
            if(menu.category.equals("preview")){
                switch(slot){case 20->{if(!appearance.allowed(d.core.creator(),d.profile)&&!p.hasPermission("worldforge.realmcore.testing"))throw new IllegalStateException("Discover the locked options before previewing them.");shrine.preview(d.core.realm(),p.getUniqueId(),d.profile);p.sendMessage("§dTemporary Core preview: 60 seconds. §7Close to discard; Apply to save unlocked choices.");}
                    case 22->{appearance.equip(d.core.realm(),d.core.creator(),d.profile);shrine.clearPreview(d.core.realm());shrine.detach(d.core.realm());shrine.refresh();p.sendMessage("§bCore appearance saved. §7Your selected cosmetics are now equipped.");}
                    case 24->{d.profile=new CoreAppearance.Profile(CoreAppearance.DEFAULTS);shrine.clearPreview(d.core.realm());}
                    case 31->{d.profile=appearance.profile(d.core.realm());shrine.clearPreview(d.core.realm());}}
                show(p,"preview");return;
            }
            var option=menu.options.get(slot);if(option!=null){if(!appearance.available(d.core.creator(),option)&&!p.hasPermission("worldforge.realmcore.testing"))throw new IllegalStateException("This cosmetic is still locked.");d.profile=d.profile.with(option.category(),option.id());show(p,option.category());}
        }catch(Exception failure){p.sendMessage("§c"+failure.getMessage());}
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent e){if(!(e.getInventory().getHolder() instanceof Menu))return;Bukkit.getScheduler().runTask(plugin,()->{if(!(e.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof Menu))discard(e.getPlayer().getUniqueId());});}
    @EventHandler public void quit(PlayerQuitEvent e){discard(e.getPlayer().getUniqueId());}
    private void discard(UUID player){Draft d=drafts.remove(player);if(d!=null)shrine.clearPreview(d.core.realm());}
    void stop(){for(UUID player:List.copyOf(drafts.keySet()))discard(player);}
}
