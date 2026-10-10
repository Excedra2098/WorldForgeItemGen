package dev.excedra.itemgen;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Persistent foundations, personal patterns, and conservative forge transaction recovery. */
final class RealmCoreStore {
    record Core(UUID realm,UUID world,UUID creator,int x,int y,int z,int design) {
        Core(UUID realm,UUID world,UUID creator,int x,int y,int z){this(realm,world,creator,x,y,z,0);}
        boolean contains(UUID world,int x,int y,int z){int radius=RealmCoreLayout.radius(design);return this.world.equals(world)&&Math.abs((long)x-this.x)<=radius&&Math.abs((long)z-this.z)<=radius&&y>=this.y+(design==5?0:design==4?-RaisedCorePadLayout.RISE:design==2?0:design>=1?RealmCoreLayout.MIN_Y:-2)&&y<=this.y+(design==5?CompactCoreLayout.MAX_Y:design>=1?RealmCoreLayout.MAX_Y:8);}
    }
    private final Path file,transactions;
    private final YamlConfiguration data;
    private final Map<UUID,Core> cores=new HashMap<>();
    private final Set<UUID> pending=new HashSet<>();
    private final Map<UUID,List<Core>> byWorld=new HashMap<>();
    private final Map<UUID,Set<TerrainPlan.Pos>> protectedCells=new HashMap<>();
    RealmCoreStore(Path folder)throws IOException {
        Files.createDirectories(folder);file=folder.resolve("realm-cores.yml");transactions=folder.resolve("core-forge-transactions");Files.createDirectories(transactions);
        data=new YamlConfiguration();
        if(Files.exists(file))try{data.load(file.toFile());}catch(Exception invalid){throw new IOException("Cannot read Realm Core data",invalid);}
        var section=data.getConfigurationSection("cores");
        if(section!=null)for(String id:section.getKeys(false))try{
            String prefix="cores."+id;Core core=new Core(UUID.fromString(id),UUID.fromString(data.getString(prefix+".world")),UUID.fromString(data.getString(prefix+".creator")),data.getInt(prefix+".x"),data.getInt(prefix+".y"),data.getInt(prefix+".z"),data.getInt(prefix+".design"));cores.put(core.realm(),core);
        }catch(RuntimeException invalid){throw new IOException("Invalid permanent Core record: "+id,invalid);}
        var discoveries=data.getConfigurationSection("discoveries");
        if(discoveries!=null)for(String player:discoveries.getKeys(false)){
            String key="discoveries."+player+".patterns";
            data.set(key,canonicalPatterns(data.getStringList(key)));
        }
        try(var files=Files.list(transactions)){
            for(Path transaction:files.filter(p->p.toString().endsWith(".yml")).toList()){
                YamlConfiguration ledger=new YamlConfiguration();
                try{ledger.load(transaction.toFile());UUID player=UUID.fromString(ledger.getString("player"));String state=ledger.getString("status");
                    if("PREPARED".equals(state))pending.add(player);
                    else if("COMPLETE".equals(state)){if(ledger.getLong("discovery-generation")==generation(player))remember(player,CoreRecipe.decode(ledger.getString("recipe")),false);}
                    else if(!"ABORTED".equals(state))throw new IOException("Unknown Core transaction state");
                }catch(Exception invalid){throw new IOException("Cannot inspect Core forge transaction: "+transaction,invalid);}
            }
        }
        for(Core core:cores.values()){byWorld.computeIfAbsent(core.world(),key->new ArrayList<>()).add(core);indexCells(core);}
        save();
    }

