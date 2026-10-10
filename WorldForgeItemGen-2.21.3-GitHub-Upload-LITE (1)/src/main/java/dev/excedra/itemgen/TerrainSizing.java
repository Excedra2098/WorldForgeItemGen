package dev.excedra.itemgen;

final class TerrainSizing {
    static int radius(int base,ShardSpec.Variant variant){
        int standard=Math.clamp(base,3,16);
        return switch(variant){case STANDARD->standard;case SHINY->(int)Math.ceil(standard*1.35);case RAINBOW->(int)Math.ceil(standard*1.75);};
    }
}
