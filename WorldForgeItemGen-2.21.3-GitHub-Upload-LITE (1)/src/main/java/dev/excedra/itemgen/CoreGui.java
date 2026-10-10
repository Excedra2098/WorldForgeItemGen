package dev.excedra.itemgen;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
/** Core-local theme: no shared fonts or vanilla item overrides. */
final class CoreGui {
 static final NamespacedKey FONT=new NamespacedKey("realmcore","gui/menu");
 static Component title(String text){NamespacedKey font=text.equals("Awakened Realm Core")?new NamespacedKey("realmcore","gui/home"):FONT;return Component.text("\uE901\uE900\uE902").font(font).color(NamedTextColor.WHITE).append(Component.text(text).font(new NamespacedKey("minecraft","default")).color(NamedTextColor.LIGHT_PURPLE));}
 static String icon(Material material,String title){String s=title.toLowerCase(Locale.ROOT);
  if(s.contains("close")||s.contains("discard"))return "close";if(s.contains("previous"))return "previous";if(s.contains("next"))return "next";
  if(s.contains("back")||s.contains("categories"))return "back";if(s.contains("locked")&&!s.contains("unlocked"))return "locked";
  if(s.contains("forge")||s.contains("experiment")||s.contains("reproduce"))return "forge";if(s.contains("discover")||s.contains("journal"))return "journal";
  if(s.contains("customiz"))return "customize";if(s.contains("apply"))return "apply";if(s.contains("restore")||s.contains("clear"))return "reset";
  if(s.contains("preview"))return "preview";if(s.contains("foundation")||s.contains("awakened core"))return "core";
  return switch(material){case PURPLE_DYE->"primary";case CYAN_DYE->"secondary";case MOSSY_STONE_BRICKS->"decor";case GLOWSTONE_DUST->"effect";case END_CRYSTAL->"attach";case AMETHYST_SHARD,ENCHANTED_BOOK->"crystal";default->"pattern";};
 }
 static ItemStack button(Material material,String title,String... lore){ItemStack item=new ItemStack(Material.PAPER);ItemMeta meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("realmcore","gui/"+icon(material,title)));meta.displayName(Component.text(title).color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));meta.lore(Arrays.stream(lore).map(s->Component.text(s).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)).toList());item.setItemMeta(meta);return item;}
 static void fill(Inventory inventory){inventory.clear();}
}
