package dev.excedra.itemgen;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
public final class SpecialShardRegistryVerification {
    private static int checks;
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    private static void rejects(Runnable action,String reason){
        try{action.run();throw new AssertionError("Accepted invalid input: "+reason);}catch(IllegalArgumentException expected){checks++;}
    }
    private static YamlConfiguration sample(){
        var y=new YamlConfiguration();
        y.set("schema-version",1);
        y.set("identity.id","winter_frostbound_2026");
        y.set("identity.name","Frostbound Fragment");
        y.set("identity.category","SEASONAL");
        y.set("identity.event-id","winter_2026");
        y.set("shard.base-land","SNOW");
        y.set("shard.tier",3);
        y.set("shard.variant","STANDARD");
        y.set("island.schematic","island.schem");
        y.set("assets.item-model","realmshards:special/frostbound");
        y.set("distribution.enabled",false);
        return y;
    }
    public static void main(String[] args){
        var y=sample();String original=y.saveToString();
        var entry=SpecialShardManifest.parse(y);
        check(entry.id().equals("winter_frostbound_2026"),"Valid manifest");
        check(y.saveToString().equals(original),"Read-only parsing");
        check(SpecialShardCategory.BUILTIN.size()==9,"Nine initial categories");
        check(SpecialShardCategory.of("my_own_event").id().equals("MY_OWN_EVENT"),"Expandable categories");
        check(SpecialShardRegistry.fromDefinitions(List.of(entry)).get(entry.id()).isPresent(),"Registry lookup");
        rejects(()->SpecialShardRegistry.fromDefinitions(List.of(entry,entry)),"duplicate ID");
        y=sample();y.set("shard.base-land","UNKNOWN");var badLand=y;rejects(()->SpecialShardManifest.parse(badLand),"land");
        y=sample();y.set("shard.tier",6);var badTier=y;rejects(()->SpecialShardManifest.parse(badTier),"tier");
        y=sample();y.set("shard.tier",2);y.set("shard.variant","RAINBOW");var badRainbow=y;rejects(()->SpecialShardManifest.parse(badRainbow),"rainbow");
        y=sample();y.set("assets.item-model","invalid model");var badModel=y;rejects(()->SpecialShardManifest.parse(badModel),"model");
        y=sample();y.set("identity.category","!");var badCategory=y;rejects(()->SpecialShardManifest.parse(badCategory),"category");
        y=sample();y.set("island.schematic","../world.yml");var traversal=y;rejects(()->SpecialShardManifest.parse(traversal),"unsafe path");
        y=sample();y.set("distribution.enabled",true);var active=y;rejects(()->SpecialShardManifest.parse(active),"distribution on");
        var registryConfig=new YamlConfiguration();registryConfig.set("schema-version",1);registryConfig.set("enabled",false);
        check(SpecialShardRegistry.load(registryConfig).all().isEmpty(),"Empty disabled registry");
        registryConfig.set("enabled",true);rejects(()->SpecialShardRegistry.load(registryConfig),"active registry");
        System.out.println("Special Shard C1 registry verification passed: "+checks+" checks.");
    }
}