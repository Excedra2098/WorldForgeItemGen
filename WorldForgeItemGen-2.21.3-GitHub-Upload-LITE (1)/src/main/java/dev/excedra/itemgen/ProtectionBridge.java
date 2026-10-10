package dev.excedra.itemgen;

import java.lang.reflect.Array;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** WorldGuard is optional; if installed but incompatible, placement fails closed. */
final class ProtectionBridge {
    interface Guard { boolean allowed(Location location); }
    private static Guard permanent=location->true;
    static void permanent(Guard guard){permanent=java.util.Objects.requireNonNull(guard);}
    static Guard checker(Player player){
        Guard external=worldGuard(player);
        return location->permanent.allowed(location)&&external.allowed(location);
    }
    /** Read-only headroom checks may cross the permanent Core, but retain region checks. */
    static Guard clearanceChecker(Player player){return worldGuard(player);}
    private static Guard worldGuard(Player player){
        Plugin wg=Bukkit.getPluginManager().getPlugin("WorldGuard");if(wg==null)return location->true;if(!wg.isEnabled())return location->false;
        try{
            ClassLoader loader=wg.getClass().getClassLoader();
            Class<?> worldguard=Class.forName("com.sk89q.worldguard.WorldGuard",true,loader);
            Object instance=worldguard.getMethod("getInstance").invoke(null);
            Object platform=worldguard.getMethod("getPlatform").invoke(instance);
            Object container=platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query=container.getClass().getMethod("createQuery").invoke(container);
            Object local=wg.getClass().getMethod("wrapPlayer",Player.class).invoke(wg,player);
            Class<?> adapted=Class.forName("com.sk89q.worldedit.util.Location",true,loader);
            var adapt=Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter",true,loader).getMethod("adapt",Location.class);
            Class<?> localType=Class.forName("com.sk89q.worldguard.LocalPlayer",true,loader);
            Class<?> flag=Class.forName("com.sk89q.worldguard.protection.flags.StateFlag",true,loader);
            Object flags=Array.newInstance(flag,0);
            var test=query.getClass().getMethod("testBuild",adapted,localType,flags.getClass());
            return location->{if(!wg.isEnabled())return false;try{return (boolean)test.invoke(query,adapt.invoke(null,location),local,flags);}catch(ReflectiveOperationException e){return false;}};
        }catch(ReflectiveOperationException|LinkageError e){return location->false;}
    }
}
