package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Reads contextual LuckPerms inheritance, never changes permission data. */
final class ShardAccess {
    private final JavaPlugin plugin;
    ShardAccess(JavaPlugin plugin){this.plugin=plugin;}
    static String normalize(String name){return name.toLowerCase(Locale.ROOT).replace("-","").replace("_","").replace(" ","");}
    static boolean allowed(Collection<String> groups,boolean owner){return groups.stream().map(ShardAccess::normalize).anyMatch(g->g.equals("owner")||!owner&&(g.equals("admin")||g.equals("headadmin")));}
    boolean staff(CommandSender sender){return sender instanceof ConsoleCommandSender || sender instanceof Player p&&allowed(groups(p),false);}
    boolean owner(Player player){return allowed(groups(player),true);}
    boolean moderator(Player player){return player.hasPermission("worldforge.realmshards.moderate")||owner(player);}
    private Set<String> groups(Player player){
        try{
            ClassLoader loader=Objects.requireNonNull(plugin.getServer().getPluginManager().getPlugin("LuckPerms")).getClass().getClassLoader();
            Class<?> api=Class.forName("net.luckperms.api.LuckPerms",true,loader), provider=Class.forName("net.luckperms.api.LuckPermsProvider",true,loader);
            Object lp=provider.getMethod("get").invoke(null);
            Object manager=api.getMethod("getUserManager").invoke(lp);
            Object user=Class.forName("net.luckperms.api.model.user.UserManager",true,loader).getMethod("getUser",UUID.class).invoke(manager,player.getUniqueId());
            if(user==null)return Set.of();
            Class<?> holder=Class.forName("net.luckperms.api.model.PermissionHolder",true,loader), query=Class.forName("net.luckperms.api.query.QueryOptions",true,loader);
            Object options=holder.getMethod("getQueryOptions").invoke(user);
            Collection<?> inherited=(Collection<?>)holder.getMethod("getInheritedGroups",query).invoke(user,options);
            Set<String> names=new HashSet<>();Class<?> group=Class.forName("net.luckperms.api.model.group.Group",true,loader);
            for(Object g:inherited)names.add((String)group.getMethod("getName").invoke(g));
            return names;
        }catch(ReflectiveOperationException|RuntimeException ex){return Set.of();}
    }
}