    record Anchor(UUID realm,UUID world,int x,int y,int z,String revision) {
        List<Integer> relative(int x,int y,int z){return List.of(Math.subtractExact(x,this.x),Math.subtractExact(y,this.y),Math.subtractExact(z,this.z));}
    }
    Anchor anchor(UUID realm){Core c=get(realm);if(c==null||!data.getBoolean("cores."+realm+".anchored"))return null;return new Anchor(realm,c.world(),c.x(),c.y(),c.z(),data.getString("cores."+realm+".revision"));}
    boolean standalone(UUID admin){return data.getBoolean("staff-modes."+admin);}
    void standalone(UUID admin,boolean enabled)throws IOException {mutate("staff-modes."+admin,enabled);}
    int[] placementAnchor(UUID realm){List<Integer> p=data.getIntegerList("placement-anchors."+realm);return p.size()==3?new int[]{p.get(0),p.get(1),p.get(2)}:null;}
    void placementAnchor(UUID realm,int x,int y,int z)throws IOException {if(get(realm)!=null)throw new IllegalStateException("Retrieve the Core before changing its placement anchor.");mutate("placement-anchors."+realm,List.of(x,y,z));}
    void migrateAnchor(Core core)throws IOException {
        if(!core.equals(get(core.realm())))throw new IllegalStateException("Core identity changed before migration.");
        if(anchor(core.realm())!=null)throw new IllegalStateException("This Core already anchors its Realm.");
        backup();String path="cores."+core.realm();String before=data.saveToString();data.set(path+".anchored",true);data.set(path+".revision",UUID.randomUUID().toString());
        try{save();}catch(IOException e){restore(before);throw e;}
    }
    void backup()throws IOException {if(Files.exists(file))Files.copy(file,file.resolveSibling("realm-cores-backup-"+UUID.randomUUID()+".yml"));}
    private void mutate(String key,Object value)throws IOException {Object before=data.get(key);data.set(key,value);try{save();}catch(IOException e){data.set(key,before);throw e;}}
    private void restore(String yaml)throws IOException {try{data.loadFromString(yaml);}catch(Exception e){throw new IOException("Cannot restore Core data",e);}}

