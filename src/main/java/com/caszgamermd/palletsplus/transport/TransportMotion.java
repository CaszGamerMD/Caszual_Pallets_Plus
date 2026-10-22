package com.caszgamermd.palletsplus.transport;

/** Minecraft metres = blocks; simulation runs at 20 ticks per second. */
public final class TransportMotion {
    public static final double TRAIN_SPEED=30.0/20,CONVEYOR_SPEED=8.0/20,DRONE_SPEED=45.0/20;
    public static final double TRAIN_ACCELERATION=3.0/(20*20),INDEX_SPEED=2.0/20,STEP=.10;
    public static final int MAX_CARS=10;
    public static double accelerate(double speed){return Math.min(TRAIN_SPEED,Math.max(0,speed)+TRAIN_ACCELERATION);}
    public static double distance(double remaining,double available){return Math.min(STEP,Math.min(remaining,Math.max(0,available)));}
    public static double spacing(double frontLength,double rearLength){return (frontLength+rearLength)*.5+.4;}
    public static int seats(int kind){return kind==1?1:kind==3?8:0;}
    public record Seat(double x,double y,double z){}
    public static Seat seat(int kind,int index){
        if(index<0||index>=seats(kind))throw new IllegalArgumentException("No such seat");
        return kind==1?new Seat(0,.75,1.10):new Seat(index%2==0?-.55:.55,.75,-1.5+(index/2));
    }
    private TransportMotion(){}
}
