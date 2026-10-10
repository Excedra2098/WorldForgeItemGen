package dev.excedra.itemgen;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.inventory.*;

public final class RealmweaverVerification {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    /** Avoids server item factories while retaining the real inventory selection logic. */
    private static final class Stack extends ItemStack {
        final String identity;
        int count;
        Stack(String identity,int count){this.identity=identity;this.count=count;}
        public int getAmount(){return count;}
        public void setAmount(int count){this.count=count;}
        public boolean isSimilar(ItemStack other){return other instanceof Stack stack&&identity.equals(stack.identity);}
        public Stack clone(){return new Stack(identity,count);}
    }
    public static void main(String[] args) {
        Map<Integer,ItemStack> slots=new HashMap<>();List<Integer> writes=new ArrayList<>();
        PlayerInventory inventory=(PlayerInventory)Proxy.newProxyInstance(RealmweaverVerification.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(proxy,method,arguments)->{
            if(method.getName().equals("getItem"))return slots.get((Integer)arguments[0]);
            if(method.getName().equals("setItem")){int slot=(Integer)arguments[0];writes.add(slot);slots.put(slot,(ItemStack)arguments[1]);return null;}
            throw new AssertionError("Unexpected inventory access: "+method.getName());
        });
        Stack wand=new Stack("wand",1),source=new Stack("recipe-a",3),expected=new Stack("recipe-a",1);
        slots.put(0,wand);slots.put(17,source);
        SelectedShardInventory.require(inventory,17,expected);
        check(writes.isEmpty()&&source.getAmount()==3,"Selection must consume nothing");
        SelectedShardInventory.consume(inventory,17,expected);
        check(slots.get(17).getAmount()==2,"Confirmation consumes exactly one selected Shard");
        check(source.getAmount()==3,"Reservation must not mutate the source snapshot");
        check(slots.get(0)==wand&&writes.equals(List.of(17)),"Wand and unrelated slots must remain untouched");
        slots.put(17,new Stack("recipe-b",2));
        reject(()->SelectedShardInventory.consume(inventory,17,expected));
        check(writes.size()==1&&slots.get(17).getAmount()==2,"Changed metadata cannot consume a different Shard");
        slots.remove(17);slots.put(18,source);
        reject(()->SelectedShardInventory.consume(inventory,17,expected));
        check(slots.get(18)==source,"Moved Shards must not be consumed from a new slot");
        for(int slot:new int[]{-1,36,40})reject(()->SelectedShardInventory.consume(inventory,slot,expected));
        slots.put(17,new Stack("recipe-a",1));SelectedShardInventory.consume(inventory,17,expected);
        check(slots.get(17)==null,"The last Shard clears only its selected slot");
        reject(()->SelectedShardInventory.consume(inventory,17,expected));
        for(var land:ShardSpec.Land.values()){
            TerrainPlan original=new TerrainPlan(7,land,87342,2),rotated=original;
            for(int turn=0;turn<4;turn++)rotated=rotated.rotated(PlacementRotation.CLOCKWISE_90);
            check(original.blocks.equals(rotated.blocks),"Four rotations preserve actual terrain: "+land);
            check(original.surface.equals(rotated.surface),"Four rotations preserve preview surfaces: "+land);
            check(original.seed==rotated.seed&&original.minY()==rotated.minY()&&original.maxY()==rotated.maxY(),"Rotation preserves pattern identity and height bounds");
        }
        System.out.println("Realmweaver verification passed: "+checks+" checks");
    }
    private static void reject(Runnable action){boolean rejected=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){rejected=true;}check(rejected,"Invalid selection must fail before inventory mutation");}
}
