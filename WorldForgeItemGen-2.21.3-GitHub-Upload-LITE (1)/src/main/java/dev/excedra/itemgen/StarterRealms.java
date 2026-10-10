package dev.excedra.itemgen;

import java.io.*;

import java.util.*;

import org.bukkit.*;

import org.bukkit.command.*;

import org.bukkit.entity.Player;

import org.bukkit.event.*;

import org.bukkit.event.inventory.*;

import org.bukkit.inventory.*;

import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.PersistentDataType;

import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;



final class StarterRealms implements Listener,CommandExecutor,TabCompleter {

    static final String[] NAMES={"Crownstone","Voidroot","Worldscar","Starfall","Cascade","Titanbone","Emberforge","Prismheart","Monolith"};

    static final String[] IDS={"crown","root","rift","star","garden","titan","forge","crystal","spire"};

    static final ShardSpec.Land[] LANDS={ShardSpec.Land.RUINS,ShardSpec.Land.FOREST,ShardSpec.Land.NETHER,ShardSpec.Land.END,ShardSpec.Land.OCEAN,ShardSpec.Land.RUINS,ShardSpec.Land.VOLCANIC,ShardSpec.Land.CRYSTAL,ShardSpec.Land.RUINS};

    final JavaPlugin plugin;final RealmShards realms;final ShardAccess access;final NamespacedKey key;final ShardClaims claims;final StarterFusionEffect fusion;

    StarterRealms(JavaPlugin p,RealmShards r,ShardAccess a)throws IOException{plugin=p;fusion=new StarterFusionEffect(p);realms=r;access=a;key=new NamespacedKey(p,"starter_realm_recipe");claims=new ShardClaims(p.getDataFolder().toPath().resolve("starter-realm-claims"));Bukkit.getPluginManager().registerEvents(this,p);p.getCommand("startershards").setExecutor(this);p.getCommand("startershards").setTabCompleter(this);}

