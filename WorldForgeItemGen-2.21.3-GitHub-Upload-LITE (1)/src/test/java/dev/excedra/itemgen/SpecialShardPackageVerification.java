package dev.excedra.itemgen;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
public final class SpecialShardPackageVerification {
    static int checks;
    static void ok(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    static void fails(Checked action,String why)throws Exception{try{action.run();throw new AssertionError("Expected rejection: "+why);}catch(IOException expected){checks++;}}
    interface Checked{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("special-c2-verification-");
        Path source=Files.createDirectory(root.resolve("draft"));
        String manifest="""
            schema-version: 1
            identity:
              id: frostbound_2026
              name: Frostbound Fragment
              category: SEASONAL
            shard:
              base-land: SNOW
              tier: 3
              variant: STANDARD
            island:
              schematic: island.schem
              dimensions:
                width: 16
                length: 16
                height: 16
              placement-anchor: CENTER
            assets:
              item-model: realmshards:special/frostbound
            distribution:
              enabled: false
            """;
        Files.writeString(source.resolve("manifest.yml"),manifest);
        Files.write(source.resolve("island.schem"),new byte[]{31,(byte)139,8,0,0,0});
        var report=SpecialShardPackageManager.inspect(source);
        ok(report.report().valid(),"Valid draft");
        ok(!report.report().warnings().isEmpty(),"Inspector limitations warned");
        Path stages=root.resolve("staged");
        Path first=SpecialShardPackageManager.stage(source,stages);
        Path second=SpecialShardPackageManager.stage(source,stages);
        ok(!first.equals(second),"Unique staging revisions");
        ok(Files.exists(first.resolve("CHECKSUMS.sha256")),"Checksums exist");
        ok(Files.exists(first.resolve("manifest.yml")),"Staged manifest");
        Path zip=root.resolve("package.zip");
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){
            for(String name:List.of("manifest.yml","island.schem")){
                out.putNextEntry(new ZipEntry(name));out.write(Files.readAllBytes(source.resolve(name)));out.closeEntry();
            }
        }
        ok(SpecialShardPackageManager.inspect(zip).report().valid(),"Valid ZIP");
        Path malicious=root.resolve("malicious.zip");
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(malicious))){
            out.putNextEntry(new ZipEntry("../escape.txt"));out.write(new byte[]{1});out.closeEntry();
        }
        fails(()->SpecialShardPackageManager.inspect(malicious),"Traversal");
        Files.delete(source.resolve("island.schem"));
        ok(!SpecialShardPackageManager.inspect(source).report().valid(),"Missing schematic rejected");
        System.out.println("Special Shard C2 package verification passed: "+checks+" checks.");
    }
}