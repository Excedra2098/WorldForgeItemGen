/*

 * Decompiled with CFR 0.152.

 * 

 * Could not load the following classes:

 *  org.bukkit.Bukkit

 *  org.bukkit.ChatColor

 *  org.bukkit.Location

 *  org.bukkit.Material

 *  org.bukkit.NamespacedKey

 *  org.bukkit.Particle

 *  org.bukkit.command.Command

 *  org.bukkit.command.CommandExecutor

 *  org.bukkit.command.CommandSender

 *  org.bukkit.command.PluginCommand

 *  org.bukkit.command.TabCompleter

 *  org.bukkit.entity.HumanEntity

 *  org.bukkit.entity.Player

 *  org.bukkit.event.EventHandler

 *  org.bukkit.event.EventPriority

 *  org.bukkit.event.Listener

 *  org.bukkit.event.block.Action

 *  org.bukkit.event.inventory.InventoryClickEvent

 *  org.bukkit.event.inventory.InventoryCloseEvent

 *  org.bukkit.event.inventory.InventoryDragEvent

 *  org.bukkit.event.player.PlayerChatEvent

 *  org.bukkit.event.player.PlayerInteractEvent

 *  org.bukkit.event.player.PlayerQuitEvent

 *  org.bukkit.inventory.EquipmentSlot

 *  org.bukkit.inventory.Inventory

 *  org.bukkit.inventory.InventoryHolder

 *  org.bukkit.inventory.ItemFlag

 *  org.bukkit.inventory.ItemStack

 *  org.bukkit.inventory.PlayerInventory

 *  org.bukkit.inventory.meta.ItemMeta

 *  org.bukkit.persistence.PersistentDataType

 *  org.bukkit.plugin.Plugin

 *  org.bukkit.plugin.java.JavaPlugin

 */

package dev.excedra.itemgen;



import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.LinkOption;

import java.nio.file.Path;

import java.nio.file.StandardOpenOption;

import java.nio.file.attribute.FileAttribute;

import java.util.ArrayList;

import java.util.Arrays;

import java.util.Collections;

import java.util.HashMap;

import java.util.HashSet;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Locale;

import java.util.Map;

import java.util.Set;

import java.util.UUID;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;

import org.bukkit.Bukkit;

import org.bukkit.ChatColor;

import org.bukkit.Location;

import org.bukkit.Material;

import org.bukkit.NamespacedKey;

import org.bukkit.Particle;

import org.bukkit.command.Command;

import org.bukkit.command.CommandExecutor;

import org.bukkit.command.CommandSender;

import org.bukkit.command.PluginCommand;

import org.bukkit.command.TabCompleter;

import org.bukkit.entity.HumanEntity;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;

import org.bukkit.event.EventPriority;

import org.bukkit.event.Listener;

import org.bukkit.event.block.Action;

import org.bukkit.event.inventory.InventoryClickEvent;

import org.bukkit.event.inventory.InventoryCloseEvent;

import org.bukkit.event.inventory.InventoryDragEvent;

import org.bukkit.event.player.PlayerChatEvent;

import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.event.player.PlayerQuitEvent;

import org.bukkit.inventory.EquipmentSlot;

import org.bukkit.inventory.Inventory;

import org.bukkit.inventory.InventoryHolder;

import org.bukkit.inventory.ItemFlag;

import org.bukkit.inventory.ItemStack;

import org.bukkit.inventory.PlayerInventory;

import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.PersistentDataType;

import org.bukkit.plugin.Plugin;

import org.bukkit.plugin.java.JavaPlugin;



public final class WorldForgeItemGen

extends JavaPlugin

implements CommandExecutor,

TabCompleter,

