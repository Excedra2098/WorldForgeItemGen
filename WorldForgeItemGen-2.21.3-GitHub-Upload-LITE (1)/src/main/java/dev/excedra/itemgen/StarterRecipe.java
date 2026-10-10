package dev.excedra.itemgen;
import java.util.*;

/** No gameplay fusion cap. Checked arithmetic prevents corrupt or wrapped compositions. */
record StarterRecipe(List<Long> counts, UUID identity, Map<String,Long> biomes) {
    StarterRecipe(List<Long> counts,UUID identity){this(counts,identity,Map.of());}
    StarterRecipe {
        counts=List.copyOf(counts); Objects.requireNonNull(identity);
        if(counts.size()!=9||counts.stream().anyMatch(n->n<0))throw new IllegalArgumentException("Invalid starter recipe.");
        var ordered=new TreeMap<String,Long>();long total=0;
        for(long n:counts)total=Math.addExact(total,n);
        for(var entry:biomes.entrySet()){ShardSpec spec=parseBiome(entry.getKey());if(!spec.key().equals(entry.getKey())||entry.getValue()<1)throw new IllegalArgumentException("Invalid biome ingredient.");ordered.put(entry.getKey(),entry.getValue());total=Math.addExact(total,entry.getValue());}
        biomes=Collections.unmodifiableMap(ordered);
        if(total<1)throw new IllegalArgumentException("A recipe needs at least one shard.");
    }
    static StarterRecipe single(int theme){var c=new ArrayList<Long>(Collections.nCopies(9,0L));c.set(theme,1L);return new StarterRecipe(c,UUID.randomUUID());}
    long starterUnits(){long total=0;for(long n:counts)total=Math.addExact(total,n);return total;}
    long units(){long total=starterUnits();for(long n:biomes.values())total=Math.addExact(total,n);return total;}
    int tier(){int tier=units()>=9?5:(int)((units()+1)/2);for(String key:biomes.keySet())tier=Math.max(tier,parseBiome(key).tier());return tier;}
    ShardSpec.Variant variant(){var result=ShardSpec.Variant.STANDARD;for(String key:biomes.keySet()){var v=parseBiome(key).variant();if(v.ordinal()>result.ordinal())result=v;}return result;}
    static ShardSpec parseBiome(String key){String[] p=key.split("_",-1);if(p.length!=3)throw new IllegalArgumentException("Invalid biome ingredient.");return new ShardSpec(Integer.parseInt(p[1].substring(1)),p[0].equals("random")?null:ShardSpec.Land.valueOf(p[0].toUpperCase(Locale.ROOT)),ShardSpec.Variant.valueOf(p[2].toUpperCase(Locale.ROOT)));}
    static StarterRecipe biome(ShardSpec spec){UUID identity=UUID.randomUUID();if(spec.land()==null)spec=new ShardSpec(spec.tier(),ShardSpec.Land.values()[Math.floorMod(identity.getLeastSignificantBits(),ShardSpec.Land.values().length)],spec.variant());return new StarterRecipe(Collections.nCopies(9,0L),identity,Map.of(spec.key(),1L));}
    long seed(){return identity.getMostSignificantBits()^Long.rotateLeft(identity.getLeastSignificantBits(),23);}
    StarterRecipe merge(StarterRecipe other){return aggregate(List.of(this,other),List.of(1,1));}
    static StarterRecipe aggregate(List<StarterRecipe> recipes,List<Integer> amounts){
        if(recipes.size()!=amounts.size()||recipes.isEmpty())throw new IllegalArgumentException("Invalid fusion ingredients.");
        var c=new ArrayList<Long>(Collections.nCopies(9,0L));
        var biomes=new TreeMap<String,Long>();
        for(int n=0;n<recipes.size();n++){int amount=amounts.get(n);if(amount<1)throw new IllegalArgumentException("Invalid shard amount.");for(int i=0;i<9;i++)c.set(i,Math.addExact(c.get(i),Math.multiplyExact(recipes.get(n).counts().get(i),amount)));for(var entry:recipes.get(n).biomes.entrySet())biomes.merge(entry.getKey(),Math.multiplyExact(entry.getValue(),amount),Math::addExact);}
        return new StarterRecipe(c,UUID.randomUUID(),biomes);
    }
    String encode(){String base=identity+":"+String.join(",",counts.stream().map(Object::toString).toList());return biomes.isEmpty()?base:base+":"+String.join(";",biomes.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());}
    static StarterRecipe decode(String s){var p=s.split(":",-1);if(p.length!=2&&p.length!=3)throw new IllegalArgumentException("Invalid starter recipe");Map<String,Long> biomes=new TreeMap<>();if(p.length==3){for(String entry:p[2].split(";",-1)){var pair=entry.split("=",-1);if(pair.length!=2||biomes.put(pair[0],Long.parseLong(pair[1]))!=null)throw new IllegalArgumentException("Invalid biome ingredient.");}}return new StarterRecipe(Arrays.stream(p[1].split(",",-1)).map(Long::parseLong).toList(),UUID.fromString(p[0]),biomes);}
}
