package dev.excedra.itemgen;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** One global NPC reward per UUID. Ambiguous interrupted grants fail closed. */
final class ShardClaims {
    enum Result { CLAIMED, ALREADY_CLAIMED, NO_SPACE }
    final Path folder;
    ShardClaims(Path folder)throws IOException{this.folder=folder;Files.createDirectories(folder);}
    boolean claimed(UUID player){return Files.exists(folder.resolve(player+".claim"));}
    synchronized Result grant(UUID player,String spec,BooleanSupplier space,BooleanSupplier deliver)throws IOException{
        if(claimed(player))return Result.ALREADY_CLAIMED;
        if(!space.getAsBoolean())return Result.NO_SPACE;
        Path file=folder.resolve(player+".claim");
        try(var channel=FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            ByteBuffer bytes=StandardCharsets.UTF_8.encode("PENDING\n"+spec+"\n"+System.currentTimeMillis()+"\n");while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
        }catch(FileAlreadyExistsException e){return Result.ALREADY_CLAIMED;}
        if(!deliver.getAsBoolean()){Files.delete(file);return Result.NO_SPACE;}
        Path temp=Files.createTempFile(folder,"claim-",".tmp");
        try{Files.writeString(temp,"CLAIMED\n"+spec+"\n"+System.currentTimeMillis()+"\n");try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temp);}
        return Result.CLAIMED;
    }
}
