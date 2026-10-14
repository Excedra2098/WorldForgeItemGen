package dev.excedra.itemgen;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
/** Offline-only archive extraction; never called by the Bukkit entrypoint. */
final class SpecialShardArchiveReader {
    static final int MAX_FILES=512;
    static final long MAX_ENTRY=32L*1024*1024, MAX_TOTAL=128L*1024*1024, MAX_ARCHIVE=64L*1024*1024;
    private SpecialShardArchiveReader(){}
    static void extract(Path archive,Path target)throws IOException {
        if(!Files.isRegularFile(archive,LinkOption.NOFOLLOW_LINKS)||Files.size(archive)>MAX_ARCHIVE)throw new IOException("Archive missing or exceeds limit");
        Files.createDirectory(target);long total=0;int count=0;Set<String> seen=new HashSet<>();
        try(ZipInputStream zin=new ZipInputStream(Files.newInputStream(archive))){
            ZipEntry entry;
            while((entry=zin.getNextEntry())!=null){
                if(++count>MAX_FILES)throw new IOException("Too many archive entries");
                String name=entry.getName();
                if(name.endsWith("/")&&entry.isDirectory())name=name.substring(0,name.length()-1);
                safe(name);if(!seen.add(name))throw new IOException("Duplicate ZIP entry: "+name);
                Path dest=target.resolve(name).normalize();
                if(!dest.startsWith(target))throw new IOException("Path outside staging root");
                if(entry.isDirectory()){Files.createDirectories(dest);continue;}
                Files.createDirectories(dest.getParent());
                if(Files.exists(dest,LinkOption.NOFOLLOW_LINKS))throw new IOException("Duplicate output: "+name);
                long written=0;
                try(OutputStream out=Files.newOutputStream(dest,StandardOpenOption.CREATE_NEW)){
                    byte[] buf=new byte[8192];int n;
                    while((n=zin.read(buf))!=-1){written+=n;total+=n;
                        if(written>MAX_ENTRY||total>MAX_TOTAL)throw new IOException("Archive extracted size exceeds limit");
                        out.write(buf,0,n);
                    }
                }
                if(entry.getSize()>=0&&entry.getSize()!=written)throw new IOException("ZIP size mismatch");
            }
        }
    }
    static void safe(String name)throws IOException {
        if(name==null||name.isBlank()||name.startsWith("/")||name.contains("\\")||name.contains(":")||name.contains("//"))
            throw new IOException("Unsafe archive entry: "+name);
        for(String part:name.split("/"))if(part.isBlank()||part.equals(".")||part.equals(".."))throw new IOException("Unsafe archive component: "+name);
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".zip")||lower.endsWith(".jar")||lower.endsWith(".tar")||lower.endsWith(".gz")||lower.endsWith(".7z")||lower.endsWith(".rar"))
            throw new IOException("Nested archive is not allowed: "+name);
    }
}