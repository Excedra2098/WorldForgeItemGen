package dev.excedra.itemgen;
import java.util.*;import java.util.zip.*;import java.nio.file.*;import java.lang.reflect.Proxy;import org.bukkit.inventory.Inventory;import com.google.gson.*;
public final class CoreVisualVerification {
 static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  int[] clears={0},sets={0};Inventory inventory=(Inventory)Proxy.newProxyInstance(CoreVisualVerification.class.getClassLoader(),new Class[]{Inventory.class},(o,m,a)->{if(m.getName().equals("clear"))clears[0]++;if(m.getName().equals("setItem"))sets[0]++;return null;});CoreGui.fill(inventory);check(clears[0]==1&&sets[0]==0,"Core panels contain no filler items or their hover tooltips");
  try(ZipFile pack=new ZipFile(args[0])){
   for(String shape:List.of("classic","spire","twin","crown","eclipse"))for(String primary:List.of("amethyst","aqua","rose","ember","jade","winter"))for(String secondary:List.of("amethyst","aqua","rose","ember","jade","winter")){
    var profile=new CoreAppearance.Profile(Map.of("crystal",shape,"primary",primary,"secondary",secondary));String id=CompactCoreLayout.crystalModel(profile);check(pack.getEntry("assets/realmcore/items/"+id+".json")!=null,"Every saved primary/secondary appearance has a rendered model");
   }
   var entry=pack.getEntry("assets/realmcore/models/item/pedestal_complete.json");check(entry!=null,"Pedestal renders from one complete model");var model=JsonParser.parseString(new String(pack.getInputStream(entry).readAllBytes())).getAsJsonObject();
   for(var pos:CompactCoreLayout.blocks().keySet()){
    double[] point={8+pos.x()*8,8+(pos.y()-1.5)*8,8+pos.z()*8};boolean covered=false;
    for(var element:model.getAsJsonArray("elements")){var e=element.getAsJsonObject();if(e.has("rotation"))continue;var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");boolean inside=true;for(int axis=0;axis<3;axis++)inside&=point[axis]>=from.get(axis).getAsDouble()&&point[axis]<=to.get(axis).getAsDouble();covered|=inside;}
    check(covered,"Single centered model covers each physical pedestal collision cell: "+pos);
   }
   for(String menu:List.of("home","menu")){var font=JsonParser.parseString(new String(pack.getInputStream(pack.getEntry("assets/realmcore/font/gui/"+menu+".json")).readAllBytes())).getAsJsonObject();check(font.getAsJsonArray("providers").get(0).getAsJsonObject().getAsJsonObject("advances").get("\ue901").getAsInt()+177+font.getAsJsonArray("providers").get(0).getAsJsonObject().getAsJsonObject("advances").get("\ue902").getAsInt()==0,"GUI panel does not displace its vanilla title");}
  }
  System.out.println("Core visual verification passed: "+checks+" checks.");
 }
}
