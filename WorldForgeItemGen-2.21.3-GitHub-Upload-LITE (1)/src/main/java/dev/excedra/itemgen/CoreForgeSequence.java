package dev.excedra.itemgen;

import java.util.*;

/** Bounded, block-free basin-to-heart ritual. Coordinates are relative to the Core. */
final class CoreForgeSequence {
    static final int DURATION=60;
    record Point(double x,double y,double z) {}
    static List<Point> frame(int tick,int design){if(design!=5)return frame(tick);return frame(tick).stream().map(p->new Point(p.x(),p.y()*.45+1.75,(p.z()-.5)*.1+.5)).toList();}
    static List<Point> frame(int tick){
        if(tick<0||tick>DURATION)throw new IllegalArgumentException("Invalid ritual tick");
        List<Point> points=new ArrayList<>();
        if(tick<20){
            double radius=.85-.025*tick;
            for(int n=0;n<16;n++){double a=n*Math.PI/8+tick*.12;points.add(new Point(.5+Math.cos(a)*radius,1.25+Math.sin(a*2)*.12,-3.5+Math.sin(a)*radius));}
        }else if(tick<40){
            double progress=(tick-20)/19.0;
            for(int n=0;n<16;n++){double a=n*Math.PI/8+tick*.15;points.add(new Point(.5+Math.cos(a)*.25,1.3+progress*4,-3.5+progress*4+Math.sin(a)*.25));}
        }else{
            double radius=2.15+(tick-40)*.0025;
            for(int n=0;n<16;n++){double a=n*Math.PI/8+tick*.1;points.add(new Point(.5+Math.cos(a)*radius,5.5+Math.sin(a*2)*.2,.5+Math.sin(a)*radius));}
        }
        return List.copyOf(points);
    }
    static int color(CoreRecipe.Influence influence){return switch(influence){case ROOTS->0x86C56B;case MASONRY->0xB7ABA0;case HIGHLAND->0xA7BCC4;case WATER->0x74DDE8;case CRYSTAL->0xC584ED;};}
}
