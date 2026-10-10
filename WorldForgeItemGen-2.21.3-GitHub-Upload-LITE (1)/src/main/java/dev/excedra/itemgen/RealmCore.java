package dev.excedra.itemgen;

import java.io.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Permanent Core and first Woodland discovery loop. It does not change staff wands. */
final class RealmCore implements Listener,CommandExecutor {
    private final JavaPlugin plugin;
    private final RealmShards shards;
    private final RealmCoreStore store;
    private final CoreShrineDesign shrine;
    private final CoreAppearance appearance;
    private final CoreCustomization customization;
    private final CoreAutomatic automatic;
    private final NamespacedKey recipeKey,creatorKey,seedKey,seedOwnerKey,seedSerialKey;
    private final IslandBridge islands=new IslandBridge();
    private final Map<UUID,BukkitRunnable> rituals=new HashMap<>();
    private final Map<UUID,UUID> ritualPlayers=new HashMap<>();
    private record Manifesting(RealmCoreStore.Core core,Set<TerrainPlan.Pos> cells,CoreManifestation visual,Runnable stop){}
    private final Map<UUID,Manifesting> manifestations=new HashMap<>();
    private enum Kind { HOME,FORGE,JOURNAL }
    private static final class Menu implements InventoryHolder {
        final UUID player;
        final RealmCoreStore.Core core;
        final Kind kind;
        final CoreRecipe.Influence influence;
        final CoreRecipe.Influence secondary;
        final CoreRecipe exact;
        final int page;
        Inventory inventory;
        Menu(Player player,RealmCoreStore.Core core,Kind kind,CoreRecipe.Influence influence,CoreRecipe.Influence secondary,CoreRecipe exact,int page){this.player=player.getUniqueId();this.core=core;this.kind=kind;this.influence=influence;this.secondary=secondary;this.exact=exact;this.page=page;}
        public Inventory getInventory(){return inventory;}
    }
    RealmCore(JavaPlugin plugin,RealmShards shards)throws IOException {
        this.plugin=plugin;this.shards=shards;store=new RealmCoreStore(plugin.getDataFolder().toPath());
        recipeKey=new NamespacedKey(plugin,"core_recipe");creatorKey=new NamespacedKey(plugin,"core_pattern_creator");
        seedKey=new NamespacedKey(plugin,"core_seed_realm");
        seedOwnerKey=new NamespacedKey(plugin,"core_seed_owner");seedSerialKey=new NamespacedKey(plugin,"core_seed_serial");
        ProtectionBridge.permanent(location->!protectedLocation(location));
        store.backup();
        appearance=new CoreAppearance(plugin.getDataFolder().toPath());
        shrine=new CoreShrineDesign(plugin,store,appearance,realm->!manifestations.containsKey(realm));
        customization=new CoreCustomization(plugin,store,appearance,shrine,this::require);
        Bukkit.getPluginManager().registerEvents(this,plugin);PluginCommand command=plugin.getCommand("realmcore");if(command!=null)command.setExecutor(this);
        Bukkit.getScheduler().runTask(plugin,()->Bukkit.getOnlinePlayers().forEach(this::returnRefunds));
        automatic=new CoreAutomatic(plugin,store,islands,this::createAutomatic);
    }
    void stop(){automatic.stop();customization.stop();shrine.stop();for(var ritual:rituals.values())ritual.cancel();rituals.clear();ritualPlayers.clear();for(var operation:List.copyOf(manifestations.values())){operation.visual().close();operation.stop().run();}manifestations.clear();ProtectionBridge.permanent(location->true);}
    boolean tagged(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(recipeKey);}
    CoreRecipe read(ItemStack item){
        if(!tagged(item))return null;
        try{return CoreRecipe.decode(item.getItemMeta().getPersistentDataContainer().get(recipeKey,PersistentDataType.STRING));}catch(RuntimeException invalid){return null;}
    }
    ItemStack item(Player player,CoreRecipe recipe){
        ItemStack item=shards.item(new ShardSpec(2,ShardSpec.Land.FOREST,ShardSpec.Variant.STANDARD));ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(recipe.title()));
        meta.lore(List.of(Component.text("Woodland • "+recipe.influence().name()+(recipe.secondary()==null?"":" + "+recipe.secondary().name())),Component.text("Pattern: "+Long.toUnsignedString(recipe.seed(),16)),Component.text("Forged through your Realm Core."),Component.text("Use the Realmweaver Wand to expand your Realm.")));
        meta.getPersistentDataContainer().set(recipeKey,PersistentDataType.STRING,recipe.encode());meta.getPersistentDataContainer().set(creatorKey,PersistentDataType.STRING,player.getUniqueId().toString());item.setItemMeta(meta);return item;
    }
    private RealmCoreStore.Core require(Player player){
        if(!player.hasPermission("worldforge.realmcore.use"))throw new IllegalStateException("You cannot use a Realm Core.");
        IslandBridge.Realm realm=islands.requireOwned(player);var core=store.get(realm.id());
        if(core==null)throw new IllegalStateException("Your Realm Core is not ready. New starter islands install it automatically; ask staff if it is still missing.");
        if(!core.world().equals(player.getWorld().getUID()))throw new IllegalStateException("The registered Core belongs to a different world instance.");
        if(player.getLocation().distanceSquared(new Location(player.getWorld(),core.x()+.5,core.y()+1,core.z()+.5))>144)
            throw new IllegalStateException("Return to your Realm Core to forge.");
        if(player.getWorld().getBlockAt(core.x()+(core.design()==5?0:core.design()>=2?1:0),core.y()+(core.design()>=2?1:0),core.z()+(core.design()==5?0:-4)).getType()!=(core.design()==5?Material.CHISELED_DEEPSLATE:core.design()>=2?Material.MOSSY_STONE_BRICKS:Material.CHISELED_DEEPSLATE))
            throw new IllegalStateException("The Core basin is missing; its registered foundation needs repair.");
        return core;
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==1&&args[0].equalsIgnoreCase("refund")&&sender instanceof Player player){returnRefunds(player);return true;}
        if(args.length>0&&Set.of("mode","anchor","migrateanchor","customize","grantcosmetic","resetcosmetics").contains(args[0].toLowerCase(Locale.ROOT))){
            try{control(sender,args);}catch(Exception e){sender.sendMessage("§c"+e.getMessage());}return true;
        }
        if(!(sender instanceof ConsoleCommandSender)&&!sender.hasPermission("worldforge.realmcore.admin")){sender.sendMessage("§eNew starter islands include a Realm Core. Right-click its crystal or pedestal to forge and customize.");return true;}
        if(args.length==2&&args[0].equalsIgnoreCase("repairpad")){try{Player target=Bukkit.getPlayerExact(args[1]);if(target==null)throw new IllegalStateException("Player must be online.");ensurePlaza(target,islands.ownedRealm(target));sender.sendMessage("§bStarter plaza checked and repaired where safe.");}catch(Exception failure){sender.sendMessage("§c"+failure.getMessage());}return true;}
        if(args.length==2&&args[0].equalsIgnoreCase("autoretry")){try{Player target=Bukkit.getPlayerExact(args[1]);if(target==null)throw new IllegalStateException("Player must be online.");automatic.retry(islands.ownedRealm(target).id());sender.sendMessage("§bAutomatic Core eligibility will be checked again.");}catch(Exception failure){sender.sendMessage("§c"+failure.getMessage());}return true;}
        if(args.length>=1&&args[0].equalsIgnoreCase("resetclaim")){
            try{
                if(!(sender instanceof ConsoleCommandSender)&&!sender.hasPermission("worldforge.realmcore.resetclaim"))throw new IllegalStateException("Core claim reset permission is required.");
                UUID target;
                if(args.length==1&&sender instanceof Player self)target=self.getUniqueId();
                else if(args.length==2){
                    try{target=UUID.fromString(args[1]);}catch(IllegalArgumentException notUuid){
                        Player online=Bukkit.getPlayerExact(args[1]);OfflinePlayer cached=online==null?Bukkit.getOfflinePlayerIfCached(args[1]):online;
                        if(cached==null)throw new IllegalStateException("Unknown player name. Use their exact name or UUID.");target=cached.getUniqueId();
                    }
                }else throw new IllegalStateException("Usage: /realmcore resetclaim [player or UUID]; omit the target to reset yourself.");
                store.resetCoreClaim(target);
                Player online=Bukkit.getPlayer(target);if(online!=null)online.sendMessage("§bYour Core Shard claim was reset by staff. You can receive it from the NPC again; old Core Shards are invalid.");
                sender.sendMessage("§bCore Shard claim reset for "+(online==null?target:online.getName())+". Discoveries and island data were preserved.");
                plugin.getLogger().info(sender.getName()+" reset the Core Shard claim for "+target);
            }catch(Exception failure){sender.sendMessage("§c"+failure.getMessage());}return true;
        }
        if(args.length>=1&&args[0].equalsIgnoreCase("resetdiscoveries")){
            try{
                UUID target;
                if(args.length==1&&sender instanceof Player self)target=self.getUniqueId();
                else if(args.length==2){
                    try{target=UUID.fromString(args[1]);}catch(IllegalArgumentException notUuid){
                        OfflinePlayer found=Bukkit.getOfflinePlayerIfCached(args[1]);
                        if(found==null)throw new IllegalStateException("Unknown player name. Use their exact name or UUID.");target=found.getUniqueId();
                    }
                }else throw new IllegalStateException("Usage: /realmcore resetdiscoveries [player or UUID]; omit the target to reset yourself.");
                if(ritualPlayers.containsValue(target))throw new IllegalStateException("Wait for this player's active forging ritual to finish before resetting discoveries.");
                store.resetDiscoveries(target);
                Player online=Bukkit.getPlayer(target);
                if(online!=null){if(online.getOpenInventory().getTopInventory().getHolder() instanceof Menu)online.closeInventory();online.sendMessage("§eYour Core discovery journal has been reset by staff. You can learn recipes again.");}
                sender.sendMessage("§bDiscovery journal reset for "+(online==null?target:online.getName())+". Their Core, land and items were preserved.");
                plugin.getLogger().info(sender.getName()+" reset Core discoveries for "+target);
            }catch(Exception failure){sender.sendMessage("§c"+failure.getMessage());}return true;
        }
        if(args.length==2&&(args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("replace"))){
            Player recipient=Bukkit.getPlayerExact(args[1]);
            if(recipient==null){sender.sendMessage("§cThat player must be online.");return true;}
            try{boolean replacement=args[0].equalsIgnoreCase("replace");if(replacement&&!sender.hasPermission("worldforge.realmcore.replace"))throw new IllegalStateException("Core replacement permission is required.");giveSeed(recipient,replacement);sender.sendMessage("§bCore Shard granted to "+recipient.getName()+".");}catch(Exception failure){sender.sendMessage("§c"+failure.getMessage());recipient.sendMessage("§e"+failure.getMessage());}return true;
        }
        if(!(sender instanceof Player player)){sender.sendMessage("Use this command in your Realm.");return true;}
        try{
            if(args.length==1&&args[0].equalsIgnoreCase("home")){
                if(!player.hasPermission("worldforge.realmcore.use"))throw new IllegalStateException("You cannot use a Realm Core.");
                var core=store.get(islands.ownedRealm(player).id());
                if(core==null)throw new IllegalStateException("Your current Realm has no registered Core. No terrain was changed.");
                islands.coreHome(player,core,true);player.sendMessage("§bReturned to your Core. /island home now uses this safe approach.");return true;
            }
            if(args.length==1&&args[0].equalsIgnoreCase("recover")){require(player);store.recover(player);return true;}
            if(args.length==1&&args[0].equalsIgnoreCase("create")){throw new IllegalStateException("Core creation commands are retired. Use the NPC's one-time Realm Core Shard.");}
            if(args.length!=0){player.sendMessage("§e/realmcore, /realmcore create, /realmcore home, or /realmcore recover");return true;}
            open(player,require(player),Kind.HOME,CoreRecipe.Influence.ROOTS,null,0);
        }catch(Exception failure){player.sendMessage("§c"+failure.getMessage());}
        return true;
    }

    RealmCoreStore.Anchor expansionAnchor(UUID realm){return store.anchor(realm);}
    void validateExpansion(UUID realm,Location target,int radius,RealmCoreStore.Anchor expected){
        var now=store.anchor(realm);if(!Objects.equals(now,expected))throw new IllegalStateException("The Realm center changed during placement. Retarget your shard.");
        if(now==null){if(plugin.getConfig().getBoolean("realmcore.expansion.require-core",true))throw new IllegalStateException("Awaken your Core first. Staff can migrate an existing Core with /realmcore migrateanchor.");return;}
        if(!now.world().equals(target.getWorld().getUID()))throw new IllegalStateException("Realm center world identity changed.");
        int limit=Math.clamp(plugin.getConfig().getInt("realmcore.expansion.maximum-radius",4096),32,1000000);
        CoreExpansionGeometry.validate(now,target.getBlockX(),target.getBlockY(),target.getBlockZ(),radius,limit,RealmCoreLayout.radius(store.get(realm).design()));
    }
    private int[] attachmentAnchor(Player player,IslandBridge.Realm realm){return attachmentAnchor(player.getWorld(),realm);}
    private int[] attachmentAnchor(World world,IslandBridge.Realm realm){
        int[] selected=store.placementAnchor(realm.id());if(selected!=null)return selected;
        var target=CoreTemplateAnchor.select(plugin.getConfig(),islands.templateKeys(realm));int x=target.x(),z=target.z();
        if(!world.isChunkLoaded(x>>4,z>>4))throw new IllegalStateException("Approach the configured island center before placing the Core.");
        int y=target.y()==null?-2147483648:target.y();
        if(y==-2147483648&&target.marked())y=CoreTemplateAnchor.markedFloor(new CoreTemplateAnchor.Column(){public boolean marker(int level){return world.getBlockAt(x,level,z).getType()==Material.PINK_GLAZED_TERRACOTTA;}public boolean lantern(int level){return world.getBlockAt(x,level,z).getType()==Material.SEA_LANTERN;}},world.getMinHeight(),world.getMaxHeight());
        if(y==-2147483648){y=world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES);if(!ConnectionBridge.terrain(world.getBlockAt(x,y,z).getType()))throw new IllegalStateException("The configured center has no suitable ground; staff should use /realmcore anchor.");}
        return new int[]{x,y,z};
    }
    private void validateFoundation(World world,int x,int y,int z,ProtectionBridge.Guard guard){
        // Validate the existing floor under each grounded shrine component, never replace it.
        Set<TerrainPlan.Pos> supports=new HashSet<>();RealmCoreLayout.attached().keySet().stream().filter(p->p.y()==1).forEach(supports::add);supports.add(new TerrainPlan.Pos(0,1,0));supports.add(new TerrainPlan.Pos(0,1,-4));
        for(var p:supports){int ax=x+p.x(),az=z+p.z();if(!world.isChunkLoaded(ax>>4,az>>4))throw new IllegalStateException("Load the island center before placement.");Block floor=world.getBlockAt(ax,y,az);if(!floor.getType().isSolid()||floor.isPassable()||!guard.allowed(floor.getLocation())||shards.reserved(floor.getLocation()))throw new IllegalStateException("The shrine requires existing solid ground at its center and pillar/basin supports; no terrain was changed.");}
    }
    private void control(CommandSender sender,String[] args)throws IOException {
        String action=args[0].toLowerCase(Locale.ROOT);
        if(action.equals("grantcosmetic")){
            if(!(sender instanceof ConsoleCommandSender)&&!sender.hasPermission("worldforge.realmcore.cosmetics.grant"))throw new IllegalStateException("Explicit cosmetic reward-grant permission is required.");
            if(args.length!=4)throw new IllegalStateException("/realmcore grantcosmetic <online player> <category> <id>");Player target=Bukkit.getPlayerExact(args[1]);if(target==null)throw new IllegalStateException("Player must be online.");var option=CoreAppearance.option(args[2],args[3]);if(option==null)throw new IllegalStateException("Unknown cosmetic.");appearance.grant(target.getUniqueId(),option);plugin.getLogger().info(sender.getName()+" granted "+CoreAppearance.key(option)+" to "+target.getName());sender.sendMessage("§bCosmetic reward granted.");return;
        }
        if(!(sender instanceof Player player))throw new IllegalStateException("Use this control in game.");
        if(action.equals("customize")){if(args.length!=1)throw new IllegalStateException("/realmcore customize");customization.open(player,require(player));return;}
        if(!player.hasPermission("worldforge.realmcore.testing"))throw new IllegalStateException("Explicit administrative Core testing permission is required.");
        var realm=action.equals("anchor")||action.equals("migrateanchor")?islands.requirePlacement(player):islands.requireOwned(player);
        switch(action){
            case "mode"->{if(args.length!=2||!Set.of("attached","standalone").contains(args[1].toLowerCase(Locale.ROOT)))throw new IllegalStateException("/realmcore mode <attached|standalone>");if(manifestations.containsKey(realm.id()))throw new IllegalStateException("Wait for the Core animation.");store.standalone(player.getUniqueId(),args[1].equalsIgnoreCase("standalone"));player.sendMessage("§bYour placement mode: "+args[1]+". Only your own Realm is eligible for standalone tests.");}
            case "anchor"->{if(args.length!=1)throw new IllegalStateException("Stand on the desired center and use /realmcore anchor.");Location p=player.getLocation();Block floor=p.clone().add(0,-1,0).getBlock();if(!floor.getType().isSolid())throw new IllegalStateException("Stand on solid ground.");store.placementAnchor(realm.id(),p.getBlockX(),floor.getY(),p.getBlockZ());player.sendMessage("§bPer-Realm center override saved. It applies to future Core attachment.");}
            case "migrateanchor"->{var core=store.get(realm.id());if(core==null)throw new IllegalStateException("No registered Core in this Realm.");if(!core.world().equals(player.getWorld().getUID()))throw new IllegalStateException("Core world identity changed; staff review is required.");if(manifestations.containsKey(realm.id())||rituals.containsKey(realm.id()))throw new IllegalStateException("Wait for Core activity.");List<String> migrationCells=store.cells(core);if(migrationCells.isEmpty())migrationCells=(core.design()>=1?RealmCoreLayout.blocks():RealmCoreLayout.legacy()).entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue().name()).toList();for(String cell:migrationCells){String[] parts=cell.split("\\|"),xyz=parts[0].split(",");int bx=core.x()+Integer.parseInt(xyz[0]),by=core.y()+Integer.parseInt(xyz[1]),bz=core.z()+Integer.parseInt(xyz[2]);if(!player.getWorld().isChunkLoaded(bx>>4,bz>>4))throw new IllegalStateException("Load the entire Core and bridge before migration.");Block b=player.getWorld().getBlockAt(bx,by,bz);if(b.getType()!=Material.valueOf(parts[1]))throw new IllegalStateException("Core cells require inspection before migration.");}store.migrateAnchor(core);player.sendMessage("§bBackup created and existing Core registered as the Realm center. No blocks or expansions moved.");}
            case "resetcosmetics"->{if(args.length!=2||!args[1].equals("confirm"))throw new IllegalStateException("/realmcore resetcosmetics confirm resets your cosmetic test progress only.");var core=require(player);appearance.equip(core.realm(),core.creator(),new CoreAppearance.Profile(CoreAppearance.DEFAULTS));appearance.resetProgress(player.getUniqueId());shrine.detach(core.realm());shrine.refresh();player.sendMessage("§bCosmetic selections reset; recipes and terrain preserved. Existing discoveries may unlock rewards again.");}
            default->throw new IllegalStateException("Unknown Core control.");
        }
    }

    private void giveSeed(Player player,boolean replacement)throws IOException {
        var realm=islands.ownedRealm(player);
        if(manifestations.containsKey(realm.id()))throw new IllegalStateException("Wait for this Core's crystal animation to finish.");
        if(store.get(realm.id())!=null)throw new IllegalStateException("This Realm already has its permanent Core.");
        if(!replacement&&store.claimed(player.getUniqueId()))throw new IllegalStateException("You already received your one-time Core Shard. Only staff can replace or retrieve it.");
        int slot=player.getInventory().firstEmpty();if(slot<0)throw new IllegalStateException("Free an inventory slot before receiving a Core Seed.");
        String serial=store.issue(player.getUniqueId(),realm.id(),replacement);
        player.getInventory().setItem(slot,coreItem(player.getUniqueId(),realm.id(),serial));player.saveData();
        store.acknowledgeRefund(player.getUniqueId(),serial);
        player.sendMessage("§bYou received your Realm Core Shard. Stand on your island, move away from the configured center and right-click to awaken the shrine.");
    }
    private ItemStack coreItem(UUID owner,UUID realm,String serial){
        ItemStack seed=new ItemStack(Material.AMETHYST_SHARD);ItemMeta meta=seed.getItemMeta();
        meta.displayName(Component.text("Realm Core Shard"));meta.lore(List.of(Component.text("Your one-time sacred foundation."),Component.text("Stand on your island near its configured center."),Component.text("Right-click: awaken the shrine on existing land."),Component.text("No Realmweaver needed. Only staff can retrieve it.")));
        meta.getPersistentDataContainer().set(seedKey,PersistentDataType.STRING,realm.toString());meta.getPersistentDataContainer().set(seedOwnerKey,PersistentDataType.STRING,owner.toString());meta.getPersistentDataContainer().set(seedSerialKey,PersistentDataType.STRING,serial);meta.setMaxStackSize(1);meta.setItemModel(new NamespacedKey("realmcore","core_seed"));seed.setItemMeta(meta);return seed;
    }
    private String seedRealm(ItemStack item){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(seedKey,PersistentDataType.STRING);}
    private void create(Player player,ItemStack seed)throws Exception {
        if(seed==null||!player.hasPermission("worldforge.realmcore.use"))throw new IllegalStateException("Use your Realm Core Shard to awaken the Core.");
        var realm=player.hasPermission("worldforge.realmcore.reclaim")?islands.requirePlacement(player):islands.requireOwned(player);
        if(manifestations.containsKey(realm.id())||manifestations.size()>=8)throw new IllegalStateException("A Core crystal animation is active. Try again shortly.");
        if(store.get(realm.id())!=null)throw new IllegalStateException("This Realm already has its Core.");
        var meta=seed.getItemMeta();String serial=meta.getPersistentDataContainer().get(seedSerialKey,PersistentDataType.STRING);
        String ownerText=meta.getPersistentDataContainer().get(seedOwnerKey,PersistentDataType.STRING);
        if(ownerText==null||serial==null)throw new IllegalStateException("This legacy Core Seed needs staff replacement before placement.");
        UUID owner=UUID.fromString(ownerText);
        if(!owner.equals(realm.owner())||!realm.id().toString().equals(seedRealm(seed)))throw new IllegalStateException("This Core Shard belongs to another Realm or owner.");
        store.validateCoreItem(owner,realm.id(),serial);
        World world=player.getWorld();Location original=player.getLocation();var direction=original.getDirection();
        int sx=Math.abs(direction.getX())>=Math.abs(direction.getZ())?(direction.getX()<0?-1:1):0;
        int sz=sx==0?(direction.getZ()<0?-1:1):0;
        boolean standalone=store.standalone(player.getUniqueId())&&player.hasPermission("worldforge.realmcore.testing");
        boolean fitted=!standalone;
        if(fitted)ensurePlaza(player,realm);
        if(standalone&&!realm.owner().equals(player.getUniqueId()))throw new IllegalStateException("Standalone testing is restricted to your own Realm.");
        int[] selected=standalone?new int[]{original.getBlockX()+sx*24,original.getBlockY()-1,original.getBlockZ()+sz*24}:attachmentAnchor(player,realm);
        int x=selected[0],y=selected[1],z=selected[2];
        if(!standalone&&original.distanceSquared(new Location(world,x+.5,y+1,z+.5))>32*32)throw new IllegalStateException("Move within 32 blocks of your configured island center.");
        var guard=ProtectionBridge.checker(player);
        TerrainPlan plan=new TerrainPlan(fitted?CompactCoreLayout.RADIUS:RealmCoreLayout.RADIUS,ShardSpec.Land.FOREST,0,RealmCoreLayout.MIN_Y,RealmCoreLayout.MAX_Y);plan.blocks.putAll(fitted?CompactCoreLayout.blocks():standalone?RealmCoreLayout.blocks():RealmCoreLayout.attached());
        for(var entry:plan.blocks.entrySet())if(entry.getKey().y()==0)plan.surface.put(TerrainPlan.key(entry.getKey().x(),entry.getKey().z()),0);
        ConnectionBridge.Ground ground=new ConnectionBridge.Ground(){
            Block at(int dx,int dy,int dz){return world.getBlockAt(x+dx,y+dy,z+dz);}
            public boolean air(int dx,int dy,int dz){return at(dx,dy,dz).getType().isAir();}
            public boolean canBuild(int dx,int dy,int dz){return guard.allowed(at(dx,dy,dz).getLocation());}
            public String state(int dx,int dy,int dz){return at(dx,dy,dz).getBlockData().getAsString();}
            public Integer floor(int dx,int dz,int near){for(int dy=near+1;dy>=near-6;dy--){Block block=at(dx,dy,dz);if(block.getType().isSolid()&&!block.isPassable()&&air(dx,dy+1,dz)&&air(dx,dy+2,dz))return dy;}return null;}
        };
        var bridge=standalone?ConnectionBridge.plan(plan,original.getBlockX()-x,original.getBlockY()-1-y,original.getBlockZ()-z,ground):new ConnectionBridge.Result(Map.of(),Map.of(),Set.of(),List.of());
        Map<TerrainPlan.Pos,Material> blocks=new LinkedHashMap<>(plan.blocks);blocks.putAll(bridge.blocks());
        for(int dx=-(fitted?2:5);dx<=(fitted?2:5);dx++)for(int dz=-(fitted?2:5);dz<=(fitted?2:5);dz++)for(int dy=standalone?RealmCoreLayout.MIN_Y:1;dy<=(fitted?CompactCoreLayout.MAX_Y:RealmCoreLayout.MAX_Y);dy++)validateCoreSpace(world,x+dx,y+dy,z+dz,guard);
        for(var cell:bridge.clearance())validateCoreSpace(world,x+cell.x(),y+cell.y(),z+cell.z(),guard);
        for(var entity:world.getEntities())if(entity instanceof LivingEntity){Location at=entity.getLocation();if(bridge.clearance().contains(new TerrainPlan.Pos(at.getBlockX()-x,at.getBlockY()-y,at.getBlockZ()-z)))throw new IllegalStateException("Clear creatures or players from the Core bridge route.");}
        if(standalone&&world.getNearbyEntities(new Location(world,x+.5,y+1,z+.5),6,9,6).stream().anyMatch(entity->entity instanceof LivingEntity))throw new IllegalStateException("Clear creatures or players from the Core site.");
        if(!standalone)validateOccupants(world,x,y,z,blocks);
        if(!standalone)validateCompactFoundation(world,x,y,z,guard);
        var core=new RealmCoreStore.Core(realm.id(),world.getUID(),owner,x,y,z,fitted?5:standalone?1:2);
        Map<TerrainPlan.Pos,String> padOriginals=fitted?padSnapshot(world,x,y,z,blocks,guard):Map.of();
        CoreManifestation visual=new CoreManifestation(world,core,blocks,false,appearance.profile(core.realm()));
        visual.flightFrom(HeldShardDisplay.position(player,false));
        try{if(fitted)store.padOriginals(core,CorePadLayout.encode(padOriginals));store.coreState(owner,serial,"PLACING");}catch(IOException failure){visual.close();throw failure;}
        boolean[] refunded={false};
        Runnable abort=()->{visual.close();manifestations.remove(realm.id());if(store.get(realm.id())!=null||refunded[0])return;refunded[0]=true;try{if(fitted)store.padOriginals(core,List.of());store.refundPlacement(owner,serial,player.getUniqueId());returnRefunds(player);}catch(IOException failure){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Core claim rollback needs review",failure);}};
        manifestations.put(realm.id(),new Manifesting(core,Set.copyOf(blocks.keySet()),visual,abort));
        try{
            ItemStack held=player.getInventory().getItemInMainHand();
            if(!seed.equals(held))throw new IllegalStateException("Your Core item changed before launch.");
            player.getInventory().setItemInMainHand(null);player.saveData();
            visual.start(plugin,()->{
              List<Block> placed=new ArrayList<>();
              try{
            if(!player.isOnline()||player.isDead()||!player.getWorld().equals(world)||!(player.hasPermission("worldforge.realmcore.reclaim")?islands.requirePlacement(player):islands.requireOwned(player)).id().equals(realm.id()))throw new IllegalStateException("Core placer left or their island changed.");
            var finalGuard=ProtectionBridge.clearanceChecker(player);
            for(int dx=-(fitted?2:5);dx<=(fitted?2:5);dx++)for(int dz=-(fitted?2:5);dz<=(fitted?2:5);dz++)for(int dy=standalone?RealmCoreLayout.MIN_Y:1;dy<=(fitted?CompactCoreLayout.MAX_Y:RealmCoreLayout.MAX_Y);dy++){
                Location at=new Location(world,x+dx,y+dy,z+dz);if(!world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))throw new IllegalStateException("Core site unloaded during the animation.");if(!world.getWorldBorder().isInside(at)||!at.getBlock().getType().isAir()||store.protectedAt(world.getUID(),at.getBlockX(),at.getBlockY(),at.getBlockZ())||shards.reserved(at)||!finalGuard.allowed(at))throw new IllegalStateException("Core site changed during the animation.");
            }
            if(!standalone)validateCompactFoundation(world,x,y,z,finalGuard);
            if(fitted&&!padSnapshot(world,x,y,z,blocks,finalGuard).equals(padOriginals))throw new IllegalStateException("The reserved Core pad changed during formation.");
            for(var cell:bridge.clearance()){
                Location at=new Location(world,x+cell.x(),y+cell.y(),z+cell.z());if(!world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))throw new IllegalStateException("Core bridge unloaded during the animation.");if(!world.getWorldBorder().isInside(at)||!at.getBlock().getType().isAir()||shards.reserved(at)||!finalGuard.allowed(at))throw new IllegalStateException("Core bridge route changed during the animation.");
            }
            if(!standalone)validateOccupants(world,x,y,z,blocks);
            for(var entity:world.getEntities())if(entity instanceof LivingEntity){Location at=entity.getLocation();if(standalone&&core.contains(world.getUID(),at.getBlockX(),at.getBlockY(),at.getBlockZ())||bridge.clearance().contains(new TerrainPlan.Pos(at.getBlockX()-x,at.getBlockY()-y,at.getBlockZ()-z)))throw new IllegalStateException("An occupant entered the Core projection.");}
            for(var entry:bridge.anchors().entrySet()){var p=entry.getKey();if(!world.getBlockAt(x+p.x(),y+p.y(),z+p.z()).getBlockData().getAsString().equals(entry.getValue()))throw new IllegalStateException("Bridge anchor changed.");}
            for(var entry:blocks.entrySet()){var p=entry.getKey();Block block=world.getBlockAt(x+p.x(),y+p.y(),z+p.z());if(!fitted&&!block.getType().isAir())throw new IllegalStateException("Core site changed.");block.setType(entry.getValue(),false);placed.add(block);}
            List<String> recorded=blocks.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue().name()).toList();
            store.construction(core,recorded,original.getX(),original.getY(),original.getZ());
              }catch(Exception failure){try{for(Block block:placed){var p=new TerrainPlan.Pos(block.getX()-x,block.getY()-y,block.getZ()-z);if(fitted)block.setBlockData(Bukkit.createBlockData(padOriginals.get(p)),false);else block.setType(Material.AIR,false);}}catch(Exception restorationFailure){visual.close();manifestations.remove(realm.id());plugin.getLogger().log(java.util.logging.Level.SEVERE,"Core pad restoration requires staff review: "+realm.id(),restorationFailure);player.sendMessage("§cCore pad restoration requires staff review; its original blocks remain saved.");return;}abort.run();if(player.isOnline())player.sendMessage("§eCore formation cancelled: "+failure.getMessage()+" Your item was preserved.");return;}
        // PLACING is already non-reusable. A failed final save cannot permit another Core.
        try{store.coreState(owner,serial,"PLACED");}catch(IOException failure){plugin.getLogger().warning("Core placed; claim state needs staff review: "+owner);}
        if(core.design()==5){Player realmOwner=Bukkit.getPlayer(core.creator());if(realmOwner!=null)try{islands.repairUnsafeHome(realmOwner,core);}catch(Exception e){plugin.getLogger().warning("Core placed; island home needs review: "+e.getMessage());}}
        player.saveData();
        player.sendMessage("§bYour Realm Core is established as the center of your Realm. Your original island home is preserved.");
        manifestations.remove(realm.id());
        shrine.refresh();
            },failure->{abort.run();if(player.isOnline())player.sendMessage("§eCore formation stopped; your item was preserved.");});
        }catch(RuntimeException failure){abort.run();throw failure;}
        player.sendMessage("§dYour Core crystal has launched. You can switch hotbar slots while it forms.");
    }
    private void validateCompactFoundation(World world,int x,int y,int z,ProtectionBridge.Guard guard){for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){Block floor=world.getBlockAt(x+dx,y,z+dz);if(!world.isChunkLoaded(floor.getX()>>4,floor.getZ()>>4)||!floor.getType().isSolid()||floor.isPassable()||!guard.allowed(floor.getLocation())||shards.reserved(floor.getLocation()))throw new IllegalStateException("The compact Core requires an intact 5x5 plaza floor.");}}
    private void ensurePlaza(Player actor,IslandBridge.Realm realm)throws Exception{
        if(store.get(realm.id())!=null)throw new IllegalStateException("Reclaim the existing Core before updating this island plaza.");
        if(store.plazaReady(realm.id()))return;
        var cells=CorePlazaLayout.cells(islands.templateKeys(realm));if(cells.isEmpty())return;World world=Bukkit.getWorld(realm.world());if(world==null)throw new IllegalStateException("The island is not loaded.");
        int[] marker=attachmentAnchor(world,realm);int x=marker[0],y=marker[1],z=marker[2];var guard=ProtectionBridge.checker(actor);boolean finished=true;
        for(var cell:cells){var p=cell.pos();if(!world.isChunkLoaded((x+p.x())>>4,(z+p.z())>>4))throw new IllegalStateException("Load the island plaza before activation.");if(!world.getBlockAt(x+p.x(),y-3+p.y(),z+p.z()).getBlockData().getAsString().equals(cell.after())){finished=false;break;}}if(finished){store.plazaReady(realm.id(),true);return;}
        Map<TerrainPlan.Pos,String> originals=new LinkedHashMap<>();for(var cell:cells){var p=cell.pos();Location at=new Location(world,x+p.x(),y+p.y(),z+p.z());if(!world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)||!world.getWorldBorder().isInside(at)||!guard.allowed(at)||shards.reserved(at)||!at.getBlock().getBlockData().getAsString().equals(cell.before()))throw new IllegalStateException("The plaza differs from the original template or is protected. Staff must inspect; player builds are preserved.");originals.put(p,cell.before());}
        Location safe=null;for(int dx=-12;dx<=12&&safe==null;dx++)for(int dz=-12;dz<=12&&safe==null;dz++)if(Math.max(Math.abs(dx),Math.abs(dz))>10){Location at=new Location(world,x+dx+.5,y+4,z+dz+.5);if(world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)&&world.getWorldBorder().isInside(at)&&at.getBlock().getType().isAir()&&at.clone().add(0,1,0).getBlock().getType().isAir()&&at.clone().add(0,-1,0).getBlock().getType().isSolid())safe=at;}
        if(safe==null)throw new IllegalStateException("No safe outer path exists for the plaza repair.");
        for(var entity:world.getNearbyEntities(new Location(world,x+.5,y+4,z+.5),11,8,11))if(entity instanceof LivingEntity)if(!entity.teleport(safe))throw new IllegalStateException("Plaza evacuation was cancelled.");
        store.plazaBackup(realm.id(),world.getUID(),x,y,z,CorePadLayout.encode(originals));List<CorePlazaLayout.Cell> changed=new ArrayList<>();try{for(var cell:cells){var p=cell.pos();world.getBlockAt(x+p.x(),y+p.y(),z+p.z()).setBlockData(Bukkit.createBlockData(cell.after()),false);changed.add(cell);}store.plazaReady(realm.id(),true);}catch(Exception failure){for(var cell:changed){var p=cell.pos();world.getBlockAt(x+p.x(),y+p.y(),z+p.z()).setBlockData(Bukkit.createBlockData(cell.before()),false);}store.plazaRollback(realm.id());throw failure;}
    }
    private void createAutomatic(Player owner,IslandBridge.Realm realm)throws Exception {
        if(store.get(realm.id())!=null||manifestations.containsKey(realm.id()))throw new IllegalStateException("The Core is already present or forming.");
        World world=Bukkit.getWorld(realm.world());if(world==null)throw new IllegalStateException("The island world has not loaded.");
        if(!CoreTemplateAnchor.select(plugin.getConfig(),islands.templateKeys(realm)).marked())throw new IllegalStateException("The starter template requires a configured Core pad marker.");
        ensurePlaza(owner,realm);int[] base=attachmentAnchor(world,realm);int x=base[0],y=base[1],z=base[2];var guard=ProtectionBridge.checker(owner);
        var cells=CompactCoreLayout.blocks();var core=new RealmCoreStore.Core(realm.id(),world.getUID(),realm.owner(),x,y,z,5);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=1;dy<=CompactCoreLayout.MAX_Y;dy++)validateCoreSpace(world,x+dx,y+dy,z+dz,guard);
        validateCompactFoundation(world,x,base[1],z,guard);var originals=padSnapshot(world,x,y,z,cells,guard);
        // Save an outer, safe return point; never fill beneath a living occupant.
        Location safe=null;for(int dx=-12;dx<=12&&safe==null;dx++)for(int dz=-12;dz<=12&&safe==null;dz++)if(Math.max(Math.abs(dx),Math.abs(dz))>2)for(int dy=-4;dy<=6;dy++){
            Location at=new Location(world,x+dx+.5,y+dy,z+dz+.5);if(!world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)||!world.getWorldBorder().isInside(at))continue;
            if(at.getBlock().getType().isAir()&&at.clone().add(0,1,0).getBlock().getType().isAir()&&at.clone().add(0,-1,0).getBlock().getType().isSolid()){safe=at;break;}}
        if(safe==null)throw new IllegalStateException("No safe outer walkway exists for the automatic Core.");
        for(var entity:world.getNearbyEntities(new Location(world,x+.5,y+3,z+.5),9,12,9))if(entity instanceof LivingEntity)
            for(var p:cells.keySet())if(entity.getBoundingBox().overlaps(new org.bukkit.util.BoundingBox(x+p.x(),y+p.y(),z+p.z(),x+p.x()+1,y+p.y()+1,z+p.z()+1))){if(!entity.teleport(safe))throw new IllegalStateException("Core arrival evacuation was cancelled.");break;}
        validateOccupants(world,x,y,z,cells);store.autoPrepare(core,CorePadLayout.encode(originals));List<Block> placed=new ArrayList<>();
        try{for(var e:cells.entrySet()){var p=e.getKey();Block block=world.getBlockAt(x+p.x(),y+p.y(),z+p.z());block.setType(e.getValue(),false);placed.add(block);}
            store.autoCommit(core,cells.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue().name()).toList(),safe.getX(),safe.getY(),safe.getZ());
        }catch(Exception failure){try{for(Block block:placed){var p=new TerrainPlan.Pos(block.getX()-x,block.getY()-y,block.getZ()-z);block.setBlockData(Bukkit.createBlockData(originals.get(p)),false);}store.autoAbort(core);}catch(Exception rollback){failure.addSuppressed(rollback);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Interrupted automatic Core requires staff review: "+realm.id(),failure);}throw failure;}
        try{islands.repairUnsafeHome(owner,core);}catch(Exception repair){plugin.getLogger().warning("Automatic Core placed; saved home needs review: "+realm.id()+": "+repair.getMessage());}
        shrine.refresh();owner.sendMessage("§dYour starter island is ready with its Awakened Realm Core. Right-click its crystal or pedestal to begin.");
    }
    private void validateCoreSpace(World world,int x,int y,int z,ProtectionBridge.Guard guard){
        Location at=new Location(world,x,y,z);
        if(!world.isChunkLoaded(x>>4,z>>4))throw new IllegalStateException("The Core site must be in loaded chunks. Approach your island center first.");
        if(y<=world.getMinHeight()||y>=world.getMaxHeight()||!world.getWorldBorder().isInside(at))throw new IllegalStateException("Core or bridge would cross the Realm boundary.");
        if(shards.reserved(at)||!guard.allowed(at))throw new IllegalStateException("Generation or protection blocks this Core route.");
        if(!at.getBlock().getType().isAir())throw new IllegalStateException("The configured Core site is obstructed. Clear the space above the island or ask staff to mark another center.");
    }
    private Map<TerrainPlan.Pos,String> padSnapshot(World world,int x,int y,int z,Map<TerrainPlan.Pos,Material> cells,ProtectionBridge.Guard guard){
        Map<TerrainPlan.Pos,String> states=new LinkedHashMap<>();
        for(var p:cells.keySet()){
            int bx=x+p.x(),by=y+p.y(),bz=z+p.z();Location at=new Location(world,bx,by,bz);
            if(!world.isChunkLoaded(bx>>4,bz>>4)||by<=world.getMinHeight()||by>=world.getMaxHeight()||!world.getWorldBorder().isInside(at)||!guard.allowed(at)||shards.reserved(at)||store.protectedAt(world.getUID(),bx,by,bz))throw new IllegalStateException("The reserved Core pad is unavailable or protected.");
            Block block=world.getBlockAt(bx,by,bz);if(!CompactCoreLayout.replaceable(block.getType()))throw new IllegalStateException("The reserved pad contains unexpected blocks. Staff must inspect it; no terrain was changed.");
            states.put(p,block.getBlockData().getAsString());
        }
        return states;
    }
    private void validateOccupants(World world,int x,int y,int z,Map<TerrainPlan.Pos,Material> blocks){
        for(var entity:world.getNearbyEntities(new Location(world,x+.5,y+5,z+.5),9,12,9))if(entity instanceof LivingEntity)
            for(var p:blocks.keySet())if(entity.getBoundingBox().overlaps(new org.bukkit.util.BoundingBox(x+p.x(),y+p.y(),z+p.z(),x+p.x()+1,y+p.y()+1,z+p.z()+1)))throw new IllegalStateException("Move players or creatures out of the shrine's pillars and basin. You may stand in its clear central walkway.");
    }
    private static ItemStack button(Material material,String title,String... lore){return CoreGui.button(material,title,lore);}
    @EventHandler public void joined(org.bukkit.event.player.PlayerJoinEvent event){Bukkit.getScheduler().runTask(plugin,()->returnRefunds(event.getPlayer()));}
    @EventHandler public void respawned(org.bukkit.event.player.PlayerRespawnEvent event){Bukkit.getScheduler().runTask(plugin,()->returnRefunds(event.getPlayer()));}
    private void returnRefunds(Player recipient){
        if(!recipient.isOnline()||recipient.isDead())return;
        for(var refund:store.refunds(recipient.getUniqueId()))try{
            if(recipient.getInventory().firstEmpty()<0){recipient.sendMessage("§eYour cancelled Core is saved. Free an inventory slot and use /realmcore refund.");continue;}
            String renewed=store.issue(refund.owner(),refund.realm(),true);
            recipient.getInventory().addItem(coreItem(refund.owner(),refund.realm(),renewed));recipient.saveData();store.acknowledgeRefund(refund.owner(),renewed);
            recipient.sendMessage("§bYour cancelled Core Shard was returned.");
        }catch(Exception failure){plugin.getLogger().log(java.util.logging.Level.WARNING,"Core refund retained for "+recipient.getUniqueId(),failure);}
    }
    private void open(Player player,RealmCoreStore.Core core,Kind kind,CoreRecipe.Influence influence,CoreRecipe exact,int page){
        open(player,core,kind,influence,exact==null?null:exact.secondary(),exact,page);
    }
    private void open(Player player,RealmCoreStore.Core core,Kind kind,CoreRecipe.Influence influence,CoreRecipe.Influence secondary,CoreRecipe exact,int page){
        Menu menu=new Menu(player,core,kind,influence,secondary,exact,page);menu.inventory=Bukkit.createInventory(menu,54,CoreGui.title(switch(kind){case HOME->"Awakened Realm Core";case FORGE->"Core • Woodland Forging";case JOURNAL->"Core • Discovery Journal";}));CoreGui.fill(menu.inventory);
        if(kind==Kind.HOME){
            menu.inventory.setItem(20,button(Material.ANVIL,"Forge / Experiment","Combine Woodland with one or two different Influences."));
            menu.inventory.setItem(24,button(Material.WRITABLE_BOOK,"Personal Discoveries","Recipes learned and exact patterns saved."));
            menu.inventory.setItem(40,button(Material.AMETHYST_SHARD,"Customize Realm Core","Crystals, colors, decorations and discovery rewards."));
        }else if(kind==Kind.FORGE){
            menu.inventory.setItem(10,button(Material.OAK_SAPLING,"Woodland Foundation","8 oak saplings + 16 dirt + 8 stone"));
            for(var option:CoreRecipe.Influence.values()){
                boolean known=store.known(player.getUniqueId(),option);
                menu.inventory.setItem(19+option.ordinal(),button(option.material,(option==influence?"Selected • ":"")+option.material.name().toLowerCase(Locale.ROOT).replace('_',' '),option.amount+" required",known?option.title:option.clue));
                menu.inventory.setItem(28+option.ordinal(),button(option.material,(option==secondary?"Secondary selected • ":"Secondary • ")+option.material.name().toLowerCase(Locale.ROOT).replace('_',' '),option.amount+" additional required",option==influence?"Choose a different Influence for this slot.":option.clue));
            }
            CoreRecipe selected=new CoreRecipe(influence,secondary,0);
            boolean known=store.known(player.getUniqueId(),selected);
            menu.inventory.setItem(34,button(Material.BARRIER,"Clear secondary Influence","Forge with just the primary Influence."));
            menu.inventory.setItem(40,button(Material.PAPER,known?selected.title():"Undiscovered Woodland Recipe",influence.clue,secondary==null?"Secondary Influence: none":secondary.clue,exact==null?"New arrangement will be discovered.":"Exact saved pattern: "+Long.toUnsignedString(exact.seed(),16)));
            menu.inventory.setItem(48,button(Material.ANVIL,exact==null?"Forge new pattern":"Reproduce saved pattern","Consumes the displayed Foundation and Influence materials.","Custom or named items are never used as ingredients."));
            menu.inventory.setItem(50,button(Material.WRITABLE_BOOK,"Discovery Journal"));
        }else{
            List<CoreRecipe> patterns=store.patterns(player.getUniqueId());
            for(int index=0;index<28&&page*28+index<patterns.size();index++){
                CoreRecipe recipe=patterns.get(page*28+index);
                menu.inventory.setItem(10+index/7*9+index%7,button(recipe.influence().material,recipe.title(),recipe.secondary()==null?"One Influence":recipe.influence().name()+" + "+recipe.secondary().name(),"Saved pattern: "+Long.toUnsignedString(recipe.seed(),16),"Select to reproduce this exact layout."));
            }
            menu.inventory.setItem(4,button(Material.BOOK,"Your personal discoveries",patterns.size()+" saved patterns",patterns.isEmpty()?"Your first lesson: moss binds living roots.":"Ingredients teach reusable relationships."));
            menu.inventory.setItem(45,button(Material.ARROW,"Previous page"));menu.inventory.setItem(53,button(Material.ARROW,"Next page"));
        }
        menu.inventory.setItem(49,button(Material.BARRIER,kind==Kind.HOME?"Close":"Back to Core"));player.openInventory(menu.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!menu.player.equals(player.getUniqueId()))return;
        int slot=event.getRawSlot();if(slot<0||slot>=54||event.getCurrentItem()==null||event.getCurrentItem().getType().isAir())return;
        try{
            var core=require(player);if(!core.equals(menu.core))throw new IllegalStateException("The Core changed; reopen its menu.");
            if(slot==49){if(menu.kind==Kind.HOME)player.closeInventory();else open(player,core,Kind.HOME,menu.influence,null,0);return;}
            if(menu.kind==Kind.HOME){if(slot==40){customization.open(player,core);return;}if(slot==20)open(player,core,Kind.FORGE,CoreRecipe.Influence.ROOTS,null,0);if(slot==24)open(player,core,Kind.JOURNAL,menu.influence,null,0);return;}
            if(menu.kind==Kind.FORGE){
                if(slot>=19&&slot<=23){var primary=CoreRecipe.Influence.values()[slot-19];if(primary==menu.secondary)throw new IllegalStateException("Choose two different Influences, or clear the secondary slot first.");open(player,core,Kind.FORGE,primary,menu.secondary,null,0);return;}
                if(slot>=28&&slot<=32){var secondary=CoreRecipe.Influence.values()[slot-28];if(secondary==menu.influence)throw new IllegalStateException("Choose two different Influences.");open(player,core,Kind.FORGE,menu.influence,secondary,null,0);return;}
                if(slot==34){open(player,core,Kind.FORGE,menu.influence,null,0);return;}
                if(slot==50){open(player,core,Kind.JOURNAL,menu.influence,null,0);return;}
                if(slot==48){
                    CoreRecipe recipe=menu.exact==null?new CoreRecipe(menu.influence,menu.secondary,UUID.randomUUID().getLeastSignificantBits()):menu.exact;
                    beginForge(player,core,recipe);
                }
                return;
            }
            List<CoreRecipe> patterns=store.patterns(player.getUniqueId());
            if(slot==45){open(player,core,Kind.JOURNAL,menu.influence,null,Math.max(0,menu.page-1));return;}
            if(slot==53){open(player,core,Kind.JOURNAL,menu.influence,null,Math.min(Math.max(0,(patterns.size()-1)/28),menu.page+1));return;}
            if(slot>=10&&slot<=43&&slot%9>=1&&slot%9<=7){int index=menu.page*28+(slot/9-1)*7+slot%9-1;if(index<patterns.size()){CoreRecipe recipe=patterns.get(index);open(player,core,Kind.FORGE,recipe.influence(),recipe,0);}}
        }catch(Exception failure){player.sendMessage("§c"+failure.getMessage());plugin.getLogger().fine("Core interaction failed: "+failure.getMessage());}
    }
    private void beginForge(Player player,RealmCoreStore.Core core,CoreRecipe recipe){
        if(manifestations.containsKey(core.realm()))throw new IllegalStateException("Wait for the Core crystal animation to finish.");
        if(rituals.containsKey(core.realm()))throw new IllegalStateException("Your Core is already forging. Stay nearby until the ritual finishes.");
        if(rituals.size()>=8)throw new IllegalStateException("The Realm Cores are busy. Try forging again shortly.");
        if(store.pending(player.getUniqueId()))throw new IllegalStateException("An interrupted forge needs staff recovery before forging again.");
        ItemStack reward=item(player,recipe);
        CoreInventoryExchange.plan(player.getInventory().getStorageContents(),recipe.costs(),reward);
        BukkitRunnable ritual=new BukkitRunnable(){
            int tick;
            public void run(){
                try{
                    if(!player.isOnline()||player.isDead())throw new IllegalStateException("Forging stopped because you left or died.");
                    if(!core.equals(require(player)))throw new IllegalStateException("Forging stopped because the Core changed.");
                    if(tick==0){player.closeInventory();player.sendMessage("§dYour Core is gathering energy. Stay within 12 blocks; materials are exchanged when the ritual finishes.");}
                    World world=player.getWorld();
                    if(tick==CoreForgeSequence.DURATION){
                        boolean discovered=!store.known(player.getUniqueId(),recipe);
                        store.forge(player,recipe,reward);
                        appearance.progress(core.creator(),store.patterns(core.creator()));
                        player.sendMessage("§b"+(discovered?"Discovered: ":"Forged: ")+recipe.title()+". Its pattern is saved in your journal.");
                    }
                    Color color=Color.fromRGB(CoreForgeSequence.color(recipe.secondary()!=null&&tick>=20?recipe.secondary():recipe.influence()));
                    for(var point:CoreForgeSequence.frame(tick,core.design()))RealmShards.dust(new Location(world,core.x()+point.x(),core.y()+point.y(),core.z()+point.z()),color,.85f);
                    if(tick%20==0)world.playSound(new Location(world,core.x()+.5,core.y()+1,core.z()+(core.design()==5?.5:-3.5)),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.6f,.7f+tick/60f);
                    if(tick==CoreForgeSequence.DURATION){finish();return;}
                    tick+=2;
                }catch(Exception failure){if(player.isOnline())player.sendMessage("§e"+failure.getMessage());finish();}
            }
            void finish(){rituals.remove(core.realm(),this);ritualPlayers.remove(core.realm());cancel();}
        };
        rituals.put(core.realm(),ritual);
        ritualPlayers.put(core.realm(),player.getUniqueId());
        try{ritual.runTaskTimer(plugin,1,2);}catch(RuntimeException failure){rituals.remove(core.realm(),ritual);ritualPlayers.remove(core.realm());throw failure;}
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void interact(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||(event.getAction()!=Action.RIGHT_CLICK_BLOCK&&event.getAction()!=Action.RIGHT_CLICK_AIR))return;
        if(event.getAction()==Action.RIGHT_CLICK_BLOCK&&event.isCancelled())return;
        if(seedRealm(event.getItem())!=null){event.setCancelled(true);try{create(event.getPlayer(),event.getItem());}catch(Exception failure){event.getPlayer().sendMessage("§c"+failure.getMessage());}return;}
        Block block=event.getClickedBlock();if(block==null)return;
        for(var core:store.all())if(core.world().equals(block.getWorld().getUID())&&(core.design()==5?Math.abs(block.getX()-core.x())<=2&&Math.abs(block.getZ()-core.z())<=2&&block.getY()>core.y()&&block.getY()<=core.y()+2:Math.abs(block.getX()-core.x())<=1&&Math.abs(block.getZ()-(core.z()-4))<=1&&block.getY()>=core.y()&&block.getY()<=core.y()+1)){
            event.setCancelled(true);try{open(event.getPlayer(),require(event.getPlayer()),Kind.HOME,CoreRecipe.Influence.ROOTS,null,0);}catch(Exception failure){event.getPlayer().sendMessage("§c"+failure.getMessage());}return;
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void crystalInteract(org.bukkit.event.player.PlayerInteractEntityEvent event){
        if(event.getHand()!=EquipmentSlot.HAND)return;
        UUID realm=shrine.interactionRealm(event.getRightClicked());if(realm==null)return;event.setCancelled(true);
        if(shards.useCoreTool(event.getPlayer(),realm))return;
        try{var core=require(event.getPlayer());if(!core.realm().equals(realm)||manifestations.containsKey(realm))throw new IllegalStateException("This crystal belongs to another Core or is still forming.");open(event.getPlayer(),core,Kind.HOME,CoreRecipe.Influence.ROOTS,null,0);}catch(Exception failure){event.getPlayer().sendMessage("§c"+failure.getMessage());}
    }
    private boolean protectedLocation(Location at){
        if(store.protectedAt(at.getWorld().getUID(),at.getBlockX(),at.getBlockY(),at.getBlockZ()))return true;
        for(var operation:manifestations.values()){
            var core=operation.core();if(!core.world().equals(at.getWorld().getUID()))continue;
            if(core.contains(core.world(),at.getBlockX(),at.getBlockY(),at.getBlockZ())||operation.cells().contains(new TerrainPlan.Pos(at.getBlockX()-core.x(),at.getBlockY()-core.y(),at.getBlockZ()-core.z())))return true;
        }return false;
    }
    boolean protectedAt(Block block){return protectedLocation(block.getLocation());}
    boolean reclaim(Player staff)throws Exception {
        var hit=staff.rayTraceBlocks(256,FluidCollisionMode.NEVER);Block target=hit==null?null:hit.getHitBlock();if(target==null)return false;
        RealmCoreStore.Core core=store.all().stream().filter(c->c.contains(target.getWorld().getUID(),target.getX(),target.getY(),target.getZ())).findFirst().orElse(null);
        return reclaim(staff,core);
    }
    boolean reclaim(Player staff,UUID realm)throws Exception{return reclaim(staff,store.get(realm));}
    private boolean reclaim(Player staff,RealmCoreStore.Core core)throws Exception{
        if(core==null)return false;
        if(!core.world().equals(staff.getWorld().getUID()))throw new IllegalStateException("The targeted Core is in another world.");
        if(manifestations.containsKey(core.realm())||manifestations.size()>=8)throw new IllegalStateException("A Core crystal animation is active. Try again shortly.");
        if(!staff.hasPermission("worldforge.realmcore.reclaim"))throw new IllegalStateException("Staff Core reclamation permission is required.");
        if(rituals.containsKey(core.realm()))throw new IllegalStateException("Wait for this Core's forging ritual to finish.");
        World world=staff.getWorld();var realm=islands.describe(world);if(!realm.id().equals(core.realm()))throw new IllegalStateException("Core Realm identity changed.");
        int slot=-1;for(int i=0;i<36;i++)if(i!=staff.getInventory().getHeldItemSlot()&&(staff.getInventory().getItem(i)==null||staff.getInventory().getItem(i).getType().isAir())){slot=i;break;}if(slot<0)throw new IllegalStateException("Free a storage slot before retrieving a Core.");
        Map<TerrainPlan.Pos,Material> cells=new LinkedHashMap<>();
        for(String encoded:store.cells(core)){String[] parts=encoded.split("\\|"),p=parts[0].split(",");cells.put(new TerrainPlan.Pos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])),Material.valueOf(parts[1]));}
        if(cells.isEmpty())cells.putAll(RealmCoreLayout.forDesign(core.design()));
        Map<TerrainPlan.Pos,String> restored=core.design()>=3?CorePadLayout.decode(store.padOriginals(core)):Map.of();
        if(core.design()>=3&&!restored.keySet().equals(cells.keySet()))throw new IllegalStateException("Core pad restoration data is incomplete; staff review is required.");
        var guard=ProtectionBridge.clearanceChecker(staff);
        List<Block> remove=new ArrayList<>();
        for(var entry:cells.entrySet()){var p=entry.getKey();Block block=world.getBlockAt(core.x()+p.x(),core.y()+p.y(),core.z()+p.z());
            if(shards.reserved(block.getLocation()))throw new IllegalStateException("Wait for terrain activity around this Core.");
            if(core.contains(world.getUID(),block.getX(),block.getY(),block.getZ())&&block.getType()!=entry.getValue())throw new IllegalStateException("The Core foundation changed; staff must inspect it before retrieval.");
            if(block.getType()!=entry.getValue())continue;
            if(!guard.allowed(block.getLocation()))throw new IllegalStateException("Region protection denies Core retrieval.");remove.add(block);
        }
        List<Location> candidates=new ArrayList<>();double[] saved=store.returnPoint(core);if(saved!=null)candidates.add(new Location(world,saved[0],saved[1],saved[2]));candidates.addAll(islands.returnCandidates(world));
        for(int dx=-10;dx<=10;dx++)for(int dz=-10;dz<=10;dz++)for(int dy=-1;dy<=6;dy++)candidates.add(new Location(world,core.x()+dx+.5,core.y()+dy,core.z()+dz+.5));
        Location safe=candidates.stream().filter(l->l.getWorld()!=null&&l.getWorld().equals(world)&&l.getWorld().getWorldBorder().isInside(l)&&l.getBlock().getType().isAir()&&l.clone().add(0,1,0).getBlock().getType().isAir()&&l.clone().add(0,-1,0).getBlock().getType().isSolid()&&!remove.contains(l.clone().add(0,-1,0).getBlock())).findFirst().orElseThrow(()->new IllegalStateException("No safe return location is available for Core occupants."));
        islands.repairCoreHome(staff,core,safe,cells.keySet());
        for(var entity:world.getEntities())if(entity instanceof LivingEntity){Location at=entity.getLocation();var below=new TerrainPlan.Pos(at.getBlockX()-core.x(),at.getBlockY()-1-core.y(),at.getBlockZ()-core.z());if(core.contains(world.getUID(),at.getBlockX(),at.getBlockY(),at.getBlockZ())||cells.containsKey(below))if(!entity.teleport(safe))throw new IllegalStateException("An occupant could not be evacuated.");}
        Map<TerrainPlan.Pos,Material> projection=new LinkedHashMap<>();for(Block block:remove)projection.put(new TerrainPlan.Pos(block.getX()-core.x(),block.getY()-core.y(),block.getZ()-core.z()),block.getType());
        CoreManifestation visual=new CoreManifestation(world,core,projection,true,appearance.profile(core.realm()));
        visual.returnTo(staff);
        String serial;try{serial=store.prepareRetrieval(core,realm.owner());}catch(Exception failure){visual.close();throw failure;}
        shrine.detach(core.realm());
        try{for(Block block:remove){if(core.design()>=3){var p=new TerrainPlan.Pos(block.getX()-core.x(),block.getY()-core.y(),block.getZ()-core.z());block.setBlockData(Bukkit.createBlockData(restored.get(p)),false);}else block.setType(Material.AIR,false);}}
        catch(Exception failure){visual.close();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Interrupted Core retrieval requires review: "+core.realm(),failure);throw new IOException("Core retrieval interrupted; no second item was issued. Staff review is required.",failure);}
        Runnable finish=()->{
            if(!manifestations.containsKey(core.realm()))return;
            try{
                store.removeCore(core);store.coreState(realm.owner(),serial,"RETRIEVED");
                ItemStack returned=coreItem(realm.owner(),core.realm(),serial);
                Map<Integer,ItemStack> leftovers=staff.isOnline()?staff.getInventory().addItem(returned):Map.of(0,returned);
                for(ItemStack item:leftovers.values()){var dropped=world.dropItem(safe,item);dropped.setOwner(staff.getUniqueId());}
                if(staff.isOnline()){staff.saveData();staff.sendMessage("§bRealm Core retrieved into staff custody. Its one-time NPC claim remains used.");}plugin.getLogger().info(staff.getName()+" retrieved Core "+core.realm());
            }catch(Exception failure){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Core retrieval finalization needs staff review: "+core.realm(),failure);}
            finally{manifestations.remove(core.realm());visual.close();}
        };
        manifestations.put(core.realm(),new Manifesting(core,Set.copyOf(cells.keySet()),visual,finish));
        try{visual.start(plugin,finish,failure->finish.run());}catch(RuntimeException failure){finish.run();}
        staff.sendMessage("§dThe Core is shrinking into a crystal for staff custody.");return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void breakBlock(BlockBreakEvent event){if(protectedAt(event.getBlock())){event.setCancelled(true);event.setDropItems(false);event.setExpToDrop(0);event.getPlayer().sendMessage("§eThe Realm Core is unbreakable. Only the staff Reclaimer can retrieve it.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void damage(BlockDamageEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void place(BlockPlaceEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void flow(BlockFromToEvent event){if(protectedAt(event.getToBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void explosion(EntityExplodeEvent event){event.blockList().removeIf(this::protectedAt);}
    @EventHandler(ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent event){event.blockList().removeIf(this::protectedAt);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent event){if(event.getBlocks().stream().anyMatch(block->protectedAt(block)||protectedAt(block.getRelative(event.getDirection()))||protectedAt(block.getRelative(event.getDirection().getOppositeFace()))))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void retract(BlockPistonRetractEvent event){if(event.getBlocks().stream().anyMatch(block->protectedAt(block)||protectedAt(block.getRelative(event.getDirection()))||protectedAt(block.getRelative(event.getDirection().getOppositeFace()))))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void entityChange(EntityChangeBlockEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void bucket(org.bukkit.event.player.PlayerBucketEmptyEvent event){if(protectedAt(event.getBlock().getRelative(event.getBlockFace())))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void fill(org.bukkit.event.player.PlayerBucketFillEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void spread(BlockSpreadEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void form(BlockFormEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void fade(BlockFadeEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void blockGrow(BlockGrowEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void ignite(BlockIgniteEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void grow(org.bukkit.event.world.StructureGrowEvent event){if(event.getBlocks().stream().anyMatch(state->protectedAt(state.getBlock())))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void burn(BlockBurnEvent event){if(protectedAt(event.getBlock()))event.setCancelled(true);}
}
