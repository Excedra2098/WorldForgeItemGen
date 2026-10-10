package dev.excedra.itemgen;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Receives immutable text snapshots only. No Bukkit/world access occurs on the worker. */
final class JournalWriter implements AutoCloseable {
    record Snapshot(Path path,String header,List<String> cells,List<String> biomes){
        Snapshot{cells=List.copyOf(cells);biomes=List.copyOf(biomes);}
        String render(){StringBuilder out=new StringBuilder(header);append(out,"cells",cells);append(out,"biomes",biomes);return out.toString();}
        private static void append(StringBuilder out,String key,List<String> values){
            if(values.isEmpty()){out.append(key).append(": []\n");return;}
            out.append(key).append(":\n");for(String value:values)out.append("- '").append(value.replace("'","''")).append("'\n");
        }
    }
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"RealmShards-journal");t.setDaemon(true);return t;});
    CompletableFuture<Void> write(Snapshot snapshot){return CompletableFuture.runAsync(()->{try{atomicText(snapshot.path(),snapshot.render());}catch(IOException e){throw new CompletionException(e);}},worker);}
    static void atomicText(Path file,String text)throws IOException{
        Path temp=file.resolveSibling(file.getFileName()+".tmp");byte[] data=text.getBytes(StandardCharsets.UTF_8);
        try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(data);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    public void close(){worker.shutdown();try{if(!worker.awaitTermination(30,TimeUnit.SECONDS))throw new IllegalStateException("Journal writes are still pending after shutdown timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted while flushing shard journal",e);}}
}
