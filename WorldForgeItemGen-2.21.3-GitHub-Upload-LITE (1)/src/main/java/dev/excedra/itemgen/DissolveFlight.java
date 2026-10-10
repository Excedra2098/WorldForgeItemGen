package dev.excedra.itemgen;

/** Real terrain rises intact, then fractures and narrows into upward crystal wisps. */
final class DissolveFlight {
    record Pose(double x,double y,double z,float size){}
    static Pose body(int t,double angle){double lift=.014*t+.0015*t*t;double drift=Math.max(0,t-10)*.014;return new Pose(Math.cos(angle)*drift,lift,Math.sin(angle)*drift,(float)Math.clamp(1-(t-16)/18.0,0,1));}
    static Pose facet(int t,double angle){double r=.055*t+.0008*t*t;return new Pose(Math.cos(angle+t*.025)*r,.07*t+.001*t*t,Math.sin(angle+t*.025)*r,(float)(.8*Math.clamp((30-t)/14.0,0,1)));}
}
