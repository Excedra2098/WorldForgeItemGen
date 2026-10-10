package dev.excedra.itemgen;

final class RuptureFlight {
    record Pose(double x,double y,double z,float scale){}
    static Pose pose(int t,double angle,float size,boolean pulse){float fade=(float)Math.clamp((30-t)/12.0,0,1);if(pulse)return new Pose(0,0,0,size*Math.min(1,t/6f)*fade);double r=(.15+.045*(size+.2))*t;return new Pose(Math.cos(angle)*r,.09*t+.001*t*t,Math.sin(angle)*r,size*fade);}
}
