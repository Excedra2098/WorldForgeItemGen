package dev.excedra.itemgen.api;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** WFE owns commands, permissions and inventory transactions. These calls create items/play visuals only. */
public interface RealmWandVisuals {
    enum Kind { SHARDBREAKER, NULLIFIER, RECLAIMER, CONVERGENCE }
    ItemStack item(Kind kind);
    Kind kind(ItemStack item);
    ItemStack mergedItem(List<ItemStack> originals);
    List<ItemStack> originalItems(ItemStack merged);
    CompletableFuture<Boolean> playMerge(Player player,List<ItemStack> originals,ItemStack merged);
    CompletableFuture<Boolean> playUnmerge(Player player,ItemStack merged,List<ItemStack> originals);
    boolean busy(Player player);
}