    StarterRecipe read(ItemStack item){if(item==null||!item.hasItemMeta())return null;String s=item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);if(s==null)return null;try{StarterRecipe recipe=StarterRecipe.decode(s);return recipe.starterUnits()>0?recipe:null;}catch(RuntimeException ex){return null;}}

    StarterRecipe ingredient(ItemStack item){StarterRecipe r=read(item);if(r!=null)return r;ShardSpec biome=realms.biomeIngredient(item);return biome==null?null:StarterRecipe.biome(biome);}

    static ShardSpec spec(StarterRecipe r){int i=0;for(int n=1;n<9;n++)if(r.counts().get(n)>r.counts().get(i))i=n;return new ShardSpec(r.tier(),StarterBiomeWeave.dominantLand(r,LANDS[i]),r.variant());}

    static int mask(StarterRecipe r){int m=0;for(int i=0;i<9;i++)if(r.counts().get(i)>0)m|=1<<i;return m;}

    ItemStack item(StarterRecipe r){ItemStack item=realms.item(spec(r));ItemMeta m=item.getItemMeta();List<String> parts=new ArrayList<>();for(int i=0;i<9;i++)if(r.counts().get(i)>0)parts.add(NAMES[i]+" x"+r.counts().get(i));m.displayName(Component.text(StarterIslandDesign.title(r)+" • Realm Relic"));List<Component> lore=new ArrayList<>();lore.add(Component.text(String.join(" + ",parts)));int shown=0;for(var e:r.biomes().entrySet()){if(shown++==8){lore.add(Component.text("+ "+(r.biomes().size()-8)+" more biome ingredients (preserved)"));break;}ShardSpec b=StarterRecipe.parseBiome(e.getKey());lore.add(Component.text(b.title()+" • Tier "+b.tier()+" • "+b.variant()+" x"+e.getValue()));}lore.add(Component.text("Power: "+r.units()+" • Tier "+r.tier()));lore.add(Component.text("Island signature: "+r.identity()));lore.add(Component.text("Right-click air to expand your owned island."));lore.add(Component.text("Starter + starter or biome: /startertrealm combine"));m.lore(lore);m.getPersistentDataContainer().set(key,PersistentDataType.STRING,r.encode());m.setItemModel(new NamespacedKey("realmshards","starter/"+model(r)));item.setItemMeta(m);return item;}

    static String model(StarterRecipe r){int primary=StarterIslandDesign.dominant(r),mask=mask(r);if(StarterIslandDesign.diversity(r)==1)return r.biomes().isEmpty()?IDS[primary]:"biome_confluence_"+mask;return (r.biomes().isEmpty()?"weighted_":"biome_weighted_")+mask+"_"+primary;}

    static class Menu implements InventoryHolder{Inventory inventory;final UUID owner;Menu(UUID id){owner=id;}public Inventory getInventory(){return inventory;}}

    static final int[] THEME_SLOTS={11,13,15,20,22,24,29,31,33};

    void open(Player p){

        Menu m=new Menu(p.getUniqueId());

        m.inventory=Bukkit.createInventory(m,54,Component.text("\uE001\uE003",net.kyori.adventure.text.format.NamedTextColor.WHITE).font(net.kyori.adventure.key.Key.key("realmshards:forge")));

        for(int slot=0;slot<54;slot++)m.inventory.setItem(slot,control("panel"," ",""));

        for(int i=0;i<9;i++)m.inventory.setItem(THEME_SLOTS[i],item(StarterRecipe.single(i)));

        ItemStack main=p.getInventory().getItemInMainHand(),off=p.getInventory().getItemInOffHand();

        m.inventory.setItem(37,ingredient(main)!=null?main.asOne():control("left","Main-hand ingredient","Hold a starter or biome shard in your main hand."));

        m.inventory.setItem(43,ingredient(off)!=null?off.asOne():control("right","Offhand ingredient","Hold a starter or biome shard in your offhand."));

        m.inventory.setItem(40,control("fuse","Confluence • Combine","Consume one relic per hand; at least one must contain starter themes."));

        if(access.owner(p))m.inventory.setItem(48,OwnerRitualTools.control("seal","Owner Testing Crystals","Open the testing atelier."));

        m.inventory.setItem(49,control("codex","Discovery Codex","Matching shards deepen a theme; mixed shards form hybrids."));

        if(access.staff(p))m.inventory.setItem(45,control("back","Biome Realms • Page 1","Return to the biome forge."));

        m.inventory.setItem(53,control("close","Close","Return to your world."));

        p.openInventory(m.inventory);

    }

    ItemStack control(String model,String name,String lore){ItemStack i=new ItemStack(Material.PAPER);ItemMeta m=i.getItemMeta();m.displayName(Component.text(name));m.lore(List.of(Component.text(lore)));m.setItemModel(new NamespacedKey("realmshards","gui/starter_"+model));i.setItemMeta(m);return i;}

    void grant(CommandSender sender,StarterGrant request){

        Player target=Bukkit.getPlayerExact(request.player());if(target==null)throw new IllegalArgumentException("Target player must be online.");

        long empty=Arrays.stream(target.getInventory().getStorageContents()).filter(i->i==null||i.getType().isAir()).count();

        if(empty<request.slots())throw new IllegalArgumentException("Player needs "+request.slots()+" empty storage slots; no shards were granted.");

        List<ItemStack> rewards=new ArrayList<>();for(int n=0;n<request.amount();n++)for(int theme:request.themes())rewards.add(item(StarterRecipe.single(theme)));

        target.getInventory().addItem(rewards.toArray(ItemStack[]::new));

        sender.sendMessage("Granted "+rewards.size()+" Starter Realm Shards to "+target.getName()+".");

        if(!sender.equals(target))target.sendMessage("You received "+rewards.size()+" Starter Realm Shards.");

    }

    void combine(Player p){if(!p.hasPermission("worldforge.realmshards.use"))throw new IllegalArgumentException("You cannot combine shards.");PlayerInventory inv=p.getInventory();StarterRecipe a=ingredient(inv.getItemInMainHand()),b=ingredient(inv.getItemInOffHand());if(a==null||b==null)throw new IllegalArgumentException("Hold a starter or biome shard in each hand; schematic shards cannot fuse.");StarterRecipe merged=a.merge(b);if(merged.starterUnits()==0)throw new IllegalArgumentException("At least one ingredient must contain starter themes.");ItemStack result=item(merged);ItemStack main=inv.getItemInMainHand(),off=inv.getItemInOffHand();ItemStack leftVisual=main.asOne(),rightVisual=off.asOne();if(main.getAmount()>1&&inv.firstEmpty()<0)throw new IllegalArgumentException("Free an inventory slot before combining.");if(main.getAmount()==1)inv.setItemInMainHand(result);else{main.setAmount(main.getAmount()-1);inv.addItem(result);}off.setAmount(off.getAmount()-1);fusion.play(p,leftVisual,rightVisual,result);p.sendMessage("Discovered "+StarterIslandDesign.title(merged)+". Place your new relic with the Realmweaver Wand.");}

    void combineAll(Player p,boolean mixed){
        if(!access.staff(p))throw new IllegalArgumentException("Only Admin, Head-Admin and Owner can use combineall.");
        PlayerInventory inv=p.getInventory();List<Integer> slots=new ArrayList<>(),amounts=new ArrayList<>();List<StarterRecipe> recipes=new ArrayList<>();
        ItemStack first=null,last=null;int items=0;
        for(int slot=0;slot<=36;slot++){
            ItemStack stack=slot==36?inv.getItemInOffHand():inv.getItem(slot);StarterRecipe recipe=mixed?ingredient(stack):read(stack);
            if(recipe==null)continue;
            slots.add(slot);recipes.add(recipe);amounts.add(stack.getAmount());items+=stack.getAmount();
            if(first==null)first=stack.asOne();last=stack.asOne();
        }
        if(items<2)throw new IllegalArgumentException("Keep at least two eligible relics in storage or offhand to combine all.");
        // Compute and construct before consuming anything. A consumed storage slot always holds the result.
        StarterRecipe merged=StarterRecipe.aggregate(recipes,amounts);if(merged.starterUnits()==0)throw new IllegalArgumentException("Mixed fusion requires at least one starter relic; nothing consumed.");ItemStack result=item(merged);
        int output=slots.getFirst();
        for(int slot:slots){if(slot==36)inv.setItemInOffHand(null);else inv.setItem(slot,null);}
        if(output==36)inv.setItemInOffHand(result);else inv.setItem(output,result);fusion.play(p,first,last,result);
        p.sendMessage("Combined "+items+" relics into "+StarterIslandDesign.title(merged)+" ("+merged.units()+" shard units).");
    }

    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] args){try{if(args.length>0&&args[0].equalsIgnoreCase("claim")){if(!(s instanceof ConsoleCommandSender)||args.length!=2)throw new IllegalArgumentException("Console: startershards claim <player>");Player p=Bukkit.getPlayerExact(args[1]);if(p==null)throw new IllegalArgumentException("Player must be online.");List<ItemStack> kit=new ArrayList<>();for(int i:new int[]{0,0,1,1,4,4})kit.add(item(StarterRecipe.single(i)));var result=claims.grant(p.getUniqueId(),"starter-kit-v1",()->p.isOnline()&&Arrays.stream(p.getInventory().getStorageContents()).filter(x->x==null||x.getType().isAir()).count()>=6,()->{for(ItemStack i:kit)p.getInventory().addItem(i);return true;});p.sendMessage("Starter kit: "+result+". Six free inventory slots are required.");return true;}if(args.length>0&&(args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("giveall"))){

        if(!access.staff(s))throw new IllegalArgumentException("Only Admin, Head-Admin, Owner or console can give starter realms.");

        grant(s,StarterGrant.parse(args,s instanceof Player player?player.getName():null));return true;

    }

    if(!(s instanceof Player p))throw new IllegalArgumentException("Use startershards give <player> <theme> [amount] or giveall <player> [amount].");

    if(args.length==0||(args.length==1&&args[0].equalsIgnoreCase("menu"))){open(p);return true;}

    if(args.length>0&&args[0].equalsIgnoreCase("combineall")){if(args.length>2||(args.length==2&&!args[1].equalsIgnoreCase("mixed")))throw new IllegalArgumentException("/startertrealm combineall [mixed]");combineAll(p,args.length==2);return true;}

    if(args.length==1&&args[0].equalsIgnoreCase("combine")){combine(p);return true;}

    throw new IllegalArgumentException("/startershards [menu|combine|combineall] • give <player> <theme> [amount] • giveall <player> [amount]");}catch(Exception ex){s.sendMessage(ex.getMessage());}return true;}

    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!m.owner.equals(p.getUniqueId())||e.getRawSlot()<0||e.getRawSlot()>=54)return;try{switch(e.getRawSlot()){case 40->{combine(p);Bukkit.getScheduler().runTask(plugin,(Runnable)p::closeInventory);}case 45->{if(access.staff(p))realms.open(p,new ShardSpec(3,ShardSpec.Land.FOREST,ShardSpec.Variant.STANDARD));}case 48->{if(access.owner(p))realms.openOwnerTools(p);}case 53->p.closeInventory();default->{}}}catch(Exception ex){p.sendMessage(ex.getMessage());}}

    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){

        List<String> choices=new ArrayList<>();boolean staff=access.staff(sender);

        if(args.length==1){if(sender instanceof Player){choices.add("menu");choices.add("combine");}if(staff){if(sender instanceof Player)choices.add("combineall");choices.add("give");choices.add("giveall");}if(sender instanceof ConsoleCommandSender)choices.add("claim");}

        else if(args.length>=2){String sub=args[0].toLowerCase(Locale.ROOT);if(!staff)return List.of();

            if(args.length==2&&sub.equals("combineall"))choices.add("mixed");
            if(args.length==2&&List.of("give","giveall","claim").contains(sub))choices.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());

            if(args.length==3&&sub.equals("give"))choices.addAll(List.of(IDS));

            if(args.length==4&&sub.equals("give"))choices.addAll(List.of("1","2","3","9"));

            if(args.length==3&&sub.equals("giveall"))choices.addAll(List.of("1","2","3","4"));

        }

        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);return choices.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();

    }

    static TerrainPlan plan(StarterRecipe recipe){return StarterIslandDesign.create(recipe);}
}
