package dev.excedra.itemgen;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Template placement coordinates are independent of mutable player home coordinates. */
final class CoreTemplateAnchor {
    record Target(int x,Integer y,int z,String template,boolean marked) {}
    private static Object value(ConfigurationSection config,String path){
        Object value=config.get(path,null);
        if(value==null&&config.getRoot()!=null&&config.getRoot().getDefaults()!=null)value=config.getRoot().getDefaults().get(path,null);
        return value;
    }
    static String key(String value){
        String name=value.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        return name.toLowerCase(Locale.ROOT).replaceFirst("\\.schem$","").replaceFirst(" \\(\\d+\\)$","");
    }
    static Target select(ConfigurationSection config,List<String> identities){
        String base="realmcore.attachment";
        for(String identity:identities){
            String normalized=key(identity);String path=base+".templates."+normalized;
            if(!(value(config,path) instanceof ConfigurationSection))continue;
            Object x=value(config,path+".x"),y=value(config,path+".y"),z=value(config,path+".z");
            if(!(x instanceof Integer)||!(z instanceof Integer))throw new IllegalStateException("Core template "+normalized+" requires integer x and z anchors.");
            if(y!=null&&!(y instanceof Integer))throw new IllegalStateException("Core template "+normalized+" requires an integer floor y, or omit y for its marker.");
            return new Target((Integer)x,(Integer)y,(Integer)z,normalized,Boolean.TRUE.equals(value(config,path+".marked-pad")));
        }
        return new Target(config.getInt(base+".x",0),config.contains(base+".y")?config.getInt(base+".y"):null,config.getInt(base+".z",0),null,false);
    }
    interface Column {boolean marker(int y);boolean lantern(int y);}
    static int markedFloor(Column column,int min,int max){
        Integer found=null;
        for(int y=min+1;y<max;y++)if(column.marker(y)&&column.lantern(y-1)){
            if(found!=null)throw new IllegalStateException("Multiple Core pad markers found; staff must set /realmcore anchor.");
            found=y;
        }
        if(found==null)throw new IllegalStateException("The template's Core pad marker is missing. Staff can repair it or set /realmcore anchor.");
        return found;
    }
}
