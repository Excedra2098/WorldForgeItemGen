package dev.excedra.itemgen;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
/** A passive, in-memory index. Not registered with any live plugin service in C1. */
final class SpecialShardRegistry {
    private final Map<String,SpecialShardDefinition> definitions;
    private SpecialShardRegistry(Map<String,SpecialShardDefinition> entries){definitions=Map.copyOf(entries);}
    static SpecialShardRegistry empty(){return new SpecialShardRegistry(Map.of());}
    static SpecialShardRegistry fromDefinitions(Collection<SpecialShardDefinition> entries){
        Map<String,SpecialShardDefinition> values=new LinkedHashMap<>();
        for(SpecialShardDefinition entry:entries)if(values.putIfAbsent(entry.id(),entry)!=null)
            throw new IllegalArgumentException("Duplicate special shard ID: "+entry.id());
        return new SpecialShardRegistry(values);
    }
    static SpecialShardRegistry load(ConfigurationSection config){
        if(config==null||config.getInt("schema-version",-1)!=1)throw new IllegalArgumentException("Unsupported registry schema");
        if(config.getBoolean("enabled",false))throw new IllegalArgumentException("C1 registry cannot be enabled");
        ConfigurationSection entries=config.getConfigurationSection("special-shards");
        if(entries==null){if(config.contains("special-shards"))throw new IllegalArgumentException("Invalid special-shards section");return empty();}
        List<SpecialShardDefinition> collected=new ArrayList<>();
        for(String id:entries.getKeys(false)){
            var section=entries.getConfigurationSection(id);
            SpecialShardDefinition definition=SpecialShardManifest.parse(section);
            if(!id.equals(definition.id()))throw new IllegalArgumentException("Shard ID mismatch: "+id);
            collected.add(definition);
        }
        return fromDefinitions(collected);
    }
    Optional<SpecialShardDefinition> get(String id){return Optional.ofNullable(definitions.get(id));}
    Collection<SpecialShardDefinition> all(){return definitions.values();}
    static SpecialShardValidationReport validate(ConfigurationSection config){
        try {load(config);return new SpecialShardValidationReport(List.of(),List.of("C1 is registry-only; no shards can be distributed or placed."));}
        catch(RuntimeException e){return new SpecialShardValidationReport(List.of(e.getMessage()),List.of());}
    }
}