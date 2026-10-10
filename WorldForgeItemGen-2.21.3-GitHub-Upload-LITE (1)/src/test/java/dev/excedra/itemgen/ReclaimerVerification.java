package dev.excedra.itemgen;
import java.nio.file.*;
import java.util.*;

final class ReclaimerVerification {
 static void run(Path folder)throws Exception{
  UUID activator=UUID.randomUUID(),other=UUID.randomUUID();Path ledgerFile=folder.resolve("refunds.yml");ReclaimLedger ledger=new ReclaimLedger(ledgerFile);
  String exact=Base64.getEncoder().encodeToString(new StarterRecipe(List.of(3L,7L,1L,0L,0L,0L,0L,0L,2L),new UUID(52,83),Map.of("snow_t5_rainbow",12L)).encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  Verification.check(ledger.queue("placement-a",activator,exact),"First reclamation queues one receipt");
  Verification.check(!ledger.queue("placement-a",activator,exact),"Repeated reclamation cannot queue duplicates");
  ledger=new ReclaimLedger(ledgerFile);Verification.check(ledger.pending(other).isEmpty()&&ledger.pending(activator).equals(List.of("placement-a")),"Durable refund belongs to activating player only");
  var saved=ledger.begin("placement-a");Verification.check(saved.shard().equals(exact),"Original refund payload survives queue/restart unchanged");
  ReclaimLedger interrupted=new ReclaimLedger(ledgerFile);Verification.check(interrupted.pending(activator).isEmpty()&&interrupted.entries.get("placement-a").state().equals("ISSUING"),"Interrupted issue requires review rather than issuing twice");
  ledger.complete("placement-a");ledger=new ReclaimLedger(ledgerFile);Verification.check(ledger.pending(activator).isEmpty()&&!ledger.queue("placement-a",activator,exact),"Completed delivery stays non-repeatable after restart");
  boolean rejected=false;try{ledger.queue("placement-a",other,exact);}catch(IllegalStateException ex){rejected=true;}Verification.check(rejected,"Cannot redirect an existing refund to another player");
  ledger.queue("full-inventory",activator,exact);Verification.check(new ReclaimLedger(ledgerFile).pending(activator).equals(List.of("full-inventory")),"Undelivered inventory-full reward remains pending across restart");
  Path broken=folder.resolve("broken.yml");Files.createDirectories(broken);ReclaimLedger fail=new ReclaimLedger(broken);boolean failed=false;try{fail.queue("failed",activator,exact);}catch(java.io.IOException ex){failed=true;}Verification.check(failed&&fail.pending(activator).isEmpty(),"Failed persistence does not leave a phantom queued reward");
  StarterRecipe recipe=new StarterRecipe(Collections.nCopies(9,1L),new UUID(14,80));TerrainPlan plan=StarterRealms.plan(recipe);List<GeneratedLand.Cell> cells=new ArrayList<>();
  for(var e:plan.blocks.entrySet())cells.add(new GeneratedLand.Cell(e.getKey(),"minecraft:air","minecraft:"+e.getValue().name().toLowerCase(Locale.ROOT)));
  var full=ReclaimMesh.build(cells,Integer.MAX_VALUE);Map<TerrainPlan.Pos,String> expected=new HashMap<>();cells.stream().filter(c->DissolvePattern.visual(c.expected())).forEach(c->expected.put(c.pos(),c.expected()));Set<TerrainPlan.Pos> seen=new HashSet<>();
  for(var box:full)for(int x=0;x<box.width();x++)for(int y=0;y<box.height();y++)for(int z=0;z<box.depth();z++){var pos=new TerrainPlan.Pos(box.x()+x,box.y()+y,box.z()+z);Verification.check(seen.add(pos)&&box.data().equals(expected.get(pos)),"Shrinking proxy cuboids contain only unique recorded blocks with exact materials");}
  Verification.check(seen.equals(expected.keySet()),"Uncapped greedy mesh reconstructs the entire recorded realm");var capped=ReclaimMesh.build(cells,128);Verification.check(capped.size()<=128&&new HashSet<>(capped).size()==capped.size(),"Complex realm mesh stays bounded without duplicate visuals");
  double previous=2;for(int tick=0;tick<=80;tick++){double scale=ReclaimMesh.shrink(tick);Verification.check(Double.isFinite(scale)&&scale>0&&scale<=previous,"Realm contracts continuously without inverted geometry");previous=scale;}Verification.check(Math.abs(previous-.015)<.00001,"Contracted realm reaches shard-sized endpoint");
  for(int tick=0;tick<=104;tick++)for(int i=0;i<3;i++){var merge=WandOrbit.merge(tick,i);var split=WandOrbit.unmerge(tick,i);Verification.check(Double.isFinite(merge.x()+merge.y()+merge.z()+split.x()+split.y()+split.z()),"Convergence and unmerge trajectories stay finite");}
  for(int i=0;i<3;i++){var finalMerge=WandOrbit.merge(72,i);Verification.check(Math.hypot(finalMerge.x(),finalMerge.z())<.000001,"All three wands meet at one merge point");var split=WandOrbit.unmerge(28,i);Verification.check(Math.abs(Math.hypot(split.x(),split.z())-1.25)<.000001,"Unmerge restores three separate wands around the core");}
  var api=Class.forName("dev.excedra.itemgen.api.RealmWandVisuals");Verification.check(api.isInterface()&&java.lang.reflect.Modifier.isPublic(api.getModifiers()),"WFE integration is a public cross-plugin service");
  for(int i=0;i<3;i++){double previousRadius=1;for(int tick=0;tick<=WandAbsorption.DURATION;tick++){var p=WandAbsorption.pose(tick,i);Verification.check(Double.isFinite(p.x()+p.y()+p.z()+p.scale())&&p.scale()>=0&&p.scale()<=.5,"Absorption stays finite and wand scale stays valid");double radius=Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z());if(tick>=22+i*18)Verification.check(radius<=previousRadius+1e-9,"Absorbed wand moves toward seed");previousRadius=radius;}Verification.check(WandAbsorption.pose(40+i*18,i).scale()==0,"Each source fully absorbed before formation");}
  Verification.check(WandAbsorption.pose(39,1).absorbed()==0&&WandAbsorption.pose(40,0).absorbed()==1,"Sources are absorbed successively, not all at once");
  System.out.println("PASS: Durable activator-only refunds, restart/duplicate/crash guards; "+full.size()+" exact shrinking cuboids from "+cells.size()+" recorded blocks; bounded proxies and merge/unmerge endpoints.");
 }
}
