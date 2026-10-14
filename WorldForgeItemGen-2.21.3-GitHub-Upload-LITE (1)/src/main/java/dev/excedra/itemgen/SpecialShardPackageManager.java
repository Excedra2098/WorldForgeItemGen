package dev.excedra.itemgen;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
/** Explicit offline entrypoint. Not registered with any Minecraft command or startup hook. */
final class SpecialShardPackageManager {
    private SpecialShardPackageManager(){}
    static SpecialShardPackageValidator.Result inspect(Path input)throws IOException{
        if(Files.isDirectory(input,LinkOption.NOFOLLOW_LINKS))return SpecialShardPackageValidator.validate(input);
        Path scratch=Files.createTempDirectory("special-shard-inspect-");
        try{SpecialShardArchiveReader.extract(input,scratch.resolve("unpacked"));return SpecialShardPackageValidator.validate(scratch.resolve("unpacked"));}
        finally{delete(scratch);}
    }
    static Path stage(Path input,Path stagingRoot)throws IOException{
        Path scratch=Files.createTempDirectory("special-shard-stage-");
        try{
            Path source=input;
            if(!Files.isDirectory(input,LinkOption.NOFOLLOW_LINKS)){source=scratch.resolve("unpacked");SpecialShardArchiveReader.extract(input,source);}
            var result=SpecialShardPackageValidator.validate(source);
            if(!result.report().valid())throw new IOException("Package rejected: "+result.report().errors());
            return SpecialShardStagingStore.stage(source,stagingRoot,result.definition().id());
        }finally{delete(scratch);}
    }
    private static void delete(Path root)throws IOException{
        try(Stream<Path> walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
    }
}