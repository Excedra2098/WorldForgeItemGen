package dev.excedra.itemgen;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;
/** Offline immutable revisions, never overwrites prior staged contents. */
final class SpecialShardStagingStore {
    private SpecialShardStagingStore(){}
    static Path stage(Path source,Path root,String id)throws IOException {
        if(!id.matches("[a-z][a-z0-9_-]{2,63}"))throw new IOException("Invalid staged ID");
        Files.createDirectories(root);
        Path project=root.resolve(id);
        Files.createDirectories(project);
        String revision="revision-"+UUID.randomUUID();
        Path temp=project.resolve("."+revision+".tmp"),dest=project.resolve(revision);
        Files.createDirectory(temp);
        try {
            try(Stream<Path> walk=Files.walk(source)){
                for(Path p:walk.toList()){
                    if(Files.isSymbolicLink(p))throw new IOException("Symlink prohibited");
                    Path rel=source.relativize(p);
                    if(rel.toString().isEmpty())continue;
                    Path out=temp.resolve(rel);
                    if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))Files.createDirectories(out);
                    else{Files.createDirectories(out.getParent());Files.copy(p,out,LinkOption.NOFOLLOW_LINKS);}
                }
            }
            StringBuilder hashList=new StringBuilder();
            try(Stream<Path> walk=Files.walk(temp)){
                for(Path p:walk.filter(x->Files.isRegularFile(x,LinkOption.NOFOLLOW_LINKS)).sorted().toList()){
                    if(p.getFileName().toString().equals("CHECKSUMS.sha256"))throw new IOException("Reserved checksum filename");
                    byte[] bytes=Files.readAllBytes(p);byte[] digest;
                    try{digest=MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new IOException(e);}
                    hashList.append(HexFormat.of().formatHex(digest)).append("  ").append(temp.relativize(p).toString().replace('\\','/')).append('\n');
                }
            }
            Files.writeString(temp.resolve("CHECKSUMS.sha256"),hashList.toString(),StandardOpenOption.CREATE_NEW);
            Files.move(temp,dest,StandardCopyOption.ATOMIC_MOVE);
            return dest;
        }catch(Exception e){
            try(Stream<Path> walk=Files.walk(temp)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}catch(IOException ignored){}
            if(e instanceof IOException io)throw io;throw new IOException("Staging failed",e);
        }
    }
}