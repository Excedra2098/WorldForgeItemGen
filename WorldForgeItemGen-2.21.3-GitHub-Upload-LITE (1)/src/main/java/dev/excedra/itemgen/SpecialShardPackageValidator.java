package dev.excedra.itemgen;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import org.bukkit.configuration.file.YamlConfiguration;
/** Validates one isolated draft directory. No server side effects. */
final class SpecialShardPackageValidator {
    record Result(SpecialShardDefinition definition,SpecialShardValidationReport report){}
    private SpecialShardPackageValidator(){}
    static Result validate(Path root) {
        List<String> errors=new ArrayList<>(),warnings=new ArrayList<>();
        SpecialShardDefinition definition=null;
        try {
            if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Package directory missing");
            try(Stream<Path> walk=Files.walk(root)){
                List<Path> paths=walk.toList();
                if(paths.size()>SpecialShardArchiveReader.MAX_FILES+1)throw new IOException("Too many files");
                long total=0;
                for(Path p:paths){if(Files.isSymbolicLink(p))throw new IOException("Symlink prohibited: "+p.getFileName());
                    if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){long size=Files.size(p);if(size>SpecialShardArchiveReader.MAX_ENTRY)throw new IOException("Oversized file");total+=size;}
                    if(total>SpecialShardArchiveReader.MAX_TOTAL)throw new IOException("Package exceeds total size limit");
                }
            }
            Path manifest=root.resolve("manifest.yml");
            if(!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing manifest.yml");
            definition=SpecialShardManifest.parse(YamlConfiguration.loadConfiguration(manifest.toFile()));
            SpecialShardSchematicInspector.inspect(root.resolve(definition.schematic()).normalize());
            SpecialShardAssetInspector.inspect(root,definition);
            warnings.add("Schematic NBT palette and geometry have not been deeply decoded.");
            warnings.add("Models/animations are not runtime compatibility tested or published.");
        }catch(Exception e){errors.add(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
        return new Result(definition,new SpecialShardValidationReport(errors,warnings));
    }
}