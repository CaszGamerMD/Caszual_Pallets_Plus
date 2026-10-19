package com.caszgamermd.palletsplus.transport;
/** Unit-testable geometry shared by guideway movement and placement. */
public final class TransportPath {
    public record Point(double x,double y,double z){}
    public static Point sample(int turn,int rise,int clockwiseQuarterTurns,double t){
        t=Math.clamp(t,0,1);
        double x=0,z=1.5-3*t;
        if(turn!=0){double a=t*Math.PI/2;x=turn*(1.5-1.5*Math.cos(a));z=1.5-1.5*Math.sin(a);}
        for(int i=0;i<Math.floorMod(clockwiseQuarterTurns,4);i++){double old=x;x=-z;z=old;}
        return new Point(x,.26+rise*t,z);
    }
    public static double length(int turn,int rise){return turn==0?Math.sqrt(9+rise*rise):Math.PI*.75;}
    private TransportPath(){}
}
