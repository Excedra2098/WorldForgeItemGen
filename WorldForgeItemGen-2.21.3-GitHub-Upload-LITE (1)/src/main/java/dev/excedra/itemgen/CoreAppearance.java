package dev.excedra.itemgen;

import java.util.*;
import java.io.IOException;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Cosmetic data is separate from terrain and one-time Core claims. */
final class CoreAppearance {
    record Option(String category,String id,String title,int discoveries,boolean secret,boolean grantOnly){}
    static final List<String> CATEGORIES=List.of("crystal","primary","secondary","effect","decor","attach");
    static final Map<String,String> DEFAULTS=Map.of("crystal","classic","primary","amethyst","secondary","aqua","effect","gentle","decor","none","attach","none");
    static final List<Option> CATALOG=List.of(
        new Option("crystal","classic","Original Amethyst",0,false,false),
        new Option("crystal","spire","Ancient Spire",2,false,false),
        new Option("crystal","twin","Twin Hearts",5,false,false),
        new Option("crystal","crown","Astral Crown",10,true,false),
        new Option("crystal","eclipse","Eclipse Heart",0,true,true),
        new Option("primary","amethyst","Amethyst",0,false,false),new Option("primary","aqua","Aquamarine",2,false,false),
        new Option("primary","rose","Rose Quartz",4,false,false),new Option("primary","ember","Ember",6,false,false),
        new Option("primary","jade","Jade",8,false,false),new Option("primary","winter","Winter Starlight",0,false,true),
        new Option("secondary","aqua","Aquamarine",0,false,false),new Option("secondary","amethyst","Amethyst",0,false,false),
        new Option("secondary","rose","Rose Quartz",4,false,false),new Option("secondary","ember","Ember",6,false,false),
        new Option("secondary","jade","Jade",8,false,false),new Option("secondary","winter","Winter Starlight",0,false,true),
        new Option("effect","gentle","Gentle Float",0,false,false),new Option("effect","still","Quiet Heart",0,false,false),
        new Option("effect","orbit","Turning Starlight",3,false,false),new Option("effect","pulse","Arcane Pulse",7,false,false),
        new Option("effect","aurora","Aurora (event)",0,true,true),
        new Option("decor","none","No extra decorations",0,false,false),new Option("decor","fragments","Floating Fragments",1,false,false),
        new Option("decor","runes","Ancient Runes",3,false,false),new Option("decor","pillars","Crystal Pillars",6,false,false),
        new Option("decor","winter","Winter Relics (event)",0,false,true),
        new Option("attach","none","No attachment",0,false,false),new Option("attach","ring","Celestial Ring",4,false,false),
        new Option("attach","relic","Orbiting Relic",8,false,false),new Option("attach","halo","Anniversary Halo",0,true,true));
    record Profile(Map<String,String> choices){
        Profile {Map<String,String> valid=new LinkedHashMap<>(DEFAULTS);for(var e:choices.entrySet())if(option(e.getKey(),e.getValue())!=null)valid.put(e.getKey(),e.getValue());choices=Map.copyOf(valid);}
        String get(String category){return choices.get(category);}
        Profile with(String category,String id){if(option(category,id)==null)throw new IllegalArgumentException("Unknown cosmetic.");Map<String,String> next=new LinkedHashMap<>(choices);next.put(category,id);return new Profile(next);}
    }
    static Option option(String category,String id){return CATALOG.stream().filter(o->o.category().equals(category)&&o.id().equals(id)).findFirst().orElse(null);}
    static int color(String id){return switch(id){case "aqua"->0x54e9ee;case "rose"->0xff8abd;case "ember"->0xff9b45;case "jade"->0x75e89b;case "winter"->0xe8f4ff;default->0xb65bff;};}
    static String crystalModel(Profile p){return "crystal_"+p.get("crystal")+"_"+p.get("primary");}
    private final Path file;
    private final YamlConfiguration data=new YamlConfiguration();
    CoreAppearance(Path folder)throws IOException {Files.createDirectories(folder);file=folder.resolve("core-customization.yml");if(Files.exists(file))try{data.load(file.toFile());}catch(Exception e){throw new IOException("Invalid Core customization data",e);}}
    Profile profile(UUID realm){Map<String,String> result=new HashMap<>();for(String c:CATEGORIES)result.put(c,data.getString("realms."+realm+"."+c,DEFAULTS.get(c)));return new Profile(result);}
    Set<String> unlocked(UUID owner){return Set.copyOf(data.getStringList("players."+owner+".unlocked"));}
    static String key(Option option){return option.category()+":"+option.id();}
    boolean available(UUID owner,Option option){return option.discoveries()==0&&!option.grantOnly()||unlocked(owner).contains(key(option));}
    boolean allowed(UUID owner,Profile profile){return CATEGORIES.stream().allMatch(c->available(owner,option(c,profile.get(c))));}
    void progress(UUID owner,List<CoreRecipe> patterns)throws IOException {
        Set<String> unlocked=new LinkedHashSet<>(unlocked(owner));int count=patterns.size();
        for(Option o:CATALOG)if(!o.grantOnly()&&count>=o.discoveries())unlocked.add(key(o));
        // A combined glow/root recipe is an additional hidden discovery.
        if(patterns.stream().anyMatch(p->p.secondary()!=null&&p.has(CoreRecipe.Influence.CRYSTAL)&&p.has(CoreRecipe.Influence.ROOTS)))unlocked.add("crystal:crown");
        String path="players."+owner;Map<String,Object> edits=new LinkedHashMap<>();edits.put(path+".unlocked",List.copyOf(unlocked));edits.put(path+".discoveries",Math.max(count,data.getInt(path+".discoveries")));
        if(!unlocked.equals(unlocked(owner))||count>data.getInt(path+".discoveries"))update(edits);
    }
    void equip(UUID realm,UUID owner,Profile profile)throws IOException {if(!allowed(owner,profile))throw new IllegalStateException("Locked cosmetics can be previewed by staff, but cannot be equipped without an explicit reward grant.");Map<String,Object> edits=new LinkedHashMap<>();profile.choices().forEach((k,v)->edits.put("realms."+realm+"."+k,v));update(edits);}
    void grant(UUID owner,Option option)throws IOException {Set<String> unlocks=new LinkedHashSet<>(unlocked(owner));unlocks.add(key(option));update(Map.of("players."+owner+".unlocked",List.copyOf(unlocks)));}
    void resetProgress(UUID owner)throws IOException {Map<String,Object> edits=new LinkedHashMap<>();edits.put("players."+owner,null);update(edits);}
    private void update(Map<String,Object> edits)throws IOException {Map<String,Object> before=new HashMap<>();edits.keySet().forEach(k->before.put(k,data.get(k)));edits.forEach(data::set);try{RealmShards.atomicSave(data,file);}catch(IOException failure){before.forEach(data::set);throw failure;}}
}
