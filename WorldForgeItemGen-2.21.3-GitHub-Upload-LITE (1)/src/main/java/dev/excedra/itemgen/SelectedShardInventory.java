package dev.excedra.itemgen;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Exact-slot selection checks shared by placement and its inventory verification. */
final class SelectedShardInventory {
    static ItemStack require(PlayerInventory inventory,int slot,ItemStack expected) {
        if(slot<0||slot>=36)throw new IllegalArgumentException("Select a storage inventory slot.");
        ItemStack current=inventory.getItem(slot);
        if(current==null||current.getAmount()<1||!current.isSimilar(expected))
            throw new IllegalStateException("Your selected shard moved or changed; select it again.");
        return current;
    }
    static void consume(PlayerInventory inventory,int slot,ItemStack expected) {
        ItemStack current=require(inventory,slot,expected);
        ItemStack remainder=current.clone();remainder.setAmount(current.getAmount()-1);
        inventory.setItem(slot,remainder.getAmount()==0?null:remainder);
    }
}
