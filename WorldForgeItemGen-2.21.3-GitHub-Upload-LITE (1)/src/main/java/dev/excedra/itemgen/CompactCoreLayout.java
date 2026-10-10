package dev.excedra.itemgen;
import java.util.*;import org.bukkit.Material;import org.bukkit.NamespacedKey;import org.bukkit.inventory.ItemStack;
/** Small collision footprint beneath separate pedestal and crystal models. */
final class CompactCoreLayout {
 static final int RADIUS=2,MAX_Y=8;static final double CRYSTAL_Y=5.25;
 static Map<TerrainPlan.Pos,Material> blocks(){var result=new LinkedHashMap<TerrainPlan.Pos,Material>();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)result.put(new TerrainPlan.Pos(x,1,z),Material.BARRIER);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)result.put(new TerrainPlan.Pos(x,2,z),Material.BARRIER);for(int x:new int[]{-2,2})for(int z:new int[]{-2,2})result.put(new TerrainPlan.Pos(x,2,z),Material.BARRIER);result.put(new TerrainPlan.Pos(0,1,0),Material.CHISELED_DEEPSLATE);return result;}
 static Set<TerrainPlan.Pos> groups(){var groups=new LinkedHashSet<TerrainPlan.Pos>();blocks().keySet().forEach(p->groups.add(new TerrainPlan.Pos(Math.floorDiv(p.x(),2),Math.floorDiv(p.y(),2),Math.floorDiv(p.z(),2))));return groups;}
 static String groupName(TerrainPlan.Pos p){return "pedestal_"+p.x()+"_"+p.y()+"_"+p.z();}
 static boolean replaceable(Material m){return m==Material.AIR||m==Material.CAVE_AIR||m==Material.VOID_AIR;}
 static ItemStack model(String model){var item=new ItemStack(Material.AMETHYST_SHARD);var meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("realmcore",model));item.setItemMeta(meta);return item;}
 static String crystalModel(CoreAppearance.Profile profile){return CoreAppearance.crystalModel(profile)+"_"+profile.get("secondary");}
}
