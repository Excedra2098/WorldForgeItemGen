package dev.excedra.itemgen;

import java.util.*;

/** Validate all grant parameters before any inventory mutation. */
record StarterGrant(String player, List<Integer> themes, int amount) {
    StarterGrant {
        themes=List.copyOf(themes);
        if(player==null||player.isBlank()||themes.isEmpty()||themes.stream().anyMatch(i->i<0||i>=9))throw new IllegalArgumentException("Invalid player or starter theme.");
        if(amount<1||amount>36||themes.size()*amount>36)throw new IllegalArgumentException("Give 1–36 individual shards, or 1–4 complete collections.");
    }
    int slots(){return themes.size()*amount;}
    static int theme(String text){for(int i=0;i<9;i++)if(StarterRealms.IDS[i].equalsIgnoreCase(text)||StarterRealms.NAMES[i].equalsIgnoreCase(text))return i;throw new IllegalArgumentException("Themes: "+String.join(", ",StarterRealms.IDS));}
    static StarterGrant parse(String[] args,String self){
        boolean all=args[0].equalsIgnoreCase("giveall");
        if(!all&&args.length==2){if(self==null)throw new IllegalArgumentException("Console: startershards give <player> <theme> [amount]");return new StarterGrant(self,List.of(theme(args[1])),1);}
        int min=all?2:3;
        if(args.length<min||args.length>min+1)throw new IllegalArgumentException(all?"/startershards giveall <player> [amount]":"/startershards give <player> <theme> [amount]");
        int amount=args.length==min+1?Integer.parseInt(args[min]):1;
        return new StarterGrant(args[1],all?java.util.stream.IntStream.range(0,9).boxed().toList():List.of(theme(args[2])),amount);
    }
}
