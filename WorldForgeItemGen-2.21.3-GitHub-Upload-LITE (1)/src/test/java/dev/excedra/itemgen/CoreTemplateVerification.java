package dev.excedra.itemgen;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CoreTemplateVerification {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejected(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalStateException expected){rejected=true;}check(rejected,message);}
    public static void main(String[] args)throws Exception {
        var config=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        List<String> names=List.of("01_terraced_rune_garden","02_sunrise_lily_basin","03_blossom_pergola_sanctuary","04_riverbridge_rune_garden");
        for(String name:names){
            var byFile=CoreTemplateAnchor.select(config,List.of("starter-alias","C:\\templates\\"+name+" (1).schem"));
            check(byFile.x()==0&&byFile.z()==0&&byFile.y()==null&&byFile.marked()&&byFile.template().equals(name),"Configured template resolves by schematic filename: "+name);
            check(CoreTemplateAnchor.select(config,List.of(name)).equals(byFile),"Template IDs and filenames choose the same pad");
            var schematic=SchematicData.read(Path.of(args[0],name+" (1).schem"));
            check(schematic.width()==47&&schematic.length()==47&&schematic.height()==49,"Audited schematic dimensions");
            var column=new CoreTemplateAnchor.Column(){
                public boolean marker(int y){return schematic.states().getOrDefault(new TerrainPlan.Pos(0,y,0),"").startsWith("minecraft:pink_glazed_terracotta");}
                public boolean lantern(int y){return schematic.states().getOrDefault(new TerrainPlan.Pos(0,y,0),"").equals("minecraft:sea_lantern");}
            };
            int floor=CoreTemplateAnchor.markedFloor(column,0,schematic.height());check(floor==22,"Anchor detects the real pad surface");
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
                check(schematic.states().containsKey(new TerrainPlan.Pos(x,floor,z)),"Pad has an existing floor");
                for(int y=1;y<=9;y++)check(!schematic.states().containsKey(new TerrainPlan.Pos(x,floor+y,z)),"Shrine clearance fits without replacing template blocks");
            }
            for(var p:RealmCoreLayout.attached().keySet())check(p.y()>0&&!schematic.states().containsKey(new TerrainPlan.Pos(p.x(),floor+p.y(),p.z())),"Attachment keeps the original pad and underside untouched");
        }
        var existing=new YamlConfiguration();existing.set("realmcore.attachment.x",12);existing.set("realmcore.attachment.z",-9);existing.setDefaults(config);
        check(CoreTemplateAnchor.select(existing,List.of(names.get(0))).marked(),"Upgrades use bundled template defaults with an existing config");
        var fallback=CoreTemplateAnchor.select(existing,List.of("unrelated-template"));check(fallback.x()==12&&fallback.z()==-9&&!fallback.marked(),"Unconfigured templates retain the existing global anchor");
        existing.set("realmcore.attachment.templates."+names.get(0)+".y",150);
        check(CoreTemplateAnchor.select(existing,List.of(names.get(0))).y()==150,"Explicit template floor is supported");
        rejected(()->CoreTemplateAnchor.markedFloor(new CoreTemplateAnchor.Column(){public boolean marker(int y){return false;}public boolean lantern(int y){return true;}},0,50),"Missing marker cannot silently move the Core");
        rejected(()->CoreTemplateAnchor.markedFloor(new CoreTemplateAnchor.Column(){public boolean marker(int y){return y==22||y==30;}public boolean lantern(int y){return true;}},0,50),"Ambiguous markers require staff intervention");
        System.out.println("Core template verification passed: "+checks+" checks across all four supplied schematics.");
    }
}
