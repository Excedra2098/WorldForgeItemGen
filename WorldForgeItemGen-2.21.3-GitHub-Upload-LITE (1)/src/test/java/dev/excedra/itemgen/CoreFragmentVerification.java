package dev.excedra.itemgen;
/** Verifies phase boundaries, bounded motion and fragment cleanup before custody flight. */
public final class CoreFragmentVerification {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  check(CoreManifestation.crystalScale(0,false)>0,"Placement begins with a visible hand crystal");
  check(CoreManifestation.crystalScale(CoreManifestation.LAUNCH_DURATION,false)==.8f,"Whole crystal reaches the center before breakup");
  check(CoreManifestation.crystalScale(76,false)==0,"Flying crystal completely fragments");
  check(CoreManifestation.crystalScale(0,true)==1,"Reclaim begins with the full equipped crystal");
  check(CoreManifestation.crystalScale(64,true)==0&&CoreManifestation.crystalScale(112,true)==0,"Reclaim has a distinct fragment-only phase");
  check(CoreManifestation.crystalScale(CoreManifestation.SEQUENCE_DURATION,true)==.8f,"Reassembled crystal matches return-flight departure scale");
  check(CoreManifestation.crystalScale(CoreManifestation.SEQUENCE_DURATION,false)==1,"Placement finishes at permanent crystal scale");
  for(boolean reclaim:new boolean[]{false,true})for(int tick=0;tick<=CoreManifestation.SEQUENCE_DURATION;tick++){
   float scale=CoreManifestation.crystalScale(tick,reclaim);check(Float.isFinite(scale)&&scale>=0&&scale<=1,"Whole crystal scale stays valid");
   int terrain=CoreManifestation.terrainTick(tick,reclaim);check(terrain>=0&&terrain<=CoreManifestation.DURATION,"Terrain animation remains within its validated range");
   for(int i=0;i<CoreManifestation.FRAGMENT_COUNT;i++){
    var frame=CoreManifestation.fragment(i,tick,reclaim);
    check(Float.isFinite(frame.x()+frame.y()+frame.z()+frame.scale())&&frame.scale()>=0&&frame.scale()<=.08f,"Tiny shards have bounded finite dimensions");
    check(Math.abs(frame.x())<3&&Math.abs(frame.z())<3&&Math.abs(frame.y())<2,"Shard cloud stays within Core visual space");
    if(tick==0||tick==CoreManifestation.SEQUENCE_DURATION)check(frame.scale()==0,"No fragment survives either endpoint");
    if(!reclaim&&tick<CoreManifestation.LAUNCH_DURATION)check(frame.scale()==0,"No shard appears before the whole crystal arrives");
    if(reclaim&&tick==CoreManifestation.SEQUENCE_DURATION)check(frame.x()==0&&frame.y()==0&&frame.z()==0,"Reclaimed shards converge exactly at the return crystal");
   }
  }
  check(CoreManifestation.terrainTick(75,false)==0,"Pedestal waits until crystal fragmentation finishes");
  check(CoreManifestation.terrainTick(CoreManifestation.SEQUENCE_DURATION,false)==CoreManifestation.DURATION,"Pedestal assembly completes before terrain commit");
  System.out.println("Core fragment verification passed: "+checks+" checks.");
 }
}
