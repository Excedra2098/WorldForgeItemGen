package dev.excedra.itemgen;
final class WandOrbit {
    record Pose(double x,double y,double z,double angle){}
    static Pose merge(int tick,int index){double t=Math.clamp((tick-16)/56.0,0,1),r=1.35*(1-t*t);double a=index*Math.PI*2/3+tick*.14;return new Pose(Math.cos(a)*r,Math.sin(a*2)*.22*(1-t),Math.sin(a)*r,a);}
    static Pose unmerge(int tick,int index){double t=Math.clamp(tick/28.0,0,1),r=1.25*(t*t*(3-2*t)),a=index*Math.PI*2/3+tick*.08;return new Pose(Math.cos(a)*r,Math.sin(a*2)*.15*t,Math.sin(a)*r,a);}
}
