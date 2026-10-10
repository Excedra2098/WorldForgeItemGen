package dev.excedra.itemgen;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Bounded Sponge v2/v3 reader. Data-bearing entities are rejected rather than lost. */
final class SchematicData {
    record Data(int width,int height,int length,Map<TerrainPlan.Pos,String> states){}
    static Data read(Path file)throws IOException{
        if(Files.size(file)>8*1024*1024)throw new IOException("Schematic exceeds 8 MB.");
        byte[] bytes;try(var gzip=new GZIPInputStream(Files.newInputStream(file))){bytes=gzip.readNBytes(32*1024*1024+1);}
        if(bytes.length>32*1024*1024)throw new IOException("Expanded schematic exceeds 32 MB.");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readUnsignedByte()!=10)throw new IOException("Expected NBT compound.");in.readUTF();
            Map<String,Object> root=compound(in,0);if(root.get("Schematic") instanceof Map<?,?> nested)root=map(nested);
            int version=number(root,"Version");if(version!=2&&version!=3)throw new IOException("Use Sponge .schem version 2 or 3; legacy .schematic is unsupported.");
            int w=number(root,"Width")&65535,h=number(root,"Height")&65535,l=number(root,"Length")&65535;
            if(w<1||h<1||l<1||w>48||l>48||h>64||(long)w*h*l>131072)throw new IOException("Maximum size is 48×64×48 and 131,072 cells.");
            if(root.get("Entities") instanceof List<?> entities&&!entities.isEmpty())throw new IOException("Remove entities before exporting this terrain schematic.");
            Map<String,Object> blocks=version==3?map(root.get("Blocks")):root;
            Object tiles=blocks.get("BlockEntities");if(tiles==null)tiles=root.get("TileEntities");
            if(tiles instanceof List<?> list&&!list.isEmpty())throw new IOException("Block entity data is unsupported. Export terrain without chest contents, signs, spawners or other block entity NBT.");
            Map<String,Object> palette=map(blocks.get("Palette"));Map<Integer,String> lookup=new HashMap<>();
            for(var e:palette.entrySet()){if(!(e.getValue() instanceof Number n)||n.intValue()<0||lookup.put(n.intValue(),e.getKey())!=null)throw new IOException("Invalid palette.");}
            Object payload=blocks.get(version==3?"Data":"BlockData");if(!(payload instanceof byte[] encoded))throw new IOException("Missing block data.");
            Map<TerrainPlan.Pos,String> states=new LinkedHashMap<>();int cursor=0;
            for(int i=0;i<w*h*l;i++){
                int value=0,shift=0,b;do{if(cursor>=encoded.length||shift>=35)throw new IOException("Invalid block varint.");b=encoded[cursor++]&255;value|=(b&127)<<shift;shift+=7;}while((b&128)!=0);
                String state=lookup.get(value);if(state==null)throw new IOException("Missing palette entry.");
                if(!state.equals("minecraft:air")&&!state.equals("minecraft:cave_air")&&!state.equals("minecraft:void_air"))states.put(new TerrainPlan.Pos(i%w-w/2,i/(w*l),i/w%l-l/2),state);
                if(states.size()>50000)throw new IOException("Maximum 50,000 non-air blocks.");
            }
            if(cursor!=encoded.length||states.isEmpty())throw new IOException("Empty or malformed schematic.");
            return new Data(w,h,l,Collections.unmodifiableMap(new LinkedHashMap<>(states)));
        }catch(ClassCastException|IllegalArgumentException e){throw new IOException("Malformed schematic.",e);}
    }
    static Map<String,Object> map(Object o)throws IOException{if(!(o instanceof Map<?,?> m))throw new IOException("Missing schematic compound.");Map<String,Object> r=new LinkedHashMap<>();for(var e:m.entrySet())r.put((String)e.getKey(),e.getValue());return r;}
    static int number(Map<String,Object> m,String key)throws IOException{if(!(m.get(key) instanceof Number n))throw new IOException("Missing "+key);return n.intValue();}
    static int length(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IOException("NBT array/list too large.");return n;}
    static Map<String,Object> compound(DataInputStream in,int depth)throws IOException{if(depth>32)throw new IOException("NBT nesting too deep.");Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<100000;i++){int type=in.readUnsignedByte();if(type==0)return m;String key=in.readUTF();if(m.put(key,tag(in,type,depth+1))!=null)throw new IOException("Duplicate NBT tag.");}throw new IOException("Too many NBT tags.");}
    static Object tag(DataInputStream in,int type,int depth)throws IOException{
        if(depth>32)throw new IOException("NBT nesting too deep.");
        return switch(type){case 1->in.readByte();case 2->in.readShort();case 3->in.readInt();case 4->in.readLong();case 5->in.readFloat();case 6->in.readDouble();
            case 7->{int n=length(in,32*1024*1024);byte[] b=new byte[n];in.readFully(b);yield b;}case 8->in.readUTF();
            case 9->{int t=in.readUnsignedByte(),n=length(in,131072);List<Object> list=new ArrayList<>();for(int i=0;i<n;i++)list.add(tag(in,t,depth+1));yield list;}
            case 10->compound(in,depth);case 11->{int n=length(in,131072);int[] a=new int[n];for(int i=0;i<n;i++)a[i]=in.readInt();yield a;}
            case 12->{int n=length(in,131072);long[] a=new long[n];for(int i=0;i<n;i++)a[i]=in.readLong();yield a;}
            default->throw new IOException("Unsupported NBT type.");};
    }
}
