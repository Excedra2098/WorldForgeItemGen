package dev.excedra.itemgen;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
/** Strict, read-only version 1 YAML-to-definition conversion. */
final class SpecialShardManifest {
    private SpecialShardManifest(){}
    static void safePath(String value) {
        if(value==null||value.isBlank()||value.startsWith("/")||value.contains("\\")||value.contains(":")||value.contains("//")||value.equals(".")||value.contains("..")||!value.matches("[a-zA-Z0-9_./-]+"))
            throw new IllegalArgumentException("Unsafe relative asset path: "+value);
    }
    static SpecialShardDefinition parse(ConfigurationSection root) {
        if(root==null||root.getInt("schema-version",-1)!=1)throw new IllegalArgumentException("Unsupported manifest schema version");
        ConfigurationSection identity=required(root,"identity"),shard=required(root,"shard"),island=required(root,"island"),assets=required(root,"assets"),distribution=required(root,"distribution");
        if(distribution.getBoolean("enabled",false))throw new IllegalArgumentException("C1 distribution cannot be enabled");
        String category=identity.getString("category");
        try {
            return new SpecialShardDefinition(
                identity.getString("id"),identity.getString("name"),SpecialShardCategory.of(category),identity.getString("event-id"),
                ShardSpec.Land.valueOf(requiredString(shard,"base-land").toUpperCase(java.util.Locale.ROOT)),
                shard.getInt("tier",-1),ShardSpec.Variant.valueOf(requiredString(shard,"variant").toUpperCase(java.util.Locale.ROOT)),
                assets.getString("item-model"),island.getString("schematic"),
                assets.getStringList("models"),assets.getStringList("textures"),assets.getStringList("animations"),assets.getStringList("sounds"),
                root.getStringList("future-features.discoveries"),root.getStringList("future-features.reactions"),root.getStringList("future-features.special-effects"),false);
        }catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid special shard definition: "+e.getMessage(),e);}
    }
    private static ConfigurationSection required(ConfigurationSection p,String key){
        ConfigurationSection result=p.getConfigurationSection(key);if(result==null)throw new IllegalArgumentException("Missing section: "+key);return result;
    }
    private static String requiredString(ConfigurationSection p,String key){
        String v=p.getString(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing: "+key);return v;
    }
}