package dev.excedra.itemgen;

import java.util.Random;

/** Deterministic trajectories for three intertwined crystal strands and staggered outward waves. */
final class PrismFlight {
    record Pose(double x,double y,double z,float scale,float rx,float ry,float rz){}
    final double radius,angle,elevation,speed;
    final float size,spinX,spinY,spinZ;
    final int delay,life;
    PrismFlight(int index,long seed,double radius,float sizeMultiplier){
        Random random=new Random(seed+index*7919L);this.radius=radius;
        angle=(index%3)*Math.PI*2/3+(index/3)*.43;elevation=Math.sin(angle*1.8)*.30-.10;
        speed=.22+random.nextDouble()*.20;size=(.60f+random.nextFloat()*.75f)*sizeMultiplier;
        spinX=.035f+random.nextFloat()*.10f;spinY=.05f+random.nextFloat()*.12f;spinZ=.05f+random.nextFloat()*.08f;
        delay=index%3*4;life=42+index%4*4;
    }
    Pose charge(double phase){double a=angle+phase*.8,r=radius*(1.12-.12*phase);return new Pose(Math.cos(a)*r,elevation*radius+Math.sin(phase*Math.PI)*.25,Math.sin(a)*r,(float)(size*Math.min(1,phase*2+.15)),(float)angle,(float)a,(float)(angle*.6));}
    Pose burst(int frame){int t=Math.max(0,frame-delay);Pose origin=charge(1);double a=angle+.8;double outward=speed*t*(1+.002*t);float fade=(float)Math.clamp((life-t)/16.0,0,1);return new Pose(origin.x()+Math.cos(a)*outward,origin.y()+(.12+.06*Math.sin(angle))*t-.002*t*t,origin.z()+Math.sin(a)*outward,size*fade,(float)angle+spinX*t,(float)a+spinY*t,(float)(angle*.6)+spinZ*t);}
    boolean expired(int frame){return frame-delay>=life;}
}
