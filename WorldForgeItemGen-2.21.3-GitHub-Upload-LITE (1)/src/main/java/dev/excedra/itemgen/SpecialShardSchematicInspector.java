package dev.excedra.itemgen;
import java.io.*;
import java.nio.file.*;
/** Lightweight structural precheck, not a full Sponge/FAWE schematic interpreter. */
final class SpecialShardSchematicInspector {
    private SpecialShardSchematicInspector(){}
    static void inspect(Path file)throws IOException {
        if(!file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".schem"))throw new IOException("Expected .schem");
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Schematic missing");
        long length=Files.size(file);if(length<4||length>32L*1024*1024)throw new IOException("Schematic size invalid");
        try(InputStream in=Files.newInputStream(file)){
            int a=in.read(),b=in.read();
            if(a!=0x1f||b!=0x8b)throw new IOException("Expected compressed NBT schematic (.schem)");
        }
    }
}