Listener {

    private final Map<String, ItemDefinition> items = new LinkedHashMap<String, ItemDefinition>();

    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<UUID, PendingInput>();

    private final Map<UUID, String> editing = new HashMap<UUID, String>();

    private final Map<UUID, MenuType> activeMenus = new HashMap<UUID, MenuType>();

    private final Map<String, Long> cooldowns = new HashMap<String, Long>();

    private final Set<String> activeAnimations = new HashSet<String>();

    private NamespacedKey itemIdKey;

    private Path itemsFile;

    private static final String BROWSER_TITLE = "\u00a75\u00a7lWorldForge Item Generator";

    private static final String EDITOR_TITLE = "\u00a75\u00a7lWorldForge Item Editor";

    private static final String ABILITY_TITLE = "\u00a75\u00a7lWorldForge Ability Editor";

    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "MYTHIC", "LEGENDARY", "FORGED", "CUSTOM");



    private RealmShards realmShards;

    public void onEnable() {

        this.itemIdKey = new NamespacedKey((Plugin)this, "custom_item_id");

        this.itemsFile = this.getDataFolder().toPath().resolve("items.yml");

        try {

            Files.createDirectories(this.getDataFolder().toPath(), new FileAttribute[0]);

            if (!Files.exists(this.itemsFile, new LinkOption[0])) {

                this.writeDefaultFile();

            }

            this.loadItems();

        }

        catch (Exception exception) {

            this.getLogger().log(Level.SEVERE, "Could not initialize items.yml", exception);

        }

        PluginCommand pluginCommand = this.getCommand("itemgen");

        if (pluginCommand != null) {

            pluginCommand.setExecutor((CommandExecutor)this);

            pluginCommand.setTabCompleter((TabCompleter)this);

        }

        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);

        realmShards = new RealmShards(this);

        realmShards.start();

        this.getLogger().info("WorldForge ItemGen v" + this.getPluginMeta().getVersion() + " enabled with " + this.items.size() + " custom item(s).");

    }



    public void onDisable() {

        if (realmShards != null) realmShards.stop();

        try {

            this.saveItems();

        }

        catch (Exception exception) {

            this.getLogger().log(Level.WARNING, "Could not save items.yml", exception);

        }

    }



    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {

        if (!commandSender.hasPermission("worldforge.itemgen.admin")) {

            commandSender.sendMessage("\u00a7cYou do not have permission to use WorldForge ItemGen.");

            return true;

        }

        if (stringArray.length == 0) {

            if (commandSender instanceof Player) {

                Player player = (Player)commandSender;

                this.openBrowser(player);

            } else {

                this.help(commandSender);

            }

            return true;

        }

        String string2 = stringArray[0].toLowerCase(Locale.ROOT);

        try {

            switch (string2) {

                case "help": {

                    this.help(commandSender);

                    break;

                }

                case "list": {

                    this.list(commandSender);

                    break;

                }

                case "reload": {

                    this.loadItems();

                    commandSender.sendMessage("\u00a7aWorldForge ItemGen reloaded \u00a7f" + this.items.size() + "\u00a7a item(s).");

                    break;

                }

                case "create": {

                    this.create(commandSender, stringArray);

                    break;

                }

                case "delete": {

                    this.delete(commandSender, stringArray);

                    break;

                }

                case "edit": {

                    this.edit(commandSender, stringArray);

                    break;

                }

                case "name": {

                    this.name(commandSender, stringArray);

                    break;

                }

                case "material": {

                    this.material(commandSender, stringArray);

                    break;

                }

                case "rarity": {

                    this.rarity(commandSender, stringArray);

                    break;

                }

                case "lore": {

                    this.lore(commandSender, stringArray);

                    break;

                }

                case "model": {

                    this.model(commandSender, stringArray);

                    break;

                }

                case "unbreakable": {

                    this.unbreakable(commandSender, stringArray);

                    break;

                }

                case "give": {

                    this.give(commandSender, stringArray);

                    break;

                }

                default: {

                    commandSender.sendMessage("\u00a7cUnknown subcommand. Use \u00a7f/itemgen help\u00a7c.");

                    break;

                }

            }

        }

        catch (Exception exception) {

            commandSender.sendMessage("\u00a7cItemGen error: \u00a7f" + exception.getMessage());

            this.getLogger().log(Level.WARNING, "Command failed", exception);

        }

        return true;

    }



    private void create(CommandSender commandSender, String[] stringArray) throws IOException {

        if (stringArray.length < 3) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen create <id> <material>");

            return;

        }

        String string = WorldForgeItemGen.cleanId(stringArray[1]);

        Material material = Material.matchMaterial((String)stringArray[2]);

        if (string.isBlank()) {

            commandSender.sendMessage("\u00a7cID must contain letters, numbers, _ or -.");

            return;

        }

        if (this.items.containsKey(string)) {

            commandSender.sendMessage("\u00a7cAn item with that ID already exists.");

            return;

        }

        if (material == null || !material.isItem()) {

            commandSender.sendMessage("\u00a7cUnknown/invalid item material: \u00a7f" + stringArray[2]);

            return;

        }

        ItemDefinition itemDefinition = new ItemDefinition(string, material.name());

        itemDefinition.name = "&5&l" + WorldForgeItemGen.prettify(string);

        itemDefinition.rarity = "CUSTOM";

        itemDefinition.lore.add("&7Created by WorldForge ItemGen");

        this.items.put(string, itemDefinition);

        this.saveItems();

        commandSender.sendMessage("\u00a7aCreated \u00a7f" + string + " \u00a7ausing \u00a7f" + material.name() + "\u00a7a.");

        if (commandSender instanceof Player) {

            Player player = (Player)commandSender;

            this.openEditor(player, string);

        }

    }



    private void delete(CommandSender commandSender, String[] stringArray) throws IOException {

        if (stringArray.length < 2) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen delete <id>");

            return;

        }

        ItemDefinition itemDefinition = this.items.remove(WorldForgeItemGen.cleanId(stringArray[1]));

        if (itemDefinition == null) {

            commandSender.sendMessage("\u00a7cNo such custom item.");

            return;

        }

        this.saveItems();

        commandSender.sendMessage("\u00a7aDeleted \u00a7f" + itemDefinition.id + "\u00a7a.");

    }



    private void edit(CommandSender commandSender, String[] stringArray) {

        if (!(commandSender instanceof Player)) {

            commandSender.sendMessage("\u00a7cOnly players can use the GUI editor.");

            return;

        }

        Player player = (Player)commandSender;

        if (stringArray.length < 2) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen edit <id>");

            return;

        }

        ItemDefinition itemDefinition = this.require(stringArray[1], commandSender);

        if (itemDefinition == null) {

            return;

        }

        this.openEditor(player, itemDefinition.id);

    }



    private void name(CommandSender commandSender, String[] stringArray) throws IOException {

        if (stringArray.length < 3) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen name <id> <name...>");

            return;

        }

        ItemDefinition itemDefinition = this.require(stringArray[1], commandSender);

        if (itemDefinition == null) {

            return;

        }

        itemDefinition.name = WorldForgeItemGen.join(stringArray, 2);

        this.saveItems();

        commandSender.sendMessage("\u00a7aName updated: " + WorldForgeItemGen.color(itemDefinition.name));

    }



    private void material(CommandSender commandSender, String[] stringArray) throws IOException {

        if (stringArray.length < 3) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen material <id> <material>");

            return;

        }

        ItemDefinition itemDefinition = this.require(stringArray[1], commandSender);

        if (itemDefinition == null) {

            return;

        }

        Material material = Material.matchMaterial((String)stringArray[2]);

        if (material == null || !material.isItem()) {

            commandSender.sendMessage("\u00a7cUnknown/invalid material.");

            return;

        }

        itemDefinition.material = material.name();

        this.saveItems();

        commandSender.sendMessage("\u00a7aMaterial set to \u00a7f" + itemDefinition.material + "\u00a7a.");

    }



    private void rarity(CommandSender commandSender, String[] stringArray) throws IOException {

        if (stringArray.length < 3) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen rarity <id> <rarity>");

            return;

        }

        ItemDefinition itemDefinition = this.require(stringArray[1], commandSender);

        if (itemDefinition == null) {

            return;

        }

        itemDefinition.rarity = stringArray[2].toUpperCase(Locale.ROOT);

        this.saveItems();

        commandSender.sendMessage("\u00a7aRarity set to \u00a7f" + itemDefinition.rarity + "\u00a7a.");

    }



    private void lore(CommandSender commandSender, String[] stringArray) throws IOException {

        if (stringArray.length < 3) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen lore <id> <add|clear> [text...]");

            return;

        }

        ItemDefinition itemDefinition = this.require(stringArray[1], commandSender);

        if (itemDefinition == null) {

            return;

        }

        if (stringArray[2].equalsIgnoreCase("clear")) {

            itemDefinition.lore.clear();

            this.saveItems();

            commandSender.sendMessage("\u00a7aLore cleared.");

            return;

        }

        if (!stringArray[2].equalsIgnoreCase("add") || stringArray.length < 4) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen lore <id> add <text...>");

            return;

        }

        itemDefinition.lore.add(WorldForgeItemGen.join(stringArray, 3));

        this.saveItems();

        commandSender.sendMessage("\u00a7aLore line added.");

    }



    private void model(CommandSender commandSender, String[] stringArray) throws IOException {

        if (stringArray.length < 3) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen model <id> <number|none>");

            return;

        }

        ItemDefinition itemDefinition = this.require(stringArray[1], commandSender);

        if (itemDefinition == null) {

            return;

        }

        itemDefinition.customModelData = stringArray[2].equalsIgnoreCase("none") ? null : Integer.valueOf(Integer.parseInt(stringArray[2]));

        this.saveItems();

        commandSender.sendMessage("\u00a7aCustom model data updated.");

    }



    private void unbreakable(CommandSender commandSender, String[] stringArray) throws IOException {

        if (stringArray.length < 3) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen unbreakable <id> <true|false>");

            return;

        }

        ItemDefinition itemDefinition = this.require(stringArray[1], commandSender);

        if (itemDefinition == null) {

            return;

        }

        itemDefinition.unbreakable = Boolean.parseBoolean(stringArray[2]);

        this.saveItems();

        commandSender.sendMessage("\u00a7aUnbreakable: \u00a7f" + itemDefinition.unbreakable);

    }



    private void give(CommandSender commandSender, String[] stringArray) {

        Player player;

        if (stringArray.length < 2) {

            commandSender.sendMessage("\u00a7eUsage: /itemgen give <id> [player] [amount]");

            return;

        }

        ItemDefinition itemDefinition = this.require(stringArray[1], commandSender);

        if (itemDefinition == null) {

            return;

        }

        if (stringArray.length >= 3) {

            player = Bukkit.getPlayer((String)stringArray[2]);

        } else if (commandSender instanceof Player) {

            Player player2;

            player = player2 = (Player)commandSender;

        } else {

            commandSender.sendMessage("\u00a7cConsole must specify a player.");

            return;

        }

        if (player == null) {

            commandSender.sendMessage("\u00a7cPlayer not found.");

            return;

        }

        int n = stringArray.length >= 4 ? Math.max(1, Math.min(64, Integer.parseInt(stringArray[3]))) : 1;

        this.giveSafely(player, this.build(itemDefinition), n);

        commandSender.sendMessage("\u00a7aGave \u00a7f" + n + "x " + itemDefinition.id + " \u00a7ato \u00a7f" + player.getName() + "\u00a7a.");

    }



    private void giveSafely(Player player, ItemStack itemStack, int n) {

        int n2;

        for (int i = n; i > 0; i -= n2) {

            n2 = Math.min(itemStack.getMaxStackSize(), i);

            ItemStack itemStack2 = itemStack.clone();

            itemStack2.setAmount(n2);

            PlayerInventory playerInventory = player.getInventory();

            int n3 = playerInventory.firstEmpty();

            if (n3 >= 0) {

                playerInventory.setItem(n3, itemStack2);

                continue;

            }

            player.sendMessage("\u00a7cYour inventory is full; could not give the remaining item(s).");

            break;

        }

    }



    private ItemDefinition require(String string, CommandSender commandSender) {

        ItemDefinition itemDefinition = this.items.get(WorldForgeItemGen.cleanId(string));

        if (itemDefinition == null) {

            commandSender.sendMessage("\u00a7cNo item found with ID \u00a7f" + string + "\u00a7c.");

        }

        return itemDefinition;

    }



    private ItemStack build(ItemDefinition itemDefinition) {

        ItemStack itemStack;

        ItemMeta itemMeta;

        Material material = Material.matchMaterial((String)itemDefinition.material);

        if (material == null) {

            material = Material.PAPER;

        }

        if ((itemMeta = (itemStack = new ItemStack(material)).getItemMeta()) != null) {

            itemMeta.setDisplayName(WorldForgeItemGen.color(itemDefinition.name));

            ArrayList<String> arrayList = new ArrayList<String>();

            for (String string : itemDefinition.lore) {

                arrayList.add(WorldForgeItemGen.color(string));

            }

            if (!itemDefinition.rarity.isBlank()) {

                if (!arrayList.isEmpty()) {

                    arrayList.add("");

                }

                arrayList.add(WorldForgeItemGen.color("&8Rarity: " + this.rarityColor(itemDefinition.rarity) + itemDefinition.rarity));

                arrayList.add(WorldForgeItemGen.color("&8ID: &7" + itemDefinition.id));

            }

            if (itemDefinition.abilityEnabled) {

                arrayList.add("");

                arrayList.add(WorldForgeItemGen.color("&5Ability: &dRIGHT_CLICK"));

                arrayList.add(WorldForgeItemGen.color("&8Cooldown: &7" + itemDefinition.abilityCooldownSeconds + "s"));

            }

            itemMeta.setLore(arrayList);

            itemMeta.setUnbreakable(itemDefinition.unbreakable);

            if (itemDefinition.customModelData != null) {

                try {

                    itemMeta.setCustomModelData(itemDefinition.customModelData);

                }

                catch (Throwable throwable) {

                    // empty catch block

                }

            }

            itemMeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});

            itemMeta.getPersistentDataContainer().set(this.itemIdKey, PersistentDataType.STRING, itemDefinition.id);

            itemStack.setItemMeta(itemMeta);

        }

        return itemStack;

    }



    private void openBrowser(Player player) {

        Inventory inventory;

        this.editing.remove(player.getUniqueId());

        ItemGenMenuHolder itemGenMenuHolder = new ItemGenMenuHolder(MenuType.BROWSER, player.getUniqueId());

        itemGenMenuHolder.inventory = inventory = Bukkit.createInventory((InventoryHolder)itemGenMenuHolder, (int)54, (String)BROWSER_TITLE);

        int n = 0;

        for (ItemDefinition itemDefinition : this.items.values()) {

            if (n >= 45) break;

            ItemStack itemStack = this.build(itemDefinition);

            ItemMeta itemMeta = itemStack.getItemMeta();

            if (itemMeta != null) {

                ArrayList<String> arrayList = itemMeta.getLore() == null ? new ArrayList<String>() : new ArrayList(itemMeta.getLore());

                arrayList.add("");

                arrayList.add("\u00a7aLeft-click: \u00a77Give yourself one");

                arrayList.add("\u00a7dRight-click: \u00a77Edit this item");

                itemMeta.setLore(arrayList);

                itemStack.setItemMeta(itemMeta);

            }

            inventory.setItem(n++, itemStack);

        }

        inventory.setItem(49, this.guiItem(Material.NETHER_STAR, "\u00a7d\u00a7lCreate New Item", List.of("\u00a77Click and type the new item ID in chat.", "\u00a77You will then be asked for a material.")));

        inventory.setItem(53, this.guiItem(Material.BOOK, "\u00a7f\u00a7lHelp", List.of("\u00a77Left-click an item to generate it.", "\u00a77Right-click an item to open its editor.", "\u00a77Text editing uses private chat prompts.")));

        player.openInventory(inventory);

        this.activeMenus.put(player.getUniqueId(), MenuType.BROWSER);

    }



    private void openEditor(Player player, String string) {

        Inventory inventory;

        ItemDefinition itemDefinition = this.items.get(string);

        if (itemDefinition == null) {

            this.openBrowser(player);

            return;

        }

        this.editing.put(player.getUniqueId(), string);

        ItemGenMenuHolder itemGenMenuHolder = new ItemGenMenuHolder(MenuType.EDITOR, player.getUniqueId());

        itemGenMenuHolder.inventory = inventory = Bukkit.createInventory((InventoryHolder)itemGenMenuHolder, (int)54, (String)EDITOR_TITLE);

        inventory.setItem(4, this.build(itemDefinition));

        inventory.setItem(10, this.guiItem(Material.CRAFTING_TABLE, "\u00a7dMaterial", List.of("\u00a77Current: \u00a7f" + itemDefinition.material, "\u00a7eClick to type a material in chat")));

        inventory.setItem(12, this.guiItem(Material.NAME_TAG, "\u00a7dDisplay Name", List.of("\u00a77Current: " + WorldForgeItemGen.color(itemDefinition.name), "\u00a7eClick to edit in chat")));

        inventory.setItem(14, this.guiItem(Material.WRITABLE_BOOK, "\u00a7dLore", List.of("\u00a77Lines: \u00a7f" + itemDefinition.lore.size(), "\u00a7aLeft-click: \u00a77add a line", "\u00a7cShift-right-click: \u00a77clear all lore")));

        inventory.setItem(16, this.guiItem(Material.AMETHYST_SHARD, "\u00a7dRarity", List.of("\u00a77Current: " + WorldForgeItemGen.color(this.rarityColor(itemDefinition.rarity) + itemDefinition.rarity), "\u00a7aLeft-click: \u00a77next", "\u00a7cRight-click: \u00a77previous")));

        inventory.setItem(19, this.guiItem(Material.COMPARATOR, "\u00a7dCustom Model Data", List.of("\u00a77Current: \u00a7f" + String.valueOf(itemDefinition.customModelData == null ? "none" : itemDefinition.customModelData), "\u00a7aLeft-click: \u00a77+1", "\u00a7cRight-click: \u00a77-1", "\u00a7eShift-click: \u00a77type exact value")));

        inventory.setItem(21, this.guiItem(itemDefinition.unbreakable ? Material.BEDROCK : Material.IRON_PICKAXE, "\u00a7dUnbreakable", List.of("\u00a77Current: \u00a7f" + itemDefinition.unbreakable, "\u00a7eClick to toggle")));

        inventory.setItem(23, this.guiItem(itemDefinition.abilityEnabled ? Material.BLAZE_POWDER : Material.GUNPOWDER, "\u00a75\u00a7lAbilities", List.of("\u00a77RIGHT_CLICK ability: " + (itemDefinition.abilityEnabled ? "\u00a7aENABLED" : "\u00a7cDISABLED"), "\u00a7eClick to configure")));

        inventory.setItem(31, this.guiItem(Material.EMERALD, "\u00a7aGive Item", List.of("\u00a77Give yourself one copy.")));

        inventory.setItem(39, this.guiItem(Material.LIME_DYE, "\u00a7aSave", List.of("\u00a77Save changes to items.yml")));

        inventory.setItem(41, this.guiItem(Material.ARROW, "\u00a7fBack", List.of("\u00a77Return to item browser")));

        inventory.setItem(49, this.guiItem(Material.BARRIER, "\u00a7cDelete Item", List.of("\u00a7cShift-click to permanently delete", "\u00a7f" + itemDefinition.id)));

        player.openInventory(inventory);

        this.activeMenus.put(player.getUniqueId(), MenuType.EDITOR);

    }



    private void openAbilityEditor(Player player, String string) {

        Inventory inventory;

        ItemDefinition itemDefinition = this.items.get(string);

        if (itemDefinition == null) {

            this.openBrowser(player);

            return;

        }

        this.editing.put(player.getUniqueId(), string);

        ItemGenMenuHolder itemGenMenuHolder = new ItemGenMenuHolder(MenuType.ABILITY, player.getUniqueId());

        itemGenMenuHolder.inventory = inventory = Bukkit.createInventory((InventoryHolder)itemGenMenuHolder, (int)54, (String)ABILITY_TITLE);

        inventory.setItem(4, this.build(itemDefinition));

        inventory.setItem(10, this.guiItem(itemDefinition.abilityEnabled ? Material.LIME_DYE : Material.GRAY_DYE, "\u00a7dAbility Enabled", List.of("\u00a77Current: \u00a7f" + itemDefinition.abilityEnabled, "\u00a7eClick to toggle")));

        inventory.setItem(12, this.guiItem(Material.STICK, "\u00a7dTrigger", List.of("\u00a7fRIGHT_CLICK", "\u00a77Main hand only")));

        inventory.setItem(14, this.guiItem(itemDefinition.abilityConsume ? Material.FIRE_CHARGE : Material.SNOWBALL, "\u00a7dConsume Item", List.of("\u00a77Current: \u00a7f" + itemDefinition.abilityConsume, "\u00a7eClick to toggle")));

        inventory.setItem(16, this.guiItem(Material.CLOCK, "\u00a7dCooldown", List.of("\u00a77Current: \u00a7f" + itemDefinition.abilityCooldownSeconds + " seconds", "\u00a7aLeft-click: \u00a77+5s", "\u00a7cRight-click: \u00a77-5s", "\u00a7eShift-click: \u00a77type exact seconds")));

        inventory.setItem(20, this.guiItem(Material.NOTE_BLOCK, "\u00a7dSound", List.of("\u00a77Current: \u00a7f" + itemDefinition.abilitySound, "\u00a7eClick to edit", "\u00a77Example: minecraft:block.amethyst_block.break", "\u00a77Type none to disable")));

        inventory.setItem(22, this.guiItem(Material.FIREWORK_STAR, "\u00a7dParticle", List.of("\u00a77Current: \u00a7f" + itemDefinition.abilityParticle, "\u00a7eClick to edit", "\u00a77Example: PORTAL", "\u00a77Type none to disable")));

        inventory.setItem(24, this.guiItem(Material.COMMAND_BLOCK, "\u00a7dConsole Command", List.of("\u00a77Current: \u00a7f" + WorldForgeItemGen.shortText(itemDefinition.abilityCommand), "\u00a7eClick to edit", "\u00a77Runs AFTER the animation finishes", "\u00a77Placeholders: %player%, %item_id%", "\u00a77Type none to disable")));

        inventory.setItem(28, this.guiItem(Material.ECHO_SHARD, "\u00a75\u00a7lAbility Animation", List.of("\u00a77Current: \u00a7f" + itemDefinition.abilityAnimation, "\u00a7aLeft-click: \u00a77next preset", "\u00a7cRight-click: \u00a77previous preset", "\u00a78SHARD_FRACTURE adds a staged", "\u00a78WorldForge-style cracking sequence.")));

        inventory.setItem(30, this.guiItem(Material.PAPER, "\u00a7dPlayer Message", List.of("\u00a77Current: " + WorldForgeItemGen.color(WorldForgeItemGen.shortText(itemDefinition.abilityMessage)), "\u00a7eClick to edit", "\u00a77Shown when activation begins", "\u00a77Type none to disable")));

        inventory.setItem(32, this.guiItem(Material.AMETHYST_SHARD, "\u00a7dAnimation Duration", List.of("\u00a77Current: \u00a7f" + itemDefinition.abilityAnimationTicks + " ticks", "\u00a77~" + String.format(Locale.ROOT, "%.2f", (double)itemDefinition.abilityAnimationTicks / 20.0) + " seconds", "\u00a7aLeft-click: \u00a77+5 ticks", "\u00a7cRight-click: \u00a77-5 ticks", "\u00a7eShift-click: \u00a77type exact ticks")));

        inventory.setItem(40, this.guiItem(Material.ARROW, "\u00a7fBack to Item Editor", List.of("\u00a77Return without losing changes")));

        player.openInventory(inventory);

        this.activeMenus.put(player.getUniqueId(), MenuType.ABILITY);

    }



    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)

    public void onClick(InventoryClickEvent inventoryClickEvent) {

        boolean bl;

        Object object;

        HumanEntity humanEntity = inventoryClickEvent.getWhoClicked();

        if (!(humanEntity instanceof Player)) {

            return;

        }

        Player player = (Player)humanEntity;

        Inventory topInventory = inventoryClickEvent.getView().getTopInventory();

        InventoryHolder inventoryHolder = topInventory.getHolder();

        ItemGenMenuHolder itemGenMenuHolder = inventoryHolder instanceof ItemGenMenuHolder ? (ItemGenMenuHolder)inventoryHolder : null;

        object = this.activeMenus.get(player.getUniqueId());

        boolean bl2 = bl = itemGenMenuHolder != null && itemGenMenuHolder.playerId.equals(player.getUniqueId());

        if (!bl && object == null) {

            return;

        }

        inventoryClickEvent.setCancelled(true);

        Object object2 = bl ? itemGenMenuHolder.type : object;

        int n = inventoryClickEvent.getRawSlot();

        if (n < 0 || n >= topInventory.getSize()) {

            return;

        }

        boolean bl3 = inventoryClickEvent.isRightClick();

        boolean bl4 = inventoryClickEvent.isShiftClick();

        String string = object2 == MenuType.BROWSER ? this.getCustomId(inventoryClickEvent.getCurrentItem()) : null;

        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.handleDelayedClick(player, (MenuType)((Object)object2), n, bl3, bl4, string), 1L);

    }



    private void handleMenuClick(Player player, MenuType menuType, int n, boolean bl, boolean bl2, String string) {

        ItemDefinition itemDefinition;

        if (menuType == MenuType.BROWSER) {

            if (n == 49) {

                this.startInput(player, InputType.CREATE_ID, null, "\u00a7dType the new item ID in chat. \u00a77Type \u00a7fcancel \u00a77to stop.");

                return;

            }

            if (string == null) {

                return;

            }

            if (bl) {

                this.openEditor(player, string);

            } else {

                ItemDefinition itemDefinition2 = this.items.get(string);

                if (itemDefinition2 != null) {

                    this.giveSafely(player, this.build(itemDefinition2), 1);

                    player.sendMessage("\u00a7aGenerated \u00a7f" + string + "\u00a7a.");

                }

            }

            return;

        }

        String string2 = this.editing.get(player.getUniqueId());

        ItemDefinition itemDefinition3 = itemDefinition = string2 == null ? null : this.items.get(string2);

        if (itemDefinition == null) {

            this.openBrowser(player);

            return;

        }

        if (menuType == MenuType.EDITOR) {

            try {

                switch (n) {

                    case 10: {

                        this.startInput(player, InputType.MATERIAL, string2, "\u00a7dType a Minecraft material name. \u00a77Example: \u00a7fAMETHYST_SHARD");

                        break;

                    }

                    case 12: {

                        this.startInput(player, InputType.NAME, string2, "\u00a7dType the new display name in chat. \u00a77You can use & color codes.");

                        break;

                    }

                    case 14: {

                        if (bl2 && bl) {

                            itemDefinition.lore.clear();

                            this.saveItems();

                            player.sendMessage("\u00a7aLore cleared.");

                            this.openEditor(player, string2);

                            break;

                        }

                        this.startInput(player, InputType.LORE, string2, "\u00a7dType one lore line in chat. \u00a77Use & color codes.");

                        break;

                    }

                    case 16: {

                        this.cycleRarity(itemDefinition, bl ? -1 : 1);

                        this.saveItems();

                        this.openEditor(player, string2);

                        break;

                    }

                    case 19: {

                        if (bl2) {

                            this.startInput(player, InputType.MODEL, string2, "\u00a7dType the CustomModelData integer, or \u00a7fnone\u00a7d.");

                            break;

                        }

                        int n2 = itemDefinition.customModelData == null ? 0 : itemDefinition.customModelData;

                        itemDefinition.customModelData = Math.max(0, n2 + (bl ? -1 : 1));

                        this.saveItems();

                        this.openEditor(player, string2);

                        break;

                    }

                    case 21: {

                        itemDefinition.unbreakable = !itemDefinition.unbreakable;

                        this.saveItems();

                        this.openEditor(player, string2);

                        break;

                    }

                    case 23: {

                        this.openAbilityEditor(player, string2);

                        break;

                    }

                    case 31: {

                        this.giveSafely(player, this.build(itemDefinition), 1);

                        player.sendMessage("\u00a7aGenerated \u00a7f" + string2 + "\u00a7a.");

                        break;

                    }

                    case 39: {

                        this.saveItems();

                        player.sendMessage("\u00a7aSaved \u00a7f" + string2 + "\u00a7a to items.yml.");

                        this.openEditor(player, string2);

                        break;

                    }

                    case 41: {

                        this.openBrowser(player);

                        break;

                    }

                    case 49: {

                        if (!bl2) break;

                        this.items.remove(string2);

                        this.saveItems();

                        player.sendMessage("\u00a7cDeleted \u00a7f" + string2 + "\u00a7c.");

                        this.openBrowser(player);

                    }

                }

            }

            catch (Exception exception) {

                player.sendMessage("\u00a7cEditor error: \u00a7f" + exception.getMessage());

            }

            return;

        }

        if (menuType == MenuType.ABILITY) {

            try {

                switch (n) {

                    case 10: {

                        itemDefinition.abilityEnabled = !itemDefinition.abilityEnabled;

                        this.saveItems();

                        this.openAbilityEditor(player, string2);

                        break;

                    }

                    case 14: {

                        itemDefinition.abilityConsume = !itemDefinition.abilityConsume;

                        this.saveItems();

                        this.openAbilityEditor(player, string2);

                        break;

                    }

                    case 16: {

                        if (bl2) {

                            this.startInput(player, InputType.COOLDOWN, string2, "\u00a7dType the cooldown in whole seconds.");

                            break;

                        }

                        itemDefinition.abilityCooldownSeconds = Math.max(0, itemDefinition.abilityCooldownSeconds + (bl ? -5 : 5));

                        this.saveItems();

                        this.openAbilityEditor(player, string2);

                        break;

                    }

                    case 20: {

                        this.startInput(player, InputType.SOUND, string2, "\u00a7dType a sound key. \u00a77Example: \u00a7fminecraft:block.amethyst_block.break \u00a77or \u00a7fnone\u00a77.");

                        break;

                    }

                    case 22: {

                        this.startInput(player, InputType.PARTICLE, string2, "\u00a7dType a Particle enum name. \u00a77Example: \u00a7fPORTAL \u00a77or \u00a7fnone\u00a77.");

                        break;

                    }

                    case 24: {

                        this.startInput(player, InputType.COMMAND, string2, "\u00a7dType a console command without /. \u00a77Use %player% and %item_id%. Type \u00a7fnone \u00a77to clear.");

                        break;

                    }

                    case 28: {

                        this.cycleAnimation(itemDefinition, bl ? -1 : 1);

                        this.saveItems();

                        this.openAbilityEditor(player, string2);

                        break;

                    }

                    case 30: {

                        this.startInput(player, InputType.MESSAGE, string2, "\u00a7dType the message shown to the player. \u00a77Use & colors, or \u00a7fnone\u00a77.");

                        break;

                    }

                    case 32: {

                        if (bl2) {

                            this.startInput(player, InputType.ANIMATION_TICKS, string2, "\u00a7dType the animation duration in ticks. \u00a7720 ticks = 1 second.");

                            break;

                        }

                        itemDefinition.abilityAnimationTicks = Math.max(5, Math.min(100, itemDefinition.abilityAnimationTicks + (bl ? -5 : 5)));

                        this.saveItems();

                        this.openAbilityEditor(player, string2);

                        break;

                    }

                    case 40: {

                        this.openEditor(player, string2);

                    }

                }

            }

            catch (Exception exception) {

                player.sendMessage("\u00a7cAbility editor error: \u00a7f" + exception.getMessage());

            }

        }

    }



    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)

    public void onDrag(InventoryDragEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder=event.getView().getTopInventory().getHolder();

        if ((holder instanceof ItemGenMenuHolder menu && menu.playerId.equals(player.getUniqueId())) || activeMenus.containsKey(player.getUniqueId())) event.setCancelled(true);

    }

    @EventHandler(priority=EventPriority.MONITOR)

    public void onInventoryClose(InventoryCloseEvent inventoryCloseEvent) {

        HumanEntity humanEntity = inventoryCloseEvent.getPlayer();

        if (!(humanEntity instanceof Player)) {

            return;

        }

        Player player = (Player)humanEntity;

        InventoryHolder closeHolder = inventoryCloseEvent.getInventory().getHolder();

        if (closeHolder instanceof ItemGenMenuHolder || this.activeMenus.containsKey(player.getUniqueId())) {

            this.activeMenus.remove(player.getUniqueId());

        }

    }



    @EventHandler

    public void onQuit(PlayerQuitEvent playerQuitEvent) {

        UUID uUID = playerQuitEvent.getPlayer().getUniqueId();

        this.activeMenus.remove(uUID);

        this.editing.remove(uUID);

        this.pendingInputs.remove(uUID);

    }



    @EventHandler

    public void onChat(PlayerChatEvent playerChatEvent) {

        Player player = playerChatEvent.getPlayer();

        PendingInput pendingInput = this.pendingInputs.remove(player.getUniqueId());

        if (pendingInput == null) {

            return;

        }

        playerChatEvent.setCancelled(true);

        String string = playerChatEvent.getMessage();

        if (string.equalsIgnoreCase("cancel")) {

            player.sendMessage("\u00a77ItemGen input cancelled.");

            this.reopenFor(player, pendingInput.itemId);

            return;

        }

        try {

            this.applyInput(player, pendingInput, string);

            this.saveItems();

        }

        catch (Exception exception) {

            player.sendMessage("\u00a7cInvalid value: \u00a7f" + exception.getMessage());

        }

        this.reopenFor(player, pendingInput.itemId);

    }



    private void startInput(Player player, InputType inputType, String string, String string2) {

        this.pendingInputs.put(player.getUniqueId(), new PendingInput(inputType, string));

        player.closeInventory();

        player.sendMessage("");

        player.sendMessage("\u00a75\u00a7lWorldForge ItemGen Input");

        player.sendMessage(string2);

        player.sendMessage("\u00a78Your next chat message will not be sent publicly.");

    }



    private void applyInput(Player player, PendingInput pendingInput, String string) throws IOException {

        if (pendingInput.type == InputType.CREATE_ID) {

            String string2 = WorldForgeItemGen.cleanId(string);

            if (string2.isBlank() || this.items.containsKey(string2)) {

                player.sendMessage("\u00a7cThat item ID is invalid or already exists.");

                return;

            }

            this.pendingInputs.put(player.getUniqueId(), new PendingInput(InputType.CREATE_MATERIAL, string2));

            player.sendMessage("\u00a7dNow type the base material. \u00a77Example: \u00a7fAMETHYST_SHARD");

            return;

        }

        if (pendingInput.type == InputType.CREATE_MATERIAL) {

            Material material = Material.matchMaterial((String)string);

            if (material == null || !material.isItem()) {

                player.sendMessage("\u00a7cInvalid material. Creation cancelled.");

                return;

            }

            ItemDefinition itemDefinition = new ItemDefinition(pendingInput.itemId, material.name());

            itemDefinition.name = "&5&l" + WorldForgeItemGen.prettify(pendingInput.itemId);

            itemDefinition.rarity = "CUSTOM";

            itemDefinition.lore.add("&7Created by WorldForge ItemGen");

            this.items.put(itemDefinition.id, itemDefinition);

            this.editing.put(player.getUniqueId(), itemDefinition.id);

            player.sendMessage("\u00a7aCreated \u00a7f" + itemDefinition.id + "\u00a7a.");

            return;

        }

        ItemDefinition itemDefinition = this.items.get(pendingInput.itemId);

        if (itemDefinition == null) {

            player.sendMessage("\u00a7cThat item no longer exists.");

            return;

        }

        switch (pendingInput.type.ordinal()) {

            case 2: {

                Material material = Material.matchMaterial((String)string);

                if (material == null || !material.isItem()) {

                    throw new IllegalArgumentException("Unknown material");

                }

                itemDefinition.material = material.name();

                break;

            }

            case 3: {

                itemDefinition.name = string;

                break;

            }

            case 4: {

                itemDefinition.lore.add(string);

                break;

            }

            case 5: {

                itemDefinition.customModelData = string.equalsIgnoreCase("none") ? null : Integer.valueOf(Math.max(0, Integer.parseInt(string)));

                break;

            }

            case 6: {

                itemDefinition.abilityCooldownSeconds = Math.max(0, Integer.parseInt(string));

                break;

            }

            case 7: {

                itemDefinition.abilitySound = WorldForgeItemGen.noneToBlank(string);

                break;

            }

            case 8: {

                String string3 = WorldForgeItemGen.noneToBlank(string).toUpperCase(Locale.ROOT);

                if (!string3.isBlank()) {

                    Particle.valueOf((String)string3);

                }

                itemDefinition.abilityParticle = string3;

                break;

            }

            case 9: {

                itemDefinition.abilityCommand = WorldForgeItemGen.noneToBlank(string);

                break;

            }

            case 10: {

                itemDefinition.abilityMessage = WorldForgeItemGen.noneToBlank(string);

                break;

            }

            case 11: {

                itemDefinition.abilityAnimationTicks = Math.max(5, Math.min(100, Integer.parseInt(string)));

                break;

            }

        }

        player.sendMessage("\u00a7aUpdated \u00a7f" + itemDefinition.id + "\u00a7a.");

    }



    private void reopenFor(Player player, String string) {

        PendingInput pendingInput = this.pendingInputs.get(player.getUniqueId());

        if (pendingInput != null) {

            return;

        }

        if (string != null && this.items.containsKey(string)) {

            this.openEditor(player, string);

        } else {

            String string2 = this.editing.get(player.getUniqueId());

            if (string2 != null && this.items.containsKey(string2)) {

                this.openEditor(player, string2);

            } else {

                this.openBrowser(player);

            }

        }

    }



    @EventHandler

    public void onUse(PlayerInteractEvent playerInteractEvent) {

        long l;

        if (playerInteractEvent.getHand() != EquipmentSlot.HAND) {

            return;

        }

        Action action = playerInteractEvent.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {

            return;

        }

        ItemStack itemStack = playerInteractEvent.getItem();

        String string = this.getCustomId(itemStack);

        if (string == null) {

            return;

        }

        ItemDefinition itemDefinition = this.items.get(string);

        if (itemDefinition == null || !itemDefinition.abilityEnabled) {

            return;

        }

        Player player = playerInteractEvent.getPlayer();

        String string2 = String.valueOf(player.getUniqueId()) + ":" + string;

        long l2 = System.currentTimeMillis();

        if (l2 < (l = this.cooldowns.getOrDefault(string2, 0L).longValue())) {

            long l3 = Math.max(1L, (l - l2 + 999L) / 1000L);

            player.sendMessage("\u00a7cAbility cooling down: \u00a7f" + l3 + "s");

            return;

        }

        if (this.activeAnimations.contains(string2)) {

            player.sendMessage("\u00a75The shard is already fracturing...");

            return;

        }

        if (itemDefinition.abilityCooldownSeconds > 0) {

            this.cooldowns.put(string2, l2 + (long)itemDefinition.abilityCooldownSeconds * 1000L);

        }

        if (!itemDefinition.abilityMessage.isBlank()) {

            player.sendMessage(WorldForgeItemGen.color(itemDefinition.abilityMessage.replace("%player%", player.getName()).replace("%item_id%", string)));

        }

        if (itemDefinition.abilityConsume && itemStack != null) {

            int n = itemStack.getAmount();

            if (n <= 1) {

                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

            } else {

                itemStack.setAmount(n - 1);

            }

        }

        if ("SHARD_FRACTURE".equalsIgnoreCase(itemDefinition.abilityAnimation)) {

            this.activeAnimations.add(string2);

            this.playShardFracture(player, itemDefinition, string, string2);

        } else {

            this.playConfiguredEffect(player, itemDefinition);

            this.finishAbility(player, itemDefinition, string, string2);

        }

    }



    private void playShardFracture(Player player, ItemDefinition itemDefinition, String string, String string2) {

        int n = Math.max(10, itemDefinition.abilityAnimationTicks);

        int n2 = Math.max(2, n / 4);

        int n3 = Math.max(n2 + 2, n / 2);

        int n4 = Math.max(n3 + 2, n * 3 / 4);

        Object object = this.spawnFlyingShardVisual(player, itemDefinition);

        this.shardPulseAt(player, itemDefinition, this.visualLocation(object, player), 10, 0.16, 0.01, "minecraft:block.amethyst_block.hit", 0.65f, 0.65f);

        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {

            this.nudgeFlyingShard(object, 0.16);

            this.shardPulseAt(player, itemDefinition, this.visualLocation(object, player), 18, 0.22, 0.025, "minecraft:block.amethyst_cluster.hit", 0.85f, 0.85f);

        }, (long)n2);

        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {

            this.nudgeFlyingShard(object, 0.11);

            Location location = this.visualLocation(object, player);

            this.shardPulseAt(player, itemDefinition, location, 30, 0.3, 0.045, "minecraft:block.amethyst_block.break", 1.0f, 1.05f);

            try {

                player.getWorld().spawnParticle(Particle.valueOf((String)"REVERSE_PORTAL"), location, 22, 0.3, 0.3, 0.3, 0.05);

            }

            catch (Throwable throwable) {

                // empty catch block

            }

        }, (long)n3);

        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {

            this.nudgeFlyingShard(object, 0.06);

            Location location = this.visualLocation(object, player);

            this.shardPulseAt(player, itemDefinition, location, 48, 0.42, 0.075, "minecraft:block.glass.break", 1.15f, 1.25f);

            try {

                player.getWorld().spawnParticle(Particle.valueOf((String)"PORTAL"), location, 60, 0.48, 0.48, 0.48, 0.09);

            }

            catch (Throwable throwable) {

                // empty catch block

            }

        }, (long)n4);

        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {

            Location location = this.visualLocation(object, player);

            this.removeVisual(object);

            try {

                player.getWorld().playSound(location, "minecraft:block.amethyst_cluster.break", 1.35f, 0.7f);

                player.getWorld().playSound(location, "minecraft:block.glass.break", 1.1f, 1.55f);

                player.getWorld().spawnParticle(Particle.valueOf((String)"REVERSE_PORTAL"), location, 85, 0.7, 0.7, 0.7, 0.14);

                player.getWorld().spawnParticle(Particle.valueOf((String)"PORTAL"), location, 45, 0.42, 0.42, 0.42, 0.16);

            }

            catch (Throwable throwable) {

                // empty catch block

            }

            this.finishAbility(player, itemDefinition, string, string2);

        }, (long)n);

    }



    private Object spawnFlyingShardVisual(Player player, ItemDefinition itemDefinition) {

        try {

            Location location = player.getLocation().add(0.0, 1.35, 0.0);

            ItemStack itemStack = this.build(itemDefinition);

            itemStack.setAmount(1);

            Object object = player.getWorld().getClass().getMethod("dropItem", Location.class, ItemStack.class).invoke((Object)player.getWorld(), location, itemStack);

            this.invokeIfPresent(object, "setPickupDelay", new Class[]{Integer.TYPE}, Integer.MAX_VALUE);

            this.invokeIfPresent(object, "setCanPlayerPickup", new Class[]{Boolean.TYPE}, false);

            this.invokeIfPresent(object, "setCanMobPickup", new Class[]{Boolean.TYPE}, false);

            this.invokeIfPresent(object, "setWillAge", new Class[]{Boolean.TYPE}, false);

            this.invokeIfPresent(object, "setGravity", new Class[]{Boolean.TYPE}, false);

            Object object2 = location.getClass().getMethod("getDirection", new Class[0]).invoke((Object)location, new Object[0]);

            object2.getClass().getMethod("multiply", Double.TYPE).invoke(object2, 0.34);

            Class<?> clazz = Class.forName("org.bukkit.util.Vector");

            object.getClass().getMethod("setVelocity", clazz).invoke(object, object2);

            return object;

        }

        catch (Throwable throwable) {

            this.getLogger().log(Level.FINE, "Could not spawn flying shard visual; using particle-only fracture.", throwable);

            return null;

        }

    }



    private void nudgeFlyingShard(Object object, double d) {

        if (object == null) {

            return;

        }

        try {

            Location location = (Location)object.getClass().getMethod("getLocation", new Class[0]).invoke(object, new Object[0]);

            Object object2 = location.getClass().getMethod("getDirection", new Class[0]).invoke((Object)location, new Object[0]);

            object2.getClass().getMethod("multiply", Double.TYPE).invoke(object2, d);

            Class<?> clazz = Class.forName("org.bukkit.util.Vector");

            object.getClass().getMethod("setVelocity", clazz).invoke(object, object2);

        }

        catch (Throwable throwable) {

            // empty catch block

        }

    }



    private Location visualLocation(Object object, Player player) {

        if (object != null) {

            try {

                Object object2 = object.getClass().getMethod("getLocation", new Class[0]).invoke(object, new Object[0]);

                if (object2 instanceof Location) {

                    Location location = (Location)object2;

                    return location;

                }

            }

            catch (Throwable throwable) {

                // empty catch block

            }

        }

        return player.getLocation().add(0.0, 1.0, 0.0);

    }



    private void removeVisual(Object object) {

        if (object == null) {

            return;

        }

        try {

            object.getClass().getMethod("remove", new Class[0]).invoke(object, new Object[0]);

        }

        catch (Throwable throwable) {

            // empty catch block

        }

    }



    private void invokeIfPresent(Object object, String string, Class<?>[] classArray, Object object2) {

        try {

            object.getClass().getMethod(string, classArray).invoke(object, object2);

        }

        catch (Throwable throwable) {

            // empty catch block

        }

    }



    private void shardPulseAt(Player player, ItemDefinition itemDefinition, Location location, int n, double d, double d2, String string, float f, float f2) {

        try {

            player.getWorld().playSound(location, string, f, f2);

        }

        catch (Throwable throwable) {

            // empty catch block

        }

        Particle particle = this.resolveParticle(itemDefinition.abilityParticle);

        if (particle != null) {

            try {

                player.getWorld().spawnParticle(particle, location, n, d, d, d, d2);

            }

            catch (Throwable throwable) {

                // empty catch block

            }

        }

    }



    private void playConfiguredEffect(Player player, ItemDefinition itemDefinition) {

        Particle particle;

        if (!itemDefinition.abilitySound.isBlank()) {

            try {

                player.getWorld().playSound(player.getLocation(), itemDefinition.abilitySound, 1.0f, 1.0f);

            }

            catch (Throwable throwable) {

                // empty catch block

            }

        }

        if ((particle = this.resolveParticle(itemDefinition.abilityParticle)) != null) {

            try {

                player.getWorld().spawnParticle(particle, player.getLocation().add(0.0, 1.0, 0.0), 35, 0.45, 0.45, 0.45, 0.03);

            }

            catch (Throwable throwable) {

                // empty catch block

            }

        }

    }



    private Particle resolveParticle(String string) {

        if (string == null || string.isBlank()) {

            return null;

        }

        try {

            return Particle.valueOf((String)string.toUpperCase(Locale.ROOT));

        }

        catch (Throwable throwable) {

            return null;

        }

    }



    private void finishAbility(Player player, ItemDefinition itemDefinition, String string, String string2) {

        this.activeAnimations.remove(string2);

        if (!itemDefinition.abilityCommand.isBlank()) {

            String string3 = itemDefinition.abilityCommand.replace("%player%", player.getName()).replace("%item_id%", string);

            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)string3);

        }

    }



    private String getCustomId(ItemStack itemStack) {

        if (itemStack == null || itemStack.getType() == Material.AIR) {

            return null;

        }

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null) {

            return null;

        }

        return (String)itemMeta.getPersistentDataContainer().get(this.itemIdKey, PersistentDataType.STRING);

    }



    private ItemStack guiItem(Material material, String string, List<String> list) {

        ItemStack itemStack = new ItemStack(material);

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta != null) {

            itemMeta.setDisplayName(string);

            itemMeta.setLore(list);

            itemStack.setItemMeta(itemMeta);

        }

        return itemStack;

    }



    private void cycleRarity(ItemDefinition itemDefinition, int n) {

        int n2 = RARITIES.indexOf(itemDefinition.rarity.toUpperCase(Locale.ROOT));

        if (n2 < 0) {

            n2 = RARITIES.size() - 1;

        }

        n2 = (n2 + n + RARITIES.size()) % RARITIES.size();

        itemDefinition.rarity = RARITIES.get(n2);

    }



    private void cycleAnimation(ItemDefinition itemDefinition, int n) {

        List<String> list = List.of("NONE", "SHARD_FRACTURE");

        int n2 = list.indexOf(itemDefinition.abilityAnimation.toUpperCase(Locale.ROOT));

        if (n2 < 0) {

            n2 = 0;

        }

        n2 = (n2 + n + list.size()) % list.size();

        itemDefinition.abilityAnimation = list.get(n2);

    }



    private String rarityColor(String string) {

        return switch (string.toUpperCase(Locale.ROOT)) {

            case "COMMON" -> "&f";

            case "UNCOMMON" -> "&a";

            case "RARE" -> "&9";

            case "EPIC" -> "&5";

            case "MYTHIC" -> "&d";

            case "LEGENDARY" -> "&6";

            case "FORGED" -> "&4";

            default -> "&b";

        };

    }



    private void list(CommandSender commandSender) {

        commandSender.sendMessage("\u00a75\u00a7lWorldForge ItemGen \u00a78- \u00a7f" + this.items.size() + " item(s)");

        for (ItemDefinition itemDefinition : this.items.values()) {

            commandSender.sendMessage("\u00a78- \u00a7d" + itemDefinition.id + " \u00a77(" + itemDefinition.material + ", " + itemDefinition.rarity + ")");

        }

    }



    private void help(CommandSender commandSender) {

        commandSender.sendMessage("\u00a75\u00a7lWorldForge Custom Item Generator v1.2.0");

        commandSender.sendMessage("\u00a7d/itemgen \u00a77- Open GUI browser/editor");

        commandSender.sendMessage("\u00a7d/itemgen edit <id> \u00a77- Open an item's GUI editor");

        commandSender.sendMessage("\u00a7d/itemgen create <id> <material>");

        commandSender.sendMessage("\u00a7d/itemgen material <id> <material>");

        commandSender.sendMessage("\u00a7d/itemgen name <id> <name...>");

        commandSender.sendMessage("\u00a7d/itemgen lore <id> add <text...> \u00a78| \u00a7dlore <id> clear");

        commandSender.sendMessage("\u00a7d/itemgen rarity <id> <rarity> \u00a78| \u00a7dmodel <id> <number|none>");

        commandSender.sendMessage("\u00a7d/itemgen unbreakable <id> <true|false> \u00a78| \u00a7dgive <id> [player] [amount]");

        commandSender.sendMessage("\u00a7d/itemgen delete <id> \u00a78| \u00a7dlist \u00a78| \u00a7dreload");

    }



    private void writeDefaultFile() throws IOException {

        ItemDefinition itemDefinition = new ItemDefinition("void_shard", "AMETHYST_SHARD");

        itemDefinition.name = "&5&lVoid Shard";

        itemDefinition.rarity = "MYTHIC";

        itemDefinition.customModelData = 1001;

        itemDefinition.lore.add("&7A fragment shaped by the WorldForge.");

        itemDefinition.lore.add("&8Configure its right-click ability in /itemgen.");

        itemDefinition.abilityEnabled = false;

        itemDefinition.abilityConsume = true;

        itemDefinition.abilityCooldownSeconds = 2;

        itemDefinition.abilitySound = "minecraft:block.amethyst_block.break";

        itemDefinition.abilityParticle = "PORTAL";

        itemDefinition.abilityMessage = "&5The Void Shard fractures in your hand...";

        itemDefinition.abilityAnimation = "SHARD_FRACTURE";

        itemDefinition.abilityAnimationTicks = 24;

        this.items.clear();

        this.items.put(itemDefinition.id, itemDefinition);

        this.saveItems();

    }



    private void loadItems() throws IOException {

        this.items.clear();

        if (!Files.exists(this.itemsFile, new LinkOption[0])) {

            return;

        }

        List<String> list = Files.readAllLines(this.itemsFile, StandardCharsets.UTF_8);

        ItemDefinition itemDefinition = null;

        boolean bl = false;

        boolean bl2 = false;

        for (String string : list) {

            String string2;

            if (string.isBlank() || string.trim().startsWith("#") || string.trim().equals("items:")) continue;

            int n = WorldForgeItemGen.leadingSpaces(string);

            String string3 = string.trim();

            if (n == 2 && string3.endsWith(":")) {

                string2 = WorldForgeItemGen.unquote(string3.substring(0, string3.length() - 1));

                itemDefinition = new ItemDefinition(string2, "PAPER");

                this.items.put(string2, itemDefinition);

                bl = false;

                bl2 = false;

                continue;

            }

            if (itemDefinition == null) continue;

            if (n == 4 && string3.startsWith("material:")) {

                itemDefinition.material = WorldForgeItemGen.value(string3);

                bl = false;

                bl2 = false;

                continue;

            }

            if (n == 4 && string3.startsWith("name:")) {

                itemDefinition.name = WorldForgeItemGen.value(string3);

                bl = false;

                bl2 = false;

                continue;

            }

            if (n == 4 && string3.startsWith("rarity:")) {

                itemDefinition.rarity = WorldForgeItemGen.value(string3);

                bl = false;

                bl2 = false;

                continue;

            }

            if (n == 4 && string3.startsWith("custom-model-data:")) {

                string2 = WorldForgeItemGen.value(string3);

                itemDefinition.customModelData = string2.equalsIgnoreCase("null") ? null : Integer.valueOf(Integer.parseInt(string2));

                bl = false;

                bl2 = false;

                continue;

            }

            if (n == 4 && string3.startsWith("unbreakable:")) {

                itemDefinition.unbreakable = Boolean.parseBoolean(WorldForgeItemGen.value(string3));

                bl = false;

                bl2 = false;

                continue;

            }

            if (n == 4 && string3.equals("lore:")) {

                itemDefinition.lore.clear();

                bl = true;

                bl2 = false;

                continue;

            }

            if (n == 4 && string3.equals("ability:")) {

                bl = false;

                bl2 = true;

                continue;

            }

            if (bl && n >= 6 && string3.startsWith("- ")) {

                itemDefinition.lore.add(WorldForgeItemGen.unquote(string3.substring(2).trim()));

                continue;

            }

            if (!bl2 || n < 6) continue;

            if (string3.startsWith("enabled:")) {

                itemDefinition.abilityEnabled = Boolean.parseBoolean(WorldForgeItemGen.value(string3));

                continue;

            }

            if (string3.startsWith("consume:")) {

                itemDefinition.abilityConsume = Boolean.parseBoolean(WorldForgeItemGen.value(string3));

                continue;

            }

            if (string3.startsWith("cooldown-seconds:")) {

                itemDefinition.abilityCooldownSeconds = Math.max(0, Integer.parseInt(WorldForgeItemGen.value(string3)));

                continue;

            }

            if (string3.startsWith("sound:")) {

                itemDefinition.abilitySound = WorldForgeItemGen.value(string3);

                continue;

            }

            if (string3.startsWith("particle:")) {

                itemDefinition.abilityParticle = WorldForgeItemGen.value(string3);

                continue;

            }

            if (string3.startsWith("command:")) {

                itemDefinition.abilityCommand = WorldForgeItemGen.value(string3);

                continue;

            }

            if (string3.startsWith("message:")) {

                itemDefinition.abilityMessage = WorldForgeItemGen.value(string3);

                continue;

            }

            if (string3.startsWith("animation:")) {

                itemDefinition.abilityAnimation = WorldForgeItemGen.value(string3).toUpperCase(Locale.ROOT);

                continue;

            }

            if (!string3.startsWith("animation-ticks:")) continue;

            itemDefinition.abilityAnimationTicks = Math.max(5, Math.min(100, Integer.parseInt(WorldForgeItemGen.value(string3))));

        }

        ItemDefinition itemDefinition2 = this.items.get("void_shard");

        if (itemDefinition2 != null && (itemDefinition2.abilityAnimation == null || itemDefinition2.abilityAnimation.isBlank() || itemDefinition2.abilityAnimation.equalsIgnoreCase("NONE"))) {

            itemDefinition2.abilityAnimation = "SHARD_FRACTURE";

            itemDefinition2.abilityAnimationTicks = 24;

        }

    }



    private void saveItems() throws IOException {

        Files.createDirectories(this.getDataFolder().toPath(), new FileAttribute[0]);

        ArrayList<String> arrayList = new ArrayList<String>();

        arrayList.add("# WorldForge ItemGen item definitions");

        arrayList.add("items:");

        for (ItemDefinition itemDefinition : this.items.values()) {

            arrayList.add("  " + WorldForgeItemGen.yaml(itemDefinition.id) + ":");

            arrayList.add("    material: " + WorldForgeItemGen.yaml(itemDefinition.material));

            arrayList.add("    name: " + WorldForgeItemGen.yaml(itemDefinition.name));

            arrayList.add("    rarity: " + WorldForgeItemGen.yaml(itemDefinition.rarity));

            arrayList.add("    custom-model-data: " + String.valueOf(itemDefinition.customModelData == null ? "null" : itemDefinition.customModelData));

            arrayList.add("    unbreakable: " + itemDefinition.unbreakable);

            arrayList.add("    lore:");

            for (String string : itemDefinition.lore) {

                arrayList.add("      - " + WorldForgeItemGen.yaml(string));

            }

            arrayList.add("    ability:");

            arrayList.add("      enabled: " + itemDefinition.abilityEnabled);

            arrayList.add("      consume: " + itemDefinition.abilityConsume);

            arrayList.add("      cooldown-seconds: " + itemDefinition.abilityCooldownSeconds);

            arrayList.add("      sound: " + WorldForgeItemGen.yaml(itemDefinition.abilitySound));

            arrayList.add("      particle: " + WorldForgeItemGen.yaml(itemDefinition.abilityParticle));

            arrayList.add("      command: " + WorldForgeItemGen.yaml(itemDefinition.abilityCommand));

            arrayList.add("      message: " + WorldForgeItemGen.yaml(itemDefinition.abilityMessage));

            arrayList.add("      animation: " + WorldForgeItemGen.yaml(itemDefinition.abilityAnimation));

            arrayList.add("      animation-ticks: " + itemDefinition.abilityAnimationTicks);

        }

        Files.write(this.itemsFile, arrayList, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    }



    private static String value(String string) {

        int n = string.indexOf(58);

        return WorldForgeItemGen.unquote(string.substring(n + 1).trim());

    }



    private static int leadingSpaces(String string) {

        int n;

        for (n = 0; n < string.length() && string.charAt(n) == ' '; ++n) {

        }

        return n;

    }



    private static String yaml(String string) {

        return "\"" + string.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";

    }



    private static String unquote(String string) {

        if (string.length() >= 2 && string.startsWith("\"") && string.endsWith("\"")) {

            string = string.substring(1, string.length() - 1);

        }

        return string.replace("\\\"", "\"").replace("\\\\", "\\");

    }



    private static String color(String string) {

        return ChatColor.translateAlternateColorCodes((char)'&', (String)string);

    }



    private static String join(String[] stringArray, int n) {

        return String.join((CharSequence)" ", Arrays.copyOfRange(stringArray, n, stringArray.length));

    }



    private static String cleanId(String string) {

        return string.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");

    }



    private static String noneToBlank(String string) {

        return string.equalsIgnoreCase("none") ? "" : string.trim();

    }



    private static String shortText(String string) {

        if (string == null || string.isBlank()) {

            return "none";

        }

        return string.length() > 28 ? string.substring(0, 28) + "..." : string;

    }



    private static String prettify(String string) {

        String[] stringArray = string.replace('-', '_').split("_");

        StringBuilder stringBuilder = new StringBuilder();

        for (String string2 : stringArray) {

            if (string2.isEmpty()) continue;

            if (!stringBuilder.isEmpty()) {

                stringBuilder.append(' ');

            }

            stringBuilder.append(Character.toUpperCase(string2.charAt(0))).append(string2.substring(1));

        }

        return stringBuilder.toString();

    }



    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {

        if (stringArray.length == 1) {

            return WorldForgeItemGen.partial(stringArray[0], List.of("create", "delete", "edit", "material", "name", "rarity", "lore", "model", "unbreakable", "give", "list", "reload", "help"));

        }

        if (stringArray.length == 2 && List.of("delete", "edit", "material", "name", "rarity", "lore", "model", "unbreakable", "give").contains(stringArray[0].toLowerCase(Locale.ROOT))) {

            return WorldForgeItemGen.partial(stringArray[1], new ArrayList<String>(this.items.keySet()));

        }

        if (stringArray.length == 3 && stringArray[0].equalsIgnoreCase("rarity")) {

            return WorldForgeItemGen.partial(stringArray[2], RARITIES);

        }

        if (stringArray.length == 3 && stringArray[0].equalsIgnoreCase("unbreakable")) {

            return WorldForgeItemGen.partial(stringArray[2], List.of("true", "false"));

        }

        if (stringArray.length == 3 && stringArray[0].equalsIgnoreCase("lore")) {

            return WorldForgeItemGen.partial(stringArray[2], List.of("add", "clear"));

        }

        return Collections.emptyList();

    }



    private static List<String> partial(String string, List<String> list) {

        String string2 = string.toLowerCase(Locale.ROOT);

        ArrayList<String> arrayList = new ArrayList<String>();

        for (String string3 : list) {

            if (!string3.toLowerCase(Locale.ROOT).startsWith(string2)) continue;

            arrayList.add(string3);

        }

        return arrayList;

    }



    private /* synthetic */ void handleDelayedClick(Player player, MenuType menuType, int n, boolean bl, boolean bl2, String string) {

        this.handleMenuClick(player, menuType, n, bl, bl2, string);

    }



    private static final class ItemDefinition {

        final String id;

        String material;

        String name = "&fCustom Item";

        String rarity = "CUSTOM";

        Integer customModelData = null;

        boolean unbreakable = false;

        final List<String> lore = new ArrayList<String>();

        boolean abilityEnabled = false;

        boolean abilityConsume = false;

        int abilityCooldownSeconds = 0;

        String abilitySound = "";

        String abilityParticle = "";

        String abilityCommand = "";

        String abilityMessage = "";

        String abilityAnimation = "NONE";

        int abilityAnimationTicks = 24;



        ItemDefinition(String string, String string2) {

            this.id = string;

            this.material = string2;

        }

    }



    private static final class ItemGenMenuHolder

    implements InventoryHolder {

        private final MenuType type;

        private final UUID playerId;

        private Inventory inventory;



        private ItemGenMenuHolder(MenuType menuType, UUID uUID) {

            this.type = menuType;

            this.playerId = uUID;

        }



        public Inventory getInventory() {

            return this.inventory;

        }

    }



    private static enum MenuType {

        BROWSER,

        EDITOR,

        ABILITY;



    }



    private static enum InputType {

        CREATE_ID,

        CREATE_MATERIAL,

        MATERIAL,

        NAME,

        LORE,

        MODEL,

        COOLDOWN,

        SOUND,

        PARTICLE,

        COMMAND,

        MESSAGE,

        ANIMATION_TICKS;



    }



    private record PendingInput(InputType type, String itemId) {

    }

}









