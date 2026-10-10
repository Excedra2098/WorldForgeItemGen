package dev.excedra.itemgen;

import java.util.Locale;

public record ShardSpec(int tier, Land land, Variant variant) {
    public enum Land { FOREST, SNOW, DESERT, VOLCANIC, OCEAN, CRYSTAL, RUINS, NETHER, END, MUSHROOM, JUNGLE, BADLANDS }
    public enum Variant { STANDARD, SHINY, RAINBOW }
    public ShardSpec {
        if (tier < 1 || tier > 5) throw new IllegalArgumentException("Tier must be 1–5");
        if (variant == null) throw new IllegalArgumentException("Variant is required");
        if (variant == Variant.RAINBOW && tier < 3) throw new IllegalArgumentException("Rainbow shards start at tier III");
    }
    public String key() { return (land == null ? "random" : land.name().toLowerCase(Locale.ROOT))+"_t"+tier+"_"+variant.name().toLowerCase(Locale.ROOT); }
    public String title() { return (land == null ? "Uncharted" : nice(land.name()))+" RealmShard"; }
    public static String nice(String s) { return s.charAt(0)+s.substring(1).toLowerCase(Locale.ROOT); }
    public static String tierName(int t) { return new String[]{"Raw Fragment","Stabilized Fragment","Awakened Shard","Greater Shard","RealmShard"}[t-1]; }
    public static String roman(int t) { return new String[]{"I","II","III","IV","V"}[t-1]; }
}
