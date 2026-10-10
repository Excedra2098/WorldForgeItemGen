package dev.excedra.itemgen;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
/** Read-only LuckPerms adapter, using the online user's contextual inherited groups. */
final class CorePlayerGroups {
 static Set<String> groups(Player player){
  var plugin=Bukkit.getPluginManager().getPlugin("LuckPerms");if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("LuckPerms must be available before automatic Core eligibility is decided.");
  try{var loader=plugin.getClass().getClassLoader();var apiType=Class.forName("net.luckperms.api.LuckPerms",true,loader);Object api=Class.forName("net.luckperms.api.LuckPermsProvider",true,loader).getMethod("get").invoke(null);
   Object manager=apiType.getMethod("getUserManager").invoke(api);Object user=Class.forName("net.luckperms.api.model.user.UserManager",true,loader).getMethod("getUser",UUID.class).invoke(manager,player.getUniqueId());if(user==null)throw new IllegalStateException("LuckPerms user is not loaded yet.");
   return resolve(loader,user);
  }catch(ReflectiveOperationException failure){throw new IllegalStateException("LuckPerms group lookup is unavailable; automatic Core placement waits.",failure);}
 }
 static Set<String> resolve(ClassLoader loader,Object user)throws ReflectiveOperationException{
   var userType=Class.forName("net.luckperms.api.model.user.User",true,loader);Object query=userType.getMethod("getQueryOptions").invoke(user);var queryType=Class.forName("net.luckperms.api.query.QueryOptions",true,loader);var groupType=Class.forName("net.luckperms.api.model.group.Group",true,loader);
   var result=new HashSet<String>();result.add((String)userType.getMethod("getPrimaryGroup").invoke(user));for(Object group:(Collection<?>)userType.getMethod("getInheritedGroups",queryType).invoke(user,query))result.add((String)groupType.getMethod("getName").invoke(group));return Set.copyOf(result);
 }
}