    Core get(UUID realm){return cores.get(realm);}
    boolean plazaReady(UUID realm){return data.getBoolean("plaza-ready."+realm);}
    void plazaReady(UUID realm,boolean ready)throws IOException{mutate("plaza-ready."+realm,ready);}
    void plazaRollback(UUID realm)throws IOException{mutate("plaza-backups."+realm,null);}
    void plazaBackup(UUID realm,UUID world,int x,int y,int z,List<String> originals)throws IOException{String before=data.saveToString();String key="plaza-backups."+realm;if(data.contains(key))throw new IllegalStateException("A plaza repair backup already exists; staff must inspect before retrying.");data.set(key,Map.of("world",world.toString(),"x",x,"y",y,"z",z,"originals",originals));try{save();}catch(IOException failure){restore(before);throw failure;}}
    boolean autoInitialized(){return data.getBoolean("auto-core.initialized");}
    String autoState(UUID realm){return data.getString("auto-core.realms."+realm,"");}
    void autoInitialize(Collection<UUID> realms)throws IOException{if(autoInitialized())return;String before=data.saveToString();data.set("auto-core.initialized",true);for(UUID id:realms)data.set("auto-core.realms."+id,"EXISTING");try{save();}catch(IOException e){restore(before);throw e;}}
    void autoState(UUID realm,String state)throws IOException{mutate("auto-core.realms."+realm,state);}
    String autoPrepare(Core core,List<String> originals)throws IOException{
        if(!CoreAutoPolicy.pending(autoState(core.realm()))||get(core.realm())!=null)throw new IllegalStateException("This Realm is not awaiting an automatic Core.");
        String claim="core-claims."+core.creator();if(Set.of("PLACING","RETRIEVING").contains(data.getString(claim+".state","")))throw new IllegalStateException("An earlier Core operation requires staff review.");
        CorePadLayout.decode(originals);String before=data.saveToString(),serial=UUID.randomUUID().toString();var prior=data.getConfigurationSection(claim);
        if(prior!=null)data.createSection("auto-core.prior-claims."+core.realm(),new LinkedHashMap<>(prior.getValues(true)));
        data.set("auto-core.details."+core.realm(),Map.of("world",core.world().toString(),"owner",core.creator().toString(),"x",core.x(),"y",core.y(),"z",core.z()));
        data.set("pad-originals."+core.realm(),originals);data.createSection(claim,Map.of("realm",core.realm().toString(),"serial",serial,"state","PLACING"));data.set("auto-core.realms."+core.realm(),"PLACING");
        try{save();}catch(IOException failure){restore(before);throw failure;}return serial;
    }
    void autoCommit(Core core,List<String> cells,double x,double y,double z)throws IOException{
        if(!"PLACING".equals(autoState(core.realm())))throw new IllegalStateException("Automatic Core transaction changed.");String before=data.saveToString();
        data.set("auto-core.realms."+core.realm(),"PLACED");data.set("core-claims."+core.creator()+".state","PLACED");data.set("auto-core.prior-claims."+core.realm(),null);data.set("auto-core.details."+core.realm(),null);
        try{construction(core,cells,x,y,z);}catch(IOException failure){restore(before);throw failure;}
    }
    void autoAbort(Core core)throws IOException{
        if(get(core.realm())!=null)throw new IllegalStateException("A committed automatic Core cannot be aborted.");String before=data.saveToString();var prior=data.getConfigurationSection("auto-core.prior-claims."+core.realm());
        String claim="core-claims."+core.creator();var values=prior==null?null:new LinkedHashMap<>(prior.getValues(true));data.set(claim,null);if(values!=null)data.createSection(claim,values);data.set("auto-core.prior-claims."+core.realm(),null);data.set("auto-core.details."+core.realm(),null);data.set("pad-originals."+core.realm(),null);data.set("auto-core.realms."+core.realm(),"FAILED");try{save();}catch(IOException failure){restore(before);throw failure;}
    }
    String issue(UUID player,UUID realm,boolean replacement)throws IOException {
        if(cores.containsKey(realm))throw new IllegalStateException("This Realm already has its Core.");
        String key="core-claims."+player;
        if(Set.of("PLACING","RETRIEVING").contains(data.getString(key+".state","")))throw new IllegalStateException("A Core operation is active or interrupted; staff review is required before replacement.");
        if(data.contains(key)&&!replacement)throw new IllegalStateException("Your one-time Core Shard has already been granted. Staff must handle a lost item.");
        String serial=UUID.randomUUID().toString();Object before=data.getConfigurationSection(key)==null?null:new LinkedHashMap<>(data.getConfigurationSection(key).getValues(true));
        data.set(key+".realm",realm.toString());data.set(key+".serial",serial);data.set(key+".state","ISSUED");
        try{save();}catch(IOException failure){data.set(key,before);throw failure;}return serial;
    }
    void coreState(UUID player,String serial,String state)throws IOException {
        String key="core-claims."+player;
        if(!serial.equals(data.getString(key+".serial")))throw new IllegalStateException("This Core Shard has been superseded or is invalid.");
        String before=data.getString(key+".state");data.set(key+".state",state);
        try{save();}catch(IOException failure){data.set(key+".state",before);throw failure;}
    }
    record Refund(UUID owner,UUID realm,String serial){}
    void refundPlacement(UUID owner,String serial,UUID recipient)throws IOException {
        String key="core-claims."+owner;
        if(!serial.equals(data.getString(key+".serial")))throw new IllegalStateException("Core refund identity changed.");
        String before=data.getString(key+".state"),prior=data.getString(key+".refund-recipient");
        data.set(key+".state","ISSUED");data.set(key+".refund-recipient",recipient.toString());
        try{save();}catch(IOException failure){data.set(key+".state",before);data.set(key+".refund-recipient",prior);throw failure;}
    }
    List<Refund> refunds(UUID recipient){
        List<Refund> result=new ArrayList<>();var section=data.getConfigurationSection("core-claims");if(section==null)return result;
        for(String owner:section.getKeys(false)){String key="core-claims."+owner;if(recipient.toString().equals(data.getString(key+".refund-recipient"))&&"ISSUED".equals(data.getString(key+".state")))result.add(new Refund(UUID.fromString(owner),UUID.fromString(data.getString(key+".realm")),data.getString(key+".serial")));}return result;
    }
    void acknowledgeRefund(UUID owner,String serial)throws IOException {
        String key="core-claims."+owner;
        if(!serial.equals(data.getString(key+".serial")))throw new IllegalStateException("Core refund identity changed.");
        String before=data.getString(key+".refund-recipient");data.set(key+".refund-recipient",null);
        try{save();}catch(IOException failure){data.set(key+".refund-recipient",before);throw failure;}
    }
    void validateCoreItem(UUID player,UUID realm,String serial){
        String key="core-claims."+player,state=data.getString(key+".state","");
        if(!realm.toString().equals(data.getString(key+".realm"))||!Objects.equals(serial,data.getString(key+".serial"))||!Set.of("ISSUED","RETRIEVED").contains(state))throw new IllegalStateException("This Core Shard is invalid, already placed, or awaiting staff review.");
    }
    boolean claimed(UUID player){return data.contains("core-claims."+player)||cores.values().stream().anyMatch(core->core.creator().equals(player));}
    void resetCoreClaim(UUID player)throws IOException {
        String key="core-claims."+player,realm=data.getString(key+".realm");
        if(cores.values().stream().anyMatch(core->core.creator().equals(player)||core.realm().toString().equals(realm)))throw new IllegalStateException("Retrieve this player's placed Core with the staff Reclaimer before resetting its claim.");
        String state=data.getString(key+".state","");
        if(Set.of("PLACING","RETRIEVING").contains(state))throw new IllegalStateException("An interrupted Core operation needs staff inspection before its claim can be reset.");
        var section=data.getConfigurationSection(key);Map<String,Object> previous=section==null?null:new LinkedHashMap<>(section.getValues(true));
        data.set(key,null);
        try{save();}catch(IOException failure){if(previous!=null)for(var entry:previous.entrySet())data.set(key+"."+entry.getKey(),entry.getValue());throw failure;}
    }
    String prepareRetrieval(Core core,UUID owner)throws IOException {
        String key="core-claims."+owner;
        if("RETRIEVING".equals(data.getString(key+".state")))throw new IllegalStateException("Interrupted Core retrieval requires staff review.");
        Object before=data.getConfigurationSection(key)==null?null:new LinkedHashMap<>(data.getConfigurationSection(key).getValues(true));
        String serial=UUID.randomUUID().toString();data.set(key+".realm",core.realm().toString());data.set(key+".serial",serial);data.set(key+".state","RETRIEVING");try{save();}catch(IOException failure){data.set(key,before);throw failure;}return serial;
    }
    List<String> cells(Core core){return data.getStringList("cores."+core.realm()+".cells");}
    List<String> padOriginals(Core core){return data.getStringList("pad-originals."+core.realm());}
    void padOriginals(Core core,List<String> originals)throws IOException {CorePadLayout.decode(originals);mutate("pad-originals."+core.realm(),originals.isEmpty()?null:originals);}
    void construction(Core core,List<String> cells,double x,double y,double z)throws IOException {
        String key="cores."+core.realm();data.set(key+".cells",cells);data.set(key+".return-x",x);data.set(key+".return-y",y);data.set(key+".return-z",z);
        data.set(key+".anchored",true);data.set(key+".revision",UUID.randomUUID().toString());
        add(core);
    }
    double[] returnPoint(Core core){String key="cores."+core.realm();return data.contains(key+".return-x")?new double[]{data.getDouble(key+".return-x"),data.getDouble(key+".return-y"),data.getDouble(key+".return-z")}:null;}
    void removeCore(Core core)throws IOException {
        String key="cores."+core.realm(),pad="pad-originals."+core.realm();Object before=data.get(key),oldPad=data.get(pad);data.set(key,null);data.set(pad,null);
        try{save();}catch(IOException failure){data.set(key,before);data.set(pad,oldPad);throw failure;}
        cores.remove(core.realm());byWorld.getOrDefault(core.world(),new ArrayList<>()).remove(core);
        protectedCells.remove(core.realm());
    }
    Collection<Core> all(){return List.copyOf(cores.values());}
    boolean protectedAt(UUID world,int x,int y,int z){
        for(Core core:byWorld.getOrDefault(world,List.of()))if(core.contains(world,x,y,z)||protectedCells.getOrDefault(core.realm(),Set.of()).contains(new TerrainPlan.Pos(x-core.x(),y-core.y(),z-core.z())))return true;
        return false;
    }
    private void indexCells(Core core){
        Set<TerrainPlan.Pos> positions=new HashSet<>();
        for(String encoded:cells(core)){String[] coordinate=encoded.split("\\|",2)[0].split(",");if(coordinate.length!=3)throw new IllegalStateException("Invalid Core cell record");positions.add(new TerrainPlan.Pos(Integer.parseInt(coordinate[0]),Integer.parseInt(coordinate[1]),Integer.parseInt(coordinate[2])));}
        protectedCells.put(core.realm(),Set.copyOf(positions));
    }
    void add(Core core)throws IOException {
        if(cores.containsKey(core.realm()))throw new IllegalStateException("Your Realm already has its permanent Core.");
        String prefix="cores."+core.realm();data.set(prefix+".world",core.world().toString());data.set(prefix+".creator",core.creator().toString());data.set(prefix+".x",core.x());data.set(prefix+".y",core.y());data.set(prefix+".z",core.z());
        data.set(prefix+".design",core.design());
        try{indexCells(core);save();cores.put(core.realm(),core);byWorld.computeIfAbsent(core.world(),key->new ArrayList<>()).add(core);}catch(IOException failure){protectedCells.remove(core.realm());data.set(prefix,null);throw failure;}
    }
    List<CoreRecipe> patterns(UUID player){
        return data.getStringList("discoveries."+player+".patterns").stream().map(CoreRecipe::decode).distinct().toList();
    }
    static List<String> canonicalPatterns(List<String> encoded){return encoded.stream().map(CoreRecipe::decode).distinct().map(CoreRecipe::encode).toList();}
    boolean known(UUID player,CoreRecipe.Influence influence){return influence==CoreRecipe.Influence.ROOTS||patterns(player).stream().anyMatch(recipe->recipe.has(influence));}
    boolean known(UUID player,CoreRecipe recipe){return recipe.secondary()==null?known(player,recipe.influence()):patterns(player).stream().anyMatch(recipe::sameFamily);}
    private void remember(UUID player,CoreRecipe recipe,boolean persist)throws IOException {
        String key="discoveries."+player+".patterns";List<String> patterns=new ArrayList<>(canonicalPatterns(data.getStringList(key)));
        if(!patterns.contains(recipe.encode())){patterns.add(recipe.encode());data.set(key,patterns);if(persist)save();}
    }
    boolean pending(UUID player){return pending.contains(player);}
    long generation(UUID player){return data.getLong("discoveries."+player+".generation");}
    void resetDiscoveries(UUID player)throws IOException {
        if(pending(player))throw new IllegalStateException("Resolve this player's interrupted forge before resetting discoveries.");
        String prefix="discoveries."+player;List<String> previous=data.getStringList(prefix+".patterns");
        Object previousGeneration=data.get(prefix+".generation");long next=Math.addExact(generation(player),1);
        data.set(prefix+".patterns",List.of());data.set(prefix+".generation",next);
        try{save();}catch(IOException failure){data.set(prefix+".patterns",previous);data.set(prefix+".generation",previousGeneration);throw failure;}
    }
    void forge(Player player,CoreRecipe recipe,ItemStack reward)throws IOException {
        UUID id=player.getUniqueId();if(pending.contains(id))throw new IllegalStateException("An interrupted forge is awaiting recovery. Use /realmcore recover.");
        ItemStack[] before=player.getInventory().getStorageContents();
        for(int slot=0;slot<before.length;slot++)if(before[slot]!=null)before[slot]=before[slot].clone();
        ItemStack[] after=CoreInventoryExchange.plan(before,recipe.costs(),reward);
        Path path=transactions.resolve(UUID.randomUUID()+".yml");
        YamlConfiguration ledger=new YamlConfiguration();ledger.set("player",id.toString());ledger.set("recipe",recipe.encode());ledger.set("discovery-generation",generation(id));ledger.set("status","PREPARED");ledger.set("before",Arrays.asList(before));ledger.set("after",Arrays.asList(after));
        RealmShards.atomicSave(ledger,path);pending.add(id);
        try{
            player.getInventory().setStorageContents(after);player.saveData();
            ledger.set("status","COMPLETE");RealmShards.atomicSave(ledger,path);pending.remove(id);
        }catch(Exception failure){
            try{player.getInventory().setStorageContents(before);player.saveData();ledger.set("status","ABORTED");RealmShards.atomicSave(ledger,path);pending.remove(id);}
            catch(Exception rollback){failure.addSuppressed(rollback);throw new IOException("Core forge needs manual transaction review; no automatic second reward will be issued",failure);}
            throw new IOException("Core forge cancelled; your original inventory was restored",failure);
        }
        // Completed ledgers recover the journal if saving discovery data fails.
        try{remember(id,recipe,true);}catch(IOException failure){player.sendMessage("§eYour Shard is ready. Its journal entry is awaiting recovery.");}
    }
    void recover(Player player)throws IOException {
        UUID id=player.getUniqueId();if(!pending.contains(id))throw new IllegalStateException("You have no interrupted Core forge.");
        try(var files=Files.list(transactions)){
            for(Path path:files.filter(p->p.toString().endsWith(".yml")).toList()){
                YamlConfiguration ledger=new YamlConfiguration();try{ledger.load(path.toFile());}catch(Exception invalid){throw new IOException("Cannot inspect forge ledger",invalid);}
                if(!id.toString().equals(ledger.getString("player"))||!"PREPARED".equals(ledger.getString("status")))continue;
                ItemStack[] current=player.getInventory().getStorageContents();
                if(matches(current,ledger.getList("after"))){ledger.set("status","COMPLETE");RealmShards.atomicSave(ledger,path);if(ledger.getLong("discovery-generation")==generation(id))remember(id,CoreRecipe.decode(ledger.getString("recipe")),true);}
                else if(matches(current,ledger.getList("before"))){ledger.set("status","ABORTED");RealmShards.atomicSave(ledger,path);}
                else throw new IOException("Inventory no longer matches either saved state; staff must inspect the Core transaction. Nothing was issued or removed.");
            }
        }
        pending.remove(id);player.sendMessage("§bSaved inventory state verified. Your Core forge is unlocked; no additional items were issued.");
    }
    private static boolean matches(ItemStack[] inventory,List<?> saved){
        if(saved==null||saved.size()!=inventory.length)return false;
        for(int i=0;i<inventory.length;i++)if(!Objects.equals(inventory[i],saved.get(i)))return false;
        return true;
    }
    private void save()throws IOException {RealmShards.atomicSave(data,file);}
}
