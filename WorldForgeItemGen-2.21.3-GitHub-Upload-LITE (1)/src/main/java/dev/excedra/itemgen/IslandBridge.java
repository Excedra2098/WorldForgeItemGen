package dev.excedra.itemgen;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Read-only adapter. Never changes Worldforge records or creates worlds. */
final class IslandBridge {
    record IslandRecord(Realm realm,String phase){}
    java.util.List<IslandRecord> records(){
        Plugin plugin=Bukkit.getPluginManager().getPlugin("Worldforge");if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Worldforge is unavailable.");
        try{Object service=plugin.getClass().getMethod("islandService").invoke(plugin);var rows=(java.util.List<?>)service.getClass().getMethod("records").invoke(service);var result=new java.util.ArrayList<IslandRecord>();
            for(Object row:rows){Class<?> type=row.getClass();result.add(new IslandRecord(new Realm((UUID)type.getMethod("realmId").invoke(row),(UUID)type.getMethod("ownerId").invoke(row),(String)type.getMethod("worldName").invoke(row)),type.getMethod("phase").invoke(row).toString()));}return java.util.List.copyOf(result);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Unsupported Worldforge island records API.",failure);}
    }
    Realm ownedRealm(Player player){
        Plugin plugin=Bukkit.getPluginManager().getPlugin("Worldforge");
        if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Worldforge is unavailable.");
        try{
            Object service=plugin.getClass().getMethod("islandService").invoke(plugin);
            Object record=((Optional<?>)service.getClass().getMethod("forPlayer",UUID.class).invoke(service,player.getUniqueId())).orElseThrow(()->new IllegalStateException("Create your island first."));
            Class<?> type=record.getClass();UUID owner=(UUID)type.getMethod("ownerId").invoke(record);
            if(!owner.equals(player.getUniqueId()))throw new IllegalStateException("Only the current Realm owner can receive or recover a Core.");
            if(!"READY".equals(type.getMethod("phase").invoke(record).toString()))throw new IllegalStateException("Your Realm is not ready.");
            return new Realm((UUID)type.getMethod("realmId").invoke(record),owner,(String)type.getMethod("worldName").invoke(record));
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Unsupported Worldforge ownership API.",failure);}
    }
    /** Repairs through Worldforge's public API, retaining its ownership and permission checks. */
    void repairCoreHome(Player staff,RealmCoreStore.Core core,org.bukkit.Location safe,java.util.Set<TerrainPlan.Pos> cells)throws ReflectiveOperationException {
        Plugin plugin=Bukkit.getPluginManager().getPlugin("Worldforge");
        Object service=plugin.getClass().getMethod("islandService").invoke(plugin);
        Object record=((Optional<?>)service.getClass().getMethod("find",UUID.class).invoke(service,core.realm())).orElseThrow();Class<?> type=record.getClass();
        int x=(int)Math.floor((double)type.getMethod("x").invoke(record)),y=(int)Math.floor((double)type.getMethod("y").invoke(record)),z=(int)Math.floor((double)type.getMethod("z").invoke(record));
        if(core.design()>=2&&!cells.contains(new TerrainPlan.Pos(x-core.x(),y-1-core.y(),z-core.z()))&&!cells.contains(new TerrainPlan.Pos(x-core.x(),y-core.y(),z-core.z()))&&!cells.contains(new TerrainPlan.Pos(x-core.x(),y+1-core.y(),z-core.z())))return;
        if(!core.contains(core.world(),x,y,z)&&!cells.contains(new TerrainPlan.Pos(x-core.x(),y-1-core.y(),z-core.z())))return;
        if(!staff.teleport(safe))throw new IllegalStateException("Staff return teleport was cancelled.");
        service.getClass().getMethod("setHome",Player.class,type).invoke(service,staff,record);
    }
    void repairUnsafeHome(Player owner,RealmCoreStore.Core core)throws ReflectiveOperationException{Plugin plugin=Bukkit.getPluginManager().getPlugin("Worldforge");Object service=plugin.getClass().getMethod("islandService").invoke(plugin);Object record=((Optional<?>)service.getClass().getMethod("find",UUID.class).invoke(service,core.realm())).orElseThrow();Class<?> type=record.getClass();var world=Bukkit.getWorld(core.world());var home=new org.bukkit.Location(world,(double)type.getMethod("x").invoke(record),(double)type.getMethod("y").invoke(record),(double)type.getMethod("z").invoke(record));if(!(boolean)service.getClass().getMethod("safe",org.bukkit.Location.class).invoke(null,home))coreHome(owner,core,true);}
    void coreHome(Player player,RealmCoreStore.Core core,boolean returnPlayer){
        Plugin plugin=Bukkit.getPluginManager().getPlugin("Worldforge");
        if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Worldforge is unavailable.");
        try{
            Object service=plugin.getClass().getMethod("islandService").invoke(plugin);
            Object record=((Optional<?>)service.getClass().getMethod("find",UUID.class).invoke(service,core.realm())).orElseThrow(()->new IllegalStateException("Core Realm record is missing."));
            Class<?> type=record.getClass();
            if(!player.getUniqueId().equals(type.getMethod("ownerId").invoke(record)))throw new IllegalStateException("Only the current Realm owner can repair its home.");
            if(!"READY".equals(type.getMethod("phase").invoke(record).toString()))throw new IllegalStateException("This Realm is not ready.");
            org.bukkit.World world=Bukkit.getWorld(core.world());
            if(world==null){
                try{service.getClass().getMethod("home",Player.class,type).invoke(service,player,record);}
                catch(java.lang.reflect.InvocationTargetException failure){if(failure.getCause()==null||!String.valueOf(failure.getCause().getMessage()).startsWith("Saved home is unsafe"))throw failure;}
                world=Bukkit.getWorld(core.world());
            }
            if(world==null)throw new IllegalStateException("Worldforge could not load the registered Core world.");
            var offset=RealmCoreLayout.approach(core.design());
            org.bukkit.Location approach=new org.bukkit.Location(world,core.x()+offset.x()+.5,core.y()+offset.y(),core.z()+offset.z()+.5);
            if(!world.getWorldBorder().isInside(approach)||!(boolean)service.getClass().getMethod("safe",org.bukkit.Location.class).invoke(null,approach))throw new IllegalStateException("The Core approach is unsafe; an admin must repair its floor or headroom.");
            if(returnPlayer&&!player.teleport(approach))throw new IllegalStateException("Return teleport was cancelled.");
            if(!player.getWorld().equals(world))throw new IllegalStateException("Enter your Core Realm before setting its home.");
            service.getClass().getMethod("setHome",Player.class,type).invoke(service,player,record);
        }catch(java.lang.reflect.InvocationTargetException failure){throw new IllegalStateException(failure.getCause()==null?"Worldforge home repair failed.":failure.getCause().getMessage(),failure);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Unsupported Worldforge home API; home was not repaired.",failure);}
    }
    record Realm(UUID id, UUID owner, String world) {}
    java.util.List<String> templateKeys(Realm realm){
        Plugin plugin=Bukkit.getPluginManager().getPlugin("Worldforge");
        if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Worldforge is unavailable.");
        try{
            Object service=plugin.getClass().getMethod("islandService").invoke(plugin);
            Object record=((Optional<?>)service.getClass().getMethod("find",UUID.class).invoke(service,realm.id())).orElseThrow(()->new IllegalStateException("Island record is missing."));
            String id=(String)record.getClass().getMethod("template").invoke(record);
            java.util.List<String> identities=new java.util.ArrayList<>();identities.add(id);
            Object templates=service.getClass().getMethod("templates").invoke(service);
            Object template=templates.getClass().getMethod("get",String.class).invoke(templates,id);
            if(template!=null){Object file=template.getClass().getMethod("file").invoke(template);if(file!=null)identities.add(file.toString());}
            return java.util.List.copyOf(identities);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Unsupported Worldforge template API; Core anchor was not guessed.",failure);}
    }
    Realm requireOwned(Player player) {
        return require(player,false);
    }
    Realm requirePlacement(Player player){return require(player,player.hasPermission("worldforge.realmshards.moderate"));}
    private Realm require(Player player,boolean moderation) {
        Plugin core = Bukkit.getPluginManager().getPlugin("Worldforge");
        if (core == null || !core.isEnabled()) throw new IllegalStateException("Worldforge must be enabled to forge terrain.");
        try {
            Object islands = core.getClass().getMethod("islandService").invoke(core);
            Optional<?> found = (Optional<?>) islands.getClass().getMethod("forWorld", org.bukkit.World.class).invoke(islands, player.getWorld());
            Object record = found.orElseThrow(() -> new IllegalStateException("Enter your island with /island home first."));
            Class<?> type = record.getClass();
            UUID owner = (UUID)type.getMethod("ownerId").invoke(record);
            UUID id = (UUID)type.getMethod("realmId").invoke(record);
            String world = (String)type.getMethod("worldName").invoke(record);
            if (!owner.equals(player.getUniqueId())&&!moderation) throw new IllegalStateException("Only the island owner or authorized moderation staff can release a RealmShard.");
            if (!"READY".equals(type.getMethod("phase").invoke(record).toString())) throw new IllegalStateException("This island is not ready.");
            boolean build = (boolean)islands.getClass().getMethod("canBuild", UUID.class, UUID.class).invoke(islands, player.getUniqueId(), id);
            if (!build&&!moderation) throw new IllegalStateException("Worldforge does not allow you to build here.");
            return new Realm(id, owner, world);
        } catch (IllegalStateException e) { throw e; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Unsupported Worldforge island API; terrain activation is disabled.", e); }
    }
    Realm describe(org.bukkit.World world){
        Plugin core=Bukkit.getPluginManager().getPlugin("Worldforge");if(core==null||!core.isEnabled())throw new IllegalStateException("Worldforge is unavailable.");
        try{Object islands=core.getClass().getMethod("islandService").invoke(core);Optional<?> found=(Optional<?>)islands.getClass().getMethod("forWorld",org.bukkit.World.class).invoke(islands,world);Object record=found.orElseThrow(()->new IllegalStateException("This is not a Worldforge island world."));Class<?> type=record.getClass();if(!type.getMethod("phase").invoke(record).toString().equals("READY"))throw new IllegalStateException("This island is not ready.");return new Realm((UUID)type.getMethod("realmId").invoke(record),(UUID)type.getMethod("ownerId").invoke(record),(String)type.getMethod("worldName").invoke(record));}catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported Worldforge island API.",e);}
    }
    java.util.List<org.bukkit.Location> returnCandidates(org.bukkit.World world){
        java.util.List<org.bukkit.Location> result=new java.util.ArrayList<>();
        Plugin core=Bukkit.getPluginManager().getPlugin("Worldforge");
        if(core!=null&&core.isEnabled())try{Object islands=core.getClass().getMethod("islandService").invoke(core);Optional<?> found=(Optional<?>)islands.getClass().getMethod("forWorld",org.bukkit.World.class).invoke(islands,world);if(found.isPresent()){Object r=found.get();Class<?> c=r.getClass();result.add(new org.bukkit.Location(world,(double)c.getMethod("x").invoke(r),(double)c.getMethod("y").invoke(r),(double)c.getMethod("z").invoke(r)));}result.add(world.getSpawnLocation());result.add((org.bukkit.Location)islands.getClass().getMethod("lobby").invoke(islands));}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot obtain a safe Worldforge return location.",e);}
        return result;
    }
}
