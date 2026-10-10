package dev.excedra.itemgen;
final class WandAbsorption {
    static final int FORM=78,RETURN=96,DURATION=124;
    record Pose(double x,double y,double z,double scale,double absorbed){}
    static Pose pose(int tick,int index){
        double t=Math.clamp((tick-(22+index*18))/18.0,0,1),smooth=t*t*(3-2*t);
        double angle=index*Math.PI*2/3-Math.PI/2+t*.55;
        double radius=1.3*(1-smooth);
        return new Pose(Math.cos(angle)*radius,(index-1)*.3*(1-smooth),Math.sin(angle)*radius,.5*Math.pow(1-smooth,1.25),smooth);
    }
    static double seedScale(int tick){return .18+.2*Math.clamp((tick-22)/54.0,0,1);}
}
