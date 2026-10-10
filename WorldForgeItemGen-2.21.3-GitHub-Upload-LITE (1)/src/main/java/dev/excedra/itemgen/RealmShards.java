package dev.excedra.itemgen;



import java.io.*;

import java.nio.file.*;

import java.util.*;

import java.util.logging.Level;

import java.util.concurrent.CompletableFuture;

import java.util.concurrent.CompletionException;

import net.kyori.adventure.text.Component;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;

import org.bukkit.block.Block;

import org.bukkit.command.*;

import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.entity.*;

import org.bukkit.event.*;

import org.bukkit.event.block.*;

import org.bukkit.event.entity.*;

import org.bukkit.event.inventory.*;

import org.bukkit.event.player.*;

import org.bukkit.event.world.WorldUnloadEvent;

import org.bukkit.inventory.*;

import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.PersistentDataType;

import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitTask;

import org.bukkit.util.Transformation;

import org.bukkit.util.Vector;

import org.joml.Quaternionf;

import org.joml.Vector3f;



final class RealmShards implements Listener, CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    private final IslandBridge islands = new IslandBridge();

    private final NamespacedKey specKey;

    private final Map<UUID,Session> active = new HashMap<>();

    private final Map<UUID,List<ItemStack>> refunds = new HashMap<>();

    private final List<BukkitTask> tasks = new ArrayList<>();

    private final Random rng = new Random();

    private Path journalFolder;

    private TestLandRemoval removals;

    private final JournalWriter journalWriter=new JournalWriter();

    private HeldShardDisplay heldDisplays;

    private final ShardAccess access;private SchematicForge schematicForge;private ShardClaims claims;

    private StarterRealms starters;private OwnerRitualTools ownerTools;private ReclaimRefunds reclaimRefunds;private WandConvergence wandConvergence;

    private RealmweaverWand realmweaver;
    private RealmCore realmCore;
    private RealmBoundaryPreview boundaryPreview;
    private int blockBudget, particleBudget;

    RealmShards(JavaPlugin plugin){this.plugin=plugin;access=new ShardAccess(plugin);specKey=new NamespacedKey(plugin,"realmshard_spec");}

    void start(){

        plugin.saveDefaultConfig();

        blockBudget=Math.clamp(plugin.getConfig().getInt("realmshards.blocks-per-tick",180),20,500);

        particleBudget=Math.clamp(plugin.getConfig().getInt("realmshards.particles-per-tick",96),16,160);

        journalFolder=plugin.getDataFolder().toPath().resolve("shard-transactions");

        try {Files.createDirectories(journalFolder);claims=new ShardClaims(plugin.getDataFolder().toPath().resolve("npc-shard-claims")); loadRefunds(); inspectRecovery();}catch(IOException e){throw new IllegalStateException("Cannot initialize shard journal",e);}

        try{schematicForge=new SchematicForge(plugin,this,access);}catch(IOException e){throw new IllegalStateException("Cannot initialize schematic forge",e);}

        try{starters=new StarterRealms(plugin,this,access);}catch(IOException e){throw new IllegalStateException("Cannot initialize starter realms",e);}

        try{reclaimRefunds=new ReclaimRefunds(plugin,journalFolder);}catch(IOException e){throw new IllegalStateException("Cannot initialize reclaim refunds",e);}
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())if(reclaimRefunds.deliver(p))p.sendMessage("§eYour queued realm item has been returned to your inventory.");},40,40));
        removals=new TestLandRemoval(plugin,journalFolder,blockBudget,islands,reclaimRefunds);ownerTools=new OwnerRitualTools(plugin,this,access);
        wandConvergence=new WandConvergence(plugin,k->switch(k){case SHARDBREAKER->ownerTools.item(false);case NULLIFIER->ownerTools.item(true);case RECLAIMER->ownerTools.reclaimer();case CONVERGENCE->ownerTools.convergence();});

        try{realmCore=new RealmCore(plugin,this);}catch(IOException failure){throw new IllegalStateException("Cannot initialize Realm Core data",failure);}
        realmweaver=new RealmweaverWand(plugin,this);
        boundaryPreview=new RealmBoundaryPreview(plugin,islands);
        Bukkit.getPluginManager().registerEvents(this,plugin);

        PluginCommand cmd=plugin.getCommand("realmshards");if(cmd!=null){cmd.setExecutor(this);cmd.setTabCompleter(this);}

        heldDisplays=new HeldShardDisplay(plugin,this::read,starters::read);heldDisplays.start();

        for(Player p:Bukkit.getOnlinePlayers()) offerPack(p);

    }

    void stop(){try{if(realmweaver!=null)realmweaver.stop();if(wandConvergence!=null)wandConvergence.stop();if(starters!=null)starters.fusion.stop();if(schematicForge!=null)schematicForge.stop();if(heldDisplays!=null)heldDisplays.stop();if(removals!=null)removals.stop();if(ownerTools!=null)ownerTools.stop();for(Session s:List.copyOf(active.values()))s.abort("Server stopping");tasks.forEach(BukkitTask::cancel);saveRefunds();}finally{if(realmCore!=null)realmCore.stop();journalWriter.close();}}

    void openStarters(Player p){starters.open(p);}
    void openOwnerTools(Player p){ownerTools.open(p);}
    boolean useCoreTool(Player player,UUID realm){return ownerTools.useCore(player,realm);}
    void reclaimCore(Player player,UUID realm)throws Exception{if(!access.moderator(player))throw new IllegalStateException("Staff moderation access is required for the Realm Reclaimer.");if(active.values().stream().anyMatch(s->s.target.getWorld().equals(player.getWorld())))throw new IllegalStateException("Wait for the shard sequence to finish.");if(realmCore==null||!realmCore.reclaim(player,realm))throw new IllegalStateException("The targeted Core is no longer available.");}
    void afterWandUse(Player p,Runnable returned){removals.afterWandUse(p.getWorld(),returned);}
    void reclaim(Player p,ItemStack wand)throws Exception{if(!access.moderator(p))throw new IllegalStateException("Staff moderation access is required for the Realm Reclaimer.");if(active.values().stream().anyMatch(s->s.target.getWorld().equals(p.getWorld())))throw new IllegalStateException("Wait for the shard sequence to finish.");if(realmCore!=null&&realmCore.reclaim(p))return;removals.reclaim(p,wand);}
    void testRemove(Player p,boolean all,ItemStack ritual)throws Exception{if(active.values().stream().anyMatch(s->s.target.getWorld().equals(p.getWorld())))throw new IllegalStateException("Wait for the shard sequence in this world to finish.");if(all)removals.removeAll(p,ritual);else removals.removeStanding(p,ritual);}

    ItemStack item(ShardSpec spec){

        ItemStack item=new ItemStack(Material.AMETHYST_SHARD);ItemMeta m=item.getItemMeta();

        NamedTextColor c=spec.variant()==ShardSpec.Variant.RAINBOW?NamedTextColor.LIGHT_PURPLE:spec.variant()==ShardSpec.Variant.SHINY?NamedTextColor.GOLD:NamedTextColor.AQUA;

        m.displayName(Component.text(spec.title(),c));

        m.lore(List.of(Component.text("Tier "+ShardSpec.roman(spec.tier())+" • "+ShardSpec.tierName(spec.tier()),NamedTextColor.GRAY),Component.text(spec.variant()==ShardSpec.Variant.STANDARD?"Standard":spec.variant()==ShardSpec.Variant.SHINY?"Rare • Shiny":"Ultra-Rare • Rainbow",c),Component.text(spec.land()==null?"An undiscovered world waits within.":"A compressed fragment of "+ShardSpec.nice(spec.land().name())+".",NamedTextColor.DARK_GRAY),Component.text("Place with your Realmweaver Wand.",NamedTextColor.GRAY),Component.text("Choose this Shard in the wand menu.",NamedTextColor.DARK_AQUA)));

        m.getPersistentDataContainer().set(specKey,PersistentDataType.STRING,spec.key());

        m.setItemModel(new NamespacedKey("realmshards",spec.key()));

        m.setEnchantmentGlintOverride(spec.variant()!=ShardSpec.Variant.STANDARD);

        item.setItemMeta(m);return item;

    }

    ShardSpec read(ItemStack item){

        if(item==null||!item.hasItemMeta())return null;

        if(starters!=null){StarterRecipe recipe=starters.read(item);if(recipe!=null)return starters.spec(recipe);if(item.getItemMeta().getPersistentDataContainer().has(starters.key,PersistentDataType.STRING))return null;}

        if(realmCore!=null&&realmCore.tagged(item))return realmCore.read(item)==null?null:new ShardSpec(2,ShardSpec.Land.FOREST,ShardSpec.Variant.STANDARD);
        String text=item.getItemMeta().getPersistentDataContainer().get(specKey,PersistentDataType.STRING);if(text==null)return null;

        try {String[] p=text.split("_");return new ShardSpec(Integer.parseInt(p[1].substring(1)),p[0].equals("random")?null:ShardSpec.Land.valueOf(p[0].toUpperCase(Locale.ROOT)),ShardSpec.Variant.valueOf(p[2].toUpperCase(Locale.ROOT)));}catch(RuntimeException e){return null;}

    }

    ShardSpec biomeIngredient(ItemStack item){
        if(item==null||!item.hasItemMeta())return null;
        var tags=item.getItemMeta().getPersistentDataContainer();
        if(tags.has(starters.key)||tags.has(schematicForge.key)||(realmCore!=null&&realmCore.tagged(item)))return null;
        return read(item);
    }

    ItemStack visual(ShardSpec spec,String suffix){ItemStack i=item(spec);ItemMeta m=i.getItemMeta();m.setItemModel(new NamespacedKey("realmshards",spec.key()+suffix));i.setItemMeta(m);return i;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){

        try {

            if(args.length>0&&args[0].equalsIgnoreCase("claim")){

                if(!(sender instanceof ConsoleCommandSender))throw new IllegalArgumentException("NPC claims must run through the server console.");

                if(args.length!=4)throw new IllegalArgumentException("realmshards claim <player> <biome|random> <1-5>");

                Player player=Bukkit.getPlayerExact(args[1]);if(player==null)throw new IllegalArgumentException("Player must be online.");

                ShardSpec.Land land=args[2].equalsIgnoreCase("random")?null:ShardSpec.Land.valueOf(args[2].toUpperCase(Locale.ROOT));int tier=Integer.parseInt(args[3]);

                ShardSpec reward=new ShardSpec(tier,land,rollVariant(tier));ItemStack item=item(reward);

                var result=claims.grant(player.getUniqueId(),reward.key(),()->player.isOnline()&&player.getInventory().firstEmpty()>=0,()->player.isOnline()&&player.getInventory().addItem(item).isEmpty());

                switch(result){case CLAIMED->player.sendMessage("You claimed your one-time RealmShard reward!");case ALREADY_CLAIMED->player.sendMessage("You have already claimed your one-time RealmShard reward.");case NO_SPACE->player.sendMessage("Free an inventory slot and click the NPC again. Your claim is still available.");}

                sender.sendMessage("NPC shard claim for "+player.getName()+": "+result);return true;

            }

            if(args.length==1&&args[0].equalsIgnoreCase("testtools")&&sender instanceof Player p&&access.moderator(p)){ownerTools.open(p);return true;}
            if(!access.staff(sender)){sender.sendMessage("§cOnly Admin, Head-Admin and Owner ranks can use /realmshards.");return true;}

            if(args.length>0&&args[0].equalsIgnoreCase("boundary")){
                if(!(sender instanceof Player p))throw new IllegalArgumentException("Use boundary calibration in-game.");
                if(args.length<2)throw new IllegalArgumentException("/realmshards boundary <profiles|preview [radius]|status>");
                switch(args[1].toLowerCase(Locale.ROOT)){
                    case "profiles" -> boundaryPreview.profiles(p);
                    case "preview" -> boundaryPreview.preview(p,args.length>2?Integer.parseInt(args[2]):24);
                    case "status" -> p.sendMessage("§dHybrid boundary: §fcalibration only; physical changes and automatic effects disabled.");
                    default -> throw new IllegalArgumentException("/realmshards boundary <profiles|preview [radius]|status>");
                }
                return true;
            }
            if(args.length>0&&args[0].equalsIgnoreCase("schematics")){if(!(sender instanceof Player p))throw new IllegalArgumentException("Open the schematic forge in-game.");schematicForge.browse(p);return true;}

            if(args.length==0||args[0].equalsIgnoreCase("menu")){if(sender instanceof Player p&&access.staff(p))open(p,new ShardSpec(3,ShardSpec.Land.FOREST,ShardSpec.Variant.STANDARD));else sender.sendMessage("Open this staff menu in-game.");return true;}

            if(args[0].equalsIgnoreCase("preview")&&sender instanceof Player p){if(!access.staff(p))return true;ShardSpec s=read(p.getInventory().getItemInMainHand());if(s==null)throw new IllegalArgumentException("Hold a RealmShard to preview it.");preview(p,s);return true;}

            if(args[0].equalsIgnoreCase("testtools")){if(!(sender instanceof Player p)||args.length!=1)throw new IllegalArgumentException("Open /realmshards testtools in-game.");ownerTools.open(p);return true;}

            if(args[0].equalsIgnoreCase("testremove")||args[0].equalsIgnoreCase("testremoveall")){

                if(!(sender instanceof Player p))throw new IllegalArgumentException("Stand on the generated fragment in-game to use testremove.");

                if(active.values().stream().anyMatch(s->s.target.getWorld().equals(p.getWorld())))throw new IllegalStateException("Wait for the shard sequence in this world to finish.");

                boolean all=args[0].equalsIgnoreCase("testremoveall")||(args.length==2&&args[1].equalsIgnoreCase("all"));if(args.length!=1&&!(args.length==2&&args[0].equalsIgnoreCase("testremove")&&all))throw new IllegalArgumentException("/realmshards testremove [all] or /realmshards testremoveall"); if(all)removals.removeAll(p);else removals.removeStanding(p);return true;

            }

            if((args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("roll"))&&args.length>=4){

                Player p=Bukkit.getPlayerExact(args[1]);if(p==null)throw new IllegalArgumentException("Player must be online.");

                ShardSpec.Land land=args[2].equalsIgnoreCase("random")?null:ShardSpec.Land.valueOf(args[2].toUpperCase(Locale.ROOT));int tier=Integer.parseInt(args[3]);

                ShardSpec.Variant variant;

                if(args[0].equalsIgnoreCase("roll")){variant=rollVariant(tier);}

                else variant=args.length>4?ShardSpec.Variant.valueOf(args[4].toUpperCase(Locale.ROOT)):ShardSpec.Variant.STANDARD;

                ItemStack i=item(new ShardSpec(tier,land,variant));if(p.getInventory().firstEmpty()<0)throw new IllegalArgumentException("Player inventory is full.");p.getInventory().addItem(i);sender.sendMessage("Granted "+i.getItemMeta().getDisplayName()+".");return true;

            }

            sender.sendMessage("/realmshards give <player> <biome|random> <1-5> [standard|shiny|rainbow]");sender.sendMessage("/realmshards roll <player> <biome|random> <1-5> • /realmshards preview • /realmshards testremove • /realmshards testremoveall");

        }catch(Exception e){sender.sendMessage("§c"+e.getMessage());}return true;

    }

    ShardSpec.Variant rollVariant(int tier){double roll=rng.nextDouble();return tier>=3&&roll<.005?ShardSpec.Variant.RAINBOW:roll<.055?ShardSpec.Variant.SHINY:ShardSpec.Variant.STANDARD;}

    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){

        if(!access.staff(s))return List.of();

        if(a.length==1){List<String> commands=new ArrayList<>(List.of("menu","preview","give","roll","testremove","testremoveall","boundary"));if(s instanceof ConsoleCommandSender)commands.add("claim");if(s instanceof Player p&&access.owner(p))commands.addAll(List.of("schematics","testtools"));return commands.stream().filter(v->v.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();}

        if(a.length==2&&a[0].equalsIgnoreCase("boundary"))return List.of("profiles","preview","status").stream().filter(v->v.startsWith(a[1].toLowerCase(Locale.ROOT))).toList();

        if(a.length==2&&a[0].equalsIgnoreCase("testremove"))return "all".startsWith(a[1].toLowerCase(Locale.ROOT))?List.of("all"):List.of();

        List<String> choices=switch(a.length){case 1->List.of();case 2->Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();case 3->{List<String> b=new ArrayList<>(Arrays.stream(ShardSpec.Land.values()).map(v->v.name().toLowerCase(Locale.ROOT)).toList());b.add("random");yield b;}case 4->List.of("1","2","3","4","5");case 5->List.of("standard","shiny","rainbow");default->List.of();};return choices.stream().filter(v->v.startsWith(a[a.length-1].toLowerCase(Locale.ROOT))).toList();

    }

    private static class ForgeMenu implements InventoryHolder {

        final UUID player; final ShardSpec selected;Inventory inventory;

        ForgeMenu(UUID p,ShardSpec s){player=p;selected=s;}

        public Inventory getInventory(){return inventory;}

    }

    void open(Player p,ShardSpec selected){

        if(!access.staff(p)){p.closeInventory();return;}

        ForgeMenu holder=new ForgeMenu(p.getUniqueId(),selected);

        holder.inventory=Bukkit.createInventory(holder,54,Component.text("\uE001\uE000",NamedTextColor.WHITE).font(net.kyori.adventure.key.Key.key("realmshards:forge")));

        for(int slot=0;slot<54;slot++)holder.inventory.setItem(slot,button(Material.BLACK_STAINED_GLASS_PANE," ","","panel"));

        int[] slots={10,11,12,13,14,15,19,20,21,22,23,24};int n=0;

        for(ShardSpec.Land land:ShardSpec.Land.values())holder.inventory.setItem(slots[n++],item(new ShardSpec(selected.tier(),land,selected.variant())));

        holder.inventory.setItem(16,item(new ShardSpec(selected.tier(),null,selected.variant())));

        holder.inventory.setItem(28,button(Material.PRISMARINE_SHARD,"§bTier "+ShardSpec.roman(selected.tier()),"Click to evolve the preview","tier"));

        holder.inventory.setItem(30,button(Material.NETHER_STAR,"§e"+ShardSpec.nice(selected.variant().name()),"Click to cycle variant • Rainbow begins at III","variant"));

        holder.inventory.setItem(32,item(selected));

        holder.inventory.setItem(34,button(Material.SPYGLASS,"§bPreview held shard","Visual rehearsal • no item consumed • no terrain placed","preview"));

        String realm;try{realm=islands.requirePlacement(p).world();}catch(IllegalStateException e){realm=e.getMessage();}

        holder.inventory.setItem(46,button(Material.COMPASS,"§6Your Realm",realm,"realm"));

        holder.inventory.setItem(49,button(Material.BEACON,"§bRelease held shard","Hold your chosen shard and right-click inside your island.","release"));

        if(access.owner(p))holder.inventory.setItem(48,OwnerRitualTools.control("seal","Owner Testing Crystals","Open the testing atelier."));

        if(access.owner(p))holder.inventory.setItem(50,button(Material.PAPER,"§dSchematic Forge","Owner • create shards from your own lands","schematic"));

        holder.inventory.setItem(51,button(Material.AMETHYST_SHARD,"§dStarter Realm Shards • Page 2","A separate collection of combinable world seeds","tier"));

        holder.inventory.setItem(52,button(Material.BARRIER,"§7Close","A world waiting to be unleashed","close"));p.openInventory(holder.inventory);

    }

    ItemStack button(Material material,String title,String lore,String model){ItemStack i=new ItemStack(material);ItemMeta m=i.getItemMeta();m.setDisplayName(title);m.setLore(List.of("§7"+lore));m.setItemModel(new NamespacedKey("realmshards","gui/"+model));i.setItemMeta(m);return i;}

    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){

        if(!(e.getView().getTopInventory().getHolder() instanceof ForgeMenu m))return;e.setCancelled(true);

        if(!(e.getWhoClicked() instanceof Player p)||!m.player.equals(p.getUniqueId())||e.getRawSlot()<0||e.getRawSlot()>=54)return;

        if(!access.staff(p)){p.closeInventory();return;}

        int slot=e.getRawSlot();ShardSpec s=m.selected;

        if(slot==48&&access.owner(p)){ownerTools.open(p);return;}

        if(slot==51){starters.open(p);return;}

        if(slot==50){try{schematicForge.browse(p);}catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}return;}

        if(slot==52){p.closeInventory();return;}

        if(slot==49){p.closeInventory();p.sendMessage("§bHold a shard, face empty space beyond your island edge, and right-click.");return;}

        if(slot==34){ShardSpec held=read(p.getInventory().getItemInMainHand());if(held==null){p.sendMessage("§7Hold a shard before opening the Forge to rehearse it.");return;}p.closeInventory();preview(p,held);return;}

        if(slot==28){int t=s.tier()%5+1;open(p,new ShardSpec(t,s.land(),t<3&&s.variant()==ShardSpec.Variant.RAINBOW?ShardSpec.Variant.STANDARD:s.variant()));return;}

        if(slot==30){ShardSpec.Variant v=ShardSpec.Variant.values()[(s.variant().ordinal()+1)%3];if(s.tier()<3&&v==ShardSpec.Variant.RAINBOW)v=ShardSpec.Variant.STANDARD;open(p,new ShardSpec(s.tier(),s.land(),v));return;}

        ShardSpec clicked=read(e.getCurrentItem());if(clicked!=null)open(p,clicked);

    }

    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof ForgeMenu)e.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void use(PlayerInteractEvent e){

        if(e.getHand()!=EquipmentSlot.HAND||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;

        // Air interactions may be pre-cancelled by Bukkit because vanilla has no action.
        if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&e.isCancelled())return;
        ShardSpec spec=read(e.getItem());if(spec==null)return;e.setCancelled(true);

        Player p=e.getPlayer();if(!p.hasPermission("worldforge.realmshards.use")){p.sendMessage("§cYou cannot activate shards.");return;}
        if(!p.hasPermission("worldforge.realmshards.place.direct")){p.sendMessage("§eUse your Realmweaver Wand to select and place this Shard.");return;}

        try{activate(p,spec);}catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}

    }

    void activate(Player p,ShardSpec original) throws IOException {
        activate(p,original,null,PlacementRotation.NONE);
    }

    /** Internal entry point for a future validated wand placement session. */
    void activate(Player p,ShardSpec original,Location placement,PlacementRotation rotation) throws IOException {
        if(!p.hasPermission("worldforge.realmshards.place.direct"))throw new IllegalStateException("Use a Realmweaver Wand to place shards.");
        activateSelected(p,prepareShard(p.getInventory().getItemInMainHand(),original),
            p.getInventory().getHeldItemSlot(),placement,rotation);
    }

    record PreparedShard(ShardSpec spec,TerrainPlan plan,ItemStack source) {}

    PreparedShard prepareShard(ItemStack item,ShardSpec original) throws IOException {
        ShardSpec chosen=new ShardSpec(original.tier(),original.land()==null?ShardSpec.Land.values()[rng.nextInt(ShardSpec.Land.values().length)]:original.land(),original.variant());
        List<Integer> radii=plugin.getConfig().getIntegerList("realmshards.tier-radius");
        int radius=TerrainSizing.radius(radii.size()==5?radii.get(chosen.tier()-1):3+chosen.tier()*2,chosen.variant());
        StarterRecipe starter=starters.read(item);
        CoreRecipe coreRecipe=realmCore==null?null:realmCore.read(item);
        if(realmCore!=null&&realmCore.tagged(item)&&coreRecipe==null)throw new IllegalArgumentException("This Core pattern cannot be decoded.");
        TerrainPlan plan=coreRecipe!=null?coreRecipe.plan():starter!=null?starters.plan(starter):schematicForge.plan(item,chosen);
        if(plan==null)plan=new TerrainPlan(radius,chosen.land(),rng.nextLong(),chosen.tier());
        ItemStack source=item.clone();source.setAmount(1);
        return new PreparedShard(chosen,plan,source);
    }

    String coreCoordinates(Player player,Location at){var center=realmCore.expansionAnchor(islands.requirePlacement(player).id());return center==null?"Realm center awaiting registration":"From Core: "+center.relative(at.getBlockX(),at.getBlockY(),at.getBlockZ());}
    PreparedShard connectShard(Player player,PreparedShard prepared,Location target,PlacementRotation rotation) {
        if(target==null||!player.getWorld().equals(target.getWorld()))throw new IllegalArgumentException("Select a target in your Realm.");
        var realm=islands.requirePlacement(player);
        realmCore.validateExpansion(realm.id(),target,prepared.plan().radius,realmCore.expansionAnchor(realm.id()));
        TerrainPlan connected=prepared.plan().rotated(rotation);
        World world=target.getWorld();
        var bridgeProtection=ProtectionBridge.checker(player);
        ConnectionBridge.Ground ground=new ConnectionBridge.Ground(){
            Block at(int x,int y,int z){return world.getBlockAt(target.getBlockX()+x,target.getBlockY()+y,target.getBlockZ()+z);}
            public boolean air(int x,int y,int z){return at(x,y,z).getType().isAir();}
            public String state(int x,int y,int z){return at(x,y,z).getBlockData().getAsString();}
            public boolean canBuild(int x,int y,int z){return bridgeProtection.allowed(at(x,y,z).getLocation());}
            public Integer floor(int x,int z,int nearY){
                for(int y=nearY+1;y>=nearY-6;y--){
                    int absoluteY=target.getBlockY()+y;
                    if(absoluteY<=world.getMinHeight()||absoluteY+2>=world.getMaxHeight())continue;
                    if(ConnectionBridge.terrain(at(x,y,z).getType())&&air(x,y+1,z)&&air(x,y+2,z))return y;
                }
                return null;
            }
        };
        Location position=player.getLocation();
        var center=realmCore.expansionAnchor(realm.id());
        var start=center==null?new TerrainPlan.Pos(position.getBlockX()-target.getBlockX(),position.getBlockY()-1-target.getBlockY(),position.getBlockZ()-target.getBlockZ()):CoreExpansionGeometry.bridgeStart(center,position.getBlockX(),position.getBlockY()-1,position.getBlockZ(),target.getBlockX(),target.getBlockY(),target.getBlockZ());
        var bridge=ConnectionBridge.plan(connected,start.x(),start.y(),start.z(),ground);
        connected.blocks.putAll(bridge.blocks());connected.bridgeAnchors.putAll(bridge.anchors());
        connected.bridgeClearance.addAll(bridge.clearance());connected.bridgePath.addAll(bridge.path());
        return new PreparedShard(prepared.spec(),connected,prepared.source());
    }

    void validatePlacement(Player player,PreparedShard prepared,Location target,PlacementRotation rotation) {
        if(target==null||!player.getWorld().equals(target.getWorld())||!Double.isFinite(target.getX())||!Double.isFinite(target.getY())||!Double.isFinite(target.getZ()))
            throw new IllegalArgumentException("Invalid realm placement location.");
        new Session(player,prepared.spec(),islands.requirePlacement(player),target,
            rotation==PlacementRotation.NONE?prepared.plan():prepared.plan().rotated(rotation),false).validate();
    }

    void activateSelected(Player p,PreparedShard prepared,int slot,Location placement,PlacementRotation rotation) throws IOException {
        if(!p.hasPermission("worldforge.realmshards.use"))throw new IllegalStateException("You cannot activate shards.");
        SelectedShardInventory.require(p.getInventory(),slot,prepared.source());
        java.util.Objects.requireNonNull(rotation,"rotation");
        if(placement!=null && (!p.getWorld().equals(placement.getWorld())
            || !Double.isFinite(placement.getX()) || !Double.isFinite(placement.getY()) || !Double.isFinite(placement.getZ())))
            throw new IllegalArgumentException("Placement must be a finite location in your current realm world.");

        if(active.containsKey(p.getUniqueId()))throw new IllegalStateException("Your shard is already awakening.");

        if(active.size()>=Math.clamp(plugin.getConfig().getInt("realmshards.max-active-server",4),1,8))throw new IllegalStateException("The Realm Forge is busy; try again shortly.");

        IslandBridge.Realm realm=islands.requirePlacement(p);

        if(removals.busy(p.getWorld()))throw new IllegalStateException("Wait for testing removal to finish in this island.");

        if(active.values().stream().anyMatch(s->!s.preview&&s.realm.id().equals(realm.id())))throw new IllegalStateException("This island is already receiving terrain.");

        ShardSpec chosen=prepared.spec();
        TerrainPlan plan=prepared.plan();int radius=plan.radius;

        double yaw=Math.toRadians(p.getLocation().getYaw());Vector dir=new Vector(-Math.sin(yaw),0,Math.cos(yaw));

        Location target=p.getLocation().clone().add(dir.multiply((radius+7)*Math.sqrt(2)));target.setY(Math.floor(p.getLocation().getY())-1);target.setX(target.getBlockX());target.setZ(target.getBlockZ());

        if(placement!=null) {
            target=placement.clone();
            target.setX(target.getBlockX());target.setY(target.getBlockY());target.setZ(target.getBlockZ());
        }
        if(rotation!=PlacementRotation.NONE)plan=plan.rotated(rotation);
        Session session=new Session(p,chosen,realm,target,plan,false);

        session.validate();

        session.reserved=prepared.source().clone();

        session.journal("PREPARED");

        SelectedShardInventory.consume(p.getInventory(),slot,prepared.source());

        active.put(p.getUniqueId(),session);

        try{session.journal("RESERVED");session.start();}catch(Exception e){session.abort("Activation failed");throw new IOException("Unable to start; shard returned.",e);}

    }

    void preview(Player p,ShardSpec s){if(active.containsKey(p.getUniqueId())){p.sendMessage("§7Wait for the current sequence to finish.");return;}if(active.size()>=4){p.sendMessage("§7The Forge is busy.");return;}Location target=p.getEyeLocation().add(p.getLocation().getDirection().multiply(3));Session session=new Session(p,new ShardSpec(s.tier(),s.land()==null?ShardSpec.Land.FOREST:s.land(),s.variant()),null,target,null,true);active.put(p.getUniqueId(),session);try{session.start();}catch(Exception e){session.abort("Preview could not start.");plugin.getLogger().log(Level.WARNING,"Shard preview failed",e);}}

    private final class Session {

        final Player player;final ShardSpec spec;final IslandBridge.Realm realm;final Location target;final TerrainPlan plan;final boolean preview;

        final UUID id=UUID.randomUUID();final List<ItemDisplay> displays=new ArrayList<>();final List<TerrainPlan.Pos> placed=new ArrayList<>();

        final ProtectionBridge.Guard protections;
        final ProtectionBridge.Guard clearanceProtections;

        final Map<TerrainPlan.Pos,String> oldData=new LinkedHashMap<>();

        final Map<TerrainPlan.Pos,String> placedData=new LinkedHashMap<>();

        final List<int[]> chunks=new ArrayList<>();

        final Map<TerrainPlan.Pos,org.bukkit.block.Biome> oldBiomes=new LinkedHashMap<>();

        final List<TerrainPlan.Pos> changedBiomes=new ArrayList<>();

        final Map<Material,org.bukkit.block.data.BlockData> materialData=new EnumMap<>(Material.class);

        final Map<String,org.bukkit.block.data.BlockData> schematicData=new HashMap<>();

        int clearanceIndex,snapshotIndex,biomeIndex;

        boolean prepared,waitingCommit,waitingComplete,waitingVisual;

        CompletableFuture<Void> journalPending;

        List<TerrainPlan.Pos> biomeEntries;

        PrismStorm storm;

        ItemStack reserved;BukkitTask task;ItemDisplay crystal;int tick=0,index=0,duration;boolean finished,committing;

        List<Map.Entry<TerrainPlan.Pos,Material>> entries;Location start;

        final RealmCoreStore.Anchor coreAnchor;
        Session(Player p,ShardSpec s,IslandBridge.Realm r,Location t,TerrainPlan plan,boolean preview){this.player=p;spec=s;realm=r;target=t;this.plan=plan;this.preview=preview;coreAnchor=r==null?null:realmCore.expansionAnchor(r.id());protections=ProtectionBridge.checker(p);clearanceProtections=ProtectionBridge.clearanceChecker(p);List<Integer> times=plugin.getConfig().getIntegerList("realmshards.tier-animation-ticks");duration=Math.clamp(times.size()==5?times.get(s.tier()-1):80+s.tier()*25,80,300);}

        boolean contains(Location l){return !preview&&l.getWorld().equals(target.getWorld())&&(Math.abs(l.getX()-target.getX())<=plan.radius+4&&Math.abs(l.getZ()-target.getZ())<=plan.radius+4&&l.getY()>=target.getY()+plan.minY()-3&&l.getY()<=target.getY()+plan.maxY()+3||plan.bridgeClearance.contains(new TerrainPlan.Pos(l.getBlockX()-target.getBlockX(),l.getBlockY()-target.getBlockY(),l.getBlockZ()-target.getBlockZ())));}

        Block block(TerrainPlan.Pos p){return target.getWorld().getBlockAt(target.getBlockX()+p.x(),target.getBlockY()+p.y(),target.getBlockZ()+p.z());}

        void validate(){

            IslandBridge.Realm now=islands.requirePlacement(player);if(!now.id().equals(realm.id())||!player.getWorld().equals(target.getWorld()))throw new IllegalStateException("You must stay inside your own island.");

            realmCore.validateExpansion(realm.id(),target,plan.radius,coreAnchor);
            World w=target.getWorld();int r=plan.radius+4;

            for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)for(int y=plan.minY()-3;y<=plan.maxY()+3;y++){

                Location l=target.clone().add(x,y,z);if(l.getY()<=w.getMinHeight()||l.getY()>=w.getMaxHeight())throw new IllegalStateException("There is not enough vertical space.");

                if(!w.getWorldBorder().isInside(l))throw new IllegalStateException("The fragment would cross your island world border.");

                if(!l.getBlock().getType().isAir())throw new IllegalStateException("Face clear void beyond your island edge; existing terrain is preserved.");

            }

            for(Entity entity:w.getNearbyEntities(target.clone().add(0,(plan.minY()+plan.maxY())/2.0,0),r,(plan.maxY()-plan.minY())/2.0+4,r))if(entity instanceof LivingEntity)throw new IllegalStateException("The release area must be clear of players and creatures.");

            validateBridge(false);
            for(TerrainPlan.Pos pos:plan.blocks.keySet())if(!protections.allowed(block(pos).getLocation()))throw new IllegalStateException("Permanent Core or region protection denies this terrain.");

        }

        void validateBridge(boolean allowPlaced){
            World world=target.getWorld();
            for(var entry:plan.bridgeAnchors.entrySet()){
                Block anchor=block(entry.getKey());
                if(!anchor.getBlockData().getAsString().equals(entry.getValue()))throw new IllegalStateException("Bridge anchor changed; retarget the Shard.");
            }
            for(var position:plan.bridgeClearance){
                Block cell=block(position);Location location=cell.getLocation();
                if(location.getY()<=world.getMinHeight()||location.getY()>=world.getMaxHeight()||!world.getWorldBorder().isInside(location))
                    throw new IllegalStateException("Bridge would cross the Realm boundary.");
                String placedState=allowPlaced?placedData.get(position):null;
                if(placedState!=null){if(!cell.getBlockData().getAsString().equals(placedState))throw new IllegalStateException("Bridge changed while forging.");}
                else if(!cell.getType().isAir())throw new IllegalStateException("Bridge route is obstructed; retarget the Shard.");
                // Air above an unchanged shore anchor is checked, never written.
                var bridgeGuard=plan.blocks.containsKey(position)?protections:clearanceProtections;
                if(!bridgeGuard.allowed(location))throw new IllegalStateException("Protection denies the bridge route.");
                if(!plan.blocks.containsKey(position)&&world.getNearbyEntities(location.clone().add(.5,.5,.5),.49,.49,.49).stream().anyMatch(e->e instanceof LivingEntity))
                    throw new IllegalStateException("Step away from the bridge route before placing the Shard.");
            }
        }

        ItemStack sessionVisual(String suffix){

            StarterRecipe recipe=starters.read(reserved);if(recipe==null)return visual(spec,suffix);

            ItemStack i=starters.item(recipe);if(!suffix.isEmpty()){ItemMeta m=i.getItemMeta();m.setItemModel(new NamespacedKey("realmshards",m.getItemModel().getKey()+suffix));i.setItemMeta(m);}return i;

        }

        void start(){

            if(!preview)entries=new ArrayList<>(plan.blocks.entrySet());

            if(!preview){int r=plan.radius+4;for(int x=Math.floorDiv(target.getBlockX()-r,16);x<=Math.floorDiv(target.getBlockX()+r,16);x++)for(int z=Math.floorDiv(target.getBlockZ()-r,16);z<=Math.floorDiv(target.getBlockZ()+r,16);z++){target.getWorld().addPluginChunkTicket(x,z,plugin);chunks.add(new int[]{x,z});}}

            if(!preview){
                Set<Long> existing=new HashSet<>();for(int[] chunk:chunks)existing.add(TerrainPlan.key(chunk[0],chunk[1]));
                for(var position:plan.bridgeClearance){
                    int cx=Math.floorDiv(target.getBlockX()+position.x(),16),cz=Math.floorDiv(target.getBlockZ()+position.z(),16);
                    if(existing.add(TerrainPlan.key(cx,cz))){target.getWorld().addPluginChunkTicket(cx,cz,plugin);chunks.add(new int[]{cx,cz});}
                }
            }
            start=player.getEyeLocation().add(player.getLocation().getDirection().multiply(.65));

            crystal=spawn(start,sessionVisual(""),.5f+spec.tier()*.12f);

            player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.8f,.6f+spec.tier()*.1f);

            task=Bukkit.getScheduler().runTaskTimer(plugin,()->{try{step();}catch(Exception e){plugin.getLogger().log(Level.WARNING,"RealmShard sequence failed",e);abort("The shard could not stabilize; it was returned.");}},1,1);

        }

        ItemDisplay spawn(Location l,ItemStack item,float size){ItemDisplay d=l.getWorld().spawn(l,ItemDisplay.class,e->{e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setPersistent(false);e.addScoreboardTag("realmshards_temporary");e.setBrightness(new Display.Brightness(15,15));e.setInterpolationDuration(2);e.setTeleportDuration(1);e.setViewRange(1.5f);});scale(d,size,0);displays.add(d);return d;}

        void step() throws IOException {

            if(finished)return;

            if(!player.isOnline()||player.isDead()||!player.getWorld().equals(target.getWorld())){abort("Activation cancelled; shard returned.");return;}

            if(!preview&&!islands.requirePlacement(player).id().equals(realm.id())){abort("Island ownership changed; shard returned.");return;}

            if(storm!=null)storm.tick();

            if(waitingVisual){workingVisual("Prism fragments fading",100);if(storm.done())complete();return;}

            if(waitingComplete){workingVisual("Securing your realm",100);if(journalPending.isDone()){finishJournal();complete();}return;}

            if(waitingCommit){workingVisual("Stabilizing terrain",100);if(journalPending.isDone()){finishJournal();waitingCommit=false;committing=true;}return;}

            if(committing){build();return;}

            if(!preview){prepareTick();if(tick>=duration){workingVisual("Preparing terrain",(int)(100.0*snapshotIndex/entries.size()));if(prepared)beginCommit();return;}}

            double phase=tick/(double)duration;double fly=Math.min(1,tick/(duration*.25));double ease=fly*fly*(3-2*fly);

            Location at=start.clone().add(target.toVector().subtract(start.toVector()).multiply(ease));at.add(0,Math.sin(tick*.12)*(.10*(1-fly)+.025),0);

            if(spec.tier()==1&&phase<.43)at.add(Math.sin(tick*.65)*.08,Math.cos(tick*.5)*.06,Math.cos(tick*.65)*.08);

            crystal.teleport(at);scale(crystal,(float)(.5+spec.tier()*.12+Math.max(0,phase-.60)*1.2),tick*.018f);

            Color color=color(spec,tick);StarterRecipe awakening=starters.read(reserved);if(awakening!=null){int[] hues={0xA777FF,0x52FFD1,0xF24AE8,0x99B6FF,0x65E4FF,0xEDE1B8,0xFF872D,0xC689FF,0x7788FF};List<Integer> included=new ArrayList<>();for(int i=0;i<9;i++)if(awakening.counts().get(i)>0)included.add(i);color=Color.fromRGB(hues[included.get((tick/12)%included.size())]);}

            int count=0;

            if(phase>.22){int rings=spec.tier()==5?3:spec.tier()==4?2:1;for(int ring=0;ring<rings;ring++)for(int n=0;n<particleBudget/(rings+2);n++){double a=n*Math.PI*2/(particleBudget/(rings+2))+tick*.03*(ring%2==0?1:-1);double radius=.6+spec.tier()*.13;double y=ring==0?0:Math.sin(a)*radius*(ring==1?.45:1);dust(at.clone().add(Math.cos(a)*radius,y,Math.sin(a)*radius),color,.55f);count++;}}

            if(phase>.43){for(int n=0;n<Math.min(16,spec.tier()*3);n++){double a=n*2.399+tick*.07;double y=(n%5-2)*.22;dust(at.clone().add(Math.cos(a)*.42,y,Math.sin(a)*.42),n%3==0?Color.WHITE:color,.65f);count++;}}

            if(phase>.60&&tick%6==0)player.playSound(at,Sound.BLOCK_AMETHYST_BLOCK_HIT,.45f,(float)(.8+phase*.7));

            if(spec.variant()==ShardSpec.Variant.SHINY&&tick%3==0)at.getWorld().spawnParticle(Particle.END_ROD,at,2,.4,.6,.4,.01);

            if(spec.variant()==ShardSpec.Variant.RAINBOW&&tick%3==0)at.getWorld().spawnParticle(Particle.ENCHANT,at,4,.5,.7,.5,.2);

            if(tick==(int)(duration*.42))for(int n=0;n<new int[]{2,3,6,9,12}[spec.tier()-1];n++)spawn(at,sessionVisual("_fragment"),.15f+.02f*spec.tier());

            for(int n=1;n<displays.size();n++){double a=n*Math.PI*2/(displays.size()-1)+tick*(.022+spec.tier()*.003);double rad=.85+spec.tier()*.12;if(phase>.78)rad+=(phase-.78)*8;Location f=at.clone().add(Math.cos(a)*rad,Math.sin(a*2)*.45,Math.sin(a)*rad);displays.get(n).teleport(f);scale(displays.get(n),.18f,n+tick*.04f);}

            if(tick==(int)(duration*.60))crystal.setItemStack(sessionVisual("_cracked"));

            if(phase>=.60&&phase<.78){if(storm==null)storm=new PrismStorm(plugin,target,spec,preview?.9:Math.min(3,.8+plan.radius*.08),false,id.getLeastSignificantBits());storm.charge(Math.clamp((phase-.60)/.18,0,1));}

            if(spec.tier()==5&&phase>.60&&tick%3==0)for(int h=0;h<10;h++)dust(at.clone().add(0,h*.35,0),Color.WHITE,.45f);

            if(tick==(int)(duration*.78)){crystal.setItemStack(sessionVisual("_core"));for(int n=displays.size()-1;n>=1;n--)displays.remove(n).remove();if(storm==null)storm=new PrismStorm(plugin,target,spec,.9,false,id.getLeastSignificantBits());storm.shatter();player.playSound(at,Sound.BLOCK_GLASS_BREAK,1f,.7f);player.playSound(at,Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,.65f,.7f);}

            if(phase>.78){float expansion=(float)((phase-.78)/.22);float tierScale=new float[]{.65f,.8f,1f,1.12f,1.23f}[spec.tier()-1];float finalSize=preview?2.4f:2*plan.radius*16/(7*.8f*tierScale);scale(crystal,1+expansion*(finalSize-1),tick*.006f);}

            if(tick%10==0)player.sendActionBar(Component.text((preview?"Rehearsal • ":"")+(phase<.25?"Launching":phase<.43?"Stabilizing":phase<.78?"Crystal fracturing":"World breaking free")+" • Tier "+ShardSpec.roman(spec.tier()),NamedTextColor.AQUA));

            tick++;

            if(tick>=duration){if(preview){complete();return;}if(prepared)beginCommit();}

        }

        void prepareTick(){

            if(prepared)return;
            validateBridge(false);

            int r=plan.radius+4,width=2*r+1,height=plan.maxY()-plan.minY()+7,total=width*width*height;

            TickWorkBudget scan=new TickWorkBudget(4096,2);World world=target.getWorld();

            while(clearanceIndex<total&&scan.acquire()){

                int cell=clearanceIndex++;int y=cell%height+plan.minY()-3;int column=cell/height;int z=column%width-r,x=column/width-r;

                Location l=target.clone().add(x,y,z);

                if(l.getY()<=world.getMinHeight()||l.getY()>=world.getMaxHeight()||!world.getWorldBorder().isInside(l))throw new IllegalStateException("Release bounds changed while stabilizing.");

                if(!l.getBlock().getType().isAir())throw new IllegalStateException("Release area changed while stabilizing.");

            }

            if(clearanceIndex<total)return;

            TickWorkBudget snapshot=new TickWorkBudget(512,2);

            while(snapshotIndex<entries.size()&&snapshot.acquire()){

                var e=entries.get(snapshotIndex++);Block b=block(e.getKey());if(!b.getType().isAir()||!protections.allowed(b.getLocation()))throw new IllegalStateException("Release area or protection changed.");

                oldData.put(e.getKey(),b.getBlockData().getAsString());

                TerrainPlan.Pos pos=new TerrainPlan.Pos(Math.floorDiv(b.getX(),4)*4-target.getBlockX(),Math.floorDiv(b.getY(),4)*4-target.getBlockY(),Math.floorDiv(b.getZ(),4)*4-target.getBlockZ());

                if(!oldBiomes.containsKey(pos))oldBiomes.put(pos,block(pos).getBiome());

            }

            if(snapshotIndex==entries.size()){prepared=true;biomeEntries=new ArrayList<>(oldBiomes.keySet());}

        }

        void beginCommit(){realmCore.validateExpansion(realm.id(),target,plan.radius,coreAnchor);journalPending=journalAsync("COMMITTING");waitingCommit=true;}

        void finishJournal()throws IOException{try{journalPending.join();}catch(CompletionException e){throw new IOException("Shard journal could not be saved",e.getCause());}}

        void workingVisual(String phase,int progress){

            tick++;if(crystal!=null){Location at=target.clone().add(0,Math.sin(tick*.10)*.025,0);crystal.teleport(at);}

            if(tick%4==0)for(int n=0;n<12;n++){double a=n*Math.PI/6+tick*.04;dust(target.clone().add(Math.cos(a)*1.4,.2,Math.sin(a)*1.4),color(spec,tick),.5f);}

            if(tick%10==0)player.sendActionBar(Component.text(phase+" • "+progress+"%",NamedTextColor.AQUA));

        }

        void build() throws IOException {
            validateBridge(true);

            realmCore.validateExpansion(realm.id(),target,plan.radius,coreAnchor);
            if(!islands.requirePlacement(player).id().equals(realm.id()))throw new IllegalStateException("Realm no longer belongs to you.");

            if(target.getWorld().getNearbyEntities(target.clone().add(0,(plan.minY()+plan.maxY())/2.0,0),plan.radius+4,(plan.maxY()-plan.minY())/2.0+4,plan.radius+4).stream().anyMatch(e->e instanceof LivingEntity))throw new IllegalStateException("A player or creature entered the release area.");

            TickWorkBudget work=new TickWorkBudget(blockBudget,3);

            for(;index<entries.size()&&work.acquire();index++){

                var e=entries.get(index);Block b=block(e.getKey());if(!b.getType().isAir()||!protections.allowed(b.getLocation()))throw new IllegalStateException("Release area changed while forging.");

                org.bukkit.block.data.BlockData data=materialData.computeIfAbsent(e.getValue(),m->{var d=m.createBlockData();if(d instanceof org.bukkit.block.data.type.Leaves leaf)leaf.setPersistent(true);return d;});if(plan.states.containsKey(e.getKey()))data=schematicData.computeIfAbsent(plan.states.get(e.getKey()),Bukkit::createBlockData);b.setBlockData(data,false);placed.add(e.getKey());placedData.put(e.getKey(),data.getAsString());

            }

            if(index<entries.size()){workingVisual("World taking shape",(int)(90.0*index/entries.size()));return;}

            TickWorkBudget biomeWork=new TickWorkBudget(64,2);

            while(biomeIndex<biomeEntries.size()&&biomeWork.acquire()){TerrainPlan.Pos pos=biomeEntries.get(biomeIndex++);block(pos).setBiome(plan.biome());changedBiomes.add(pos);}

            workingVisual("Awakening the biome",90+(int)(10.0*biomeIndex/Math.max(1,biomeEntries.size())));

            if(biomeIndex==biomeEntries.size()){journalPending=journalAsync("COMPLETE");committing=false;waitingComplete=true;}

        }

        void complete(){if(storm!=null&&!storm.done()){waitingVisual=true;return;}finished=true;cleanup();player.playSound(target,Sound.UI_TOAST_CHALLENGE_COMPLETE,.6f,1.2f);player.sendMessage(preview?"§bRehearsal complete. Your shard is unchanged.":"§bA "+ShardSpec.nice(spec.land().name())+" fragment has joined your realm.");}

        void abort(String message){if(finished)return;finished=true;boolean rollback=true;

            for(TerrainPlan.Pos p:placed){Block b=block(p);if(b.getBlockData().getAsString().equals(placedData.get(p)))b.setBlockData(Bukkit.createBlockData(oldData.get(p)),false);else rollback=false;}

            for(TerrainPlan.Pos p:changedBiomes){Block b=block(p);if(b.getBiome().equals(plan.biome()))b.setBiome(oldBiomes.get(p));else rollback=false;}

            cleanup();

            if(!preview&&reserved!=null){if(rollback){try{journal("REFUND_PENDING");queueRefund(player.getUniqueId(),reserved);journal("REFUNDED");deliverRefunds(player);}catch(Exception e){plugin.getLogger().log(Level.SEVERE,"Refund requires manual review for "+id,e);}}else try{journal("REVIEW_REQUIRED");}catch(IOException e){plugin.getLogger().log(Level.SEVERE,"Cannot journal rollback",e);}}

            if(player.isOnline())player.sendMessage("§7"+message);

        }

        void cleanup(){if(task!=null)task.cancel();if(storm!=null)storm.close();displays.forEach(Entity::remove);for(int[] c:chunks)target.getWorld().removePluginChunkTicket(c[0],c[1],plugin);active.remove(player.getUniqueId(),this);}

        void journal(String status) throws IOException {

            try{journalAsync(status).join();}catch(CompletionException e){throw new IOException("Shard journal could not be saved",e.getCause());}

        }

        CompletableFuture<Void> journalAsync(String status){

            YamlConfiguration y=new YamlConfiguration();y.set("status",status);y.set("player",player.getUniqueId().toString());y.set("world",target.getWorld().getName());y.set("realm",realm.id().toString());y.set("spec",spec.key());y.set("reserved",reserved);

            y.set("origin",List.of(target.getBlockX(),target.getBlockY(),target.getBlockZ()));y.set("seed",plan.seed);
            if(coreAnchor!=null){y.set("realm-center.world",coreAnchor.world().toString());y.set("realm-center.revision",coreAnchor.revision());y.set("realm-center.origin",List.of(coreAnchor.x(),coreAnchor.y(),coreAnchor.z()));y.set("realm-center.relative-origin",coreAnchor.relative(target.getBlockX(),target.getBlockY(),target.getBlockZ()));}
            if(!plan.bridgePath.isEmpty()){
                y.set("bridge.path",plan.bridgePath.stream().map(p->p.x()+","+p.y()+","+p.z()).toList());
                y.set("bridge.anchors",plan.bridgeAnchors.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue()).toList());
            }

            y.set("schematic-template",schematicForge.id(reserved));y.set("world-uuid",target.getWorld().getUID().toString());y.set("radius",plan.radius);y.set("generated-biome",plan.biome().getKey().toString());

            List<String> cells=new ArrayList<>();for(var e:oldData.entrySet()){TerrainPlan.Pos p=e.getKey();cells.add(p.x()+","+p.y()+","+p.z()+"|"+e.getValue()+"|"+plan.blocks.get(p).name()+"|"+placedData.getOrDefault(p,""));}

            List<String> biomes=new ArrayList<>();for(var e:oldBiomes.entrySet()){TerrainPlan.Pos p=e.getKey();biomes.add(p.x()+","+p.y()+","+p.z()+"|"+e.getValue().getKey());}

            return journalWriter.write(new JournalWriter.Snapshot(journalFolder.resolve(id+".yml"),y.saveToString(),cells,biomes));

        }

    }

    static void scale(ItemDisplay d,float size,float angle){d.setInterpolationDelay(0);d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY(angle),new Vector3f(size),new Quaternionf()));}

    static void dust(Location l,Color c,float size){l.getWorld().spawnParticle(Particle.DUST,l,1,0,0,0,0,new Particle.DustOptions(c,size));}

    static Color color(ShardSpec s,int tick){if(s.variant()==ShardSpec.Variant.SHINY)return Color.fromRGB(255,210,90);if(s.variant()==ShardSpec.Variant.RAINBOW){int rgb=java.awt.Color.HSBtoRGB((tick%120)/120f,.8f,1);return Color.fromRGB(rgb&0xffffff);}return switch(s.land()==null?ShardSpec.Land.FOREST:s.land()){case SNOW->Color.fromRGB(160,225,255);case DESERT,BADLANDS->Color.fromRGB(255,180,80);case VOLCANIC,NETHER->Color.fromRGB(255,65,70);case CRYSTAL,END->Color.fromRGB(185,95,255);case MUSHROOM->Color.fromRGB(255,100,145);case OCEAN->Color.fromRGB(60,155,255);default->Color.fromRGB(70,245,195);};}

    void heldAura(){for(Player p:Bukkit.getOnlinePlayers()){ShardSpec s=read(p.getInventory().getItemInMainHand());if(s==null||s.variant()==ShardSpec.Variant.STANDARD||active.containsKey(p.getUniqueId()))continue;Location l=p.getEyeLocation().add(p.getLocation().getDirection().multiply(.7)).add(0,-.45,0);for(int n=0;n<6;n++){double a=n*Math.PI/3+System.currentTimeMillis()*.002;dust(l.clone().add(Math.cos(a)*.22,Math.sin(a)*.15,Math.sin(a)*.22),color(s,(int)(System.currentTimeMillis()/50%120)),.4f);}}}

    @EventHandler public void quit(PlayerQuitEvent e){Session s=active.get(e.getPlayer().getUniqueId());if(s!=null)s.abort("Activation cancelled.");}

    @EventHandler public void death(PlayerDeathEvent e){Session s=active.get(e.getEntity().getUniqueId());if(s!=null)s.abort("Activation cancelled.");}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void unload(WorldUnloadEvent e){if(removals.busy(e.getWorld())||active.values().stream().anyMatch(s->s.target.getWorld().equals(e.getWorld())))e.setCancelled(true);}

    boolean reserved(Location l){return removals.reserved(l)||active.values().stream().anyMatch(s->s.contains(l));}

    @EventHandler(ignoreCancelled=true) public void place(BlockPlaceEvent e){if(reserved(e.getBlock().getLocation()))e.setCancelled(true);}

    @EventHandler(ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(reserved(e.getBlock().getLocation()))e.setCancelled(true);}

    @EventHandler(ignoreCancelled=true) public void flow(BlockFromToEvent e){if(reserved(e.getToBlock().getLocation()))e.setCancelled(true);}

    @EventHandler(ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(b->reserved(b.getLocation()));}

    @EventHandler(ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(b->reserved(b.getLocation()));}

    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->reserved(b.getLocation())||reserved(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}

    @EventHandler(ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->reserved(b.getLocation())||reserved(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}

    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->{deliverRefunds(e.getPlayer());offerPack(e.getPlayer());},20);}

    void offerPack(Player p){String url=plugin.getConfig().getString("realmshards.resource-pack.url","");String hash=plugin.getConfig().getString("realmshards.resource-pack.sha1","");if(url.isBlank())return;if(!url.startsWith("https://")||!hash.matches("[0-9a-fA-F]{40}")){plugin.getLogger().warning("RealmShard pack requires an HTTPS URL and 40-character SHA1.");return;}p.addResourcePack(UUID.fromString("d65df663-4264-4b67-8203-3c9c063f5e21"),url,HexFormat.of().parseHex(hash),"RealmShards models and Realm Forge interface",plugin.getConfig().getBoolean("realmshards.resource-pack.required",true));}

    void queueRefund(UUID id,ItemStack i){refunds.computeIfAbsent(id,k->new ArrayList<>()).add(i.clone());saveRefunds();}

    void deliverRefunds(Player p){if(!p.isOnline())return;List<ItemStack> list=refunds.get(p.getUniqueId());if(list==null)return;Iterator<ItemStack> it=list.iterator();while(it.hasNext()){if(p.getInventory().firstEmpty()<0)break;p.getInventory().addItem(it.next());it.remove();}if(list.isEmpty())refunds.remove(p.getUniqueId());saveRefunds();}

    void loadRefunds(){YamlConfiguration y=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("shard-refunds.yml").toFile());for(String k:y.getKeys(false)){List<ItemStack> list=new ArrayList<>();for(Object v:y.getList(k,List.of()))if(v instanceof ItemStack i)list.add(i);refunds.put(UUID.fromString(k),list);}}

    void saveRefunds(){YamlConfiguration y=new YamlConfiguration();refunds.forEach((id,list)->y.set(id.toString(),list));try{atomicSave(y,plugin.getDataFolder().toPath().resolve("shard-refunds.yml"));}catch(IOException e){throw new IllegalStateException("Cannot persist shard refunds",e);}}

    void inspectRecovery() throws IOException {

        try(var files=Files.list(journalFolder)){for(Path path:files.filter(p->p.toString().endsWith(".yml")).toList()){YamlConfiguration y=YamlConfiguration.loadConfiguration(path.toFile());String status=y.getString("status","");if(!List.of("COMPLETE","REMOVED","RECLAIMED","REFUNDED","REVIEW_REQUIRED").contains(status)){y.set("status","REVIEW_REQUIRED");y.set("previous-status",status);atomicSave(y,path);plugin.getLogger().severe("Interrupted shard transaction "+path.getFileName()+" requires review. No automatic terrain edits or duplicate refunds will be made.");}}}

        for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e.getScoreboardTags().contains("realmshards_temporary"))e.remove();

    }

    static void atomicSave(YamlConfiguration y,Path file) throws IOException {Path temp=file.resolveSibling(file.getFileName()+".tmp");byte[] data=y.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8);try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(data);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}

}





