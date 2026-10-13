package dev.excedra.itemgen;
import java.util.List;
import java.util.Objects;
/** Immutable design-time information, never an in-game shard identity. */
record SpecialShardDefinition(String id,String name,SpecialShardCategory category,String eventId,
        ShardSpec.Land land,int tier,ShardSpec.Variant variant,String modelId,String schematic,
        List<String> models,List<String> textures,List<String> animations,List<String> sounds,
        List<String> discoveries,List<String> reactions,List<String> effects,boolean distributionEnabled) {
    SpecialShardDefinition {
        Objects.requireNonNull(category);Objects.requireNonNull(land);Objects.requireNonNull(variant);
        if(id==null||!id.matches("[a-z][a-z0-9_-]{2,63}"))throw new IllegalArgumentException("Invalid shard ID");
        if(name==null||name.isBlank()||name.length()>100)throw new IllegalArgumentException("Invalid display name");
        if(eventId!=null&&!eventId.matches("[a-z][a-z0-9_-]{2,63}"))throw new IllegalArgumentException("Invalid event ID");
        new ShardSpec(tier,land,variant);
        if(modelId==null||!modelId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid model ID");
        SpecialShardManifest.safePath(schematic);
        models=List.copyOf(models);textures=List.copyOf(textures);animations=List.copyOf(animations);sounds=List.copyOf(sounds);
        discoveries=List.copyOf(discoveries);reactions=List.copyOf(reactions);effects=List.copyOf(effects);
        for(String path:models)SpecialShardManifest.safePath(path);
        for(String path:textures)SpecialShardManifest.safePath(path);
        for(String path:animations)SpecialShardManifest.safePath(path);
        for(String path:sounds)SpecialShardManifest.safePath(path);
        if(distributionEnabled)throw new IllegalArgumentException("C1 distribution must remain disabled");
    }
}