package dev.excedra.itemgen;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Offline dependency checks only; does not publish or run model/animation assets. */
final class SpecialShardAssetInspector {
    private SpecialShardAssetInspector(){}
    static void inspect(Path root,SpecialShardDefinition d)throws IOException {
        for(String path:d.models())check(root,path,Set.of(".json",".bbmodel"),"model");
        for(String path:d.textures())check(root,path,Set.of(".png"),"texture");
        for(String path:d.animations())check(root,path,Set.of(".json"),"animation");
        for(String path:d.sounds())check(root,path,Set.of(".ogg"),"sound");
    }
    static void check(Path root,String name,Set<String> extensions,String type)throws IOException{
        SpecialShardManifest.safePath(name);
        Path file=root.resolve(name).normalize();
        if(!file.startsWith(root)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file))throw new IOException("Missing "+type+": "+name);
        if(extensions.stream().noneMatch(x->name.toLowerCase(Locale.ROOT).endsWith(x)))throw new IOException("Unsupported "+type+" extension: "+name);
        if(Files.size(file)==0||Files.size(file)>32L*1024*1024)throw new IOException("Invalid "+type+" size: "+name);
        if(name.endsWith(".png"))try(InputStream in=Files.newInputStream(file)){byte[] h=in.readNBytes(8);if(!Arrays.equals(h,new byte[]{(byte)137,80,78,71,13,10,26,10}))throw new IOException("Invalid PNG signature: "+name);}
        if(name.endsWith(".ogg"))try(InputStream in=Files.newInputStream(file)){if(!Arrays.equals(in.readNBytes(4),new byte[]{79,103,103,83}))throw new IOException("Invalid OGG signature: "+name);}
    }
}