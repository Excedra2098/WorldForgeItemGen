package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Calculates an exchange before touching a player's inventory. */
final class CoreInventoryExchange {
    static ItemStack[] plan(ItemStack[] inventory,Map<Material,Integer> costs,ItemStack reward){
        ItemStack[] result=new ItemStack[inventory.length];
        for(int i=0;i<inventory.length;i++)result[i]=inventory[i]==null?null:inventory[i].clone();
        for(var cost:costs.entrySet()){
            int remaining=cost.getValue();
            if(remaining<=0)throw new IllegalArgumentException("Invalid recipe cost");
            for(int slot=0;slot<result.length&&remaining>0;slot++){
                ItemStack item=result[slot];
                // Named, enchanted, tagged and plugin-authored items are never ingredients.
                if(item==null||item.getType()!=cost.getKey()||item.hasItemMeta())continue;
                int used=Math.min(remaining,item.getAmount());remaining-=used;
                item.setAmount(item.getAmount()-used);if(item.getAmount()==0)result[slot]=null;
            }
            if(remaining>0)throw new IllegalStateException("Missing "+remaining+" "+cost.getKey().name().toLowerCase(Locale.ROOT).replace('_',' '));
        }
        for(int slot=0;slot<result.length;slot++)if(result[slot]==null||result[slot].getType()==Material.AIR||result[slot].getType()==Material.CAVE_AIR||result[slot].getType()==Material.VOID_AIR){
            result[slot]=reward.clone();return result;
        }
        throw new IllegalStateException("Free a storage slot for the finished Shard.");
    }
}
