package dev.excedra.itemgen;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

final class SchematicVerification {
    static void run(Path folder)throws Exception{
        Files.createDirectories(folder);
        for(int version:new int[]{2,3}){
            Path file=folder.resolve("fixture-v"+version+".schem");write(file,version,false,false);
            var data=SchematicData.read(file);
            Verification.check(data.width()==3&&data.height()==2&&data.length()==2,"Schematic dimensions");
            Verification.check(data.states().size()==3,"Air must not be placed");
            Verification.check("minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]".equals(data.states().get(new TerrainPlan.Pos(0,0,-1))),"Block orientation and full properties survive import");
            Verification.check(data.states().get(new TerrainPlan.Pos(1,1,0)).equals("minecraft:stone"),"Sponge x/z/y order and centered base");
            Path invalid=folder.resolve("entity-v"+version+".schem");write(invalid,version,true,false);reject(invalid,"Data-bearing block entities must be rejected");
            Path truncated=folder.resolve("truncated-v"+version+".schem");write(truncated,version,false,true);reject(truncated,"Truncated data must be rejected");
        }
        Path legacy=folder.resolve("legacy.schem");write(legacy,1,false,false);reject(legacy,"Legacy formats explicitly rejected");
        for(String group:List.of("Admin","admin","Head-Admin","head_admin","headadmin","Owner"))Verification.check(ShardAccess.allowed(List.of(group),false),"Allowed staff rank "+group);
        for(String group:List.of("default","moderator","helper","vip","admin-helper","owner2","*"))Verification.check(!ShardAccess.allowed(List.of(group),false),"Non-staff rank denied "+group);
        Verification.check(ShardAccess.allowed(List.of("admin","owner"),true),"Owner inheritance grants schematic forge");
        Verification.check(!ShardAccess.allowed(List.of("admin","head-admin"),true),"Admin and Head-Admin cannot forge schematic shards");
        Verification.check(!ShardAccess.allowed(List.of(),false),"Missing ranks fail closed");
        System.out.println("PASS: Sponge v2/v3 fixtures, block orientation, air exclusion, coordinate order, NBT rejection and staff/Owner rank matrix.");
    }
    static void reject(Path file,String message)throws Exception{boolean denied=false;try{SchematicData.read(file);}catch(IOException e){denied=true;}Verification.check(denied,message);}
    static void named(DataOutputStream out,int type,String key)throws IOException{out.writeByte(type);out.writeUTF(key);}
    static void write(Path file,int version,boolean tile,boolean shortData)throws IOException{
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))){
            named(out,10,"");if(version==3)named(out,10,"Schematic");
            named(out,3,"Version");out.writeInt(version);named(out,3,"DataVersion");out.writeInt(5000);
            for(var e:Map.of("Width",3,"Height",2,"Length",2).entrySet()){named(out,2,e.getKey());out.writeShort(e.getValue());}
            if(version==3)named(out,10,"Blocks");named(out,10,"Palette");
            named(out,3,"minecraft:air");out.writeInt(0);named(out,3,"minecraft:stone");out.writeInt(1);
            named(out,3,"minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]");out.writeInt(2);out.writeByte(0);
            named(out,7,version==3?"Data":"BlockData");byte[] data={1,2,0,0,0,0,0,0,0,0,0,1};out.writeInt(shortData?2:12);out.write(data,0,shortData?2:12);
            named(out,9,"BlockEntities");out.writeByte(10);out.writeInt(tile?1:0);if(tile)out.writeByte(0);
            if(version==3){out.writeByte(0);out.writeByte(0);}out.writeByte(0);
        }
    }
}
